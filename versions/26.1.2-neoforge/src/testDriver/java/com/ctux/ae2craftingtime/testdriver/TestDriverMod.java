package com.ctux.ae2craftingtime.testdriver;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(TestDriverMod.MOD_ID)
public final class TestDriverMod {
    public static final String MOD_ID = "ae2craftingtime_test_driver";

    public TestDriverMod() {
        ConnectionObservation.ready();
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) {
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    ConnectionObservation.beginConnection();
                    ConnectionProbe.server(player);
                }
            });
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                    ConnectionObservation.endConnection();
            });
            if (net.neoforged.fml.loading.FMLLoader.getCurrent().getDist().isClient())
                NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> {
                    ConnectionObservation.beginConnection();
                    ConnectionProbe.client();
                });
            if (net.neoforged.fml.loading.FMLLoader.getCurrent().getDist().isClient())
                NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> ConnectionObservation.endConnection());
        }
        if (net.neoforged.fml.loading.FMLLoader.getCurrent().getDist().isClient())
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent event) -> startClient());
    }

    private static void startClient() {
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) return;
        var options = DriverOptions.load();
        if (options == null) {
            return;
        }
        try {
            var version = ModList.get().getModContainerById(MOD_ID).orElseThrow().getModInfo().getVersion();
            var driverFile = "ae2-crafting-time-" + version + "-neoforge-26.1.2-test-driver.jar";
            var runtime = new TestDriverRuntime(options, driverFile);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) -> {
                runtime.tick();
            });
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) -> runtime.beforeRender());
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) -> runtime.afterRender());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    runtime.close();
                } catch (Exception ignored) {
                }
            }, "ae2ct-test-driver-shutdown"));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot start AE2 Crafting Time test driver", error);
        }
    }
}
