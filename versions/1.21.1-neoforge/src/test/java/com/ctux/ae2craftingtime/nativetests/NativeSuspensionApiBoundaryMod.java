package com.ctux.ae2craftingtime.nativetests;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.TestDriverRuntime;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Uses an actual NeoForge CPU menu to verify the unavailable Forge-only API. */
@Mod("ae2ct_native_suspension_api_boundary")
public final class NativeSuspensionApiBoundaryMod {
    private final Path output = Path.of(System.getProperty("ae2craftingtime.test.nativeSuspensionApiOutput"));
    private final ArrayList<String> checks = new ArrayList<>();
    private TestDriverRuntime runtime;
    private Object flow;
    private Object standard;
    private long started;
    private long openedAt;
    private long readinessAt;
    private java.util.concurrent.CompletableFuture<Boolean> menuOpening;
    private long cpuRenders;
    private boolean opening;
    private boolean guarded;
    private boolean finished;

    public NativeSuspensionApiBoundaryMod() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) -> {
            if (runtime != null) runtime.beforeRender();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) -> {
            if (runtime != null) runtime.afterRender();
            if (opening && Minecraft.getInstance().screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen)
                cpuRenders++;
        });
    }

    private void tick() {
        if (finished) return;
        var minecraft = Minecraft.getInstance();
        if (runtime == null && (minecraft.level == null || minecraft.player == null
                || minecraft.getSingleplayerServer() == null || minecraft.getOverlay() != null)) return;
        try {
            if (runtime == null) {
                assertTrue(Boolean.getBoolean("ae2craftingtime.test.observeConnection"));
                var options = DriverOptions.load();
                assertEquals("standard-status-controls", options.scenario());
                Files.createDirectories(output);
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-1.2.13-neoforge-1.21.1-test-driver.jar");
                flow = field(TestDriverRuntime.class, "scenario", runtime);
                standard = field(flow.getClass(), "standard", flow);
                started = System.nanoTime();
            }
            assertTrue(System.nanoTime() - started < 1_200_000_000_000L);
            var type = standard.getClass();
            if (!guarded && field(type, "phase", standard).toString().equals("TERMINAL")) {
                assertNull(field(type, "operation", standard));
                if (!opening) {
                    assertNull(minecraft.screen);
                    var fixture = field(type, "fixture", standard);
                    var terminal = (BlockPos) field(fixture.getClass(), "terminal", fixture);
                    var cpu = terminal.west(2);
                    if (readinessAt == 0) readinessAt = System.nanoTime();
                    assertTrue(System.nanoTime() - readinessAt < 30_000_000_000L, "Native client CPU not ready");
                    if (!(minecraft.level.getBlockEntity(cpu) instanceof appeng.blockentity.crafting.CraftingBlockEntity owner)
                            || !owner.isFormed() || !owner.isActive()) return;
                    var id = minecraft.player.getUUID();
                    menuOpening = minecraft.getSingleplayerServer().submit(() -> {
                        var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(id);
                        var actual = (appeng.blockentity.crafting.CraftingBlockEntity) player.serverLevel().getBlockEntity(cpu);
                        assertTrue(actual.isFormed() && actual.isActive());
                        return appeng.menu.MenuOpener.open(appeng.menu.me.crafting.CraftingCPUMenu.TYPE, player,
                                appeng.menu.locator.MenuLocators.forBlockEntity(actual));
                    });
                    opening = true;
                    openedAt = System.nanoTime();
                    return;
                }
                assertTrue(System.nanoTime() - openedAt < 30_000_000_000L, "Native CPU menu did not render");
                if (!menuOpening.isDone()) return;
                assertTrue(menuOpening.join(), "Native AE2 menu opener rejected the real CPU");
                if (!(minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen)
                        || cpuRenders < 8) return;
                var menu = minecraft.player.containerMenu;
                assertInstanceOf(appeng.menu.me.crafting.CraftingCPUMenu.class, menu);
                assertThrows(NoSuchMethodException.class, () -> menu.getClass().getMethod("ae2craftingtime$suspensionSnapshot"));
                var selected = type.getDeclaredMethod("selectedSuspensionSnapshot", Minecraft.class);
                selected.setAccessible(true);
                var invocation = assertThrows(InvocationTargetException.class, () -> selected.invoke(null, minecraft));
                var error = assertInstanceOf(IllegalStateException.class, invocation.getCause());
                assertEquals("Forge suspension menu state unavailable", error.getMessage());
                assertInstanceOf(NoSuchMethodException.class, error.getCause());
                assertSame(menu, minecraft.player.containerMenu);
                checks.add("native-menu-missing-forge-api");
                Files.writeString(output.resolve("native-menu-missing-forge-api.json"), new com.google.gson.Gson().toJson(Map.of(
                        "screen", minecraft.screen.getClass().getName(), "menu", menu.getClass().getName(),
                        "nativeRenderCallbacks", cpuRenders, "cause", error.getCause().getClass().getName(),
                        "scope", "actual NeoForge CPU menu lacks the Forge-only mixin method; no fake menu or job")));
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("native-menu-missing-forge-api.png"));
                }
                minecraft.player.closeContainer();
                assertNull(minecraft.screen);
                guarded = true;
                opening = false;
                return;
            }
            runtime.tick();
            var path = DriverOptions.load().output().resolve("result.json");
            if (Files.exists(path)) {
                var result = new com.google.gson.Gson().fromJson(Files.readString(path), com.google.gson.JsonObject.class);
                assertEquals("PASS", result.get("result").getAsString());
                assertTrue(guarded);
                Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(Map.of(
                        "result", "PASS", "checks", checks, "normalResult", result)));
                finished = true;
                runtime.close();
                minecraft.stop();
            }
        } catch (Throwable error) {
            finished = true;
            try {
                Files.writeString(output.resolve("failure.txt"), error.toString());
                Files.writeString(output.resolve("failure-context.json"), new com.google.gson.Gson().toJson(Map.of(
                        "screen", minecraft.screen == null ? "absent" : minecraft.screen.getClass().getName(),
                        "menu", minecraft.player == null ? "absent" : minecraft.player.containerMenu.getClass().getName(),
                        "menuOpenCompleted", menuOpening != null && menuOpening.isDone(), "cpuRenderCallbacks", cpuRenders)));
                try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(output.resolve("failure.png"));
                }
            }
            catch (Exception secondary) { error.addSuppressed(secondary); }
            minecraft.stop();
            throw new AssertionError("Native suspension API boundary failed", error);
        }
    }

    private static Object field(Class<?> type, String name, Object owner) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
