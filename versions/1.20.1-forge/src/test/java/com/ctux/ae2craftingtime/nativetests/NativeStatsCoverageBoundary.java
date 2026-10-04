package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.mixin.ChatComponentAccessor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Completed-job truth stays on the real server; only the client chat payload is temporarily invalid. */
final class NativeStatsCoverageBoundary {
    private final boolean partial;
    private final ArrayList<Long> frames = new ArrayList<>();
    private Object probe;
    private long completedFrame = -1;

    NativeStatsCoverageBoundary(boolean partial) { this.partial = partial; }

    boolean ready(Minecraft minecraft, Object original) throws Exception {
        var type = original.getClass();
        var stats = field(type, "stats", original);
        return (boolean) field(type, "chatCleared", original)
                && (boolean) field(stats.getClass(), "clicked", stats)
                && ((ChatComponentAccessor) minecraft.gui.getChat()).ae2craftingtime_test_driver$messages().stream()
                    .anyMatch(message -> message.content().getString().contains("minecraft:smooth_stone x1:")
                        && message.content().getString().endsWith("coverage " + (partial ? "1/2" : "2/2")));
    }

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var source = UiObservationStore.latest();
        if (source == null || frames.contains(source.frame())) return false;
        var type = original.getClass();
        var stats = field(type, "stats", original);
        if (probe == null) {
            assertTrue(ready(minecraft, original));
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            probe = constructor.newInstance("craft-lifecycle", DriverOptions.load().world(), output, false);
            for (var name : List.of("fixture", "phase", "partialJob")) set(type, name, probe, field(type, name, original));
            set(type, "chatCleared", probe, true);
            set(type, "stats", probe, stats);
        }
        if (completedFrame >= 0) {
            assertTrue(source.frame() > completedFrame);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(output.resolve(partial ? "stats-partial-coverage-restored.png" : "stats-full-coverage-restored.png"));
            }
            return true;
        }
        var messages = ((ChatComponentAccessor) minecraft.gui.getChat()).ae2craftingtime_test_driver$messages();
        var originals = List.copyOf(messages);
        var deadline = field(stats.getClass(), "nextStatsClick", stats);
        var before = Map.copyOf(ordinaryChecks);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        var inputs = new ArrayList<String>();
        try {
            for (int i = 0; i < messages.size(); i++) {
                var real = messages.get(i);
                var text = real.content().getString();
                if (text.endsWith("coverage " + (partial ? "1/2" : "2/2"))) {
                    var input = text.substring(0, text.lastIndexOf("coverage ")) + "coverage unavailable";
                    inputs.add(input);
                    messages.set(i, new GuiMessage(real.addedTime(), Component.literal(input), real.signature(), real.tag()));
                }
            }
            assertFalse(inputs.isEmpty());
            var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                    java.util.function.Consumer.class, java.util.function.BiConsumer.class);
            method.setAccessible(true);
            frames.add(source.frame());
            var checks = new LinkedHashMap<String, Boolean>();
            try {
                assertEquals(false, method.invoke(probe, minecraft, marker, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid stats coverage captured success"),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Invalid stats coverage moved the mouse")));
            } catch (InvocationTargetException error) {
                var cause = assertInstanceOf(IllegalStateException.class, error.getCause());
                assertEquals("Stats chat did not report the completed job coverage", cause.getMessage());
                assertTrue(frames.size() >= 8);
                completedFrame = source.frame();
                Files.writeString(output.resolve(partial ? "stats-partial-coverage-inputs.json" : "stats-full-coverage-inputs.json"),
                        new com.google.gson.Gson().toJson(Map.of("frames", frames, "sourceMessages", originals.stream()
                            .map(m -> m.content().getString()).toList(), "invalidInputs", inputs,
                            "scope", "real completed job passes original server accuracy predicate; client chat payload restored before draw",
                            "originalSnapshot", source, "ordinaryChecksUnchanged", true, "savedConfigUnchanged", true)));
            }
            assertTrue(checks.isEmpty());
        } finally {
            messages.clear();
            messages.addAll(originals);
            set(stats.getClass(), "nextStatsClick", stats, deadline);
        }
        for (int i = 0; i < messages.size(); i++) assertSame(originals.get(i), messages.get(i));
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertSame(source, UiObservationStore.latest());
        assertEquals(before, ordinaryChecks);
        assertEquals("GALLERY_DETAILS", field(type, "phase", probe).toString());
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        return false;
    }
}
