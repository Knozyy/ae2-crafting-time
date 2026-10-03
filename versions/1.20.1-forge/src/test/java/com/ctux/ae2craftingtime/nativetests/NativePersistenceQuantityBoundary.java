package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Real native renders of deliberate quantity payloads, not server job-count evidence. */
final class NativePersistenceQuantityBoundary {
    static final List<String> CASES = List.of("persist-scheduled-only", "persist-stored-only", "persist-zero-quantities");
    private final ArrayList<List<UiSnapshot>> evidence = new ArrayList<>();
    private CraftingStatusAccessor accessor;
    private CraftingStatus original;
    private Object current;
    private int index;
    private long injectedAfter;
    private long started;
    private int captures;

    boolean tick(Minecraft minecraft, Class<?> type, Object marker, Map<?, ?> checks, Path output) throws Exception {
        try {
            return check(minecraft, type, marker, checks, output);
        } catch (Exception | Error error) {
            restore();
            throw error;
        }
    }

    private boolean check(Minecraft minecraft, Class<?> type, Object marker, Map<?, ?> checks, Path output) throws Exception {
        var snapshot = UiObservationStore.latest();
        assertNotNull(snapshot);
        if (accessor == null) {
            accessor = (CraftingStatusAccessor) minecraft.screen;
            original = accessor.ae2craftingtime_test_driver$status();
            assertFalse(original.getEntries().isEmpty());
            assertTrue(checks.containsValue(false), "Incomplete checks must block continuation");
            assertFalse(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features().enabled(
                    com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS));
            started = System.nanoTime();
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Quantity boundaries did not stabilize");
        if (current == null) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            current = constructor.newInstance("standard-status-controls", DriverOptions.load().world(), output, false);
            var stageType = Class.forName(type.getName() + "$Stage");
            set(type, "phase", current, java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals("STATUS_PERSIST")).findFirst().orElseThrow());
            set(type, "amountPersistSaving", current, true);
            var entry = original.getEntries().get(0);
            accessor.ae2craftingtime_test_driver$setStatus(new CraftingStatus(true, 0, 0, 0,
                    List.of(new CraftingStatusEntry(900020L + index, entry.getWhat(),
                            index == 1 ? 5 : 0, 0, index == 0 ? 5 : 0))));
            injectedAfter = snapshot.frame();
            captures = 0;
            evidence.add(new ArrayList<>());
            return false;
        }
        var frames = evidence.get(index);
        if (snapshot.frame() <= injectedAfter || !frames.isEmpty()
                && frames.get(frames.size() - 1).frame() == snapshot.frame()) return false;
        assertTrue(snapshot.rows().stream().allMatch(row -> row.activeAmount() == 0));
        if (index < 2) assertTrue(snapshot.rows().stream().anyMatch(row -> row.storedAmount() == (index == 1 ? 5 : 0)
                && row.pendingAmount() == (index == 0 ? 5 : 0)), "Actual native row must match the injected payload");
        else assertTrue(snapshot.rows().stream().noneMatch(row -> row.storedAmount() > 0 || row.pendingAmount() > 0));
        var originalChecks = Map.copyOf(checks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var bytes = Files.readAllBytes(config);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        boolean rejected = false;
        try {
            assertEquals(false, method.invoke(current, minecraft, marker, checks,
                    (java.util.function.Consumer<String>) name -> {
                        assertEquals("status-saved-off.png", name);
                        captures++;
                    }, (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Persistence moved the mouse")));
        } catch (InvocationTargetException error) {
            assertTrue(index < 2, "Zero quantities must remain pending");
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertTrue(error.getCause().getMessage().startsWith("Cannot relaunch with incomplete status checks:"));
            rejected = true;
        }
        frames.add(snapshot);
        var stability = field(type, "frames", current);
        boolean stable = (int) field(stability.getClass(), "count", stability)
                >= (int) field(stability.getClass(), "required", stability);
        assertEquals(index < 2 && stable, rejected);
        assertEquals(rejected ? 1 : 0, captures);
        assertFalse((boolean) field(type, "amountContinuationWritten", current));
        assertFalse(Files.exists(output.resolve("status-amounts-continuation.json")));
        assertNull(field(type, "operation", current));
        assertEquals("STATUS_PERSIST", field(type, "phase", current).toString());
        assertEquals(originalChecks, checks);
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertArrayEquals(bytes, Files.readAllBytes(config));
        if (!stable) return false;
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve(CASES.get(index) + ".png"));
        }
        current = null;
        if (++index < CASES.size()) return false;
        Files.writeString(output.resolve("persistence-quantity-native-frames.json"),
                new com.google.gson.Gson().toJson(Map.of("scope", "actual native renders of deliberate client quantity payloads; not server counts",
                        "cases", CASES, "frames", evidence)));
        restore();
        return true;
    }

    private void restore() {
        if (accessor != null && original != null) accessor.ae2craftingtime_test_driver$setStatus(original);
    }
}
