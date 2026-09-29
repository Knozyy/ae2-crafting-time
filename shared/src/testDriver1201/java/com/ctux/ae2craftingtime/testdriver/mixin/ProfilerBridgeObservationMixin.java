package com.ctux.ae2craftingtime.testdriver.mixin;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.testdriver.DispatchObservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ProfilerBridge.class, remap = false)
public abstract class ProfilerBridgeObservationMixin {
    @Inject(method = "startJob(Ljava/lang/String;Ljava/lang/Object;Lappeng/api/networking/crafting/ICraftingPlan;JJ)V", at = @At("HEAD"))
    private static void accepted(String network, Object scope, ICraftingPlan plan, long tick, long nanos, CallbackInfo ci) {
        acceptedJob(network, scope, plan);
    }

    @Inject(method = "startJob(Ljava/lang/String;Ljava/lang/Object;Lappeng/api/networking/crafting/ICraftingPlan;JJLjava/util/UUID;)V", at = @At("HEAD"))
    private static void acceptedWithOwner(String network, Object scope, ICraftingPlan plan, long tick, long nanos,
            java.util.UUID owner, CallbackInfo ci) {
        acceptedJob(network, scope, plan);
    }

    private static void acceptedJob(String network, Object scope, ICraftingPlan plan) {
        if (plan == null || plan.finalOutput() == null) return;
        var finalOutput = plan.finalOutput().what();
        long amount = plan.emittedItems().get(finalOutput);
        for (var pattern : plan.patternTimes().entrySet()) {
            for (var output : pattern.getKey().getOutputs()) {
                if (output.what().equals(finalOutput)) amount += output.amount() * pattern.getValue();
            }
        }
        DispatchObservation.accepted(network, finalOutput.getId().toString(), scope, amount);
    }

    @Inject(method = "start(Ljava/lang/String;Ljava/lang/Object;Lappeng/api/stacks/AEKey;JJ)V", at = @At("HEAD"))
    private static void dispatched(String network, Object scope, AEKey key, long amount, long tick, CallbackInfo ci) {
        if (key == null) return;
        DispatchObservation.amount(scope, key.getId().toString(), amount, true);
    }

    @Inject(method = "complete(Ljava/lang/String;Ljava/lang/Object;Lappeng/api/stacks/AEKey;JJLnet/minecraft/server/MinecraftServer;)V", at = @At("HEAD"))
    private static void returned(String network, Object scope, AEKey key, long amount, long tick,
            net.minecraft.server.MinecraftServer server, CallbackInfo ci) {
        if (key == null) return;
        DispatchObservation.amount(scope, key.getId().toString(), amount, false);
    }

    @Inject(method = "finishJob(Ljava/lang/Object;ZJJLnet/minecraft/server/MinecraftServer;)V", at = @At("HEAD"))
    private static void finished(Object scope, boolean success, long tick, long nanos,
            net.minecraft.server.MinecraftServer server, CallbackInfo ci) {
        DispatchObservation.finished(scope, success);
    }

    @Inject(method = "updateCapacity", at = @At("HEAD"))
    private static void tick(Object scope, int used, int total, long tick, CallbackInfo ci) {
        DispatchObservation.tick(scope);
    }
}
