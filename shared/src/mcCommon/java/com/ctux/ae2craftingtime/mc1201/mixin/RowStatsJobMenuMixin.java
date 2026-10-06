package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.core.RowStatsJob;
import com.ctux.ae2craftingtime.mc1201.RowStatsJobMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CraftingCPUMenu.class)
public abstract class RowStatsJobMenuMixin implements RowStatsJobMenu {
    @Unique private RowStatsJob ae2craftingtime$job = new RowStatsJob(-1, RowStatsJob.NO_JOB);

    public RowStatsJob ae2craftingtime$rowStatsJob() { return ae2craftingtime$job; }
    public void ae2craftingtime$rowStatsJob(RowStatsJob job) { ae2craftingtime$job = job; }

}
