package com.ctux.ae2craftingtime.testdriver.mixin;

import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$text(Font font, Component text, int x, int y, int color, boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        UiObservationStore.text((GuiGraphics) (Object) this, text, x, y, font.width(text), font.lineHeight);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$nativeTitle(Font font, FormattedCharSequence text, int x, int y,
            int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        var rendered = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            rendered.appendCodePoint(codePoint);
            return true;
        });
        UiObservationStore.nativeTitle((GuiGraphics) (Object) this, rendered.toString(), x, y,
                font.width(text), font.lineHeight);
    }

    @Inject(method = "fill(IIIII)V", at = @At("HEAD"))
    private void ae2craftingtime_test_driver$fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        UiObservationStore.fill((GuiGraphics) (Object) this, x1, y1, x2, y2, color);
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$itemTooltip(Font font, List<Component> lines,
            Optional<TooltipComponent> visual, int x, int y, CallbackInfo ci) {
        UiObservationStore.wirelessTooltip(lines);
    }

    @Inject(method = "renderComponentTooltip", at = @At("HEAD"))
    private void ae2craftingtime_test_driver$componentTooltip(Font font, List<Component> lines, int x, int y,
            CallbackInfo ci) {
        UiObservationStore.wirelessTooltip(lines);
    }
}
