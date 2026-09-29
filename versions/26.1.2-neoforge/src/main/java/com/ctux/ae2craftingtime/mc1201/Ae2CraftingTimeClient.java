package com.ctux.ae2craftingtime.mc1201;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = Ae2CraftingTime.MOD_ID, value = Dist.CLIENT)
public final class Ae2CraftingTimeClient {
    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new OptionsScreen(parent));
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ClientOptionsRuntime.initialize(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(TtcDetailsKeyMapping.category());
        IntegrationLog.required("key-registration", () -> event.register(TtcDetailsKeyMapping.showDetails()));
    }

    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ProviderBeamRenderType.PIPELINE);
    }

    private Ae2CraftingTimeClient() {
    }
}
