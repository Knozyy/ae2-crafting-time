package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.ctux.ae2craftingtime.core.CraftingRowState;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.TtcColor;
import com.ctux.ae2craftingtime.mc1201.AeKeyAmounts;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.TtcBadge;
import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import com.ctux.ae2craftingtime.mc1201.TtcColorContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Mixin(AbstractTableRenderer.class)
public abstract class AbstractTableRendererMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
                    remap = true),
            remap = false)
    @SuppressWarnings("mapping")
    private int ae2craftingtime$drawTextWithShadow(GuiGraphics guiGraphics, Font font, Component text,
            int x, int y, int color, boolean shadow) {
        var translatable = TtcComponents.translation(text);
        if (translatable != null) {
            var isAe2CraftingTime = translatable.getKey().startsWith("text.ae2craftingtime.");
            if (isAe2CraftingTime && ae2craftingtime$isTtcLine(translatable)) {
                var width = font.width(text);
                var scale = CraftingRowState.isWidthLimited(translatable.getKey())
                        ? CraftingRowState.badgeTextScale(width) : 1f;
                if (scale < 1f) {
                    var left = x + width - CraftingRowState.BADGE_TEXT_WIDTH;
                    TtcBadge.fillRoundedRect(guiGraphics, left - 2, y - 2, x + width + 2,
                            y + font.lineHeight + 2, TtcBadge.BACKGROUND);
                    var pose = guiGraphics.pose();
                    pose.pushPose();
                    pose.translate(left, y, 0);
                    pose.scale(scale, 1, 1);
                    guiGraphics.drawString(font, text, 0, 0, color, ClientOptionsRuntime.textShadow());
                    pose.popPose();
                    return x + width;
                }
                TtcBadge.fillRoundedRect(guiGraphics, x - 2, y - 2, x + width + 2, y + font.lineHeight + 2,
                        TtcBadge.BACKGROUND);
            }
            return guiGraphics.drawString(font, text, x, y, color,
                    ClientOptionsRuntime.current().textShadow(isAe2CraftingTime, shadow));
        }
        return guiGraphics.drawString(font, text, x, y, color, shadow);
    }

    @Unique
    private static boolean ae2craftingtime$isTtcLine(TranslatableContents translatable) {
        return CraftingRowState.isBadge(translatable.getKey());
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$beginTtcColors(GuiGraphics guiGraphics, int mouseX, int mouseY,
            List<?> entries, int scrollOffset, CallbackInfo ci) {
        if (!ae2craftingtime$hasTtc((Object) this)) {
            return;
        }

        TtcColorContext.set(colors(entries));
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$endTtcColors(GuiGraphics guiGraphics, int mouseX, int mouseY,
            List<?> entries, int scrollOffset, CallbackInfo ci) {
        if (ae2craftingtime$hasTtc((Object) this)) {
            TtcColorContext.clear();
        }
    }

    private static boolean ae2craftingtime$hasTtc(Object renderer) {
        return renderer instanceof CraftConfirmTableRenderer || renderer instanceof CraftingStatusTableRenderer;
    }

    private static Map<ProfileKey, Integer> colors(List<?> entries) {
        var secondsByKey = new HashMap<ProfileKey, Long>();
        var min = Long.MAX_VALUE;
        var max = Long.MIN_VALUE;

        for (var entry : entries) {
            var seconds = seconds(entry);
            if (seconds.isEmpty()) {
                continue;
            }

            var key = key(entry);
            var value = seconds.getAsLong();
            secondsByKey.put(key, value);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        var colors = new HashMap<ProfileKey, Integer>();
        for (var entry : secondsByKey.entrySet()) {
            colors.put(entry.getKey(), com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.ttcColor(entry.getValue(), min, max));
        }
        return colors;
    }

    private static OptionalLong seconds(Object entry) {
        if (entry instanceof CraftingPlanSummaryEntry planEntry) {
            if (planEntry.getCraftAmount() <= 0) {
                return OptionalLong.empty();
            }

            var key = ProfilerBridge.key(planEntry.getWhat());
            var stats = ClientStats.CACHE.get(key);
            if (stats.isEmpty()) {
                return OptionalLong.empty();
            }

            return TimeEstimate.seconds(AeKeyAmounts.normalize(planEntry.getWhat(), planEntry.getCraftAmount()),
                    stats.get());
        }

        if (!(entry instanceof CraftingStatusEntry statusEntry)) {
            return OptionalLong.empty();
        }

        var amount = statusEntry.getActiveAmount() + statusEntry.getPendingAmount();
        if (amount <= 0) {
            return OptionalLong.empty();
        }

        var key = ProfilerBridge.key(statusEntry.getWhat());
        if (CraftingRowState.blockReason(statusEntry.getPendingAmount(), ClientStats.blockReason(key)) != null) {
            return OptionalLong.empty();
        }
        if (statusEntry.getActiveAmount() == 0 && statusEntry.getPendingAmount() > 0
                && ClientStats.CACHE.waitingTicks(key).isPresent()) {
            return OptionalLong.empty();
        }
        var stats = ClientStats.CACHE.get(key);
        if (stats.isEmpty()) {
            return OptionalLong.empty();
        }

        return TimeEstimate.seconds(AeKeyAmounts.normalize(statusEntry.getWhat(), amount),
                stats.get());
    }

    private static ProfileKey key(Object entry) {
        if (entry instanceof CraftingPlanSummaryEntry planEntry) {
            return ProfilerBridge.key(planEntry.getWhat());
        }
        return ProfilerBridge.key(((CraftingStatusEntry) entry).getWhat());
    }
}
