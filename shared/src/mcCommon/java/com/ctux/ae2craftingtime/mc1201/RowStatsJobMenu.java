package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.RowStatsJob;

public interface RowStatsJobMenu {
    static void broadcast(appeng.menu.me.crafting.CraftingCPUMenu menu) {
        if (!(menu.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.containerMenu != menu || !StatsNetwork.canSend(player)) return;
        var state = (RowStatsJobMenu) menu;
        var next = new RowStatsJob(StatsRequestContext.cpuContext(menu),
                StatsRequestContext.currentJobId(StatsRequestContext.current(player).craftingCpu()));
        if (!next.equals(state.ae2craftingtime$rowStatsJob())) {
            state.ae2craftingtime$rowStatsJob(next);
            StatsNetwork.sendTo(player, new com.ctux.ae2craftingtime.mc1201.net.RowStatsJobS2C(next));
        }
    }

    RowStatsJob ae2craftingtime$rowStatsJob();
    void ae2craftingtime$rowStatsJob(RowStatsJob job);
}
