package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Ae2CraftingTime.MOD_ID)
@SuppressWarnings("removal")
public final class Ae2CraftingTime {
    public static final String MOD_ID = "ae2craftingtime";
    public static final String COMMON_CONFIG_FILE = "ae2craftingtime-common.toml";

    public Ae2CraftingTime() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("mekanism"))
            ChanceOutputHooks.install(MekanismSawmillChanceDetector::detect);
        IntegrationLog.start("1.20.1-forge", net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient(), "forge",
                id -> net.minecraftforge.fml.ModList.get().getModContainerById(id).map(mod -> mod.getModInfo().getVersion().toString()).orElse(null));
        IntegrationLog.required("config-registration", () -> ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Ae2CraftingTimeConfig.SPEC, COMMON_CONFIG_FILE));
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) Ae2CraftingTimeClient.registerConfigScreen();
        var modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getModId().equals(MOD_ID)) IntegrationLog.configuration();
        });
        modBus.addListener((net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getModId().equals(MOD_ID)) IntegrationLog.configuration();
        });
        IntegrationLog.required("network-registration", StatsNetwork::register);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TagsUpdatedEvent event) ->
                ProfilerBridge.clearChanceEvidence());
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
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
        ServerOptionsRuntime.initialize(event.getServer(), net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(COMMON_CONFIG_FILE));
        var data = event.getServer().overworld().getDataStorage()
                .computeIfAbsent(Ae2CraftingTimeSavedData::load, Ae2CraftingTimeSavedData::new,
                        Ae2CraftingTimeSavedData.FILE_ID);
        ProfilerBridge.load(data);
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ProfilerBridge.flushCompletedSamples();
        DelayedNotificationServer.flush(event.getServer());
            ProviderStartTracker.endTick();
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        ProfilerBridge.clearChanceEvidence();
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
