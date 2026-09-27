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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@SuppressWarnings({ "deprecation", "removal" })
public final class StatsNetwork {
    private static final String PROTOCOL = "23";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ae2CraftingTime.MOD_ID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals),
            NetworkRegistry.acceptMissingOr(PROTOCOL::equals));

    public static boolean canSend() {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        return connection != null && CHANNEL.isRemotePresent(connection.getConnection());
    }

    public static boolean canSendCpuTtc() { return canSend(); }

    public static boolean canSend(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void register() {
        var id = 0;
        CHANNEL.registerMessage(id++, StatsRequestC2S.class, StatsRequestC2S::encode, StatsRequestC2S::decode,
                StatsRequestC2S::handle);
        CHANNEL.registerMessage(id++, StatsSnapshotS2C.class, StatsSnapshotS2C::encode, StatsSnapshotS2C::decode,
                StatsSnapshotS2C::handle);
        CHANNEL.registerMessage(id++, StatsChatC2S.class, StatsChatC2S::encode, StatsChatC2S::decode,
                StatsChatC2S::handle);
        CHANNEL.registerMessage(id++, ProviderHighlightS2C.class, ProviderHighlightS2C::encode,
                ProviderHighlightS2C::decode, ProviderHighlightS2C::handle);
        CHANNEL.registerMessage(id++, ProviderLocateC2S.class, ProviderLocateC2S::encode,
                ProviderLocateC2S::decode, ProviderLocateC2S::handle);
        CHANNEL.registerMessage(id++, CpuTtcRequestC2S.class, CpuTtcRequestC2S::encode,
                CpuTtcRequestC2S::decode, CpuTtcRequestC2S::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CpuTtcSnapshotS2C.class, CpuTtcSnapshotS2C::encode,
                CpuTtcSnapshotS2C::decode, CpuTtcSnapshotS2C::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PlanRecurrenceS2C.class, PlanRecurrenceS2C::encode,
                PlanRecurrenceS2C::decode, PlanRecurrenceS2C::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PlanStoredVariantsS2C.class, PlanStoredVariantsS2C::encode,
                PlanStoredVariantsS2C::decode, PlanStoredVariantsS2C::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WarningPreferenceC2S.class, WarningPreferenceC2S::encode,
                WarningPreferenceC2S::decode, WarningPreferenceC2S::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ServerOptionsSnapshotS2C.class, ServerOptionsSnapshotS2C::encode,
                ServerOptionsSnapshotS2C::decode, ServerOptionsSnapshotS2C::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id, ServerOptionsUpdateC2S.class, ServerOptionsUpdateC2S::encode,
                ServerOptionsUpdateC2S::decode, ServerOptionsUpdateC2S::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendTo(ServerPlayer player, StatsSnapshotS2C packet) {
        if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendTo(ServerPlayer player, CpuTtcSnapshotS2C packet) {
        if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendTo(ServerPlayer player, ServerOptionsSnapshotS2C packet) {
        if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendTo(ServerPlayer player, PlanRecurrenceS2C packet) { if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }
    public static void sendTo(ServerPlayer player, PlanStoredVariantsS2C packet) { if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet); }

    public static void sendTo(ServerPlayer player, ProviderHighlightS2C packet) {
        if (canSend(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(StatsChatC2S packet) {
        if (canSend()) CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(ProviderLocateC2S packet) {
        if (canSend()) CHANNEL.sendToServer(packet);
    }

    public static void sendToServer(WarningPreferenceC2S packet) {
        if (canSend()) CHANNEL.sendToServer(packet);
    }
    public static void sendToServer(ServerOptionsUpdateC2S packet) { if (canSend()) CHANNEL.sendToServer(packet); }

    public static void sendToServer(StatsRequestC2S packet) { if (canSend()) CHANNEL.sendToServer(packet); }
    public static void sendToServer(CpuTtcRequestC2S packet) { if (canSend()) CHANNEL.sendToServer(packet); }

    private StatsNetwork() {
    }
}
