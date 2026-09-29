package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.mc1201.BlockReasonNotifier;
import com.ctux.ae2craftingtime.mc1201.DelayedNotificationServer;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic", remap = false)
public abstract class Ae2LtTimeWheelCraftingCpuLogicMixin {
    @Unique
    private static Method ae2craftingtime$successfulDispatches;

    @Unique
    private IGrid ae2craftingtime$grid;

    @Inject(method = "trySubmitJob", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$startJobAccuracy(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful()) {
            ae2craftingtime$grid = grid;
            ProfilerBridge.startJob(ProfilerBridge.networkId(grid), this, plan, ae2craftingtime$tick(),
                    System.nanoTime(), ProfilerBridge.jobOwner(source));
            IntegrationLog.cpu("ae2lt", "cpu-submit");
        }
    }

    @Inject(
            method = "insertWaitingFor(Lcom/moakiee/ae2lt/crafting/timewheel/"
                    + "Ae2LtTimeWheelCraftingCpuLogic$TimeWheelJob;Lappeng/api/stacks/AEKey;J)V",
            at = @At("HEAD"),
            remap = false)
    private void ae2craftingtime$profileExpectedOutput(@Coerce Object activeJob, AEKey what, long amount,
            CallbackInfo ci) {
        if (amount > 0) {
            ProfilerBridge.start(ProfilerBridge.networkId(ae2craftingtime$grid), this, what, amount,
                    ae2craftingtime$tick());
            IntegrationLog.cpu("ae2lt", "cpu-dispatch");
        }
    }

    @Inject(
            method = "extractWaitingFor(Lcom/moakiee/ae2lt/crafting/timewheel/"
                    + "Ae2LtTimeWheelCraftingCpuLogic$TimeWheelJob;Lappeng/api/stacks/AEKey;J)J",
            at = @At("RETURN"),
            remap = false)
    private void ae2craftingtime$profileCompletedOutput(@Coerce Object activeJob, AEKey what, long amount,
            CallbackInfoReturnable<Long> cir) {
        // Standalone final outputs complete waiting demand but fall through to ME storage: insert returns zero.
        ProfilerBridge.complete(ProfilerBridge.networkId(ae2craftingtime$grid), this, what, cir.getReturnValue(),
                ae2craftingtime$tick());
        IntegrationLog.positive("ae2lt", "cpu-output", cir.getReturnValue());
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$finishJob(boolean success, CallbackInfo ci) {
        ProfilerBridge.finishJob(this, success, ae2craftingtime$tick(), System.nanoTime(),
                ae2craftingtime$server());
        IntegrationLog.cpu("ae2lt", "cpu-finish");
    }

    @Inject(
            method = "tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;"
                    + "Lappeng/me/service/CraftingService;IJLcom/moakiee/thunderbolt/core/crafting/batch/"
                    + "TickProviderDispatchSchedule;)Lcom/moakiee/ae2lt/crafting/timewheel/"
                    + "Ae2LtTimeWheelCraftingCpuLogic$TickUsage;",
            at = @At("RETURN"),
            remap = false)
    private void ae2craftingtime$trackParallelCapacity(IEnergyService energyService, CraftingService craftingService,
            int maxOps, long maxCopies, @Coerce Object dispatchSchedule, CallbackInfoReturnable<?> cir) {
        var tick = ae2craftingtime$tick();
        var server = ae2craftingtime$server();
        ProfilerBridge.updateCapacity(this, Math.min(maxOps, ae2craftingtime$successfulDispatches(cir.getReturnValue())),
                maxOps, tick);
        IntegrationLog.cpu("ae2lt", "cpu-capacity");
        DelayedNotificationServer.tick(this, ae2craftingtime$grid, this, tick, server);
    }

    @Unique
    private static int ae2craftingtime$successfulDispatches(Object usage) {
        try {
            if (ae2craftingtime$successfulDispatches == null) {
                ae2craftingtime$successfulDispatches = usage.getClass().getMethod("successfulDispatches");
            }
            return ((Number) ae2craftingtime$successfulDispatches.invoke(usage)).intValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AE2 Lightning Tech TickUsage API changed", e);
        }
    }

    @Unique
    private long ae2craftingtime$tick() {
        return TickHandler.instance().getCurrentTick();
    }

    @Unique
    private net.minecraft.server.MinecraftServer ae2craftingtime$server() {
        try {
            var grid = ae2craftingtime$grid;
            if (grid == null || grid.getPivot() == null) {
                return null;
            }
            return grid.getPivot().getLevel().getServer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
