package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.TestDriverRuntime;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Invalid observation DTOs keep the original native recovery pending. */
final class NativeSuspensionObservationBoundary {
    private final int stage;
    private final List<String> cases;

    NativeSuspensionObservationBoundary() { this(14); }

    NativeSuspensionObservationBoundary(int stage) {
        this.stage = stage;
        cases = switch (stage) {
            case 3 -> List.of("observation-absent", "title-absent", "title-bounds-absent", "title-outside");
            case 14 -> List.of("observation-absent", "screen-mismatch", "stale-suspended-title");
            case 19 -> List.of("control-absent", "control-hidden", "control-inactive");
            case 23 -> List.of("control-absent", "control-inactive");
            default -> List.of("control-absent");
        };
    }
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<UiSnapshot> inputs = new ArrayList<>();
    private final ArrayList<Long> consumed = new ArrayList<>();
    private final long started = System.nanoTime();
    private int index;
    private long lastFrame = -1;

    boolean tick(Minecraft minecraft, TestDriverRuntime runtime, Object standard, Path output) throws Exception {
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Native observation hold exceeded thirty seconds");
        var type = standard.getClass();
        assertEquals(stage, field(type, "suspensionStage", standard));
        assertInstanceOf(appeng.client.gui.me.crafting.CraftingCPUScreen.class, minecraft.screen);
        var source = UiObservationStore.latest();
        if (source == null || !source.screen().equals(minecraft.screen.getClass().getName())
                || source.frame() == lastFrame || source.text().isEmpty()) return false;
        lastFrame = source.frame();
        var nativeMenu = minecraft.player.containerMenu;
        var nativeScreen = minecraft.screen;
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        var name = cases.get(index);
        boolean action = stage != 3 && stage != 14;
        var label = stage == 9 ? "Resume" : stage == 23 ? "Cancel" : "Suspend";
        var button = action ? minecraft.screen.children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> b.getMessage().getString().equals(label))
                .findFirst().orElse(null) : null;
        if (action && (button == null || !button.visible || !button.active)) return false;
        var message = button == null ? null : button.getMessage();
        boolean visible = button != null && button.visible;
        boolean active = button != null && button.active;
        var text = source.text();
        if (stage == 3) {
            var title = text.stream().filter(line -> line.key().equals("gui.ae2craftingtime.suspended"))
                    .findFirst().orElse(null);
            if (title == null || title.bounds() == null || !title.bounds().inside(source.gui())) return false;
            var changed = new ArrayList<UiSnapshot.ObservedText>();
            for (var line : text) {
                if (!line.key().equals("gui.ae2craftingtime.suspended")) { changed.add(line); continue; }
                if (name.equals("title-absent")) continue;
                var bounds = name.equals("title-bounds-absent") ? null
                        : name.equals("title-outside") ? new com.ctux.ae2craftingtime.testdriver.Rect(
                                source.gui().x() + source.gui().width() + 1, line.bounds().y(),
                                line.bounds().width(), line.bounds().height()) : line.bounds();
                changed.add(new UiSnapshot.ObservedText(line.key(), line.rendered(), line.arguments(),
                        bounds, line.color(), line.bold()));
            }
            text = List.copyOf(changed);
        }
        if (name.equals("stale-suspended-title")) {
            var original = text.get(0);
            var changed = new ArrayList<>(text);
            changed.add(new UiSnapshot.ObservedText("gui.ae2craftingtime.suspended", original.rendered(),
                    original.arguments(), original.bounds(), original.color(), original.bold()));
            text = List.copyOf(changed);
        }
        UiSnapshot input = action ? source : name.equals("observation-absent") ? null
                : new UiSnapshot(name.equals("screen-mismatch") ? "missing-native-observation" : source.screen(),
                        source.menu(), source.gui(), source.screenWidth(), source.screenHeight(), source.guiScale(),
                        source.frame(), source.scroll(), source.rows(), text, source.badges(), source.widgets(),
                        source.itemCells(), source.tooltip(), source.cpuCards(), source.rawCpuSerials());
        try {
            if (button != null) {
                if (name.equals("control-absent")) button.setMessage(Component.literal("native test: unavailable control"));
                else if (name.equals("control-hidden")) button.visible = false;
                else button.active = false;
            }
            set(UiObservationStore.class, "latest", null, input);
            var pending = field(type, "operation", standard);
            runtime.tick();
            assertEquals(stage, field(type, "suspensionStage", standard));
            assertSame(nativeMenu, minecraft.player.containerMenu);
            assertSame(nativeScreen, minecraft.screen);
            if (saved == null) assertFalse(Files.exists(config));
            else assertArrayEquals(saved, Files.readAllBytes(config));
            assertInstanceOf(appeng.client.gui.me.crafting.CraftingCPUScreen.class, minecraft.screen);
            originals.add(source);
            inputs.add(input);
            // False early readiness does not prove that the native UI guard ran.
            if (pending instanceof CompletableFuture<?> future && future.isDone()
                    && Boolean.TRUE.equals(future.join()) && field(type, "operation", standard) == null)
                consumed.add(source.frame());
        } finally {
            set(UiObservationStore.class, "latest", null, source);
            if (button != null) {
                button.setMessage(message); button.visible = visible; button.active = active;
                assertSame(message, button.getMessage());
                assertEquals(visible, button.visible); assertEquals(active, button.active);
            }
        }
        if (consumed.size() < 8) return false;
        Files.writeString(output.resolve((action ? "action-stage-" + stage + "-" : stage == 3 ? "paused-" : "disabled-") + name + "-inputs.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", action ? "actual native control inputs restored before draw; original successful server predicates"
                        : "actual native CPU frames and separate invalid observation DTOs; DTOs are not rendered frames",
                "originals", originals, "inputs", inputs, "consumedServerPredicateFrames", consumed,
                "stage", stage, "nativeMenuPreserved", true)));
        originals.clear(); inputs.clear(); consumed.clear();
        index++;
        return index == cases.size();
    }
}
