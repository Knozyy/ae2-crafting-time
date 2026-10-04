package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.core.CraftingRowState;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.LayoutValidator;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Distinguishes real badge renders from a deliberately relabeled observation input. */
final class NativeBadgeNativeTextBoundary {
    private Boolean backgroundBefore;
    private Object screen;
    private Object menu;
    private byte[] saved;
    private Map<?, ?> checksBefore;
    private long started;
    private Object flow;
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<UiSnapshot> inputs = new ArrayList<>();

    boolean tick(Minecraft minecraft, Class<?> type, Map<?, ?> checks, Path output) throws Exception {
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        if (backgroundBefore == null) {
            assertTrue(ClientOptionsRuntime.profilingEnabled());
            backgroundBefore = ClientOptionsRuntime.current().badgeBackground();
            assertFalse(backgroundBefore, "Require the ordinary unchanged saved-Off background");
            screen = minecraft.screen;
            menu = minecraft.player.containerMenu;
            saved = Files.readAllBytes(config);
            checksBefore = Map.copyOf(checks);
            started = System.nanoTime();
            ClientOptionsRuntime.current().features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
            return false;
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Native badge input deadline exceeded");
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertEquals(checksBefore, checks);
        assertArrayEquals(saved, Files.readAllBytes(config));
        var source = UiObservationStore.latest();
        if (source == null || source.badges().isEmpty()
                || source.rows().stream().filter(row -> row.craftAmount() > 0).count() < 2
                || !LayoutValidator.validateBadges(source).isEmpty()) return false;
        if (!originals.isEmpty() && originals.get(originals.size() - 1).frame() == source.frame()) return false;
        if (flow == null) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            flow = constructor.newInstance("badge-background", DriverOptions.load().world(), output, false);
            var stages = Class.forName(type.getName() + "$Stage");
            set(type, "phase", flow, java.util.Arrays.stream(stages.getEnumConstants())
                    .filter(value -> value.toString().equals("ACTIVE")).findFirst().orElseThrow());
            set(type, "badgeStep", flow, 1);
        }
        var outside = source.text().stream().filter(value -> value.bounds() != null
                && source.badges().stream().noneMatch(badge -> value.bounds().inside(badge)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Native text outside badge bounds is absent"));
        var text = source.text().stream().map(value -> value == outside
                || CraftingRowState.isBadge(value.key())
                    && source.badges().stream().anyMatch(badge -> value.bounds().inside(badge))
                ? new UiSnapshot.ObservedText("native-status-text", value.rendered(), value.arguments(),
                        value.bounds(), value.color(), value.bold()) : value).toList();
        assertTrue(text.stream().anyMatch(value -> value.key().equals("native-status-text")
                && source.badges().stream().anyMatch(badge -> value.bounds().inside(badge))));
        assertTrue(text.stream().anyMatch(value -> value.key().equals("native-status-text")
                && source.badges().stream().noneMatch(badge -> value.bounds().inside(badge))));
        var input = new UiSnapshot(source.screen(), source.menu(), source.gui(), source.screenWidth(),
                source.screenHeight(), source.guiScale(), source.frame(), source.scroll(), source.rows(), text,
                source.badges(), source.widgets(), source.itemCells(), source.tooltip(), source.cpuCards(), source.rawCpuSerials());
        var tick = type.getDeclaredMethod("badgeTick", Minecraft.class, Map.class, java.util.function.Consumer.class);
        tick.setAccessible(true);
        boolean rejected = false;
        try {
            set(UiObservationStore.class, "latest", null, input);
            try {
                assertEquals(false, tick.invoke(flow, minecraft, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid badge input captured success: " + name)));
            } catch (InvocationTargetException error) {
                assertInstanceOf(IllegalStateException.class, error.getCause());
                assertEquals("Custom badge appearance was lost", error.getCause().getMessage());
                rejected = true;
            }
        } finally {
            set(UiObservationStore.class, "latest", null, source);
        }
        var stability = field(type, "frames", flow);
        boolean ready = (int) field(stability.getClass(), "count", stability)
                >= (int) field(stability.getClass(), "required", stability);
        assertEquals(ready, rejected);
        assertEquals(1, field(type, "badgeStep", flow));
        assertNull(field(type, "operation", flow));
        assertEquals(checksBefore, checks);
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertArrayEquals(saved, Files.readAllBytes(config));
        if ((int) field(stability.getClass(), "count", stability) == 1) {
            originals.clear();
            inputs.clear();
        }
        originals.add(source);
        inputs.add(input);
        if (!ready) return false;
        restore();
        assertEquals(backgroundBefore, ClientOptionsRuntime.current().badgeBackground());
        Files.writeString(output.resolve("native-text-badge-bounds-inputs.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", "original native badge renders and separately relabeled DTO inputs; inputs are not rendered frames",
                "originals", originals, "inputs", inputs, "backgroundRestored", true, "savedBytesUnchanged", true, "outsideNativeText", outside)));
        return true;
    }

    void restore() {
        if (backgroundBefore != null)
            ClientOptionsRuntime.current().features().setEnabled(OptionFeature.BADGE_BACKGROUND, backgroundBefore);
    }
}
