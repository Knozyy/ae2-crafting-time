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
final class NativeVariantBoundaryRunner {
    private final Path output = Path.of(System.getProperty("ae2craftingtime.test.nativeVariantOutput"));
    private final NativeVariantGuardBoundary clean = new NativeVariantGuardBoundary(false);
    private final NativeVariantGuardBoundary diagnosed = new NativeVariantGuardBoundary(true);
    private TestDriverRuntime runtime;
    private Object flow;
    private Object standard;
    private long started;
    private boolean cleanDone;
    private boolean diagnosedDone;
    private boolean finished;

    NativeVariantBoundaryRunner() {
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
                assertEquals("stored-variant-plan", options.scenario());
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-forge-1.20.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L, "Native variant run exceeded twenty minutes");
            var type = standard.getClass();
            var snapshot = UiObservationStore.latest();
            if (snapshot != null && field(type, "phase", standard).toString().equals("PLAN_SORT")
                    && (boolean) field(type, "variantHover", standard)) {
                var step = (int) field(type, "variantStep", standard);
                var marker = field(flow.getClass(), "marker", flow);
                var checks = (Map<?, ?>) field(flow.getClass(), "checks", flow);
                if (!cleanDone && step == 0) {
                    cleanDone = clean.tick(minecraft, standard, marker, checks, output);
                    return;
                }
                if (!diagnosedDone && step == 1) {
                    diagnosedDone = diagnosed.tick(minecraft, standard, marker, checks, output);
                    return;
                }
            }
            runtime.tick();
            var resultPath = DriverOptions.load().output().resolve("result.json");
            if (Files.exists(resultPath)) {
                var result = new com.google.gson.Gson().fromJson(Files.readString(resultPath), com.google.gson.JsonObject.class);
                assertEquals("PASS", result.get("result").getAsString(), "Ordinary stored-variant scenario failed");
                assertTrue(cleanDone && diagnosedDone, "Both native guard checkpoints must execute");
                Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                        "result", "PASS", "guardCases", clean.caseCount() + diagnosed.caseCount(),
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
            throw new AssertionError("Native variant boundary failed", error);
        }
    }
}
