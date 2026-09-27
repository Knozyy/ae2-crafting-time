package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftConfirmTableRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.TtcSort;
import com.ctux.ae2craftingtime.mc1201.AeKeyAmounts;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ClientStatsRequests;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.StatsClickHandler;
import com.ctux.ae2craftingtime.mc1201.StatsChatMessages;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.TtcBadge;
import com.ctux.ae2craftingtime.mc1201.TtcDetailsClick;
import com.ctux.ae2craftingtime.mc1201.TtcDetailsKeyMapping;
import com.ctux.ae2craftingtime.mc1201.TtcSortButton;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

@Mixin(CraftConfirmScreen.class)
public abstract class CraftConfirmScreenMixin extends AEBaseScreen<CraftConfirmMenu> implements StatsClickHandler {
    @Unique
    private static final int AE2CRAFTINGTIME_TABLE_X = 9;
    @Unique
    private static final int AE2CRAFTINGTIME_TABLE_Y = 19;
    @Unique
    private static final int AE2CRAFTINGTIME_CELL_WIDTH = 67;
    @Unique
    private static final int AE2CRAFTINGTIME_CELL_HEIGHT = 22;
    @Unique
    private static final int AE2CRAFTINGTIME_CELL_BORDER = 1;
    @Unique
    private static final int AE2CRAFTINGTIME_COLS = 3;
    @Unique
    private static final int AE2CRAFTINGTIME_ROWS = 5;
    @Unique
    private int ae2craftingtime$ttcSortMode = 2;

    @Shadow(remap = false)
    private Scrollbar scrollbar;

