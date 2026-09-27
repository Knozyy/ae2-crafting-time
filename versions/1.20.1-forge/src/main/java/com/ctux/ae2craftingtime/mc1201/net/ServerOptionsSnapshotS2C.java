package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.mc1201.ClientServerOptions;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record ServerOptionsSnapshotS2C(byte[] bytes) {
    public static void encode(ServerOptionsSnapshotS2C packet, FriendlyByteBuf buffer) {
        buffer.writeByteArray(packet.bytes);
    }

    public static ServerOptionsSnapshotS2C decode(FriendlyByteBuf buffer) {
        return new ServerOptionsSnapshotS2C(buffer.readByteArray(64));
    }

    public static void handle(ServerOptionsSnapshotS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.getNetworkManager(), () -> ClientServerOptions.receive(packet.bytes)));
        context.setPacketHandled(true);
    }
}
