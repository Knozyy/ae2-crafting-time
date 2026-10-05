package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.JobTransitionDetector;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.RowStatsRequestId;
import com.ctux.ae2craftingtime.core.RowStatsSession;
import com.ctux.ae2craftingtime.core.StatsRequestQueue;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;
import net.minecraft.client.Minecraft;

public final class ClientStatsRequests {
    private static final StatsRequestQueue QUEUE = new StatsRequestQueue();
    private static final RowStatsSession SESSION = new RowStatsSession();
    private static final JobTransitionDetector JOB = new JobTransitionDetector();

    public static void request(ProfileKey key) { request(key, true); }
    public static void requestBackground(ProfileKey key) { request(key, false); }

    private static void request(ProfileKey key, boolean visible) {
        if (prepare()) QUEUE.request(key, visible, now());
    }

    public static void tick() {
        if (!prepare()) return;
        var keys = QUEUE.drain(now());
        if (!keys.isEmpty()) StatsNetwork.sendToServer(new StatsRequestC2S(
                keys.stream().map(ProfileKey::outputId).distinct().toList(),
                SESSION.next(StatsRequestContext.cpuContext(Minecraft.getInstance().player.containerMenu))));
    }

    /** A job replaced on the same CPU leaves menu and CPU selection unchanged, so it is detected from the status. */
    public static void observeJob(long startItemCount, long elapsedTime) {
        if (JOB.observe(startItemCount, elapsedTime)) {
            ClientStats.CACHE.clearCpuState();
            QUEUE.clear();
            SESSION.clear();
            ClientStats.clear();
        }
    }

    public static boolean acceptSnapshot(RowStatsRequestId requestId, long responseCpuContext) {
        if (!prepare()) return false;
        return SESSION.accept(requestId,
                StatsRequestContext.cpuContext(Minecraft.getInstance().player.containerMenu), responseCpuContext);
    }

    private static boolean prepare() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen == null || !StatsNetwork.canSend()) {
            clear();
            return false;
        }
        if (QUEUE.context(new Context(minecraft.getConnection(), minecraft.screen, minecraft.player.containerMenu),
                StatsRequestContext.cpuContext(minecraft.player.containerMenu))) {
            SESSION.clear();
            JOB.clear();
            ClientStats.clear();
        }
        return true;
    }

    private static long now() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }
    public static void clear() {
        QUEUE.clear();
        SESSION.clear();
        JOB.clear();
    }
    private record Context(Object connection, Object screen, Object menu) { }
    private ClientStatsRequests() { }
}
