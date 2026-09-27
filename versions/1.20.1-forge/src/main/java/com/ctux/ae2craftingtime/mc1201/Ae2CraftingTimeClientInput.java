package com.ctux.ae2craftingtime.mc1201;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Ae2CraftingTime.MOD_ID, value = Dist.CLIENT)
public final class Ae2CraftingTimeClientInput {
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientConnectionSession.open();
        ClientOptionsRuntime.syncWarningPreference();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientConnectionSession.reset();
    }

    @SubscribeEvent
    public static void showTtcDetails(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS && TtcDetailsClick.tryHandle(event.getButton())) {
            event.setCanceled(true);
        }
    }

    private Ae2CraftingTimeClientInput() {
    }
}
