package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.mc1201.NeoEcoDispatchObserver;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.mc1201.BlockReasonNotifier;
import com.ctux.ae2craftingtime.mc1201.DelayedNotificationServer;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic", remap = false)
public abstract class ECOCraftingCpuLogicMixin implements NeoEcoDispatchObserver {
    @Unique
    private IGrid ae2craftingtime$grid;

    @Unique
    private long ae2craftingtime$usedSlots;

    @Unique
    private int ae2craftingtime$totalSlots = 1;

    @Unique
    private boolean ae2craftingtime$inserting;

    @Unique
    private boolean ae2craftingtime$deferredFinish;

    @Unique
    private boolean ae2craftingtime$deferredSuccess;

    @Inject(method = "trySubmitJob", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$startJobAccuracy(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful()) {
            ae2craftingtime$grid = grid;
            ProfilerBridge.startJob(ProfilerBridge.networkId(grid), this, plan, ae2craftingtime$tick(),
                    System.nanoTime(), ProfilerBridge.jobOwner(source));
            IntegrationLog.cpu("neoecoae", "cpu-submit");
        }
    }

    @ModifyVariable(method = "executeCrafting", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int ae2craftingtime$captureCapacity(int maxPatterns) {
        ae2craftingtime$totalSlots = Math.max(ae2craftingtime$totalSlots, maxPatterns);
        return maxPatterns;
    }

    @Override
    public void ae2craftingtime$dispatched(AEKey what, long amount, Actionable type) {
        if (type == Actionable.MODULATE) {
            ProfilerBridge.start(ProfilerBridge.networkId(ae2craftingtime$grid), this, what, amount,
                    ae2craftingtime$tick());
            IntegrationLog.cpu("neoecoae", "cpu-dispatch");
        }
    }

    @Inject(method = "tryPushVerifiedFastPathBatch", at = @At("RETURN"), remap = false, require = 0)
    private void ae2craftingtime$observeFastPath(CallbackInfoReturnable<Number> cir) {
        IntegrationLog.positive("neoecoae", "cpu-dispatch-fastpath", cir.getReturnValue().longValue());
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$finishExpectedOutputs(CallbackInfoReturnable<Integer> cir) {
        ae2craftingtime$usedSlots = Math.min(Integer.MAX_VALUE, ae2craftingtime$usedSlots + cir.getReturnValue());
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$beginCapacity(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        ae2craftingtime$usedSlots = 0;
        ae2craftingtime$totalSlots = 1;
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$finishCapacity(IEnergyService energyService, CraftingService craftingService,
            CallbackInfo ci) {
        var tick = ae2craftingtime$tick();
        var server = ae2craftingtime$server();
        ProfilerBridge.updateCapacity(this, (int) Math.min(ae2craftingtime$usedSlots, ae2craftingtime$totalSlots),
                ae2craftingtime$totalSlots, tick);
        IntegrationLog.cpu("neoecoae", "cpu-capacity");
        DelayedNotificationServer.tick(this, ae2craftingtime$grid, this, tick, server);
    }

    @Inject(method = "insert", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$beginCompletedOutput(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (type == Actionable.MODULATE) {
            ae2craftingtime$inserting = true;
            ae2craftingtime$deferredFinish = false;
        }
    }

    @Inject(method = "insert", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$profileCompletedOutput(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (type != Actionable.MODULATE) {
            return;
        }

        var accepted = cir.getReturnValue();
        if (accepted > 0) {
            ProfilerBridge.complete(ProfilerBridge.networkId(ae2craftingtime$grid), this, what, accepted,
                    ae2craftingtime$tick());
            IntegrationLog.cpu("neoecoae", "cpu-output");
        }
        ae2craftingtime$inserting = false;
        if (ae2craftingtime$deferredFinish) {
            ProfilerBridge.finishJob(this, ae2craftingtime$deferredSuccess, ae2craftingtime$tick(), System.nanoTime(),
                    ae2craftingtime$server());
            IntegrationLog.cpu("neoecoae", "cpu-finish");
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$finishJob(boolean success, CallbackInfo ci) {
        if (ae2craftingtime$inserting) {
            ae2craftingtime$deferredFinish = true;
            ae2craftingtime$deferredSuccess = success;
        } else {
            ProfilerBridge.finishJob(this, success, ae2craftingtime$tick(), System.nanoTime(),
                    ae2craftingtime$server());
            IntegrationLog.cpu("neoecoae", "cpu-finish");
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
