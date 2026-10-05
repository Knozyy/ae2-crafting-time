package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.mc1201.BlockReasonNotifier;
import com.ctux.ae2craftingtime.mc1201.DelayedNotificationServer;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.ProviderDispatchObserver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {
    @Shadow(remap = false)
    @Final
    private CraftingCPUCluster cluster;

    @Shadow(remap = false)
    @Final
    private int[] usedOps;

    @Unique
    private IPatternDetails ae2craftingtime$dispatchPattern;

    @Unique
    private ProviderDispatchObserver ae2craftingtime$dispatchObserver;

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$beginDispatchEvaluation(int operations, CraftingService craftingService,
            IEnergyService energyService, net.minecraft.world.level.Level level,
            CallbackInfoReturnable<Integer> cir) {
        ae2craftingtime$finishDispatchEvaluation();
    }

    // Capture the execution local even when an addon supplies providers without calling the original lookup.
    @ModifyVariable(method = "executeCrafting", at = @At("STORE"), ordinal = 0, remap = false)
    private IPatternDetails ae2craftingtime$captureDispatchPattern(IPatternDetails pattern) {
        ae2craftingtime$finishDispatchEvaluation();
        ae2craftingtime$dispatchPattern = pattern;
        ae2craftingtime$dispatchObserver = !ProfilerBridge.trackingEnabled(cluster) ? null : new ProviderDispatchObserver(
                ProfilerBridge.networkId(cluster.getGrid()), cluster.getGrid(),
                cluster, pattern, cluster.getLevel().getGameTime());
        return pattern;
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$clearDispatchPattern(CallbackInfoReturnable<Integer> cir) {
        ae2craftingtime$finishDispatchEvaluation();
        ae2craftingtime$dispatchPattern = null;
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lappeng/api/networking/energy/IEnergyService;extractAEPower(DLappeng/api/config/Actionable;Lappeng/api/config/PowerMultiplier;)D"),
            remap = false)
    private double ae2craftingtime$observeDispatchPower(IEnergyService energy, double required,
            Actionable mode, PowerMultiplier multiplier) {
        var extracted = energy.extractAEPower(required, mode, multiplier);
        if (ProfilerBridge.trackingEnabled(cluster)) ProfilerBridge.observeDispatchPower(ProfilerBridge.networkId(cluster.getGrid()), cluster,
                ae2craftingtime$dispatchPattern, required, extracted, cluster.getLevel().getGameTime());
        return extracted;
    }

    // Observe the selected providers after addons wrap or replace the lookup.
    @Redirect(
            method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;"),
            remap = false)
    private Iterator<ICraftingProvider> ae2craftingtime$observeProviders(Iterable<ICraftingProvider> providers) {
        return ae2craftingtime$dispatchObserver == null
                ? providers.iterator()
                : ae2craftingtime$dispatchObserver.iterator(providers);
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;isBusy()Z"), remap = false)
    private boolean ae2craftingtime$observeProviderBusy(ICraftingProvider provider) {
        return ae2craftingtime$dispatchObserver == null
                ? provider.isBusy()
                : ae2craftingtime$dispatchObserver.busy(provider);
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"),
            remap = false)
    private boolean ae2craftingtime$observeProviderPush(ICraftingProvider provider, IPatternDetails pattern,
            appeng.api.stacks.KeyCounter[] input, Operation<Boolean> original) {
        return ae2craftingtime$dispatchObserver == null
                ? original.call(provider, pattern, input)
                : ae2craftingtime$dispatchObserver.push(provider, pattern, input, original);
    }

    @Unique
    private void ae2craftingtime$finishDispatchEvaluation() {
        if (ae2craftingtime$dispatchObserver != null) {
            ae2craftingtime$dispatchObserver.finish();
            ae2craftingtime$dispatchObserver = null;
        }
    }

    @Redirect(
            method = "executeCrafting",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/ListCraftingInventory;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)V"),
            remap = false)
    private void ae2craftingtime$profileExpectedOutput(ListCraftingInventory inventory, AEKey what, long amount,
            Actionable type) {
        inventory.insert(what, amount, type);
        if (type == Actionable.MODULATE && amount > 0 && ProfilerBridge.trackingEnabled(cluster)) {
            ProfilerBridge.start(ProfilerBridge.networkId(cluster.getGrid()), cluster, what, amount,
                    cluster.getLevel().getGameTime());
            IntegrationLog.cpu("ae2craftingtime", "cpu-dispatch");
        }
    }

    @Inject(method = "insert", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$profileCompletedOutput(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (type != Actionable.MODULATE || what == null || amount <= 0 || !ProfilerBridge.trackingEnabled(cluster)) {
            return;
        }

        var waiting = ((CraftingCpuLogic) (Object) this).getWaitingFor(what);
        var accepted = Math.min(amount, waiting);
        if (accepted > 0) {
            ProfilerBridge.complete(ProfilerBridge.networkId(cluster.getGrid()), cluster, what, accepted,
                    cluster.getLevel().getGameTime(), cluster.getLevel().getServer());
            IntegrationLog.cpu("ae2craftingtime", "cpu-output");
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$clearPendingOutputs(boolean success, CallbackInfo ci) {
        ProfilerBridge.finishJob(cluster, success, cluster.getLevel().getGameTime(), System.nanoTime(),
                cluster.getLevel().getServer());
        IntegrationLog.cpu("ae2craftingtime", "cpu-finish");
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$trackParallelCapacity(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo ci) {
        var totalSlots = cluster.getCoProcessors() + 1;
        var usedSlots = Math.min(totalSlots, usedOps[0] + usedOps[1] + usedOps[2]);
        var tick = cluster.getLevel().getGameTime();
        var server = cluster.getLevel().getServer();
        ProfilerBridge.updateCapacity(cluster, usedSlots, totalSlots, tick);
        IntegrationLog.cpu("ae2craftingtime", "cpu-capacity");
        DelayedNotificationServer.tick(cluster, cluster.getGrid(), this, tick, server);
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"), remap = false)
    private void ae2craftingtime$startJobAccuracy(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful() && ProfilerBridge.trackingEnabled(cluster)) {
            ProfilerBridge.startJob(ProfilerBridge.networkId(grid), cluster, plan, cluster.getLevel().getGameTime(),
                    System.nanoTime(), ProfilerBridge.jobOwner(source));
            IntegrationLog.cpu("ae2craftingtime", "cpu-submit");
        }
    }
}
