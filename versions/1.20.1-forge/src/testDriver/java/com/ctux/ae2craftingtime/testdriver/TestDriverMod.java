package com.ctux.ae2craftingtime.testdriver;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod(TestDriverMod.MOD_ID)
public final class TestDriverMod {
    public static final String MOD_ID = "ae2craftingtime_test_driver";

    public TestDriverMod() {
        ConnectionObservation.ready();
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) {
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                    ConnectionObservation.beginConnection();
                    ConnectionProbe.server(player);
                }
            });
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                    ConnectionObservation.endConnection();
            });
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) -> {
                        ConnectionObservation.beginConnection();
                        ConnectionProbe.client();
                    }));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> ConnectionObservation.endConnection()));
        }
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection"))
            ResourceFixtureFluid.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TestDriverMod::startClient);
    }

    private static void startClient() {
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) return;
        var options = DriverOptions.load();
        if (options == null) {
            return;
        }
        try {
            var version = ModList.get().getModContainerById(MOD_ID).orElseThrow().getModInfo().getVersion();
            var driverFile = "ae2-crafting-time-" + version + "-forge-1.20.1-test-driver.jar";
            var runtime = new TestDriverRuntime(options, driverFile);
            var readInterestRecovery = options.connectedDedicated() ? new ForgeReadInterestRecovery() : null;
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.RenderTickEvent event) -> {
                if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                    if (readInterestRecovery != null) readInterestRecovery.tick();
                    runtime.tick();
                }
            });
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Pre event) -> runtime.beforeRender());
            MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ScreenEvent.Render.Post event) -> runtime.afterRender());
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