    protected CraftConfirmScreenMixin(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (TtcDetailsClick.tryHandle(button)
                || (TtcDetailsKeyMapping.matchesMouse(button) || TtcDetailsKeyMapping.matchesResetMouse(button))
                && ae2craftingtime$handleClickedStats(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$addTtcSortButton(CraftConfirmMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style, CallbackInfo ci) {
        ClientStats.CACHE.clear();
        ClientStatsRequests.clear();
        ae2craftingtime$ttcSortMode = ClientOptionsRuntime.current().planSort();
        if (ClientOptionsRuntime.enabled(OptionFeature.PLAN_SORT_CONTROL))
            addToLeftToolbar(new TtcSortButton(this::ae2craftingtime$cycleTtcSortMode,
                    () -> ae2craftingtime$ttcSortMode));
    }

    @SuppressWarnings("mapping")
    @ModifyArg(
            method = "drawFG",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/me/crafting/CraftConfirmTableRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;IILjava/util/List;I)V",
                    remap = true),
            index = 3,
            remap = false)
    private List<CraftingPlanSummaryEntry> ae2craftingtime$sortPlanByTtc(List<CraftingPlanSummaryEntry> entries) {
        var sorted = TtcSort.copyPrioritizedSorted(entries, entry -> entry.getMissingAmount() > 0,
                CraftConfirmScreenMixin::ae2craftingtime$seconds, Comparator.naturalOrder(),
                ClientOptionsRuntime.enabled(OptionFeature.PLAN_SORT_CONTROL) && ae2craftingtime$ttcSortMode != 0,
                ae2craftingtime$ttcSortMode == 2);
        IntegrationLog.observe("ae2craftingtime", "plan-sort");
        return sorted;
    }

    @Inject(method = "drawFG", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$drawTotalTtc(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (!ClientOptionsRuntime.enabled(OptionFeature.PLAN_TOTAL)) return;
        var plan = getMenu().getPlan();
        if (plan == null) {
            return;
        }

        var estimates = new ArrayList<OptionalLong>();
        for (var entry : plan.getEntries()) {
            estimates.add(ae2craftingtime$seconds(entry));
        }

        TimeEstimate.formatTotal(estimates).ifPresent(eta -> {
            var text = TtcComponents.rowSymbolsInTextColor(
                    TtcText.totalTtc(eta).withStyle(style -> style.withColor(0x404040)), 0x404040);
            var font = getMinecraft().font;
            var textX = 109 - font.width(text) / 2;
            var totalWidth = font.width(text);
            TtcBadge.fillRoundedRect(guiGraphics, textX - 2, 176, textX + totalWidth + 2,
                    178 + font.lineHeight + 2, TtcBadge.BACKGROUND);
            guiGraphics.drawString(font, text, textX, 178,
                    0x404040, ClientOptionsRuntime.textShadow());
            IntegrationLog.observe("ae2craftingtime", "plan-total");
        });
    }

    @Unique
    private void ae2craftingtime$cycleTtcSortMode() {
        ae2craftingtime$ttcSortMode = (ae2craftingtime$ttcSortMode + 1) % 3;
    }

    @Override
    public boolean ae2craftingtime$handleClickedStats(double mouseX, double mouseY, int button) {
        var plan = getMenu().getPlan();
        if (plan == null) {
            return false;
        }

        var entries = ae2craftingtime$sortPlanByTtc(plan.getEntries());
        var entry = ae2craftingtime$clickedEntry(mouseX, mouseY, entries);
        if (entry == null) {
            return false;
        }

        if (entry.getCraftAmount() <= 0) {
            return false;
        }

        var key = ProfilerBridge.key(entry.getWhat());
        if (TtcDetailsKeyMapping.matchesResetMouse(button)) {
            if (!ClientOptionsRuntime.enabled(OptionFeature.RESET_HISTORY_CLICK)) return false;
            StatsChatMessages.reset(key, entry.getWhat().getDisplayName().getString());
            IntegrationLog.observe("ae2craftingtime", "plan-reset");
            return true;
        }
        if (!ClientOptionsRuntime.enabled(OptionFeature.TTC_DETAILS_CLICK)) return false;
        StatsChatMessages.show(key, entry.getWhat().getDisplayName().getString(),
                AeKeyAmounts.normalize(entry.getWhat(), entry.getCraftAmount()));
        IntegrationLog.observe("ae2craftingtime", "plan-details");
        return true;
    }

    @Unique
    private CraftingPlanSummaryEntry ae2craftingtime$clickedEntry(double mouseX, double mouseY,
            List<CraftingPlanSummaryEntry> entries) {
        var index = ae2craftingtime$clickedTableIndex(mouseX, mouseY);
        if (index >= 0 && index < entries.size()) {
            return entries.get(index);
        }

        var hovered = getStackUnderMouse(mouseX, mouseY);
        var hoveredKey = hovered == null ? null : ProfilerBridge.key(hovered.stack().what());
        if (hoveredKey == null) {
            return null;
        }
        for (var entry : entries) {
            if (hoveredKey.equals(ProfilerBridge.key(entry.getWhat()))) {
                return entry;
            }
        }
        return null;
    }

    @Unique
    private int ae2craftingtime$clickedTableIndex(double mouseX, double mouseY) {
        var x = (int) mouseX - getGuiLeft() - AE2CRAFTINGTIME_TABLE_X;
        var y = (int) mouseY - getGuiTop() - AE2CRAFTINGTIME_TABLE_Y;
        var pitchX = AE2CRAFTINGTIME_CELL_WIDTH + AE2CRAFTINGTIME_CELL_BORDER;
        var pitchY = AE2CRAFTINGTIME_CELL_HEIGHT + AE2CRAFTINGTIME_CELL_BORDER;
        if (x < 0 || y < 0 || x % pitchX >= AE2CRAFTINGTIME_CELL_WIDTH || y % pitchY >= AE2CRAFTINGTIME_CELL_HEIGHT) {
            return -1;
        }

        var col = x / pitchX;
        var row = y / pitchY;
        if (col >= AE2CRAFTINGTIME_COLS || row >= AE2CRAFTINGTIME_ROWS) {
            return -1;
        }
        return (row + scrollbar.getCurrentScroll()) * AE2CRAFTINGTIME_COLS + col;
    }

    @Unique
    private static OptionalLong ae2craftingtime$seconds(CraftingPlanSummaryEntry entry) {
        if (entry.getCraftAmount() <= 0) {
            return OptionalLong.empty();
        }

        var key = ProfilerBridge.key(entry.getWhat());
        var stats = ClientStats.CACHE.get(key);
        if (stats.isEmpty()) {
            ClientStatsRequests.request(key);
            return OptionalLong.empty();
        }

        return TimeEstimate.seconds(AeKeyAmounts.normalize(entry.getWhat(), entry.getCraftAmount()), stats.get());
    }
}
