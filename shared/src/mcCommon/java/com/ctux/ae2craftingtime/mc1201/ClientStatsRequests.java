package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.RowStatsRequestId;
import com.ctux.ae2craftingtime.core.RowStatsRequests;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;
import net.minecraft.client.Minecraft;

public final class ClientStatsRequests {
    private static final RowStatsRequests REQUESTS = new RowStatsRequests();

    public static void request(ProfileKey key) { request(key, true); }
    public static void requestBackground(ProfileKey key) { request(key, false); }

    private static void request(ProfileKey key, boolean visible) {
        if (prepare()) REQUESTS.request(key, visible, now());
    }

    public static void tick() {
        if (!prepare()) return;
        var keys = REQUESTS.drain(now());
        if (!keys.isEmpty()) StatsNetwork.sendToServer(new StatsRequestC2S(
                keys.stream().map(ProfileKey::outputId).distinct().toList(),
                REQUESTS.next(StatsRequestContext.cpuContext(Minecraft.getInstance().player.containerMenu))));
    }

    /** A job replaced on the same CPU leaves menu and CPU selection unchanged, so it is detected from the status. */
    public static void observeJob(long startItemCount, long elapsedTime) {
        if (REQUESTS.observeJob(startItemCount, elapsedTime)) {
            ClientStats.CACHE.clearCpuState();
            ClientStats.clear();
        }
    }

    public static boolean acceptSnapshot(RowStatsRequestId requestId, long responseCpuContext) {
        if (!prepare()) return false;
        return REQUESTS.accept(requestId,
                StatsRequestContext.cpuContext(Minecraft.getInstance().player.containerMenu), responseCpuContext);
    }

    private static boolean prepare() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen == null || !StatsNetwork.canSend()) {
            clear();
            return false;
        }
        if (REQUESTS.prepare(new Context(minecraft.getConnection(), minecraft.screen, minecraft.player.containerMenu),
                StatsRequestContext.cpuContext(minecraft.player.containerMenu))) {
            ClientStats.clear();
        }
        return true;
    }

    private static long now() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }
    public static void clear() { REQUESTS.clear(); }
    private record Context(Object connection, Object screen, Object menu) { }
    private ClientStatsRequests() { }
}
