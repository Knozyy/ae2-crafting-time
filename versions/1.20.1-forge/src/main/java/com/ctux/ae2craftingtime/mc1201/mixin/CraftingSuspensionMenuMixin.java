package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.core.CraftingSuspension;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionAccess;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionMenuState;
import com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.StatsRequestContext;
import com.ctux.ae2craftingtime.mc1201.net.CraftingSuspensionS2C;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCPUMenu.class)
public abstract class CraftingSuspensionMenuMixin implements CraftingSuspensionMenuState {
    @Unique private CraftingSuspension.Snapshot ae2craftingtime$snapshot;

    @Override
    public CraftingSuspension.Snapshot ae2craftingtime$suspensionSnapshot() {
        var menu = (CraftingCPUMenu) (Object) this;
        var snapshot = ae2craftingtime$snapshot;
        return snapshot != null && snapshot.containerId() == menu.containerId
                && snapshot.cpuContext() == StatsRequestContext.cpuContext(menu) ? snapshot : null;
    }

    @Override
    public void ae2craftingtime$acceptSuspension(CraftingSuspension.Snapshot snapshot) {
        ae2craftingtime$snapshot = snapshot;
    }

    @Inject(method = "broadcastChanges", at = @At("RETURN"))
    private void ae2craftingtime$broadcast(CallbackInfo ci) {
        var menu = (CraftingCPUMenu) (Object) this;
        if (!(menu.getPlayer() instanceof ServerPlayer player) || player.containerMenu != menu
                || !StatsNetwork.canSend(player)) return;
        var cpu = ((CraftingCPUMenuAccessor) menu).ae2craftingtime$getCpu();
        var standard = cpu != null && cpu.getClass() == appeng.me.cluster.implementations.CraftingCPUCluster.class
                && cpu.craftingLogic.getClass() == appeng.crafting.execution.CraftingCpuLogic.class;
        var logic = standard ? (CraftingSuspensionAccess) cpu.craftingLogic : null;
        var jobId = logic == null ? CraftingSuspension.NO_JOB : logic.ae2craftingtime$jobId();
        var enabled = standard && ServerOptionsRuntime.enabled(OptionFeature.CRAFTING_SUSPENSION);
        var next = new CraftingSuspension.Snapshot(menu.containerId, StatsRequestContext.cpuContext(menu),
                jobId, standard, enabled, enabled && logic.ae2craftingtime$suspended());
        if (!Objects.equals(next, ae2craftingtime$snapshot)) {
            ae2craftingtime$snapshot = next;
            StatsNetwork.sendTo(player, new CraftingSuspensionS2C(next));
        }
    }
}
