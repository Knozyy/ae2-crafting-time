package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.mc1201.ClientServerOptions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerOptionsSnapshotS2C(byte[] bytes) implements CustomPacketPayload {
    public static final Type<ServerOptionsSnapshotS2C> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2craftingtime", "server_options_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerOptionsSnapshotS2C> STREAM_CODEC = StreamCodec.ofMember(
            ServerOptionsSnapshotS2C::encode, ServerOptionsSnapshotS2C::decode);

    @Override public Type<ServerOptionsSnapshotS2C> type() { return TYPE; }
    public static void encode(ServerOptionsSnapshotS2C packet, FriendlyByteBuf buffer) { buffer.writeByteArray(packet.bytes); }
    public static ServerOptionsSnapshotS2C decode(FriendlyByteBuf buffer) {
        return new ServerOptionsSnapshotS2C(buffer.readByteArray(64));
    }
    public static void handle(ServerOptionsSnapshotS2C packet, IPayloadContext context) {
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.connection(), () -> ClientServerOptions.receive(packet.bytes)));
    }
}
