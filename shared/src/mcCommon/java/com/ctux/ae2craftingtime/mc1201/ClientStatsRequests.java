package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.StatsRequestQueue;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;
import net.minecraft.client.Minecraft;

public final class ClientStatsRequests {
    private static final StatsRequestQueue QUEUE = new StatsRequestQueue();

    public static void request(ProfileKey key) { request(key, true); }
    public static void requestBackground(ProfileKey key) { request(key, false); }

    private static void request(ProfileKey key, boolean visible) {
        if (prepare()) QUEUE.request(key, visible, now());
    }

    public static void tick() {
        if (!prepare()) return;
        var keys = QUEUE.drain(now());
        if (!keys.isEmpty()) StatsNetwork.sendToServer(new StatsRequestC2S(
                keys.stream().map(ProfileKey::outputId).distinct().toList()));
    }

    private static boolean prepare() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen == null || !StatsNetwork.canSend()) {
            clear();
            return false;
        }
        QUEUE.context(minecraft.screen, StatsRequestContext.cpuContext(minecraft.player.containerMenu));
        return true;
    }

    private static long now() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }
    public static void clear() { QUEUE.clear(); }
    private ClientStatsRequests() { }
}
