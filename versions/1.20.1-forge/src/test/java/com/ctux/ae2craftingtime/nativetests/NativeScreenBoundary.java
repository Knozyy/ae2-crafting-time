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

/** Native stages must wait when the currently open real menu belongs to another flow. */
final class NativeScreenBoundary {
    private final boolean planScreen;
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final ArrayList<Object> flows = new ArrayList<>();
    private final List<String> phases;
    private long started;

    NativeScreenBoundary(boolean planScreen) {
        this.planScreen = planScreen;
        phases = planScreen ? List.of("ACTIVE", "STATUS_AMOUNTS", "STATUS_ADDON_AMOUNTS",
                "STATUS_SCALES", "STATUS_OPTIONS", "STATUS_PERSIST") : List.of("PLAN_SORT");
    }

    boolean tick(Minecraft minecraft, Class<?> type, Object marker, Map<?, ?> checks, Path output) throws Exception {
        if (started == 0) started = System.nanoTime();
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Wrong-menu checks exceeded their deadline");
        var source = UiObservationStore.latest();
        if (source == null || !source.screen().equals(minecraft.screen.getClass().getName())) return false;
        if (!originals.isEmpty() && source.frame() == originals.get(originals.size()-1).frame()) return false;
        Class<?> expectedScreen = planScreen ? appeng.client.gui.me.crafting.CraftConfirmScreen.class
                : appeng.client.gui.me.crafting.CraftingStatusScreen.class;
        assertInstanceOf(expectedScreen, minecraft.screen);
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var stageType = Class.forName(type.getName() + "$Stage");
        if (flows.isEmpty()) {
            for (var phase : phases) {
                var flow = constructor.newInstance(planScreen ? "standard-status-controls" : "recurrent-plan",
                        DriverOptions.load().world(), output, false);
                set(type, "phase", flow, java.util.Arrays.stream(stageType.getEnumConstants())
                        .filter(value -> value.toString().equals(phase)).findFirst().orElseThrow());
                flows.add(flow);
            }
        }
        var tick = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        tick.setAccessible(true);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var before = Map.copyOf(checks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var bytes = Files.readAllBytes(config);
        boolean ready = true;
        for (int i=0;i<flows.size();i++) {
            var flow = flows.get(i);
            assertEquals(false, tick.invoke(flow, minecraft, marker, checks,
                    (java.util.function.Consumer<String>) name -> fail("Wrong menu captured success: " + name),
                    (java.util.function.BiConsumer<Integer, Integer>) (x,y) -> fail("Wrong menu moved the mouse")));
            assertEquals(phases.get(i), field(type,"phase",flow).toString());
            assertNull(field(type,"operation",flow));
            assertFalse((boolean)field(type,"amountPersistOpen",flow));
            assertFalse((boolean)field(type,"amountOptionSaving",flow));
            assertEquals(0,field(type,"quantityCase",flow));
            assertEquals(0,field(type,"addonQuantityCase",flow));
            var stability=field(type,"frames",flow);
            ready &= (int)field(stability.getClass(),"count",stability)
                    >= (int)field(stability.getClass(),"required",stability);
            assertEquals(before,checks);
            assertSame(screen,minecraft.screen);
            assertSame(menu,minecraft.player.containerMenu);
            assertArrayEquals(bytes,Files.readAllBytes(config));
        }
        var badge = type.getDeclaredMethod("badgeTick", Minecraft.class, Map.class, java.util.function.Consumer.class);
        badge.setAccessible(true);
        for (int step : new int[]{0,1,3}) {
            var flow = constructor.newInstance("badge-background", DriverOptions.load().world(), output, false);
            set(type,"phase",flow,java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals(planScreen ? "ACTIVE" : "PLAN_SORT")).findFirst().orElseThrow());
            set(type,"badgeStep",flow,step);
            assertEquals(false,badge.invoke(flow,minecraft,checks,
                    (java.util.function.Consumer<String>) name -> fail("Wrong badge menu captured success: "+name)));
            assertEquals(step,field(type,"badgeStep",flow));
            assertEquals(planScreen ? "ACTIVE" : "PLAN_SORT",field(type,"phase",flow).toString());
            var stability = field(type,"frames",flow);
            assertEquals(0,field(stability.getClass(),"count",stability));
            assertFalse((boolean)field(type,"badgeEditOpen",flow));
            assertNull(field(type,"operation",flow));
        }
        if (planScreen) {
            var scale = type.getDeclaredMethod("badgeScaleTick",Minecraft.class,java.util.function.Consumer.class);
            scale.setAccessible(true);
            var flow = constructor.newInstance("badge-background",DriverOptions.load().world(),output,false);
            set(type,"badgeScaleStep",flow,2);
            assertEquals(false,scale.invoke(flow,minecraft,
                    (java.util.function.Consumer<String>) name -> fail("Wrong scale menu captured success: "+name)));
            assertEquals(2,field(type,"badgeScaleStep",flow));
            assertNull(field(type,"operation",flow));
        }
        assertEquals(before,checks);
        assertSame(screen,minecraft.screen);
        assertSame(menu,minecraft.player.containerMenu);
        assertArrayEquals(bytes,Files.readAllBytes(config));
        originals.add(source);
        if (!ready) return false;
        Files.writeString(output.resolve(planScreen ? "wrong-status-stage-menu-frames.json"
                : "wrong-plan-stage-menu-frames.json"),new com.google.gson.Gson().toJson(Map.of(
                "scope","original native frames; separate deliberately mismatched stages must remain pending",
                "stages",phases,"badgeSteps",List.of(0,1,3),"scaleChecked",planScreen,"frames",originals)));
        return true;
    }
}
