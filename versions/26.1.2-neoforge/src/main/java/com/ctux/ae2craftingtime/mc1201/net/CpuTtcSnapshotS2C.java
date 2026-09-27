package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.mc1201.CpuTtcClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CpuTtcSnapshotS2C(CpuTtcPacketCodec.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<CpuTtcSnapshotS2C> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("ae2craftingtime", "cpu_ttc_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CpuTtcSnapshotS2C> STREAM_CODEC = StreamCodec.ofMember(
            CpuTtcSnapshotS2C::encode, CpuTtcSnapshotS2C::decode);
    @Override public Type<CpuTtcSnapshotS2C> type() { return TYPE; }
    public static void encode(CpuTtcSnapshotS2C packet, FriendlyByteBuf buffer) { CpuTtcPacketCodec.writeSnapshot(buffer, packet.snapshot); }
    public static CpuTtcSnapshotS2C decode(FriendlyByteBuf buffer) { return new CpuTtcSnapshotS2C(CpuTtcPacketCodec.readSnapshot(buffer)); }
    public static void handle(CpuTtcSnapshotS2C packet, IPayloadContext context) { context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.connection(), () -> CpuTtcClient.receive(packet.snapshot))); }
}
