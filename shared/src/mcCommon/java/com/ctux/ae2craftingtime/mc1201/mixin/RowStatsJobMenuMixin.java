package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.core.RowStatsJob;
import com.ctux.ae2craftingtime.mc1201.RowStatsJobMenu;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.StatsRequestContext;
import com.ctux.ae2craftingtime.mc1201.net.RowStatsJobS2C;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCPUMenu.class)
public abstract class RowStatsJobMenuMixin implements RowStatsJobMenu {
    @Unique private RowStatsJob ae2craftingtime$job = new RowStatsJob(-1, RowStatsJob.NO_JOB);

    public RowStatsJob ae2craftingtime$rowStatsJob() { return ae2craftingtime$job; }
    public void ae2craftingtime$rowStatsJob(RowStatsJob job) { ae2craftingtime$job = job; }

    // Required ordering: retire old diagnostics before AE2 sends the replacement's rows.
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2craftingtime$syncJob(CallbackInfo ci) {
        var menu = (CraftingCPUMenu) (Object) this;
        if (!(menu.getPlayer() instanceof ServerPlayer player) || player.containerMenu != menu
                || !StatsNetwork.canSend(player)) return;
        var next = new RowStatsJob(StatsRequestContext.cpuContext(menu),
                StatsRequestContext.currentJobId(StatsRequestContext.current(player).craftingCpu()));
        if (!next.equals(ae2craftingtime$job)) {
            ae2craftingtime$job = next;
            StatsNetwork.sendTo(player, new RowStatsJobS2C(next));
        }
    }
}
