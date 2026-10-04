package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Invalid observation DTOs must not advance readiness; they are not rendered frame evidence. */
final class NativeBadgeInputBoundary {
    private record Input(String name, UiSnapshot value) {}

    static void verify(Minecraft minecraft, Class<?> type, Map<?, ?> checks, Path output) throws Exception {
        verify(minecraft, type, checks, output, false);
    }

    static void verifyPlan(Minecraft minecraft, Class<?> type, Map<?, ?> checks, Path output) throws Exception {
        verify(minecraft, type, checks, output, true);
    }

    private static void verify(Minecraft minecraft, Class<?> type, Map<?, ?> checks, Path output, boolean plan) throws Exception {
        var source = UiObservationStore.latest();
        assertNotNull(source);
        assertTrue(source.rows().stream().filter(row -> row.craftAmount() > 0).count() >= 2);
        var positiveRow = source.rows().stream().filter(row -> row.craftAmount() > 0).findFirst().orElseThrow();
        var inputs = new ArrayList<>(List.of(new Input("missing-observation", null),
                new Input("wrong-screen-identity", copy(source, "invalid-observation-screen", source.rows(), List.of())),
                new Input("empty-rows", copy(source, source.screen(), List.of(), List.of())),
                new Input("one-positive-row", copy(source, source.screen(), List.of(positiveRow), List.of())),
                new Input("panel-used-as-badge", copy(source, source.screen(), source.rows(), List.of(source.gui())))));
        if (plan) {
            assertTrue(source.rows().stream().noneMatch(row -> row.missingAmount() > 0));
            inputs.add(new Input("stocked-plan-without-missing-row", source));
        }
        assertFalse(com.ctux.ae2craftingtime.testdriver.LayoutValidator
                .validateBadges(inputs.get(4).value()).isEmpty(), "Panel input must actually violate badge layout");
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var stageType = Class.forName(type.getName() + "$Stage");
        var active = java.util.Arrays.stream(stageType.getEnumConstants())
                .filter(value -> value.toString().equals(plan ? "PLAN_SORT" : "ACTIVE")).findFirst().orElseThrow();
        var originalChecks = Map.copyOf(checks);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var bytes = Files.readAllBytes(config);
        var scale = minecraft.options.guiScale().get();
        var passed = new ArrayList<String>();
        try {
            for (var methodName : plan ? new String[]{"badgeTick"} : new String[]{"badgeTick", "badgeScaleTick"}) {
                var method = methodName.equals("badgeTick")
                        ? type.getDeclaredMethod(methodName, Minecraft.class, Map.class, java.util.function.Consumer.class)
                        : type.getDeclaredMethod(methodName, Minecraft.class, java.util.function.Consumer.class);
                method.setAccessible(true);
                for (int step : methodName.equals("badgeTick") ? new int[]{1, 3} : new int[]{2}) {
                    for (var input : inputs) {
                        var flow = constructor.newInstance("badge-background", DriverOptions.load().world(), output, false);
                        set(type, "phase", flow, active);
                        set(type, "badgeStep", flow, step);
                        set(type, "badgeScaleStep", flow, 2);
                        set(UiObservationStore.class, "latest", null, input.value());
                        java.util.function.Consumer<String> capture = name -> fail("Invalid observation captured success: " + name);
                        var result = methodName.equals("badgeTick") ? method.invoke(flow, minecraft, checks, capture)
                                : method.invoke(flow, minecraft, capture);
                        assertEquals(false, result, methodName + " " + input.name());
                        var stability = field(type, "frames", flow);
                        assertEquals(0, field(stability.getClass(), "count", stability), "Invalid input advanced readiness");
                        assertEquals(step, field(type, "badgeStep", flow));
                        assertEquals(2, field(type, "badgeScaleStep", flow));
                        assertNull(field(type, "operation", flow));
                        assertEquals(originalChecks, checks);
                        assertSame(screen, minecraft.screen);
                        assertSame(menu, minecraft.player.containerMenu);
                        assertEquals(scale, minecraft.options.guiScale().get());
                        assertArrayEquals(bytes, Files.readAllBytes(config));
                        passed.add(methodName + "/" + step + "/" + input.name());
                    }
                }
            }
            assertEquals(plan ? 12 : 15, passed.size());
            var evidence = new com.google.gson.JsonObject();
            evidence.addProperty("scope", "invalid DTO guard inputs plus the original stocked plan; altered inputs are not rendered frames");
            evidence.addProperty("originalFrame", source.frame());
            evidence.add("cases", new com.google.gson.Gson().toJsonTree(passed));
            evidence.add("inputs", new com.google.gson.Gson().toJsonTree(inputs));
            Files.writeString(output.resolve(plan ? "invalid-plan-badge-observation-inputs.json" : "invalid-badge-observation-inputs.json"), evidence.toString());
        } finally {
            set(UiObservationStore.class, "latest", null, source);
        }
    }

    private static UiSnapshot copy(UiSnapshot source, String screen, List<UiSnapshot.Row> rows,
            List<com.ctux.ae2craftingtime.testdriver.Rect> badges) {
        return new UiSnapshot(screen, source.menu(), source.gui(), source.screenWidth(), source.screenHeight(),
                source.guiScale(), source.frame(), source.scroll(), rows, source.text(), badges, source.widgets(),
                source.itemCells(), source.tooltip(), source.cpuCards(), source.rawCpuSerials());
    }
}
