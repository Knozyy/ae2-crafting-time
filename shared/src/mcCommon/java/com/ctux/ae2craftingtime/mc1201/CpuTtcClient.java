package com.ctux.ae2craftingtime.mc1201;

import appeng.menu.me.crafting.CraftingStatusMenu;
import com.ctux.ae2craftingtime.core.CpuTtcCache;
import com.ctux.ae2craftingtime.core.CpuTtcDisplayOrder;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcPacketCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class CpuTtcClient {
    private static final AtomicLong NEXT_SESSION = new AtomicLong();
    private static final CpuTtcCache CACHE = new CpuTtcCache();
    private static CraftingStatusMenu activeMenu;
    private static int sortMode = 2;
    private static long frameRevision = Long.MIN_VALUE;
    private static Map<Integer, Long> frameSeconds = Map.of();
    private static final CpuTtcDisplayOrder.State DISPLAY = new CpuTtcDisplayOrder.State();

    public static void open(CraftingStatusMenu menu) {
        if (activeMenu == menu) {
            return;
        }
        activeMenu = menu;
        sortMode = 2;
        CACHE.open(NEXT_SESSION.getAndIncrement(), menu.containerId);
        invalidateOrder();
    }

    public static void setSortMode(CraftingStatusMenu menu, int mode) {
        if (mode < 0 || mode > 2) {
            throw new IllegalArgumentException("invalid TTC sort mode");
        }
        open(menu);
        if (sortMode != mode) {
            sortMode = mode;
            CACHE.setCollectionMode(mode != 0, now());
            invalidateOrder();
        }
    }

    public static void beginFrame(CraftingStatusMenu menu) {
        open(menu);
        var now = now();
        var enabled = CpuTtcRequests.enabled();
        var views = menu.cpuList.cpus().stream().map(CpuTtcClient::view).toList();
        CACHE.observe(views, enabled && sortMode != 0, now);
        if (!enabled) {
            CACHE.channelUnavailable();
        }
        var snapshot = CACHE.snapshot(now);
        frameRevision = snapshot.revision();
        frameSeconds = enabled ? snapshot.seconds() : Map.of();
    }

    public static List<CraftingStatusMenu.CraftingCpuListEntry> displayList(CraftingStatusMenu menu) {
        var raw = menu.cpuList.cpus();
        var enabled = CpuTtcRequests.enabled();
        return DISPLAY.display(raw, CraftingStatusMenu.CraftingCpuListEntry::serial,
                cpu -> cpu.currentJob() != null, cpu -> seconds(cpu.serial()), frameRevision,
                ClientOptionsRuntime.enabled(OptionFeature.CPU_SORT_CONTROL) ? sortMode : 0, enabled);
    }

    public static boolean ttcOrderActive(CraftingStatusMenu menu) {
        open(menu);
        return ClientOptionsRuntime.enabled(OptionFeature.CPU_SORT_CONTROL)
                && CpuTtcDisplayOrder.ttcOrderActive(sortMode, CpuTtcRequests.enabled());
    }

    public static void refresh(CraftingStatusMenu menu,
            List<CraftingStatusMenu.CraftingCpuListEntry> displayed, int scroll) {
        if (!active() || !CpuTtcRequests.enabled()) {
            return;
        }
        var priorities = new ArrayList<Integer>(CpuTtcCache.MAX_PRIORITIES);
        var selected = menu.getSelectedCpuSerial();
        if (selected > 0) {
            priorities.add(selected);
        }
        displayed.stream().skip(Math.max(0, scroll)).limit(6)
                .map(CraftingStatusMenu.CraftingCpuListEntry::serial).forEach(priorities::add);
        CACHE.refresh(priorities, now()).ifPresent(request -> CpuTtcRequests.send(
                new CpuTtcPacketCodec.Request(request.containerId(), request.session(), request.sequence(),
                        request.serials())));
    }

    public static void receive(CpuTtcPacketCodec.Snapshot snapshot) {
        if (!active()) {
            clear();
            return;
        }
        CACHE.apply(snapshot.session(), snapshot.sequence(), snapshot.entries(), now());
    }

    public static OptionalLong seconds(int serial) {
        var player = Minecraft.getInstance().player;
        if (activeMenu != null && player != null && activeMenu == player.containerMenu
                && com.ctux.ae2craftingtime.core.CraftingSuspension.masksSelectedCard(
                        serial, activeMenu.getSelectedCpuSerial(), StatsNetwork.selectedSuspended()))
            return OptionalLong.empty();
        var seconds = frameSeconds.get(serial);
        return seconds == null ? OptionalLong.empty() : OptionalLong.of(seconds);
    }

    public static CpuTtcCache.CpuView view(CraftingStatusMenu.CraftingCpuListEntry cpu) {
        var job = cpu.currentJob();
        return job == null ? new CpuTtcCache.CpuView(cpu.serial(), null, 0, 0)
                : new CpuTtcCache.CpuView(cpu.serial(), ProfilerBridge.key(job.what()).outputId(), job.amount(),
                        Math.max(0, cpu.elapsedTimeNanos()));
    }

    public static boolean stillCurrent(CraftingStatusMenu menu, CpuTtcCache.CpuView drawn) {
        return menu.cpuList.cpus().stream().filter(cpu -> cpu.serial() == drawn.serial()).map(CpuTtcClient::view)
                .anyMatch(current -> CpuTtcDisplayOrder.hitCurrent(drawn, current));
    }

    public static void clear() {
        CACHE.clear();
        activeMenu = null;
        sortMode = 2;
        frameRevision = Long.MIN_VALUE;
        frameSeconds = Map.of();
        invalidateOrder();
    }

    public static void close(CraftingStatusMenu menu) {
        if (activeMenu == menu) {
            clear();
        }
    }

    private static boolean active() {
        var minecraft = Minecraft.getInstance();
        return activeMenu != null && minecraft.getConnection() != null
                && minecraft.screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() == activeMenu;
    }

    private static void invalidateOrder() {
        DISPLAY.clear();
    }

    private static long now() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private CpuTtcClient() {
    }
}
