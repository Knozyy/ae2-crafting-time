package com.ctux.ae2craftingtime.testdriver.mixin;

import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$text(Font font, Component text, int x, int y, int color, boolean shadow,
            CallbackInfo ci) {
        UiObservationStore.text((GuiGraphicsExtractor) (Object) this, text, x, y, font.width(text), font.lineHeight,
                color);
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$nativeTitle(Font font, FormattedCharSequence text, int x, int y,
            int color, boolean shadow, CallbackInfo ci) {
        var rendered = new StringBuilder();
        final Integer[] textColor = {null};
        final boolean[] bold = {false};
        text.accept((index, style, codePoint) -> {
            rendered.appendCodePoint(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                textColor[0] = style.getColor() == null ? color : style.getColor().getValue();
                bold[0] = style.isBold();
            }
            return true;
        });
        UiObservationStore.nativeTitle((GuiGraphicsExtractor) (Object) this, rendered.toString(), x, y,
                font.width(text), font.lineHeight, textColor[0], bold[0]);
    }

    @Inject(method = "fill(IIIII)V", at = @At("HEAD"))
    private void ae2craftingtime_test_driver$fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        UiObservationStore.fill((GuiGraphicsExtractor) (Object) this, x1, y1, x2, y2, color);
    }

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"))
    private void ae2craftingtime_test_driver$itemTooltip(Font font, List<Component> lines,
            Optional<TooltipComponent> visual, int x, int y, net.minecraft.resources.Identifier style, CallbackInfo ci) {
        UiObservationStore.wirelessTooltip(lines);
    }

    @Inject(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V", at = @At("HEAD"))
    private void ae2craftingtime_test_driver$componentTooltip(Font font, List<Component> lines, int x, int y,
            CallbackInfo ci) {
        UiObservationStore.wirelessTooltip(lines);
    }
}
