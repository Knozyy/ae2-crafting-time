package com.ctux.ae2craftingtime.testdriver;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class TestDriverMod implements ClientModInitializer {
    public static final String MOD_ID = "ae2craftingtime_test_driver";
    private static TestDriverRuntime runtime;

    @Override
    public void onInitializeClient() {
        ConnectionObservation.ready();
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection"))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                    (handler, sender, client) -> {
                        ConnectionObservation.beginConnection();
                        ConnectionProbe.client();
                    });
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                    (handler, client) -> ConnectionObservation.endConnection());
            return;
        }
        var options = DriverOptions.load();
        if (options == null) return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                var version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion();
                runtime = new TestDriverRuntime(options, "ae2-crafting-time-" + version.getFriendlyString()
                        + "-fabric-1.20.1-test-driver.jar");
            } catch (Exception error) {
                throw new IllegalStateException("Cannot start AE2 Crafting Time test driver", error);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.beforeRender(screen).register((s, graphics, x, y, delta) -> runtime.beforeRender());
            ScreenEvents.afterRender(screen).register((s, graphics, x, y, delta) -> runtime.afterRender());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                runtime.close();
            } catch (Exception error) {
                throw new IllegalStateException("Cannot close test driver", error);
            }
        });
    }

    public static void afterFrame() {
        if (runtime != null) runtime.tick();
    }
}
