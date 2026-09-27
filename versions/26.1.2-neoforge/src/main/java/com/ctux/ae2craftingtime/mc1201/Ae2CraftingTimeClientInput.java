package com.ctux.ae2craftingtime.mc1201;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Ae2CraftingTime.MOD_ID, value = Dist.CLIENT)
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
        var mouse = Minecraft.getInstance().mouseHandler;
        var click = new MouseButtonEvent(mouse.xpos(), mouse.ypos(), event.getMouseButtonInfo());
        if (event.getAction() == GLFW.GLFW_PRESS && TtcDetailsClick.tryHandle(click)) {
            event.setCanceled(true);
        }
    }

    private Ae2CraftingTimeClientInput() {
    }
}
