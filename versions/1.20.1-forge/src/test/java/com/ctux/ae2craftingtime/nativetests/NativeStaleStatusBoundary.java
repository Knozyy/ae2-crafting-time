package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.CaptureEvidence;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Replays unmodified native frames across a real profiling metadata transition. */
final class NativeStaleStatusBoundary {
    private final ArrayList<UiSnapshot> frames = new ArrayList<>();
    private List<?> readiness;

    void observe(Minecraft minecraft) {
        if (frames.size() == 8 || !(minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingStatusScreen)
                || !com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.profilingEnabled()) return;
        var snapshot = UiObservationStore.latest();
        if (snapshot == null || snapshot.rows().stream().noneMatch(row ->
                (row.storedAmount() > 0 || row.activeAmount() > 0 || row.pendingAmount() > 0)
                && row.description().stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.status.amounts")))) return;
        var key = CaptureEvidence.readiness(snapshot);
        if (!key.equals(readiness)) { frames.clear(); readiness = key; }
        if (frames.isEmpty() || frames.get(frames.size() - 1).frame() != snapshot.frame()) frames.add(snapshot);
    }

    void verify(Minecraft minecraft, Class<?> type, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        assertEquals(8, frames.size(), "Eight genuine stable compact frames must precede profiling Off");
        assertFalse(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.profilingEnabled());
        var current = UiObservationStore.latest();
        assertNotNull(current);
        assertTrue(frames.get(7).frame() < current.frame(), "Replay must use older actual frames");
        var screen = minecraft.screen;
        var configPath = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.readAllBytes(configPath);
        var before = Map.copyOf(ordinaryChecks);
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var options = com.ctux.ae2craftingtime.testdriver.DriverOptions.load();
        var flow = constructor.newInstance("standard-status-controls", options.world(), output, false);
        var stageType = Class.forName(type.getName() + "$Stage");
        set(type, "phase", flow, java.util.Arrays.stream(stageType.getEnumConstants())
                .filter(value -> value.toString().equals("STATUS_SERVER_OFF")).findFirst().orElseThrow());
        set(type, "amountServerOffApplied", flow, true);
        var tick = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        tick.setAccessible(true);
        try {
            for (var frame : frames) {
                set(UiObservationStore.class, "latest", null, frame);
                assertEquals(false, tick.invoke(flow, minecraft, marker, ordinaryChecks,
                        (java.util.function.Consumer<String>) name -> fail("Stale compact frame captured Off success: " + name),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Stale compact frame moved the mouse")));
            }
            var stability = field(type, "frames", flow);
            assertTrue((int) field(stability.getClass(), "count", stability)
                    >= (int) field(stability.getClass(), "required", stability));
            assertFalse((boolean) field(type, "amountServerOffCaptured", flow));
            assertNull(field(type, "operation", flow), "Replay submitted unexpected server work");
            assertEquals(before, ordinaryChecks);
            assertSame(screen, minecraft.screen);
            assertArrayEquals(saved, Files.readAllBytes(configPath));
            Files.writeString(output.resolve("genuine-pre-profile-off-frames.json"), new com.google.gson.Gson().toJson(frames));
        } finally {
            set(UiObservationStore.class, "latest", null, current);
        }
    }
}
