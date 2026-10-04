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
    enum Mode { ITEM, ADDON, SCALE }
    private final boolean addon;
    private final boolean scale;
    private final List<String> cases;
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<List<UiSnapshot>> inputs = new ArrayList<>();
    private final ArrayList<Object> flows = new ArrayList<>();
    private final boolean[] restored;
    private long started;

    NativeQuantityObservationBoundary() { this(Mode.ITEM); }
    NativeQuantityObservationBoundary(Mode mode) {
        addon = mode == Mode.ADDON;
        scale = mode == Mode.SCALE;
        cases = scale ? List.of("wrong-stored", "wrong-active", "wrong-pending", "zero-scale", "wrong-requested-scale")
                : addon ? List.of("wrong-output", "wrong-stored", "wrong-active", "wrong-pending", "missing-tooltip", "capture-delay")
                : List.of("wrong-output", "wrong-stored", "wrong-active", "wrong-pending", "missing-tooltip");
        restored = new boolean[cases.size()];
    }

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
        long stored = addon ? (long) field(item.getClass(), "stored", item) : scale ? 1_000_000_000L : 4;
        long active = addon ? (long) field(item.getClass(), "active", item) : scale ? 2_000_000_000L : 10;
        long pending = addon ? (long) field(item.getClass(), "pending", item) : scale ? 3_000_000_000L : 200;
        if (scale && source.guiScale() != 1) return false;
        if (!row.outputId().equals(key) || row.storedAmount() != stored
                || row.activeAmount() != active || row.pendingAmount() != pending) return false;
        if (!originals.isEmpty() && originals.get(originals.size() - 1).frame() == source.frame()) return false;
        if (flows.isEmpty()) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            var stageType = Class.forName(type.getName() + "$Stage");
            var phase = java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals(scale ? "STATUS_SCALES" : addon ? "STATUS_ADDON_AMOUNTS" : "STATUS_AMOUNTS"))
                    .findFirst().orElseThrow();
            for (int variant = 0; variant < restored.length; variant++) {
                var flow = constructor.newInstance("standard-status-controls", DriverOptions.load().world(), output, false);
                set(type, "phase", flow, phase);
                if (addon) set(type, "addonQuantityCases", flow, addons);
                if (scale) set(type, "quantityScaleSet", flow, true);
                if (!scale && variant >= 4) {
                    if (addon) {
                        set(type, "addonQuantityBadgeCaptured", flow, true);
                        set(type, "addonQuantityHovered", flow, variant == 4);
                        if (variant == 5) set(type, "addonQuantityBadgeFrame", flow,
                                (long) field(com.ctux.ae2craftingtime.testdriver.TestDriverRuntime.class,
                                        "renderedFrames", null) + 2);
                    } else set(type, "quantityHovered", flow, true);
                }
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
                var badRow = new UiSnapshot.Row(!scale && variant == 0 ? "invalid-quantity-output" : row.outputId(),
                        row.craftAmount(), row.missingAmount(), row.cell(), row.description(),
                        row.storedAmount() + (variant == (scale ? 0 : 1) ? 1 : 0),
                        row.activeAmount() + (variant == (scale ? 1 : 2) ? 1 : 0),
                        row.pendingAmount() + (variant == (scale ? 2 : 3) ? 1 : 0));
                var input = new UiSnapshot(source.screen(), source.menu(), source.gui(), source.screenWidth(),
                        source.screenHeight(), scale && variant == 3 ? 0 : scale && variant == 4 ? 2 : source.guiScale(),
                        source.frame(), source.scroll(), List.of(badRow),
                        source.text(), source.badges(), source.widgets(), source.itemCells(), source.tooltip(),
                        source.cpuCards(), source.rawCpuSerials());
                if (!scale && variant >= 4) input = new UiSnapshot(input.screen(), input.menu(), input.gui(),
                        input.screenWidth(), input.screenHeight(), input.guiScale(), input.frame(), input.scroll(),
                        input.rows(), input.text(), input.badges(), input.widgets(), input.itemCells(), List.of(),
                        input.cpuCards(), input.rawCpuSerials());
                set(UiObservationStore.class, "latest", null, input);
                var payload = accessor.ae2craftingtime_test_driver$status();
                var flow = flows.get(variant);
                assertEquals(false, method.invoke(flow, minecraft, marker, checks,
                        (java.util.function.Consumer<String>) name -> fail("Invalid quantity captured success: " + name),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Invalid quantity moved the mouse")));
                inputs.get(variant).add(input);
                var after = accessor.ae2craftingtime_test_driver$status();
                var stability = field(type, "frames", flow);
                if (variant >= (scale ? 3 : 4)) {
                    assertSame(payload, after, "Readiness wait replaced the native quantity payload");
                    restored[variant] = (int) field(stability.getClass(), "count", stability)
                            >= (int) field(stability.getClass(), "required", stability);
                }
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
                assertEquals(scale ? "STATUS_SCALES" : addon ? "STATUS_ADDON_AMOUNTS" : "STATUS_AMOUNTS", field(type, "phase", flow).toString());
                assertEquals(0, field(type, "quantityCase", flow));
                assertEquals(0, field(type, "addonQuantityCase", flow));
                assertEquals(0, field(type, "quantityScaleCase", flow));
                assertEquals(scale, field(type, "quantityScaleSet", flow));
                if (scale) assertEquals(Integer.valueOf(1), minecraft.options.guiScale().get());
                assertEquals(!scale && !addon && variant >= 4, field(type, "quantityHovered", flow));
                assertEquals(addon && variant == 4, field(type, "addonQuantityHovered", flow));
                assertEquals(addon && variant >= 4, field(type, "addonQuantityBadgeCaptured", flow));
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
        Files.writeString(output.resolve(scale ? "invalid-scale-observation-inputs.json" : addon ? "invalid-addon-quantity-observation-inputs.json"
                : "invalid-quantity-observation-inputs.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", "invalid DTO quantities, tooltip or scale inputs and actual capture-delay guard; only originals are native rendered frames",
                "cases", cases,
                "originals", originals, "inputs", inputs)));
        return true;
    }
}
