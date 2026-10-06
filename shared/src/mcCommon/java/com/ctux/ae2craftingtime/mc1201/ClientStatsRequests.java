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

    public static void receiveJob(com.ctux.ae2craftingtime.core.RowStatsJob job) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu.containerId != (int) (job.cpuContext() >> 32)
                || !(player.containerMenu instanceof RowStatsJobMenu menu)) return;
        menu.ae2craftingtime$rowStatsJob(job);
        prepare();
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
        var menu = minecraft.player.containerMenu;
        var jobId = menu instanceof RowStatsJobMenu jobMenu
                ? jobMenu.ae2craftingtime$rowStatsJob().forContext(StatsRequestContext.cpuContext(menu))
                : com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB;
        if (REQUESTS.observeJob(jobId)) ClientStats.clear();
        return true;
    }

    private static long now() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }
    public static void clear() { REQUESTS.clear(); }
    private record Context(Object connection, Object screen, Object menu) { }
    private ClientStatsRequests() { }
}
