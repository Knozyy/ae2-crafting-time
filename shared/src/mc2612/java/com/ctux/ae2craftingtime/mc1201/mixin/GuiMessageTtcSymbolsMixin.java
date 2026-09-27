package com.ctux.ae2craftingtime.mc1201.mixin;

import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GuiMessage.class)
public abstract class GuiMessageTtcSymbolsMixin {
    @ModifyArg(method = "splitLines", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;",
            remap = false), index = 0, remap = false, require = 0)
    private FormattedText ae2craftingtime$decorateChat(FormattedText original) {
        return original instanceof Component component ? TtcComponents.decorate(component) : original;
    }
}
