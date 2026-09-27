package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.mc1201.CpuTtcClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CpuTtcSnapshotS2C(CpuTtcPacketCodec.Snapshot snapshot) {
    public static void encode(CpuTtcSnapshotS2C packet, FriendlyByteBuf buffer) {
        CpuTtcPacketCodec.writeSnapshot(buffer, packet.snapshot);
    }

    public static CpuTtcSnapshotS2C decode(FriendlyByteBuf buffer) {
        return new CpuTtcSnapshotS2C(CpuTtcPacketCodec.readSnapshot(buffer));
    }

    public static void handle(CpuTtcSnapshotS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.getNetworkManager(), () -> CpuTtcClient.receive(packet.snapshot)));
        context.setPacketHandled(true);
    }
}
