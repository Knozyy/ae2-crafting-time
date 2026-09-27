package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ClientStatsCache;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import appeng.menu.me.crafting.CraftingStatusMenu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public final class ClientStats {
    public static final ClientStatsCache CACHE = new ClientStatsCache();
    private static final Map<ProfileKey, Long> NETWORK_AMOUNTS = new HashMap<>();

    public static OptionalLong networkAmount(ProfileKey key) {
        var amount = NETWORK_AMOUNTS.get(key);
        return amount == null ? OptionalLong.empty() : OptionalLong.of(amount);
    }

    public static void replaceNetworkAmounts(List<String> requestedKeys, Map<String, Long> amounts) {
        for (var key : requestedKeys) {
            NETWORK_AMOUNTS.remove(new ProfileKey(key));
        }
        amounts.forEach((key, amount) -> NETWORK_AMOUNTS.put(new ProfileKey(key), amount));
    }

    public static void replaceWaitingTicks(List<String> requestedKeys, Map<String, Long> values) {
        var waiting = new HashMap<ProfileKey, Long>();
        values.forEach((key, ticks) -> waiting.put(new ProfileKey(key), ticks));
        CACHE.replaceWaiting(requestedKeys.stream().map(ProfileKey::new).toList(), waiting);
    }

    public static void replaceBlockReasons(List<String> requestedKeys, Map<String, CraftingBlockReason> values, long cpuContext) {
        CACHE.replaceBlockReasons(requestedKeys.stream().map(ProfileKey::new).toList(),
                values.entrySet().stream().collect(Collectors.toMap(entry -> new ProfileKey(entry.getKey()), Map.Entry::getValue)), cpuContext);
    }

    public static CraftingBlockReason blockReason(ProfileKey key) {
        var context = Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                ? StatsRequestContext.cpuContext(screen.getMenu()) : -1;
        return CACHE.blockReason(key, context);
    }

    public static OptionalLong totalTtcSeconds() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() instanceof CraftingStatusMenu status && CpuTtcRequests.enabled()) {
            return CpuTtcClient.seconds(status.getSelectedCpuSerial());
        }
        var context = Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                ? StatsRequestContext.cpuContext(screen.getMenu()) : -1;
        return CACHE.totalTtcSeconds(context);
    }

    public static void replaceTotalTtcSeconds(OptionalLong value, long cpuContext) {
        CACHE.replaceTotalTtcSeconds(value, cpuContext);
    }

    public static void clear() {
        CACHE.clear();
        NETWORK_AMOUNTS.clear();
    }

    private ClientStats() {
    }
}
