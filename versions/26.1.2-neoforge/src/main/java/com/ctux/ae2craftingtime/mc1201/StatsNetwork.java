package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcRequestC2S;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.ProviderLocateC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsChatC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceS2C;
import com.ctux.ae2craftingtime.mc1201.net.PlanStoredVariantsS2C;
import com.ctux.ae2craftingtime.mc1201.net.WarningPreferenceC2S;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsUpdateC2S;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class StatsNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        IntegrationLog.required("network-registration", () -> registerPayloads(event));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("22").optional();
        registrar.playToServer(StatsRequestC2S.TYPE, StatsRequestC2S.STREAM_CODEC, StatsRequestC2S::handle);
        registrar.playToServer(StatsChatC2S.TYPE, StatsChatC2S.STREAM_CODEC, StatsChatC2S::handle);
        registrar.playToClient(StatsSnapshotS2C.TYPE, StatsSnapshotS2C.STREAM_CODEC, StatsSnapshotS2C::handle);
        registrar.playToClient(ProviderHighlightS2C.TYPE, ProviderHighlightS2C.STREAM_CODEC,
                ProviderHighlightS2C::handle);
        registrar.playToServer(ProviderLocateC2S.TYPE, ProviderLocateC2S.STREAM_CODEC, ProviderLocateC2S::handle);
        registrar.playToServer(CpuTtcRequestC2S.TYPE, CpuTtcRequestC2S.STREAM_CODEC, CpuTtcRequestC2S::handle);
        registrar.playToClient(CpuTtcSnapshotS2C.TYPE, CpuTtcSnapshotS2C.STREAM_CODEC, CpuTtcSnapshotS2C::handle);
        registrar.playToClient(PlanRecurrenceS2C.TYPE, PlanRecurrenceS2C.STREAM_CODEC, PlanRecurrenceS2C::handle);
        registrar.playToClient(PlanStoredVariantsS2C.TYPE, PlanStoredVariantsS2C.STREAM_CODEC, PlanStoredVariantsS2C::handle);
        registrar.playToServer(WarningPreferenceC2S.TYPE, WarningPreferenceC2S.STREAM_CODEC, WarningPreferenceC2S::handle);
        registrar.playToClient(ServerOptionsSnapshotS2C.TYPE, ServerOptionsSnapshotS2C.STREAM_CODEC, ServerOptionsSnapshotS2C::handle);
        registrar.playToServer(ServerOptionsUpdateC2S.TYPE, ServerOptionsUpdateC2S.STREAM_CODEC, ServerOptionsUpdateC2S::handle);
    }

    public static void sendTo(ServerPlayer player, StatsSnapshotS2C packet) {
        if (canSend(player, StatsSnapshotS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTo(ServerPlayer player, ProviderHighlightS2C packet) {
        if (canSend(player, ProviderHighlightS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTo(ServerPlayer player, CpuTtcSnapshotS2C packet) {
        if (canSend(player, CpuTtcSnapshotS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void sendTo(ServerPlayer player, ServerOptionsSnapshotS2C packet) {
        if (canSend(player, ServerOptionsSnapshotS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet);
    }
    public static void sendTo(ServerPlayer player, PlanRecurrenceS2C packet) { if (canSend(player, PlanRecurrenceS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet); }
    public static void sendTo(ServerPlayer player, PlanStoredVariantsS2C packet) { if (canSend(player, PlanStoredVariantsS2C.TYPE.id())) PacketDistributor.sendToPlayer(player, packet); }

    public static void sendToServer(StatsChatC2S packet) {
        if (canSend(StatsChatC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(ProviderLocateC2S packet) {
        if (canSend(ProviderLocateC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet);
    }

    public static void sendToServer(WarningPreferenceC2S packet) {
        if (canSend(WarningPreferenceC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet);
    }
    public static void sendToServer(ServerOptionsUpdateC2S packet) { if (canSend(ServerOptionsUpdateC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet); }
    public static void sendToServer(StatsRequestC2S packet) { if (canSend(StatsRequestC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet); }
    public static void sendToServer(CpuTtcRequestC2S packet) { if (canSend(CpuTtcRequestC2S.TYPE.id())) ClientPacketDistributor.sendToServer(packet); }

    public static boolean canSend() { return canSend(StatsRequestC2S.TYPE.id()); }
    public static boolean canSendCpuTtc() { return canSend(CpuTtcRequestC2S.TYPE.id()); }

    private static boolean canSend(net.minecraft.resources.Identifier id) {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && NetworkRegistry.hasChannel(listener, id);
    }

    private static boolean canSend(ServerPlayer player, net.minecraft.resources.Identifier id) {
        return NetworkRegistry.hasChannel(player.connection, id);
    }

    public static boolean canSend(ServerPlayer player) { return canSend(player, StatsSnapshotS2C.TYPE.id()); }

    private StatsNetwork() {
    }
}
