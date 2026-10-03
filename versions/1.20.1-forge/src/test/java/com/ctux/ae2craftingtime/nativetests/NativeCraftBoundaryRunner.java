package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.me.crafting.CraftingStatusScreen;
import appeng.menu.me.crafting.CraftingStatus;
import com.ctux.ae2craftingtime.mc1201.OptionsScreen;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.TestDriverRuntime;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Checks native faults, then requires the original runtime's complete scenario result. */
final class NativeCraftBoundaryRunner {
    private final ArrayList<String> passed = new ArrayList<>();
    private final Path output = Path.of(System.getProperty("ae2craftingtime.test.nativeCraftOutput"));
    private TestDriverRuntime runtime;
    private Object flow;
    private Object standard;
    private Class<?> standardType;
    private String pending;
    private long snapshotBefore;
    private long pendingStarted;
    private int caseBefore;
    private Map<?, ?> checksBefore;
    private byte[] configBefore;
    private CraftingStatus statusBefore;
    private boolean finished;
    private long started;

    NativeCraftBoundaryRunner() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Pre event) -> {
            if (runtime != null) runtime.beforeRender();
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Post event) -> {
            if (runtime != null) runtime.afterRender();
        });
    }

    private void tick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        if (runtime == null && (minecraft.level == null || minecraft.player == null
                || minecraft.getSingleplayerServer() == null || minecraft.getOverlay() != null)) return;
        try {
            if (runtime == null) {
                assertTrue(Boolean.getBoolean("ae2craftingtime.test.observeConnection"), "Ordinary runtime must be disabled");
                var options = DriverOptions.load();
                assertEquals("standard-status-controls", options.scenario());
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-forge-1.20.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                standardType = standard.getClass();
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L, "Native crafting checks exceeded twenty minutes");
            if (pending == null) beginFault(minecraft);
            if (pending != null) {
                set(TestDriverRuntime.class, "renderedFrames", null,
                        (long) field(TestDriverRuntime.class, "renderedFrames", null) + 1);
                checkFault(minecraft);
                return;
            }
            runtime.tick();
            var resultPath = DriverOptions.load().output().resolve("result.json");
            if (Files.exists(resultPath)) {
                var result = new com.google.gson.Gson().fromJson(Files.readString(resultPath), com.google.gson.JsonObject.class);
                assertEquals("PASS", result.get("result").getAsString(), "Original scenario failed");
                assertEquals(3, passed.size(), "Every required native fault must execute");
                Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                        "result", "PASS", "checks", passed, "normalResult", result,
                        "runtimeClassSha256", runtimeHash())));
                runtime.close();
                finished = true;
                minecraft.stop();
            }
        } catch (Throwable error) {
            finished = true;
            try {
                Files.createDirectories(output);
                var trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(output.resolve("failure.txt"), trace.toString());
                if (runtime != null) runtime.close();
            } catch (Exception secondary) { error.addSuppressed(secondary); }
            minecraft.stop();
            throw new AssertionError("Native crafting boundary failed", error);
        }
    }

    private void beginFault(Minecraft minecraft) throws Exception {
        var phase = field(standardType, "phase", standard).toString();
        var snapshot = UiObservationStore.latest();
        if (snapshot == null) return;
        if ((phase.equals("STATUS_AMOUNTS") && (int) field(standardType, "quantityCase", standard) == 0
                || phase.equals("STATUS_SCALES") && (boolean) field(standardType, "quantityScaleSet", standard)
                && field(standardType, "amountFontReload", standard) == null)
                && minecraft.screen instanceof CraftingStatusScreen && !snapshot.rows().isEmpty()
                && !passed.contains(phase)) {
            statusBefore = ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$status();
            assertFalse(statusBefore.getEntries().isEmpty());
            begin(minecraft, phase, snapshot.frame());
            caseBefore = (int) field(standardType, phase.equals("STATUS_AMOUNTS") ? "quantityCase" : "quantityScaleCase", standard);
            ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$setStatus(
                    new CraftingStatus(true, 0, 0, 0, List.of()));
        } else if (phase.equals("STATUS_OPTIONS") && minecraft.screen instanceof OptionsScreen
                && !(boolean) field(standardType, "amountOptionSaving", standard) && !passed.contains("cancel-before-save")) {
            begin(minecraft, "cancel-before-save", snapshot.frame());
            var cancel = minecraft.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals(
                            net.minecraft.client.resources.language.I18n.get("gui.cancel"))).findFirst().orElseThrow();
            click(minecraft, cancel);
            assertInstanceOf(CraftingStatusScreen.class, minecraft.screen);
        }
    }

    private void begin(Minecraft minecraft, String name, long frame) throws Exception {
        pending = name;
        pendingStarted = System.nanoTime();
        snapshotBefore = frame;
        checksBefore = Map.copyOf((Map<?, ?>) field(flow.getClass(), "checks", flow));
        configBefore = Files.readAllBytes(configPath(minecraft));
    }

    private void checkFault(Minecraft minecraft) throws Exception {
        assertTrue(System.nanoTime() - pendingStarted < 30_000_000_000L, "Fault did not reach its native guard: " + pending);
        var snapshot = UiObservationStore.latest();
        if (snapshot == null || snapshot.frame() == snapshotBefore) return;
        var marker = field(flow.getClass(), "marker", flow);
        var checks = field(flow.getClass(), "checks", flow);
        var method = standardType.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        try {
            var capture = (java.util.function.Consumer<String>) name -> fail("Fault advanced to a success capture: " + name);
            var mouse = (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> {};
            assertEquals(false, method.invoke(standard, minecraft, marker, checks, capture, mouse));
            if (!pending.equals("cancel-before-save")) {
                var restored = ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$status();
                if (!restored.getEntries().isEmpty()) {
                    assertTrue(snapshot.rows().isEmpty(), "Driver must restore a genuinely missing rendered row");
                    var expected = statusBefore.getEntries().get(0);
                    var actual = restored.getEntries().get(0);
                    assertEquals(expected.getWhat(), actual.getWhat());
                    assertEquals(expected.getStoredAmount(), actual.getStoredAmount());
                    assertEquals(expected.getActiveAmount(), actual.getActiveAmount());
                    assertEquals(expected.getPendingAmount(), actual.getPendingAmount());
                    assertEquals(caseBefore, field(standardType,
                            pending.equals("STATUS_AMOUNTS") ? "quantityCase" : "quantityScaleCase", standard));
                    finishFault(minecraft);
                }
            }
        } catch (InvocationTargetException error) {
            assertEquals("cancel-before-save", pending);
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertEquals("Amount options screen closed before save: case=0", error.getCause().getMessage());
            finishFault(minecraft);
            minecraft.setScreen(new OptionsScreen(minecraft.screen));
        } finally {
            assertArrayEquals(configBefore, Files.readAllBytes(configPath(minecraft)), "Fault changed saved options");
            assertEquals(checksBefore, checks, "Fault prematurely marked normal checks");
        }
    }

    private void finishFault(Minecraft minecraft) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve(pending + ".png"));
        }
        passed.add(pending);
        pending = null;
    }

    private static Path configPath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
    }

    private static void click(Minecraft minecraft, Button button) throws Exception {
        var platform = Class.forName("com.ctux.ae2craftingtime.testdriver.DriverPlatform");
        var click = platform.getDeclaredMethod("click", Minecraft.class, int.class, int.class);
        click.setAccessible(true);
        click.invoke(null, minecraft, button.getX() + 4, button.getY() + 4);
    }
}
