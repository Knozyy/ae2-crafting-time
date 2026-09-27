package com.ctux.ae2craftingtime.testdriver.mixin;

import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.testdriver.ConnectionObservation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StatsNetwork.class, remap = false)
public abstract class StatsNetworkObservationMixin {
    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/StatsSnapshotS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedStatsSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.StatsSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "StatsSnapshotS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/StatsSnapshotS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentStatsSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.StatsSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "StatsSnapshotS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/CpuTtcSnapshotS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedCpuTtcSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.CpuTtcSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "CpuTtcSnapshotS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/CpuTtcSnapshotS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentCpuTtcSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.CpuTtcSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "CpuTtcSnapshotS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/ProviderHighlightS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedProviderHighlightS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "ProviderHighlightS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/ProviderHighlightS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentProviderHighlightS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "ProviderHighlightS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/PlanRecurrenceS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedPlanRecurrenceS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "PlanRecurrenceS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/PlanRecurrenceS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentPlanRecurrenceS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "PlanRecurrenceS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/PlanStoredVariantsS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedPlanStoredVariantsS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.PlanStoredVariantsS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "PlanStoredVariantsS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/PlanStoredVariantsS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentPlanStoredVariantsS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.PlanStoredVariantsS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "PlanStoredVariantsS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/ServerOptionsSnapshotS2C;)V", at = @At("HEAD"), require = 1)
    private static void attemptedServerOptionsSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.ServerOptionsSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.attempted("s2c", "ServerOptionsSnapshotS2C", player);
    }

    @Inject(method = "sendTo(Lnet/minecraft/server/level/ServerPlayer;Lcom/ctux/ae2craftingtime/mc1201/net/ServerOptionsSnapshotS2C;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentServerOptionsSnapshotS2C(ServerPlayer player, com.ctux.ae2craftingtime.mc1201.net.ServerOptionsSnapshotS2C packet, CallbackInfo ci) {
        ConnectionObservation.sent("s2c", "ServerOptionsSnapshotS2C", player);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/StatsRequestC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedStatsRequestC2S(com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "StatsRequestC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/StatsRequestC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentStatsRequestC2S(com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "StatsRequestC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/StatsChatC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedStatsChatC2S(com.ctux.ae2craftingtime.mc1201.net.StatsChatC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "StatsChatC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/StatsChatC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentStatsChatC2S(com.ctux.ae2craftingtime.mc1201.net.StatsChatC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "StatsChatC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/ProviderLocateC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedProviderLocateC2S(com.ctux.ae2craftingtime.mc1201.net.ProviderLocateC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "ProviderLocateC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/ProviderLocateC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentProviderLocateC2S(com.ctux.ae2craftingtime.mc1201.net.ProviderLocateC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "ProviderLocateC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/CpuTtcRequestC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedCpuTtcRequestC2S(com.ctux.ae2craftingtime.mc1201.net.CpuTtcRequestC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "CpuTtcRequestC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/CpuTtcRequestC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentCpuTtcRequestC2S(com.ctux.ae2craftingtime.mc1201.net.CpuTtcRequestC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "CpuTtcRequestC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/WarningPreferenceC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedWarningPreferenceC2S(com.ctux.ae2craftingtime.mc1201.net.WarningPreferenceC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "WarningPreferenceC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/WarningPreferenceC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentWarningPreferenceC2S(com.ctux.ae2craftingtime.mc1201.net.WarningPreferenceC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "WarningPreferenceC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/ServerOptionsUpdateC2S;)V", at = @At("HEAD"), require = 1)
    private static void attemptedServerOptionsUpdateC2S(com.ctux.ae2craftingtime.mc1201.net.ServerOptionsUpdateC2S packet, CallbackInfo ci) {
        ConnectionObservation.attempted("c2s", "ServerOptionsUpdateC2S", null);
    }

    @Inject(method = "sendToServer(Lcom/ctux/ae2craftingtime/mc1201/net/ServerOptionsUpdateC2S;)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V", remap = false), require = 1)
    private static void sentServerOptionsUpdateC2S(com.ctux.ae2craftingtime.mc1201.net.ServerOptionsUpdateC2S packet, CallbackInfo ci) {
        ConnectionObservation.sent("c2s", "ServerOptionsUpdateC2S", null);
    }

}
