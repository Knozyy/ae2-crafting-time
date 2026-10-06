package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.RowStatsJob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RowStatsJobS2C(RowStatsJob job) implements CustomPacketPayload {
    public static final Type<RowStatsJobS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("ae2craftingtime", "row_stats_job"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RowStatsJobS2C> STREAM_CODEC = StreamCodec.ofMember(RowStatsJobS2C::encode, RowStatsJobS2C::decode);
    public Type<RowStatsJobS2C> type() { return TYPE; }
    public static void encode(RowStatsJobS2C packet, FriendlyByteBuf buffer) { StatsPacketCodec.writeJob(buffer, packet.job); }
    public static RowStatsJobS2C decode(FriendlyByteBuf buffer) { return new RowStatsJobS2C(StatsPacketCodec.readJob(buffer)); }
    public static void handle(RowStatsJobS2C packet, IPayloadContext context) {
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.connection(),
                () -> com.ctux.ae2craftingtime.mc1201.ClientStatsRequests.receiveJob(packet.job)));
    }
}
