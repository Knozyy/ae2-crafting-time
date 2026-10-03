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
    private Path fontPack;
    private Path heldFontPack;
    private int scaleCaptures;
    private Object persistenceFlow;
    private com.ctux.ae2craftingtime.core.ClientConfig persistenceConfig;
    private int persistenceCaptures;
    private boolean finished;
    private long started;
    private NativeStartBoundary startBoundary;

    NativeCraftBoundaryRunner() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Pre event) -> {
            if (startBoundary != null) startBoundary.hold();
            if ("persist-stale-compact".equals(pending)) compactRendering(true);
            if (runtime != null) runtime.beforeRender();
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Post event) -> {
            if (startBoundary != null) startBoundary.render(event);
            if (runtime != null) runtime.afterRender();
            if ("persist-stale-compact".equals(pending)) compactRendering(false);
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
                if (Boolean.getBoolean("ae2craftingtime.test.nativeAddonRows")) {
                    assertTrue(net.minecraftforge.fml.ModList.get().isLoaded("appbot"));
                    assertTrue(net.minecraftforge.fml.ModList.get().isLoaded("appmek"));
                }
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-forge-1.20.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                standardType = standard.getClass();
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L, "Native crafting checks exceeded twenty minutes");
            if (startBoundary == null && !passed.contains("inactive-start")
                    && field(standardType, "phase", standard).toString().equals("SUBMIT")
                    && minecraft.screen instanceof appeng.client.gui.me.crafting.CraftConfirmScreen
                    && UiObservationStore.latest() != null && !UiObservationStore.latest().rows().isEmpty()
                    && UiObservationStore.latest().rows().stream().noneMatch(row -> row.missingAmount() > 0)) {
                var start = minecraft.screen.children().stream()
                        .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                        .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                        .filter(widget -> widget.active && widget.getMessage().getString().equals("Start"))
                        .findFirst();
                if (start.isPresent()) startBoundary = new NativeStartBoundary(minecraft, start.get(), standardType,
                        (Map<?, ?>) field(flow.getClass(), "checks", flow));
            }
            if (startBoundary != null) {
                set(TestDriverRuntime.class, "renderedFrames", null,
                        (long) field(TestDriverRuntime.class, "renderedFrames", null) + 1);
                if (startBoundary.tick(minecraft, field(flow.getClass(), "marker", flow),
                        (Map<?, ?>) field(flow.getClass(), "checks", flow), output)) {
                    passed.add("inactive-start");
                    startBoundary = null;
                }
                return;
            }
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
                assertEquals(Boolean.getBoolean("ae2craftingtime.test.nativeAddonRows") ? 11 : 10,
                        passed.size(), "Every required native fault must execute");
                assertInstanceOf(CraftingStatusScreen.class, minecraft.screen);
                var observed = UiObservationStore.latest();
                assertNotNull(observed);
                UiObservationStore.begin(minecraft);
                minecraft.player.closeContainer();
                assertNull(minecraft.screen, "A native world menu must actually close");
                UiObservationStore.finish(minecraft);
                assertSame(observed, UiObservationStore.latest(), "Closed menu published a stale observation frame");
                passed.add("closed-world-observation");
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
                if (startBoundary != null) startBoundary.restore();
                restoreFontPack();
                if (persistenceConfig != null)
                    com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.apply(persistenceConfig);
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
                || phase.equals("STATUS_ADDON_AMOUNTS") && Boolean.getBoolean("ae2craftingtime.test.nativeAddonRows")
                && (int) field(standardType, "addonQuantityCase", standard) == 0
                || phase.equals("STATUS_SCALES") && (boolean) field(standardType, "quantityScaleSet", standard)
                && field(standardType, "amountFontReload", standard) == null)
                && minecraft.screen instanceof CraftingStatusScreen && !snapshot.rows().isEmpty()
                && !passed.contains(phase)) {
            statusBefore = ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$status();
            assertFalse(statusBefore.getEntries().isEmpty());
            if (phase.equals("STATUS_ADDON_AMOUNTS")) {
                var addons = (List<?>) field(standardType, "addonQuantityCases", standard);
                assertEquals(2, addons.size(), "Both real mana and chemical key fixtures must be available");
                var addon = addons.get(0);
                if (!statusBefore.getEntries().get(0).getWhat().equals(field(addon.getClass(), "key", addon))) return;
            }
            begin(minecraft, phase, snapshot.frame());
            caseBefore = (int) field(standardType, caseField(phase), standard);
            ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$setStatus(
                    new CraftingStatus(true, 0, 0, 0, List.of()));
        } else if (phase.equals("STATUS_OPTIONS") && minecraft.screen instanceof CraftingStatusScreen
                && !passed.contains("persist-compact-on")) {
            begin(minecraft, "persist-compact-on", snapshot.frame());
            persistenceConfig = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().copy();
            assertTrue(persistenceConfig.features().enabled(
                    com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS));
            var constructor = standardType.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            var options = DriverOptions.load();
            persistenceFlow = constructor.newInstance("standard-status-controls", options.world(), output, false);
            var stageType = Class.forName(standardType.getName() + "$Stage");
            var persist = java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals("STATUS_PERSIST")).findFirst().orElseThrow();
            set(standardType, "phase", persistenceFlow, persist);
            set(standardType, "amountPersistSaving", persistenceFlow, true);
        } else if (phase.equals("STATUS_OPTIONS") && minecraft.screen instanceof CraftingStatusScreen
                && passed.contains("persist-incomplete") && !passed.contains("persist-badge-incomplete")) {
            persistenceConfig = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().copy();
            var config = persistenceConfig.copy();
            config.features().setEnabled(com.ctux.ae2craftingtime.core.OptionFeature.BADGE_BACKGROUND, false);
            config.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0x245A7D);
            config.setBadgeOpacity(96);
            com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.apply(config);
            begin(minecraft, "persist-badge-incomplete", snapshot.frame());
            var constructor = standardType.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            persistenceFlow = constructor.newInstance("badge-background", DriverOptions.load().world(), output, false);
            var stageType = Class.forName(standardType.getName() + "$Stage");
            set(standardType, "phase", persistenceFlow, java.util.Arrays.stream(stageType.getEnumConstants())
                    .filter(value -> value.toString().equals("BADGE_PERSIST")).findFirst().orElseThrow());
            set(standardType, "badgePersistSaving", persistenceFlow, true);
            persistenceCaptures = 0;
        } else if (phase.equals("STATUS_OPTIONS") && minecraft.screen instanceof OptionsScreen
                && !(boolean) field(standardType, "amountOptionSaving", standard) && !passed.contains("cancel-before-save")) {
            begin(minecraft, "cancel-before-save", snapshot.frame());
            var click = standardType.getDeclaredMethod("clickOptionButton", Minecraft.class, String.class);
            click.setAccessible(true);
            click.invoke(standard, minecraft, net.minecraft.client.resources.language.I18n.get("gui.cancel"));
            assertInstanceOf(CraftingStatusScreen.class, minecraft.screen);
        } else if (phase.equals("STATUS_SCALES") && minecraft.screen instanceof CraftingStatusScreen
                && (int) field(standardType, "amountFontMode", standard) == 0
                && (int) field(standardType, "quantityScaleCase", standard) == 2
                && (boolean) field(standardType, "quantityScaleSet", standard)
                && field(standardType, "amountFontReload", standard) == null
                && !passed.contains("missing-font-pack")) {
            begin(minecraft, "missing-font-pack", snapshot.frame());
            caseBefore = 2;
            fontPack = minecraft.gameDirectory.toPath().resolve("resourcepacks/ae2ct-status-wide");
            heldFontPack = output.resolve("held-font-pack");
            assertTrue(Files.isDirectory(fontPack), "Native font fixture must exist before its removal");
            assertFalse(Files.exists(heldFontPack));
            Files.move(fontPack, heldFontPack);
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
        com.ctux.ae2craftingtime.core.ClientConfig nextConfig = null;
        try {
            var capture = (java.util.function.Consumer<String>) name -> {
                if (pending.equals("persist-incomplete") || pending.equals("persist-badge-incomplete")) {
                    assertEquals(pending.equals("persist-incomplete") ? "status-saved-off.png" : "badge-saved-off.png", name);
                    persistenceCaptures++;
                } else {
                    if (!pending.equals("missing-font-pack")) fail("Fault advanced to a success capture: " + name);
                    assertEquals("status-scale-default-auto.png", name);
                    scaleCaptures++;
                }
            };
            var mouse = (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> {};
            assertEquals(false, method.invoke(persistenceFlow == null ? standard : persistenceFlow,
                    minecraft, marker, checks, capture, mouse));
            if (pending.startsWith("persist-")) {
                assertEquals(0, persistenceCaptures, "Incomplete status flow must reject before completing a capture");
                if (pending.equals("persist-no-row") || pending.equals("persist-stale-compact")) {
                    var stability = field(standardType, "frames", persistenceFlow);
                    if ((int) field(stability.getClass(), "count", stability)
                            < (int) field(stability.getClass(), "required", stability)) return;
                }
                if (pending.equals("persist-no-row")) {
                    assertTrue(snapshot.rows().isEmpty(), "Persistence must wait for a genuinely absent rendered row");
                    assertFalse((boolean) field(standardType, "amountContinuationWritten", persistenceFlow));
                    assertFalse(Files.exists(output.resolve("status-amounts-continuation.json")));
                    finishFault(minecraft);
                    ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$setStatus(statusBefore);
                    begin(minecraft, "persist-incomplete", snapshot.frame());
                } else if (pending.equals("persist-stale-compact")) {
                    assertFalse(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features().enabled(
                            com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS));
                    assertTrue(snapshot.rows().stream().anyMatch(row -> row.description().stream()
                            .anyMatch(text -> text.key().equals("text.ae2craftingtime.status.amounts"))));
                    assertFalse((boolean) field(standardType, "amountContinuationWritten", persistenceFlow));
                    assertFalse(Files.exists(output.resolve("status-amounts-continuation.json")));
                    finishFault(minecraft);
                    begin(minecraft, "persist-no-row", snapshot.frame());
                    statusBefore = ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$status();
                    assertFalse(statusBefore.getEntries().isEmpty());
                    ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$setStatus(
                            new CraftingStatus(true, 0, 0, 0, List.of()));
                }
            } else if (pending.equals("missing-font-pack")) {
                assertEquals(0, scaleCaptures, "Valid scale capture must reach the missing-pack rejection");
            } else if (!pending.equals("cancel-before-save")) {
                var restored = ((CraftingStatusAccessor) minecraft.screen).ae2craftingtime_test_driver$status();
                if (!restored.getEntries().isEmpty()) {
                    assertTrue(snapshot.rows().isEmpty(), "Driver must restore a genuinely missing rendered row");
                    var expected = statusBefore.getEntries().get(0);
                    var actual = restored.getEntries().get(0);
                    assertEquals(expected.getWhat(), actual.getWhat());
                    assertEquals(expected.getStoredAmount(), actual.getStoredAmount());
                    assertEquals(expected.getActiveAmount(), actual.getActiveAmount());
                    assertEquals(expected.getPendingAmount(), actual.getPendingAmount());
                    assertEquals(caseBefore, field(standardType, caseField(pending), standard));
                    finishFault(minecraft);
                }
            }
        } catch (InvocationTargetException error) {
            assertInstanceOf(IllegalStateException.class, error.getCause());
            if (pending.equals("persist-compact-on")) {
                assertEquals("Compact amounts were not saved off before relaunch", error.getCause().getMessage());
                assertFalse(((Map<?, ?>) checks).values().stream().allMatch(Boolean.TRUE::equals),
                        "Use the genuinely incomplete ordinary check map");
                finishFault(minecraft);
                begin(minecraft, "persist-stale-compact", snapshot.frame());
                var off = persistenceConfig.copy();
                off.features().setEnabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS, false);
                nextConfig = off;
            } else if (pending.equals("persist-incomplete") || pending.equals("persist-badge-incomplete")) {
                var badge = pending.equals("persist-badge-incomplete");
                assertEquals("Cannot relaunch with incomplete " + (badge ? "badge" : "status") + " checks: " + checks,
                        error.getCause().getMessage());
                assertEquals(1, persistenceCaptures);
                assertFalse((boolean) field(standardType, badge ? "badgeContinuationWritten" : "amountContinuationWritten", persistenceFlow));
                assertFalse(Files.exists(output.resolve(badge ? "badge-background-continuation.json" : "status-amounts-continuation.json")));
                finishFault(minecraft);
                nextConfig = persistenceConfig;
                persistenceFlow = null;
            } else if (pending.equals("missing-font-pack")) {
                assertEquals("Disposable uniform-font pack was not staged", error.getCause().getMessage());
                assertEquals(1, scaleCaptures);
                assertEquals(3, field(standardType, "quantityScaleCase", standard));
                assertEquals(0, field(standardType, "amountFontMode", standard));
                assertNull(field(standardType, "amountFontReload", standard));
                assertFalse(minecraft.getResourcePackRepository().getAvailableIds().contains("file/ae2ct-status-wide"));
                restoreFontPack();
                minecraft.getResourcePackRepository().reload();
                assertTrue(minecraft.getResourcePackRepository().getAvailableIds().contains("file/ae2ct-status-wide"));
                set(standardType, "quantityScaleCase", standard, caseBefore);
                finishFault(minecraft);
            } else {
                assertEquals("cancel-before-save", pending);
                assertEquals("Amount options screen closed before save: case=0", error.getCause().getMessage());
                finishFault(minecraft);
                minecraft.setScreen(new OptionsScreen(minecraft.screen));
            }
        } finally {
            assertArrayEquals(configBefore, Files.readAllBytes(configPath(minecraft)), "Fault changed saved options");
            assertEquals(checksBefore, checks, "Fault prematurely marked normal checks");
        }
        if (nextConfig != null) {
            // Verify the rejection before intentionally saving the next native setup.
            com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.apply(nextConfig);
            configBefore = Files.readAllBytes(configPath(minecraft));
            if (pending == null) persistenceConfig = null;
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

    private static void compactRendering(boolean enabled) {
        com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features().setEnabled(
                com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS, enabled);
    }

    private static String caseField(String phase) {
        return switch (phase) {
            case "STATUS_AMOUNTS" -> "quantityCase";
            case "STATUS_ADDON_AMOUNTS" -> "addonQuantityCase";
            case "STATUS_SCALES" -> "quantityScaleCase";
            default -> throw new AssertionError("Unknown native amount phase: " + phase);
        };
    }

    private void restoreFontPack() throws java.io.IOException {
        if (heldFontPack != null && Files.exists(heldFontPack)) Files.move(heldFontPack, fontPack);
    }

}
