package com.ctux.ae2craftingtime.testdriver;

import net.fabricmc.api.ModInitializer;

public final class TestDriverConnectionServer implements ModInitializer {
    @Override public void onInitialize() {
        ConnectionObservation.ready();
        if (Boolean.getBoolean("ae2craftingtime.test.observeConnection")) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                    (handler, sender, server) -> {
                        ConnectionObservation.beginConnection();
                        ConnectionProbe.server(handler.getPlayer());
                    });
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                    (handler, server) -> ConnectionObservation.endConnection());
        }
    }
}
