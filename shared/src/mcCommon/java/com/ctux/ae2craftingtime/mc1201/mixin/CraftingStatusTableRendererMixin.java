package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;
import appeng.api.stacks.AmountFormat;
import appeng.core.localization.GuiText;
import com.ctux.ae2craftingtime.core.CraftingRowState;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.mc1201.AeKeyAmounts;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ClientStatsRequests;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.TtcColorContext;
import com.ctux.ae2craftingtime.mc1201.RowTextColor;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CraftingStatusTableRenderer.class)
public abstract class CraftingStatusTableRendererMixin {
    @Inject(method = "getEntryDescription", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$appendVisibleTimeToCraft(CraftingStatusEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        var lines = cir.getReturnValue();
        MutableComponent amounts = null;
        if (ClientOptionsRuntime.enabled(OptionFeature.COMPACT_STATUS_AMOUNTS)) {
            var key = entry.getWhat();
            long stored = entry.getStoredAmount(), active = entry.getActiveAmount(), pending = entry.getPendingAmount();
            amounts = ae2craftingtime$compactAmounts(lines, stored,
                    stored > 0 ? key.formatAmount(stored, AmountFormat.SLOT) : null,
                    active, active > 0 ? key.formatAmount(active, AmountFormat.SLOT) : null,
                    pending, pending > 0 ? key.formatAmount(pending, AmountFormat.SLOT) : null);
        }
        var beforeTtc = lines.size();
        ae2craftingtime$appendTtc(entry, lines);
        if (amounts != null) {
            RowTextColor.amounts(amounts, lines.size() > beforeTtc ? lines.get(beforeTtc) : null,
                    ClientOptionsRuntime.current());
        }
        if (amounts != null || lines.size() > beforeTtc) IntegrationLog.observe("ae2craftingtime", "status-row");
    }

    @Inject(method = "getEntryTooltip", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$appendTooltipTimeToCraft(CraftingStatusEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        if (ClientOptionsRuntime.enabled(OptionFeature.COMPACT_STATUS_AMOUNTS)
                && (entry.getStoredAmount() > 0 || entry.getActiveAmount() > 0 || entry.getPendingAmount() > 0)) {
            cir.getReturnValue().add(TtcText.statusAmountsLegend());
        }
        if (StatsNetwork.selectedSuspended()) return;
        ae2craftingtime$appendTooltip(cir.getReturnValue(), entry.getActiveAmount(), entry.getPendingAmount(),
                ae2craftingtime$noSpace(entry), ae2craftingtime$blockReason(entry),
                () -> (ClientOptionsRuntime.enabled(OptionFeature.DETAILED_TOOLTIPS)
                        || ClientOptionsRuntime.enabled(OptionFeature.CHANCE_OUTPUT_STATUS)
                        && ClientStats.chanceOutput(ProfilerBridge.key(entry.getWhat())).isPresent())
                        && ae2craftingtime$appendStatsTooltip(entry, cir.getReturnValue()));
        var reason = ae2craftingtime$blockReason(entry);
        if (reason != null && ClientOptionsRuntime.enabled(OptionFeature.statusFor(reason))
                && ClientOptionsRuntime.enabled(OptionFeature.CHANCE_OUTPUT_STATUS)) {
            ClientStats.chanceOutput(ProfilerBridge.key(entry.getWhat())).ifPresent(chance ->
                    cir.getReturnValue().add(Component.translatable("text.ae2craftingtime.chance_output.context",
                            java.math.BigDecimal.valueOf(chance, 2).stripTrailingZeros().toPlainString())));
        }
    }

    private static MutableComponent ae2craftingtime$compactAmounts(List<Component> lines, long stored, String storedText,
            long active, String activeText, long pending, String pendingText) {
        var expected = List.of(GuiText.FromStorage.text(storedText == null ? "" : storedText),
                GuiText.Crafting.text(activeText == null ? "" : activeText),
                GuiText.Scheduled.text(pendingText == null ? "" : pendingText));
        var positions = new int[] {-1, -1, -1};
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!(line.getContents() instanceof TranslatableContents text)) continue;
            for (int category = 0; category < expected.size(); category++) {
                var nativeText = (TranslatableContents) expected.get(category).getContents();
                if (!text.getKey().equals(nativeText.getKey())) continue;
                long amount = category == 0 ? stored : category == 1 ? active : pending;
                if (amount <= 0 || positions[category] >= 0 || !line.equals(expected.get(category))) return null;
                positions[category] = i;
            }
        }
        int first = lines.size();
        for (int category = 0; category < positions.length; category++) {
            long amount = category == 0 ? stored : category == 1 ? active : pending;
            if (amount > 0 && positions[category] < 0) return null;
            if (positions[category] >= 0) first = Math.min(first, positions[category]);
        }
        if (first == lines.size()) return null;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (i == positions[0] || i == positions[1] || i == positions[2]) lines.remove(i);
        }
        var compact = TtcText.statusAmounts(CraftingRowState.compactAmounts(
                stored, storedText, active, activeText, pending, pendingText));
        lines.add(first, compact);
        return compact;
    }

    private static void ae2craftingtime$appendTooltip(List<Component> lines, long active, long pending, boolean noSpace,
            CraftingBlockReason reason, java.util.function.BooleanSupplier appendStats) {
        if (!ClientOptionsRuntime.enabled(OptionFeature.STATUS_ROWS)) return;
        var showLocateHint = false;
        if (noSpace && ClientOptionsRuntime.enabled(OptionFeature.NO_SPACE_STATUS)) {
            lines.addAll(TtcText.noSpaceTooltip());
            showLocateHint = true;
        } else {
            var amount = active + pending;
            if (amount <= 0) {
                return;
            }

            if (reason != null && ClientOptionsRuntime.enabled(OptionFeature.statusFor(reason))) {
                lines.addAll(TtcText.blockReasonTooltip(reason, active > 0 && pending > 0));
                showLocateHint = true;
            } else {
                showLocateHint = appendStats.getAsBoolean();
            }
        }
        ae2craftingtime$appendControlHints(lines, showLocateHint);
        IntegrationLog.observe("ae2craftingtime", "status-tooltip");
    }

    private static void ae2craftingtime$appendControlHints(List<Component> lines, boolean showLocateHint) {
        if (!ClientOptionsRuntime.enabled(OptionFeature.CONTROL_HINTS)) return;
        if (showLocateHint && ClientOptionsRuntime.enabled(OptionFeature.PROVIDER_LOCATE_CLICK)) {
            lines.add(TtcText.locateHint().withStyle(ChatFormatting.GRAY));
        }
        if (ClientOptionsRuntime.enabled(OptionFeature.TTC_DETAILS_CLICK))
            lines.add(TtcText.detailsHint().withStyle(ChatFormatting.GRAY));
        if (ClientOptionsRuntime.enabled(OptionFeature.RESET_HISTORY_CLICK))
            lines.add(TtcText.resetHint().withStyle(ChatFormatting.GRAY));
    }

    private static void ae2craftingtime$appendTtc(CraftingStatusEntry entry, List<Component> lines) {
        if (StatsNetwork.selectedSuspended()) return;
        if (!ClientOptionsRuntime.enabled(OptionFeature.STATUS_ROWS)) return;
        if (ae2craftingtime$noSpace(entry) && ClientOptionsRuntime.enabled(OptionFeature.NO_SPACE_STATUS)) {
            lines.add(TtcText.noSpace());
            return;
        }
        var amount = entry.getActiveAmount() + entry.getPendingAmount();
        if (amount <= 0) {
            return;
        }

        var key = ProfilerBridge.key(entry.getWhat());
        ClientStatsRequests.request(key);
        var reason = ae2craftingtime$blockReason(entry);
        if (reason != null && ClientOptionsRuntime.enabled(OptionFeature.statusFor(reason))) {
            lines.add(TtcText.blockReason(reason));
            return;
        }
        if (entry.getActiveAmount() == 0 && entry.getPendingAmount() > 0) {
            var waiting = ClientStats.CACHE.waitingTicks(key);
            if (waiting.isPresent() && ClientOptionsRuntime.enabled(OptionFeature.WAITING_STATUS)) {
                lines.add(RowTextColor.neutral(TtcText.waiting(), ClientOptionsRuntime.current()));
                return;
            }
        }
        if (ClientOptionsRuntime.enabled(OptionFeature.CHANCE_OUTPUT_STATUS)
                && ClientStats.chanceOutput(key).isPresent()) {
            lines.add(TtcText.chanceOutput());
            return;
        }
        ClientStats.CACHE.get(key).ifPresentOrElse(stats -> {
            var stall = ClientStats.CACHE.stall(key);
            if (stall.isPresent() && ClientOptionsRuntime.enabled(OptionFeature.DELAYED_STATUS)) {
                lines.add(delayedTtcLine());
                return;
            }
            TimeEstimate.format(AeKeyAmounts.normalize(entry.getWhat(), amount), stats)
                    .ifPresentOrElse(eta -> lines.add(ttcLine(key, eta)),
                            () -> { if (ClientOptionsRuntime.enabled(OptionFeature.COLLECTING_STATUS))
                                lines.add(RowTextColor.neutral(TtcText.ttcCollectingData(),
                                        ClientOptionsRuntime.current())); });
        }, () -> { if (ClientOptionsRuntime.enabled(OptionFeature.COLLECTING_STATUS))
            lines.add(RowTextColor.neutral(TtcText.ttcCollectingData(), ClientOptionsRuntime.current())); });
    }

    private static boolean ae2craftingtime$appendStatsTooltip(CraftingStatusEntry entry, List<Component> lines) {
        var amount = entry.getActiveAmount() + entry.getPendingAmount();
        var key = ProfilerBridge.key(entry.getWhat());
        var normalized = AeKeyAmounts.normalize(entry.getWhat(), amount);
        ClientStatsRequests.request(key);
        var stats = ClientStats.CACHE.get(key);
        var chance = ClientOptionsRuntime.enabled(OptionFeature.CHANCE_OUTPUT_STATUS)
                ? ClientStats.chanceOutput(key) : java.util.OptionalLong.empty();
        var stall = ClientStats.CACHE.stall(key);
        if (chance.isPresent()) {
            lines.addAll(TtcText.chanceOutputTooltip(entry.getActiveAmount(), chance.getAsLong(),
                    stall.map(com.ctux.ae2craftingtime.core.StallDiagnostic::idleTicks).orElse(0L)));
            return true;
        }
        if (stats.isEmpty()) {
            return false;
        }
        if (stall.isPresent() && ClientOptionsRuntime.enabled(OptionFeature.DELAYED_STATUS)) {
            lines.addAll(TtcText.stallLines(normalized, entry.getPendingAmount(), stats.get(), stall.get()));
            return true;
        }
        lines.addAll(TtcText.statsLines(stats.get()));
        return false;
    }

    private static Component delayedTtcLine() {
        return TtcText.ttcDelayed()
                .withStyle(style -> style.withColor(TextColor.fromRgb(
                        ClientOptionsRuntime.current().color(ClientConfig.Color.DELAYED))).withBold(false));
    }

    private static boolean ae2craftingtime$noSpace(CraftingStatusEntry entry) {
        if (StatsNetwork.selectedSuspended()) return false;
        return CraftingRowState.noSpace(
                Minecraft.getInstance().screen instanceof CraftingCPUScreen<?> screen
                        && screen.getMenu().isCantStoreItems(),
                entry.getStoredAmount(), entry.getActiveAmount(), entry.getPendingAmount());
    }

    private static CraftingBlockReason ae2craftingtime$blockReason(CraftingStatusEntry entry) {
        if (StatsNetwork.selectedSuspended()) return null;
        return CraftingRowState.blockReason(entry.getPendingAmount(),
                ClientStats.blockReason(ProfilerBridge.key(entry.getWhat())));
    }

    private static Component ttcLine(com.ctux.ae2craftingtime.core.ProfileKey key, String eta) {
        return RowTextColor.estimate(eta, TtcColorContext.get(key), ClientOptionsRuntime.current());
    }
}
