package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** Holds a real Start control inactive without replacing the plan, CPU or clock. */
final class NativeStartBoundary {
    private final AbstractWidget start;
    private final Object scenario;
    private final Class<?> type;
    private final long begun = System.nanoTime();
    private final ArrayList<Long> replans = new ArrayList<>();
    private final Map<?, ?> originalChecks;
    private final Path config;
    private final byte[] saved;

    NativeStartBoundary(Minecraft minecraft, AbstractWidget start, Class<?> type, Map<?, ?> checks) throws Exception {
        assertTrue(start.active);
        this.start = start;
        this.type = type;
        originalChecks = Map.copyOf(checks);
        config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        if (!Files.exists(config)) com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.apply(
                com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().copy());
        saved = Files.readAllBytes(config);
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var options = DriverOptions.load();
        scenario = constructor.newInstance("badge-background", options.world(), options.output(), false);
        var stage = Class.forName(type.getName() + "$Stage");
        set(type, "phase", scenario, java.util.Arrays.stream(stage.getEnumConstants())
                .filter(value -> value.toString().equals("SUBMIT")).findFirst().orElseThrow());
        set(type, "badgePlanStocked", scenario, true);
        assertEquals(0, field(type, "badgeReplanAttempts", scenario));
        hold();
    }

    void hold() { start.active = false; }

    void restore() { start.active = true; }

    void render(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
        hold();
        start.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    boolean tick(Minecraft minecraft, Object marker, Map<?, ?> checks, Path output) throws Exception {
        assertTrue(System.nanoTime() - begun < 45_000_000_000L, "Start rejection exceeded 45 seconds");
        assertInstanceOf(CraftConfirmScreen.class, minecraft.screen);
        assertTrue(minecraft.screen.children().contains(start), "Native Start widget changed during replanning");
        hold();
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var attempts = (int) field(type, "badgeReplanAttempts", scenario);
        var deadline = (long) field(type, "badgeNextReplanAt", scenario);
        var now = System.nanoTime();
        try {
            assertEquals(false, method.invoke(scenario, minecraft, marker, checks,
                    (java.util.function.Consumer<String>) name -> fail("Inactive Start captured success: " + name),
                    (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> {}));
            var after = (int) field(type, "badgeReplanAttempts", scenario);
            if (after != attempts) {
                assertEquals(attempts + 1, after);
                assertTrue(now >= deadline, "Native replan ran before its real deadline");
                assertTrue(after <= 3);
                replans.add(now);
                assertTrue((long) field(type, "badgeNextReplanAt", scenario) >= now + 10_000_000_000L);
            }
            return false;
        } catch (InvocationTargetException error) {
            assertInstanceOf(IllegalStateException.class, error.getCause());
            var menu = ((CraftConfirmScreen) minecraft.screen).getMenu();
            assertEquals("Crafting Plan stayed partial after supplying input; no CPU=" + menu.hasNoCPU()
                    + ", simulation=" + (menu.getPlan() != null && menu.getPlan().isSimulation()),
                    error.getCause().getMessage());
            assertEquals(3, replans.size());
            assertEquals(4, field(type, "badgeReplanAttempts", scenario));
            assertTrue(now >= deadline);
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(output.resolve("inactive-start.png"));
            }
            Files.writeString(output.resolve("inactive-start.json"), new com.google.gson.Gson().toJson(Map.of(
                    "replansNanoTime", replans, "rejectedNanoTime", now, "lastDeadlineNanoTime", deadline,
                    "diagnostic", error.getCause().getMessage())));
            restore();
            return true;
        } finally {
            assertEquals("SUBMIT", field(type, "phase", scenario).toString());
            assertEquals(originalChecks, checks, "Inactive Start changed ordinary checks");
            assertArrayEquals(saved, Files.readAllBytes(config), "Inactive Start changed saved options");
        }
    }
}
