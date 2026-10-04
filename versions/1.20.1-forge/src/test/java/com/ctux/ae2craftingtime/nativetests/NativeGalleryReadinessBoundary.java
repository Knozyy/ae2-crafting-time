package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Separate observation inputs leave the actual native plan and samples intact. */
final class NativeGalleryReadinessBoundary {
    private final boolean profiled;
    private final ArrayList<Object> probes = new ArrayList<>();
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<List<UiSnapshot>> inputs = new ArrayList<>();
    private long started;
    private long lastFrame = -1;
    private long completedFrame = -1;

    NativeGalleryReadinessBoundary(boolean profiled) { this.profiled = profiled; }

    boolean ready(Object original) throws Exception {
        var source = UiObservationStore.latest();
        if (source == null || !source.tooltip().isEmpty() || source.text().isEmpty()) return false;
        var method = original.getClass().getDeclaredMethod("galleryPlanReady", List.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, source.rows(), profiled ? 1 : 0);
    }

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var source = UiObservationStore.latest();
        if (source == null || source.frame() == lastFrame) return false;
        if (completedFrame >= 0) {
            assertTrue(source.frame() > completedFrame);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(output.resolve(profiled ? "gallery-profiled-readiness-restored.png" : "gallery-unprofiled-readiness-restored.png"));
            }
            return true;
        }
        var type = original.getClass();
        if (probes.isEmpty()) {
            started = System.nanoTime();
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            for (int i = 0; i < (profiled ? 2 : 3); i++) {
                var probe = constructor.newInstance("craft-lifecycle", DriverOptions.load().world(), output, false);
                for (var name : List.of("fixture", "phase", "partialJob")) set(type, name, probe, field(type, name, original));
                probes.add(probe);
                inputs.add(new ArrayList<>());
            }
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Gallery readiness checkpoint exceeded thirty seconds");
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var before = Map.copyOf(ordinaryChecks);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        lastFrame = source.frame();
        originals.add(source);
        boolean stable = true;
        for (int i = 0; i < probes.size(); i++) {
            boolean tooltip = i == probes.size() - 1;
            var rows = source.rows();
            if (!tooltip) {
                rows = !profiled && i == 0 ? rows.stream().filter(r -> r.outputId().equals("minecraft:stone")).toList()
                        : rows.stream().map(r -> new UiSnapshot.Row(r.outputId(), r.craftAmount(), r.missingAmount(),
                            r.cell(), List.of(), r.storedAmount(), r.activeAmount(), r.pendingAmount())).toList();
            }
            var input = new UiSnapshot(source.screen(), source.menu(), source.gui(), source.screenWidth(), source.screenHeight(),
                    source.guiScale(), source.frame(), source.scroll(), rows, source.text(), source.badges(), source.widgets(),
                    source.itemCells(), tooltip ? List.of(source.text().get(0)) : List.of(), source.cpuCards(), source.rawCpuSerials());
            inputs.get(i).add(input);
            var checks = new LinkedHashMap<String, Boolean>();
            var mouse = new ArrayList<List<Integer>>();
            try {
                set(UiObservationStore.class, "latest", null, input);
                assertEquals(false, method.invoke(probes.get(i), minecraft, marker, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid readiness captured success"),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> mouse.add(List.of(x, y))));
            } finally { set(UiObservationStore.class, "latest", null, source); }
            assertTrue(checks.isEmpty());
            assertEquals(field(type, "phase", original), field(type, "phase", probes.get(i)));
            assertTrue(mouse.isEmpty() || !profiled && tooltip && mouse.equals(List.of(List.of(0, 0))));
            var frames = field(type, "frames", probes.get(i));
            stable &= (int) field(frames.getClass(), "count", frames) >= 8;
        }
        assertSame(source, UiObservationStore.latest());
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertEquals(before, ordinaryChecks);
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        if (stable) {
            completedFrame = source.frame();
            Files.writeString(output.resolve(profiled ? "gallery-profiled-readiness-inputs.json" : "gallery-unprofiled-readiness-inputs.json"),
                    new com.google.gson.Gson().toJson(Map.of("originals", originals, "inputs", inputs,
                        "scope", "separate invalid observation DTOs; hover callback outputs recorded, not executed native mouse input",
                        "nativeReferencesRestored", true, "savedConfigUnchanged", true, "ordinaryChecksUnchanged", true)));
        }
        return false;
    }
}
