package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.TestDriverRuntime;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Runs boundary inputs against native plans, then requires ordinary scenario recovery. */
final class NativeGalleryBoundaryRunner {
    private final Path output = Path.of(System.getProperty("ae2craftingtime.test.nativeGalleryOutput"));
    private final NativeStatsCoverageBoundary full = new NativeStatsCoverageBoundary(false);
    private final NativeStatsCoverageBoundary partial = new NativeStatsCoverageBoundary(true);
    private final NativeGalleryReadinessBoundary unprofiledPlan = new NativeGalleryReadinessBoundary(false);
    private final NativeGalleryReadinessBoundary profiledPlan = new NativeGalleryReadinessBoundary(true);
    private final NativeGalleryAccuracyBoundary fullAccuracy = new NativeGalleryAccuracyBoundary();
    private final NativeGalleryAccuracyBoundary partialAccuracy = new NativeGalleryAccuracyBoundary();
    private TestDriverRuntime runtime;
    private Object flow;
    private Object standard;
    private long started;
    private boolean fullDone;
    private boolean partialDone;
    private boolean unprofiledDone;
    private boolean profiledDone;
    private boolean fullAccuracyDone;
    private boolean partialAccuracyDone;
    private boolean finished;

    NativeGalleryBoundaryRunner() {
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
                assertTrue(Boolean.getBoolean("ae2craftingtime.test.observeConnection"));
                var options = DriverOptions.load();
                assertEquals("craft-lifecycle", options.scenario());
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-forge-1.20.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L, "Native gallery run exceeded twenty minutes");
            var type = standard.getClass();
            var snapshot = UiObservationStore.latest();
            var phase = field(type, "phase", standard).toString();
            if (snapshot != null && !unprofiledDone && phase.equals("PLAN_SORT")
                    && !(boolean) field(type, "reviewJob", standard) && unprofiledPlan.ready(standard)) {
                unprofiledDone = unprofiledPlan.tick(minecraft, standard, field(flow.getClass(), "marker", flow),
                        (Map<?, ?>) field(flow.getClass(), "checks", flow), output);
                return;
            }
            if (snapshot != null && !profiledDone && phase.equals("GALLERY_PROFILED_PLAN") && profiledPlan.ready(standard)) {
                profiledDone = profiledPlan.tick(minecraft, standard, field(flow.getClass(), "marker", flow),
                        (Map<?, ?>) field(flow.getClass(), "checks", flow), output);
                return;
            }
            if (snapshot != null && field(type, "phase", standard).toString().equals("GALLERY_DETAILS")) {
                boolean partialJob = (boolean) field(type, "partialJob", standard);
                var boundary = partialJob ? partial : full;
                if (!(partialJob ? partialDone : fullDone) && boundary.ready(minecraft, standard)) {
                    if (!(partialJob ? partialAccuracyDone : fullAccuracyDone)) {
                        boolean done = (partialJob ? partialAccuracy : fullAccuracy).tick(minecraft, standard,
                                field(flow.getClass(), "marker", flow), (Map<?, ?>) field(flow.getClass(), "checks", flow), output);
                        if (partialJob) partialAccuracyDone = done;
                        else fullAccuracyDone = done;
                        return;
                    }
                    boolean done = boundary.tick(minecraft, standard, field(flow.getClass(), "marker", flow),
                            (Map<?, ?>) field(flow.getClass(), "checks", flow), output);
                    if (partialJob) partialDone = done;
                    else fullDone = done;
                    return;
                }
            }
            runtime.tick();
            var resultPath = DriverOptions.load().output().resolve("result.json");
            if (Files.exists(resultPath)) {
                var result = new com.google.gson.Gson().fromJson(Files.readString(resultPath), com.google.gson.JsonObject.class);
                assertEquals("PASS", result.get("result").getAsString(), "Ordinary craft-lifecycle scenario failed");
                assertTrue(fullDone && partialDone && unprofiledDone && profiledDone && fullAccuracyDone && partialAccuracyDone,
                        "Every native gallery boundary must execute");
                Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                        "result", "PASS", "guardCases", 9,
                        "checks", java.util.List.of("unprofiled-readiness", "profiled-readiness", "full-accuracy-expectation",
                            "partial-accuracy-expectation", "full-chat-coverage-guard", "partial-chat-coverage-guard"),
                        "normalResult", result, "runtimeClassSha256", runtimeHash())));
                finished = true;
                runtime.close();
                minecraft.stop();
            }
        } catch (Throwable error) {
            finished = true;
            try { Files.writeString(output.resolve("failure.txt"), error.toString()); }
            catch (Exception secondary) { error.addSuppressed(secondary); }
            minecraft.stop();
            throw new AssertionError("Native gallery boundary failed", error);
        }
    }
}
