package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.api.stacks.AmountFormat;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.mc1201.AeKeyAmounts;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ClientStatsRequests;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.PlanAmountLines;
import com.ctux.ae2craftingtime.mc1201.TtcColorContext;
import com.ctux.ae2craftingtime.mc1201.RowTextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry;

@Mixin(CraftConfirmTableRenderer.class)
public abstract class CraftConfirmTableRendererMixin {
    @WrapOperation(method = {"getEntryDescription", "getEntryTooltip"}, at = @At(value = "INVOKE",
            target = "Lappeng/core/localization/GuiText;text"), remap = false)
    private net.minecraft.network.chat.MutableComponent ae2craftingtime$recurrentLabel(
            appeng.core.localization.GuiText text, Object[] arguments,
            Operation<net.minecraft.network.chat.MutableComponent> original, CraftingPlanSummaryEntry entry) {
        return text == appeng.core.localization.GuiText.Missing && entry.getMissingAmount() > 0
                && ((RecurrentPlanEntry) entry).ae2craftingtime$recurrent()
                && ClientOptionsRuntime.enabled(OptionFeature.RECURRENT_STATUS)
                ? TtcText.recurrent(arguments) : original.call(text, arguments);
    }

    @Inject(method = "getEntryDescription", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$appendVisibleTimeToCraft(CraftingPlanSummaryEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        var lines = cir.getReturnValue();
        var before = lines.size();
        MutableComponent amounts = null;
        if (ClientOptionsRuntime.current().features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS)) {
            var key = entry.getWhat();
            long stored = entry.getStoredAmount(), craft = entry.getCraftAmount();
            amounts = PlanAmountLines.compact(lines, stored,
                    stored > 0 ? key.formatAmount(stored, AmountFormat.SLOT) : null,
                    craft, craft > 0 ? key.formatAmount(craft, AmountFormat.SLOT) : null);
        }
        if (ae2craftingtime$showStoredVariant(entry)) {
            lines.add(TtcText.storedVariant());
        }
        var beforeTtc = lines.size();
        ae2craftingtime$appendTtc(entry, lines);
        if (amounts != null) {
            var status = lines.size() > beforeTtc ? lines.get(beforeTtc) : null;
            RowTextColor.amounts(amounts, status, ClientOptionsRuntime.current());
        }
        IntegrationLog.growth("plan-row", before, lines.size());
    }

    @Inject(method = "getEntryTooltip", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$appendTooltipTimeToCraft(CraftingPlanSummaryEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        if (ClientOptionsRuntime.current().features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS)
                && (entry.getStoredAmount() > 0 || entry.getCraftAmount() > 0)) {
            cir.getReturnValue().add(TtcText.planAmountsLegend());
        }
        if (ae2craftingtime$showStoredVariant(entry)) {
            cir.getReturnValue().addAll(TtcText.storedVariantHints());
        }
        if (entry.getMissingAmount() > 0 && ((RecurrentPlanEntry) entry).ae2craftingtime$recurrent()
                && ClientOptionsRuntime.enabled(OptionFeature.RECURRENT_STATUS)
                && ClientOptionsRuntime.profilingEnabled()) {
            cir.getReturnValue().add(TtcText.recurrentHint());
        }
        if (entry.getCraftAmount() <= 0) {
            return;
        }

        if (ClientOptionsRuntime.enabled(OptionFeature.DETAILED_TOOLTIPS)) {
            ae2craftingtime$appendStatsTooltip(entry, cir.getReturnValue());
        }
        if (ClientOptionsRuntime.enabled(OptionFeature.CONTROL_HINTS)) {
            if (ClientOptionsRuntime.enabled(OptionFeature.TTC_DETAILS_CLICK))
                cir.getReturnValue().add(TtcText.detailsHint().withStyle(ChatFormatting.GRAY));
            if (ClientOptionsRuntime.enabled(OptionFeature.RESET_HISTORY_CLICK))
                cir.getReturnValue().add(TtcText.resetHint().withStyle(ChatFormatting.GRAY));
        }
        IntegrationLog.observe("ae2craftingtime", "plan-tooltip");
    }

    private static boolean ae2craftingtime$showStoredVariant(CraftingPlanSummaryEntry entry) {
        return com.ctux.ae2craftingtime.core.PlanStoredVariantLifecycle.show(
                entry.getWhat() instanceof appeng.api.stacks.AEItemKey, entry.getMissingAmount(),
                ((RecurrentPlanEntry) entry).ae2craftingtime$storedVariant(),
                ClientOptionsRuntime.profilingEnabled());
    }

    private static void ae2craftingtime$appendTtc(CraftingPlanSummaryEntry entry, List<Component> lines) {
        if (!ClientOptionsRuntime.enabled(OptionFeature.PLAN_ROWS) || entry.getCraftAmount() <= 0) {
            return;
        }

        var key = ProfilerBridge.key(entry.getWhat());
        ClientStatsRequests.request(key);
        ClientStats.CACHE.get(key).ifPresentOrElse(stats -> TimeEstimate
                .format(AeKeyAmounts.normalize(entry.getWhat(), entry.getCraftAmount()), stats)
                .ifPresentOrElse(eta -> lines.add(ttcLine(key, eta)),
                        () -> ae2craftingtime$addCollecting(lines)),
                () -> ae2craftingtime$addCollecting(lines));
    }

    private static void ae2craftingtime$addCollecting(List<Component> lines) {
        if (ClientOptionsRuntime.enabled(OptionFeature.COLLECTING_STATUS)) lines.add(TtcText.ttcCollectingData());
    }

    private static void ae2craftingtime$appendStatsTooltip(CraftingPlanSummaryEntry entry, List<Component> lines) {
        var key = ProfilerBridge.key(entry.getWhat());
        ClientStats.CACHE.get(key).ifPresentOrElse(stats -> {
            lines.addAll(TtcText.statsLines(stats));
        }, () -> ClientStatsRequests.request(key));
    }

    private static Component ttcLine(com.ctux.ae2craftingtime.core.ProfileKey key, String eta) {
        return RowTextColor.estimate(eta, TtcColorContext.get(key), ClientOptionsRuntime.current());
    }
}
