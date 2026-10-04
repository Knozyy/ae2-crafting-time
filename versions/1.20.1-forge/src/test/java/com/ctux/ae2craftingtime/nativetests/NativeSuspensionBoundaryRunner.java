package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.ctux.ae2craftingtime.mc1201.OptionsScreen;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.TestDriverRuntime;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Exercises actual scenario waits while preserving native furnace inputs, provider patterns and job UUIDs. */
final class NativeSuspensionBoundaryRunner {
    private final Path output = Path.of(System.getProperty("ae2craftingtime.test.nativeSuspensionOutput"));
    private final ArrayList<String> passed = new ArrayList<>();
    private TestDriverRuntime runtime;
    private Object flow;
    private Object standard;
    private long started;
    private boolean finished;
    private Screen optionsParent;
    private byte[] optionsSaved;
    private long optionsFrame = -1;
    private int optionsFrames;
    private boolean closeReplacementMenu;
    private InputsHold hold;

    NativeSuspensionBoundaryRunner() {
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
                assertEquals("crafting-suspension", options.scenario());
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-forge-1.20.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L, "Native suspension exceeded twenty minutes");
            var type = standard.getClass();
            int stage = (int) field(type, "suspensionStage", standard);
            if (hold != null) {
                if (hold.tick(minecraft, runtime, standard, output)) {
                    passed.add("inputs-pending-" + hold.stage);
                    if (hold.stage == 21) closeReplacementMenu = true;
                    hold = null;
                }
                return;
            }
            if ((stage == 21 || stage == 27) && !passed.contains("inputs-pending-" + stage)) {
                assertNull(field(type, "operation", standard));
                hold = new InputsHold(minecraft, standard, stage);
                return;
            }
            if (closeReplacementMenu) {
                assertEquals(21, stage);
                minecraft.player.closeContainer();
                assertNull(minecraft.screen);
                closeReplacementMenu = false;
                passed.add("replacement-menu-closed");
                return; // The original flow must open its actual CPU again on the next native tick.
            }
            if (stage == 14 && !passed.contains("disabled-options-pending")) {
                var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
                if (optionsParent == null) {
                    assertInstanceOf(appeng.client.gui.me.crafting.CraftingCPUScreen.class, minecraft.screen);
                    optionsParent = minecraft.screen;
                    optionsSaved = Files.exists(config) ? Files.readAllBytes(config) : null;
                    minecraft.setScreen(new OptionsScreen(optionsParent));
                    return;
                }
                assertInstanceOf(OptionsScreen.class, minecraft.screen);
                runtime.tick();
                assertEquals(14, field(type, "suspensionStage", standard));
                var snapshot = UiObservationStore.latest();
                if (snapshot != null && snapshot.screen().equals(minecraft.screen.getClass().getName())
                        && snapshot.frame() != optionsFrame) {
                    optionsFrame = snapshot.frame();
                    optionsFrames++;
                }
                if (optionsFrames < 8) return;
                capture(minecraft, "disabled-options-pending", Map.of("snapshot", snapshot, "stage", stage));
                var cancel = minecraft.screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().equals("Cancel"))
                        .findFirst().orElseThrow();
                var platform = Class.forName("com.ctux.ae2craftingtime.testdriver.DriverPlatform");
                var click = platform.getDeclaredMethod("click", Minecraft.class, int.class, int.class);
                click.setAccessible(true);
                click.invoke(null, minecraft, cancel.getX() + 4, cancel.getY() + 4);
                assertSame(optionsParent, minecraft.screen);
                if (optionsSaved == null) assertFalse(Files.exists(config));
                else assertArrayEquals(optionsSaved, Files.readAllBytes(config));
                passed.add("disabled-options-pending");
                return;
            }
            runtime.tick();
            var resultPath = DriverOptions.load().output().resolve("result.json");
            if (Files.exists(resultPath)) {
                var result = new com.google.gson.Gson().fromJson(Files.readString(resultPath), com.google.gson.JsonObject.class);
                assertEquals("PASS", result.get("result").getAsString(), "Ordinary suspension recovery failed");
                assertEquals(4, passed.size());
                Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                        "result", "PASS", "checks", passed, "normalResult", result, "runtimeClassSha256", runtimeHash())));
                finished = true;
                runtime.close();
                minecraft.stop();
            }
        } catch (Throwable error) {
            finished = true;
            try {
                if (hold != null) hold.cleanup(minecraft).get(5, TimeUnit.SECONDS);
                if (optionsParent != null && minecraft.screen instanceof OptionsScreen) minecraft.setScreen(optionsParent);
                Files.writeString(output.resolve("failure.txt"), error.toString());
            } catch (Exception secondary) { error.addSuppressed(secondary); }
            minecraft.stop();
            throw new AssertionError("Native suspension boundary failed", error);
        }
    }

    private void capture(Minecraft minecraft, String name, Object receipt) throws Exception {
        Files.writeString(output.resolve(name + ".json"), new com.google.gson.Gson().toJson(receipt));
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve(name + ".png"));
        }
    }

    private final class InputsHold {
        private final int stage;
        private final Object fixture;
        private final BlockPos terminal;
        private final java.lang.reflect.Method stateMethod;
        private final ArrayList<FurnaceBlockEntity> furnaces = new ArrayList<>();
        private final ArrayList<PatternProviderBlockEntity> providers = new ArrayList<>();
        private final ArrayList<ItemStack> inputs = new ArrayList<>();
        private final ArrayList<ItemStack> patterns = new ArrayList<>();
        private final long started = System.nanoTime();
        private CompletableFuture<Boolean> removal;
        private CompletableFuture<Boolean> guard;
        private CompletableFuture<Boolean> restoration;
        private Object before;
        private Object held;
        private Object restored;

        InputsHold(Minecraft minecraft, Object standard, int stage) throws Exception {
            this.stage = stage;
            fixture = field(standard.getClass(), "fixture", standard);
            terminal = (BlockPos) field(fixture.getClass(), "terminal", fixture);
            stateMethod = fixture.getClass().getDeclaredMethod("suspensionState", ServerPlayer.class, int.class);
            stateMethod.setAccessible(true);
        }

        boolean tick(Minecraft minecraft, TestDriverRuntime runtime, Object standard, Path output) throws Exception {
            assertTrue(System.nanoTime() - started < 30_000_000_000L, "Native input hold exceeded thirty seconds");
            var type = standard.getClass();
            assertEquals(stage, field(type, "suspensionStage", standard));
            if (removal == null) {
                removal = minecraft.getSingleplayerServer().submit(() -> {
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    before = stateMethod.invoke(fixture, player, 0);
                    assertTrue((boolean) field(before.getClass(), "busy", before));
                    if ((int) field(before.getClass(), "furnaceInput", before) == 0) return false;
                    for (int offset : new int[]{4, 8}) {
                        var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset));
                        var furnace = (FurnaceBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset).below());
                        assertNotNull(provider); assertNotNull(furnace);
                        providers.add(provider); furnaces.add(furnace);
                        patterns.add(provider.getLogic().getPatternInv().getStackInSlot(0));
                        inputs.add(furnace.getItem(0));
                        provider.getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
                        provider.getLogic().updatePatterns();
                        furnace.setItem(0, ItemStack.EMPTY);
                        furnace.setChanged();
                    }
                    held = stateMethod.invoke(fixture, player, 0);
                    assertEquals(0, field(held.getClass(), "furnaceInput", held));
                    assertEquals(field(before.getClass(), "jobId", before), field(held.getClass(), "jobId", held));
                    assertTrue((boolean) field(held.getClass(), "busy", held));
                    return true;
                });
                return false;
            }
            if (!removal.isDone()) return false;
            if (!removal.get()) { removal = null; return false; }
            if (guard == null) {
                runtime.tick();
                @SuppressWarnings("unchecked") var future = (CompletableFuture<Boolean>) field(type, "operation", standard);
                assertNotNull(future, "Original scenario did not schedule its actual server predicate");
                guard = future;
                return false;
            }
            if (!guard.isDone()) return false;
            assertFalse(guard.get(), "Actual furnace-input absence must keep the original scenario pending");
            if (field(type, "operation", standard) != null) {
                runtime.tick();
                assertNull(field(type, "operation", standard));
                assertEquals(stage, field(type, "suspensionStage", standard));
            }
            if (restoration == null) { restoration = cleanup(minecraft); return false; }
            if (!restoration.isDone()) return false;
            assertTrue(restoration.get());
            capture(minecraft, "inputs-pending-" + stage, Map.of("before", before, "held", held, "restored", restored,
                    "originalPredicate", false, "sameJob", true, "restoredInputs", true,
                    "scope", "native server inventory and job assertions; screenshot does not prove server-only counts"));
            return true;
        }

        CompletableFuture<Boolean> cleanup(Minecraft minecraft) {
            return minecraft.getSingleplayerServer().submit(() -> {
                for (int i = 0; i < inputs.size(); i++) {
                    assertTrue(furnaces.get(i).getItem(0).isEmpty(), "Native provider dispatched while its pattern was held");
                    furnaces.get(i).setItem(0, inputs.get(i));
                    furnaces.get(i).setChanged();
                    providers.get(i).getLogic().getPatternInv().setItemDirect(0, patterns.get(i));
                    providers.get(i).getLogic().updatePatterns();
                }
                if (before != null && !inputs.isEmpty()) {
                    var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(minecraft.player.getUUID());
                    restored = stateMethod.invoke(fixture, player, 0);
                    assertEquals(field(before.getClass(), "furnaceInput", before), field(restored.getClass(), "furnaceInput", restored));
                    assertEquals(field(before.getClass(), "jobId", before), field(restored.getClass(), "jobId", restored));
                    inputs.clear(); patterns.clear();
                }
                return true;
            });
        }
    }
}
