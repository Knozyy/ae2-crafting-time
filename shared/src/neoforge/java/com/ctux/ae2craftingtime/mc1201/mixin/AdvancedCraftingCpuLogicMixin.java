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
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.mc1201.BlockReasonNotifier;
import com.ctux.ae2craftingtime.mc1201.DelayedNotificationServer;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.IntegrationLog;
import com.ctux.ae2craftingtime.mc1201.ProviderDispatchObserver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedCraftingCpuLogicMixin {
    @Shadow
    @Final
    private AdvCraftingCPU cpu;

    @Shadow
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

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"),
            remap = false)
    private Iterable<ICraftingProvider> ae2craftingtime$beginProviderObservation(CraftingService service,
            IPatternDetails pattern, Operation<Iterable<ICraftingProvider>> original) {
        ae2craftingtime$finishDispatchEvaluation();
        ae2craftingtime$dispatchPattern = pattern;
        ae2craftingtime$dispatchObserver = !ProfilerBridge.trackingEnabled(cpu) ? null : new ProviderDispatchObserver(
                ProfilerBridge.networkId(cpu.getGrid()), cpu.getGrid(),
                cpu, pattern, cpu.getLevel().getGameTime());
        return original.call(service, pattern);
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
        if (ProfilerBridge.trackingEnabled(cpu)) ProfilerBridge.observeDispatchPower(ProfilerBridge.networkId(cpu.getGrid()), cpu,
                ae2craftingtime$dispatchPattern, required, extracted, cpu.getLevel().getGameTime());
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
        if (type == Actionable.MODULATE && amount > 0 && ProfilerBridge.trackingEnabled(cpu)) {
            ProfilerBridge.start(ProfilerBridge.networkId(cpu.getGrid()), cpu, what, amount,
                    cpu.getLevel().getGameTime());
            IntegrationLog.cpu("advanced_ae", "cpu-dispatch");
        }
    }

    @Inject(method = "insert", at = @At("HEAD"), remap = false)
    private void ae2craftingtime$profileCompletedOutput(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (type != Actionable.MODULATE || what == null || amount <= 0 || !ProfilerBridge.trackingEnabled(cpu)) {
            return;
        }

        var waiting = ((AdvCraftingCPULogic) (Object) this).getWaitingFor(what);
        var accepted = Math.min(amount, waiting);
        if (accepted > 0) {
            ProfilerBridge.complete(ProfilerBridge.networkId(cpu.getGrid()), cpu, what, accepted,
                    cpu.getLevel().getGameTime());
            IntegrationLog.cpu("advanced_ae", "cpu-output");
        }
    }

    @Inject(method = "finishJob", at = @At("HEAD"), remap = false, require = 0)
    private void ae2craftingtime$clearPendingOutputs(boolean success, CallbackInfo ci) {
        ProfilerBridge.finishJob(cpu, success, cpu.getLevel().getGameTime(), System.nanoTime(),
                cpu.getLevel().getServer());
        IntegrationLog.cpu("advanced_ae", "cpu-finish");
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"), remap = false, require = 0)
    private void ae2craftingtime$startJobAccuracy(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful() && ProfilerBridge.trackingEnabled(cpu)) {
            ProfilerBridge.startJob(ProfilerBridge.networkId(grid), cpu, plan, cpu.getLevel().getGameTime(),
                    System.nanoTime(), ProfilerBridge.jobOwner(source));
            IntegrationLog.cpu("advanced_ae", "cpu-submit");
        }
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"), remap = false, require = 0)
    private void ae2craftingtime$trackParallelCapacity(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo ci) {
        var totalSlots = cpu.getCoProcessors() + 1;
        var usedSlots = Math.min(totalSlots, usedOps[0] + usedOps[1] + usedOps[2]);
        var tick = cpu.getLevel().getGameTime();
        var server = cpu.getLevel().getServer();
        ProfilerBridge.updateCapacity(cpu, usedSlots, totalSlots, tick);
        IntegrationLog.cpu("advanced_ae", "cpu-capacity");
        DelayedNotificationServer.tick(cpu, cpu.getGrid(), this, tick, server);
    }
}
