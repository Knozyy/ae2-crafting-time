package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class Ae2CraftingTime implements ModInitializer {
    public static final String MOD_ID = "ae2craftingtime";
    public static final String COMMON_CONFIG_FILE = "ae2craftingtime-common.toml";

    @Override
    public void onInitialize() {
        var loader = FabricLoader.getInstance();
        IntegrationLog.start("1.20.1-fabric", loader.getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT, "fabricloader",
                id -> loader.getModContainer(id).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null));
        IntegrationLog.required("config-registration", () -> Ae2CraftingTimeConfig.load(FabricLoader.getInstance().getConfigDir().resolve(COMMON_CONFIG_FILE)));
        IntegrationLog.configuration();
        IntegrationLog.required("network-registration", StatsNetwork::registerServer);
        UseItemCallback.EVENT.register(CraftingTimeGuideBook::use);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                ProviderLocateCommand.build((source, id) -> ProviderLocateCommand.locate(source, id,
                        (player, highlight) -> StatsNetwork.sendTo(player, new ProviderHighlightS2C(
                                highlight.networkId(), highlight.dimensionId(), highlight.positions(),
                                highlight.outputId(), highlight.durationSeconds(), highlight.plateOnly(),
                                highlight.displayKey(), highlight.chatLocate()))))));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerOptionsRuntime.initialize(server, FabricLoader.getInstance().getConfigDir().resolve(COMMON_CONFIG_FILE));
            var data = server.overworld().getDataStorage()
                    .computeIfAbsent(Ae2CraftingTimeSavedData::load, Ae2CraftingTimeSavedData::new,
                            Ae2CraftingTimeSavedData.FILE_ID);
            ProfilerBridge.load(data);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProfilerBridge.flushCompletedSamples();
            DelayedNotificationServer.flush(server);
            ProviderStartTracker.endTick();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ProfilerBridge.flushCompletedSamples();
            CpuTtcRequestHandler.clear();
        StatsRequestHandler.clear();
            WarningPreferenceServer.clearAll();
            ProviderStartTracker.clearAll();
        ServerOptionsRuntime.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ProfilerBridge.resyncPlatesForPlayer(handler.getPlayer());
            ServerOptionsRuntime.sendTo(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CpuTtcRequestHandler.clear(handler.getPlayer().getUUID());
            StatsRequestHandler.clear(handler.getPlayer().getUUID());
            WarningPreferenceServer.clear(handler.getPlayer());
        });
        IntegrationLog.summary();
    }

}
