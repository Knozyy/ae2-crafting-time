package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Uses actual profiling-Off renders to reject unremembered text or an incorrect saved appearance. */
final class NativeBadgeObservationBoundary {
    private final ArrayList<UiSnapshot> frames = new ArrayList<>();
    private final ArrayList<Object> flows = new ArrayList<>();

    boolean tick(Minecraft minecraft, Class<?> type, Map<?, ?> checks, Path output) throws Exception {
        var snapshot = UiObservationStore.latest();
        if (snapshot == null || !snapshot.badges().isEmpty()
                || snapshot.rows().stream().filter(row -> row.craftAmount() > 0).count() < 2) return false;
        assertFalse(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.profilingEnabled());
        assertTrue(snapshot.text().stream().anyMatch(text -> text.key().equals("native-status-text")));
        if (!frames.isEmpty() && frames.get(frames.size() - 1).frame() == snapshot.frame()) return false;
        if (flows.isEmpty()) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            var stageType = Class.forName(type.getName() + "$Stage");
            var active = java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals("ACTIVE")).findFirst().orElseThrow();
            var header = snapshot.text().stream().filter(text -> text.key().equals("gui.ae2.CPUs"))
                    .findFirst().orElseThrow();
            for (int variant = 0; variant < 3; variant++) {
                var flow = constructor.newInstance("badge-background", DriverOptions.load().world(), output, false);
                set(type, "phase", flow, active);
                set(type, "badgeStep", flow, 3);
                // A genuine CPU header is not a remembered row badge; never invent text or bounds.
                if (variant == 1) set(type, "badgeTextBefore", flow, List.of(header.key() + ":" + header.bounds()));
                if (variant == 2) {
                    assertTrue(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground());
                    set(type, "badgeRowsBefore", flow, snapshot.rows().stream()
                            .map(row -> row.outputId() + ":" + row.craftAmount()).toList());
                    set(type, "badgeTextBefore", flow, snapshot.text().stream()
                            .filter(text -> text.key().equals("native-status-text")
                                    && snapshot.rows().stream().anyMatch(row -> text.bounds().inside(row.cell())))
                            .map(text -> text.key() + ":" + text.bounds()).toList());
                }
                flows.add(flow);
            }
        }
        var before = Map.copyOf(checks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var bytes = Files.readAllBytes(config);
        var screen = minecraft.screen;
        var tick = type.getDeclaredMethod("badgeTick", Minecraft.class, Map.class, java.util.function.Consumer.class);
        tick.setAccessible(true);
        boolean stable = true;
        for (int variant = 0; variant < flows.size(); variant++) {
            var flow = flows.get(variant);
            boolean rejectedAppearance = false;
            try {
                assertEquals(false, tick.invoke(flow, minecraft, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid badge observation captured success: " + name)));
            } catch (InvocationTargetException error) {
                if (variant != 2) throw error;
                assertInstanceOf(IllegalStateException.class, error.getCause());
                assertEquals("Badge background state differs at ACTIVE step 3", error.getCause().getMessage());
                rejectedAppearance = true;
            }
            var stability = field(type, "frames", flow);
            boolean ready = (int) field(stability.getClass(), "count", stability)
                    >= (int) field(stability.getClass(), "required", stability);
            if (variant == 2) assertEquals(ready, rejectedAppearance);
            stable &= ready;
            assertEquals(3, field(type, "badgeStep", flow));
            assertNull(field(type, "operation", flow));
            assertEquals(before, checks);
            assertSame(screen, minecraft.screen);
            assertArrayEquals(bytes, Files.readAllBytes(config));
        }
        frames.add(snapshot);
        if (!stable) return false;
        Files.writeString(output.resolve("genuine-unremembered-badge-frames.json"), new com.google.gson.Gson().toJson(frames));
        return true;
    }
}
