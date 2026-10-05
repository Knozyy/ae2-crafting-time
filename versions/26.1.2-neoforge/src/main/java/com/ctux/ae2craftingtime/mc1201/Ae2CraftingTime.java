package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Ae2CraftingTime.MOD_ID)
public final class Ae2CraftingTime {
    public static final String MOD_ID = "ae2craftingtime";
    public static final String COMMON_CONFIG_FILE = "ae2craftingtime-common.toml";

    public Ae2CraftingTime(IEventBus modBus, ModContainer modContainer) {
        IntegrationLog.start("26.1.2-neoforge", net.neoforged.fml.loading.FMLLoader.getCurrent().getDist().isClient(), "neoforge",
                id -> net.neoforged.fml.ModList.get().getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse(null));
        IntegrationLog.required("config-registration", () -> modContainer.registerConfig(ModConfig.Type.COMMON, Ae2CraftingTimeConfig.SPEC, COMMON_CONFIG_FILE));
        if (net.neoforged.fml.loading.FMLLoader.getCurrent().getDist().isClient()) Ae2CraftingTimeClient.registerConfigScreen(modContainer);
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getModId().equals(MOD_ID)) IntegrationLog.configuration();
        });
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getModId().equals(MOD_ID)) IntegrationLog.configuration();
        });
        modBus.addListener(StatsNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        IntegrationLog.summary();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(ProviderLocateCommand.build((source, id) ->
                ProviderLocateCommand.locate(source, id, (player, highlight) -> StatsNetwork.sendTo(player,
                        new ProviderHighlightS2C(highlight.networkId(), highlight.dimensionId(),
                                highlight.positions(), highlight.outputId(), highlight.durationSeconds(),
                                highlight.plateOnly(), highlight.displayKey(), highlight.chatLocate())))));
    }

    private void onServerStarted(ServerStartedEvent event) {
        ServerOptionsRuntime.initialize(event.getServer(), net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(COMMON_CONFIG_FILE));
        var data = event.getServer().overworld().getDataStorage()
                .computeIfAbsent(Ae2CraftingTimeSavedData.TYPE);
        ProfilerBridge.load(data);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        ProfilerBridge.flushCompletedSamples();
        DelayedNotificationServer.flush(event.getServer());
            ProviderStartTracker.endTick();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ProfilerBridge.flushCompletedSamples();
        CpuTtcRequestHandler.clear();
        StatsRequestHandler.clear();
        WarningPreferenceServer.clearAll();
        ProviderStartTracker.clearAll();
        ServerOptionsRuntime.clear();
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            CpuTtcRequestHandler.clear(player.getUUID());
            StatsRequestHandler.clear(player.getUUID());
            WarningPreferenceServer.clear(player);
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ProfilerBridge.resyncPlatesForPlayer(player);
            ServerOptionsRuntime.sendTo(player);
        }
    }
}
