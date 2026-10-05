package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.me.crafting.CraftingStatusScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.localization.GuiText;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.me.crafting.CraftingStatus;
import appeng.menu.me.crafting.CraftingStatusEntry;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.ctux.ae2craftingtime.core.CraftingRowState;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.TtcSort;
import com.ctux.ae2craftingtime.mc1201.AeKeyAmounts;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ClientStatsRequests;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.CpuTtcClient;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.ProviderLocateClick;
import com.ctux.ae2craftingtime.mc1201.StatsChatMessages;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.StatsClickHandler;
import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import com.ctux.ae2craftingtime.mc1201.TtcDetailsClick;
import com.ctux.ae2craftingtime.mc1201.TtcDetailsKeyMapping;
import com.ctux.ae2craftingtime.mc1201.TtcSortButton;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

@Mixin(CraftingCPUScreen.class)
public abstract class CraftingCPUScreenMixin<T extends CraftingCPUMenu> extends AEBaseScreen<T>
        implements StatsClickHandler {
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
    private static final int AE2CRAFTINGTIME_ROWS = 6;
    @Unique
    private int ae2craftingtime$ttcSortMode = 2;
    @Unique
    private long ae2craftingtime$lastLocateClickMs;
    @Unique
    private String ae2craftingtime$lastLocateOutputId;

    @Shadow(remap = false)
    private CraftingStatus status;

    @Shadow(remap = false)
    private Scrollbar scrollbar;

    protected CraftingCPUScreenMixin(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && ClientOptionsRuntime.enabled(OptionFeature.PROVIDER_LOCATE_CLICK)
                && ae2craftingtime$tryLocateDoubleClick(mouseX, mouseY)) {
            getMinecraft().setScreen(null);
            return true;
        }
        if (TtcDetailsClick.tryHandle(button)
                || (TtcDetailsKeyMapping.matchesMouse(button) || TtcDetailsKeyMapping.matchesResetMouse(button))
                && ae2craftingtime$handleClickedStats(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$addStatusTtcSortButton(T menu, Inventory playerInventory, Component title,
            ScreenStyle style, CallbackInfo ci) {
        if ((Object) this instanceof CraftingStatusScreen) {
            ClientStats.CACHE.clearCpuState();
            ClientStatsRequests.clear();
            ae2craftingtime$ttcSortMode = ClientOptionsRuntime.current().statusSort();
            CpuTtcClient.setSortMode((CraftingStatusMenu) menu, ae2craftingtime$ttcSortMode);
            if (ClientOptionsRuntime.enabled(OptionFeature.STATUS_SORT_CONTROL))
                addToLeftToolbar(new TtcSortButton(this::ae2craftingtime$cycleTtcSortMode,
                        () -> ae2craftingtime$ttcSortMode));
        }
    }

    @Inject(method = "postUpdate", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$invalidateOnJobChange(CraftingStatus update, CallbackInfo ci) {
        ClientStatsRequests.observeJob(update.getStartItemCount(), update.getElapsedTime());
    }

    @Group(name = "sortStatusByTtc", min = 1, max = 1)
    @ModifyArg(method = "postUpdate", at = @At(value = "INVOKE", target = "Lappeng/menu/me/crafting/CraftingStatus;<init>(ZJJJLjava/util/List;)V"), index = 4, remap = false, require = 0)
    private List<CraftingStatusEntry> ae2craftingtime$sortStatusByTtcLegacy(List<CraftingStatusEntry> entries) {
        return ae2craftingtime$sortStatusByTtc(entries);
    }

    @Group(name = "sortStatusByTtc", min = 1, max = 1)
    @ModifyArg(method = "postUpdate", at = @At(value = "INVOKE", target = "Lappeng/menu/me/crafting/CraftingStatus;<init>(ZJJJLjava/util/List;Z)V"), index = 4, remap = false, require = 0)
    private List<CraftingStatusEntry> ae2craftingtime$sortStatusByTtcWithSuspended(List<CraftingStatusEntry> entries) {
        return ae2craftingtime$sortStatusByTtc(entries);
    }

    @Unique
    private List<CraftingStatusEntry> ae2craftingtime$sortStatusByTtc(List<CraftingStatusEntry> entries) {
        if (!((Object) this instanceof CraftingStatusScreen) || ae2craftingtime$ttcSortMode == 0
                || !ClientOptionsRuntime.enabled(OptionFeature.STATUS_SORT_CONTROL)) {
            return entries;
        }

        var sorted = TtcSort.copySorted(entries, CraftingCPUScreenMixin::ae2craftingtime$seconds,
                Comparator.naturalOrder(), ae2craftingtime$ttcSortMode == 2);
        IntegrationLog.observe("ae2craftingtime", "status-sort");
        return sorted;
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$refreshArrivingTtc(CallbackInfo ci) {
        if ((Object) this instanceof CraftingStatusScreen) {
            CpuTtcClient.beginFrame((CraftingStatusMenu) menu);
        }
        if (status != null && !ae2craftingtime$sortStatusByTtc(status.getEntries()).equals(status.getEntries())) {
            postUpdate(status);
        }
    }
    @SuppressWarnings("mapping")
    @ModifyArg(
            method = "updateBeforeRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/client/gui/me/crafting/CraftingCPUScreen;setTextContent(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V",
                    remap = true),
            index = 1,
            remap = false)
    private Component ae2craftingtime$appendStatusTotalTtc(Component title) {
        if (!ClientOptionsRuntime.enabled(OptionFeature.STATUS_TOTAL)) return title;
        if (status == null || status.getRemainingItemCount() <= 0
                || status.getEntries().stream().noneMatch(entry -> entry.getActiveAmount() > 0
                        || entry.getPendingAmount() > 0)) {
            return title;
        }

        Component display = getGuiDisplayName(GuiText.CraftingStatus.text());
        var eta = TimeEstimate.formatTotal(List.of(ClientStats.totalTtcSeconds()));
        if (eta.isPresent()) {
            display = TtcComponents.rowSymbolsInTextColor(TtcText.totalTtc(eta.get())
                    .withStyle(style -> style.withColor(0x404040)), 0x404040);
            IntegrationLog.observe("ae2craftingtime", "status-total");
        }
        if (menu.isCantStoreItems()) {
            display = display.copy().append(" - ")
                    .append(GuiText.CantStoreItems.text().withStyle(ChatFormatting.RED));
        }
        return display;
    }

    @Unique
    private void ae2craftingtime$cycleTtcSortMode() {
        ae2craftingtime$ttcSortMode = (ae2craftingtime$ttcSortMode + 1) % 3;
        CpuTtcClient.setSortMode((CraftingStatusMenu) menu, ae2craftingtime$ttcSortMode);
        if (status != null) {
            postUpdate(status);
        }
    }

    @Shadow(remap = false)
    public abstract void postUpdate(CraftingStatus status);

    @Override
    public boolean ae2craftingtime$handleClickedStats(double mouseX, double mouseY, int button) {
        if (status == null) {
            return false;
        }

        var entries = status.getEntries();
        var entry = ae2craftingtime$clickedEntry(mouseX, mouseY, entries);
        if (entry == null) {
            return false;
        }

        var amount = entry.getActiveAmount() + entry.getPendingAmount();
        if (amount <= 0) {
            return false;
        }

        var key = ProfilerBridge.key(entry.getWhat());
        if (TtcDetailsKeyMapping.matchesResetMouse(button)) {
            if (!ClientOptionsRuntime.enabled(OptionFeature.RESET_HISTORY_CLICK)) return false;
            StatsChatMessages.reset(key, entry.getWhat().getDisplayName().getString());
            IntegrationLog.observe("ae2craftingtime", "status-reset");
            return true;
        }
        if (!ClientOptionsRuntime.enabled(OptionFeature.TTC_DETAILS_CLICK)) return false;
        StatsChatMessages.show(key, entry.getWhat().getDisplayName().getString(),
                AeKeyAmounts.normalize(entry.getWhat(), amount));
        IntegrationLog.observe("ae2craftingtime", "status-details");
        return true;
    }

    @Unique
    private boolean ae2craftingtime$tryLocateDoubleClick(double mouseX, double mouseY) {
        if (status == null) {
            return false;
        }
        var entry = ae2craftingtime$clickedEntry(mouseX, mouseY, status.getEntries());
        if (entry == null || entry.getWhat() == null) {
            return false;
        }
        var outputId = ProfilerBridge.key(entry.getWhat()).outputId();
        var now = System.currentTimeMillis();
        if (outputId.equals(ae2craftingtime$lastLocateOutputId)
                && now - ae2craftingtime$lastLocateClickMs < 400) {
            ae2craftingtime$lastLocateOutputId = null;
            return ProviderLocateClick.requestLocate(outputId);
        }
        ae2craftingtime$lastLocateClickMs = now;
        ae2craftingtime$lastLocateOutputId = outputId;
        return false;
    }

    @Unique
    private CraftingStatusEntry ae2craftingtime$clickedEntry(double mouseX, double mouseY,
            List<CraftingStatusEntry> entries) {
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
    private static OptionalLong ae2craftingtime$seconds(CraftingStatusEntry entry) {
        var amount = entry.getActiveAmount() + entry.getPendingAmount();
        if (amount <= 0) {
            return OptionalLong.empty();
        }

        var key = ProfilerBridge.key(entry.getWhat());
        ClientStatsRequests.requestBackground(key);
        if (CraftingRowState.blockReason(entry.getPendingAmount(), ClientStats.blockReason(key)) != null) {
            return OptionalLong.empty();
        }
        if (entry.getActiveAmount() == 0 && entry.getPendingAmount() > 0
                && ClientStats.CACHE.waitingTicks(key).isPresent()) {
            return OptionalLong.empty();
        }
        var stats = ClientStats.CACHE.get(key);
        if (stats.isEmpty()) {
            return OptionalLong.empty();
        }

        return TimeEstimate.seconds(AeKeyAmounts.normalize(entry.getWhat(), amount), stats.get());
    }
}
