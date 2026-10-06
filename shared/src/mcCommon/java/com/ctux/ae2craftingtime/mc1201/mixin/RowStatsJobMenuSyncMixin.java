package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.mc1201.RowStatsJobMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCPUMenu.class)
public abstract class RowStatsJobMenuSyncMixin {
    // Required: retire old diagnostics before AE2 broadcasts the replacement rows.
    @Inject(method = "broadcastChanges", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$syncJob(CallbackInfo ci) {
        RowStatsJobMenu.broadcast((CraftingCPUMenu) (Object) this);
    }
}
