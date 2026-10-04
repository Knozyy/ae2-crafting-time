package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Invalid observed quantities must restore the native payload without completing a case. */
final class NativeQuantityObservationBoundary {
    private final boolean addon;
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<List<UiSnapshot>> inputs = new ArrayList<>();
    private final ArrayList<Object> flows = new ArrayList<>();
    private final boolean[] restored = new boolean[4];
    private long started;

    NativeQuantityObservationBoundary() { this(false); }
    NativeQuantityObservationBoundary(boolean addon) { this.addon = addon; }

    boolean tick(Minecraft minecraft, Class<?> type, Object template, Object marker, Map<?, ?> checks, Path output) throws Exception {
        if (started == 0) started = System.nanoTime();
        assertTrue(System.nanoTime() - started < 30_000_000_000L,
                "Invalid quantity observations did not reach their native guards");
        var source = UiObservationStore.latest();
        if (source == null || source.rows().size() != 1) return false;
        var row = source.rows().get(0);
        var addons = addon ? (List<?>) field(type, "addonQuantityCases", template) : List.of();
        var item = addon ? addons.get(0) : null;
        var key = addon ? ((appeng.api.stacks.AEKey) field(item.getClass(), "key", item)).getId().toString()
                : "minecraft:stone";
        long stored = addon ? (long) field(item.getClass(), "stored", item) : 4;
        long active = addon ? (long) field(item.getClass(), "active", item) : 10;
        long pending = addon ? (long) field(item.getClass(), "pending", item) : 200;
        if (!row.outputId().equals(key) || row.storedAmount() != stored
                || row.activeAmount() != active || row.pendingAmount() != pending) return false;
        if (!originals.isEmpty() && originals.get(originals.size() - 1).frame() == source.frame()) return false;
        if (flows.isEmpty()) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            var stageType = Class.forName(type.getName() + "$Stage");
            var phase = java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals(addon ? "STATUS_ADDON_AMOUNTS" : "STATUS_AMOUNTS"))
                    .findFirst().orElseThrow();
            for (int variant = 0; variant < restored.length; variant++) {
                var flow = constructor.newInstance("standard-status-controls", DriverOptions.load().world(), output, false);
                set(type, "phase", flow, phase);
                if (addon) set(type, "addonQuantityCases", flow, addons);
                flows.add(flow);
                inputs.add(new ArrayList<>());
            }
        }
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var before = Map.copyOf(checks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var bytes = Files.readAllBytes(config);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var accessor = (CraftingStatusAccessor) screen;
        var originalPayload = accessor.ae2craftingtime_test_driver$status();
        try {
            for (int variant = 0; variant < restored.length; variant++) {
                if (restored[variant]) continue;
                var badRow = new UiSnapshot.Row(variant == 0 ? "invalid-quantity-output" : row.outputId(),
                        row.craftAmount(), row.missingAmount(), row.cell(), row.description(),
                        row.storedAmount() + (variant == 1 ? 1 : 0),
                        row.activeAmount() + (variant == 2 ? 1 : 0),
                        row.pendingAmount() + (variant == 3 ? 1 : 0));
                var input = new UiSnapshot(source.screen(), source.menu(), source.gui(), source.screenWidth(),
                        source.screenHeight(), source.guiScale(), source.frame(), source.scroll(), List.of(badRow),
                        source.text(), source.badges(), source.widgets(), source.itemCells(), source.tooltip(),
                        source.cpuCards(), source.rawCpuSerials());
                set(UiObservationStore.class, "latest", null, input);
                var payload = accessor.ae2craftingtime_test_driver$status();
                var flow = flows.get(variant);
                assertEquals(false, method.invoke(flow, minecraft, marker, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid quantity captured success: " + name),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Invalid quantity moved the mouse")));
                inputs.get(variant).add(input);
                var after = accessor.ae2craftingtime_test_driver$status();
                var stability = field(type, "frames", flow);
                if (after != payload) {
                    restored[variant] = true;
                    assertTrue(inputs.get(variant).size() >= (int) field(stability.getClass(), "required", stability));
                    assertEquals(0, field(stability.getClass(), "count", stability));
                    var expected = after.getEntries().get(0);
                    assertEquals(key, expected.getWhat().getId().toString());
                    assertEquals(stored, expected.getStoredAmount());
                    assertEquals(active, expected.getActiveAmount());
                    assertEquals(pending, expected.getPendingAmount());
                }
                assertEquals(addon ? "STATUS_ADDON_AMOUNTS" : "STATUS_AMOUNTS", field(type, "phase", flow).toString());
                assertEquals(0, field(type, "quantityCase", flow));
                assertEquals(0, field(type, "addonQuantityCase", flow));
                assertFalse((boolean) field(type, "quantityHovered", flow));
                assertFalse((boolean) field(type, "addonQuantityHovered", flow));
                assertFalse((boolean) field(type, "addonQuantityBadgeCaptured", flow));
                assertNull(field(type, "operation", flow));
                assertEquals(before, checks);
                assertSame(screen, minecraft.screen);
                assertSame(menu, minecraft.player.containerMenu);
                assertArrayEquals(bytes, Files.readAllBytes(config));
            }
        } finally {
            accessor.ae2craftingtime_test_driver$setStatus(originalPayload);
            set(UiObservationStore.class, "latest", null, source);
        }
        originals.add(source);
        for (boolean done : restored) if (!done) return false;
        Files.writeString(output.resolve(addon ? "invalid-addon-quantity-observation-inputs.json"
                : "invalid-quantity-observation-inputs.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", "four deliberately invalid DTO quantity inputs; only originals are native rendered frames",
                "cases", List.of("wrong-output", "wrong-stored", "wrong-active", "wrong-pending"),
                "originals", originals, "inputs", inputs)));
        return true;
    }
}
