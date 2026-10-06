package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.RowStatsJob;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

public record RowStatsJobS2C(RowStatsJob job) {
    public static void encode(RowStatsJobS2C packet, FriendlyByteBuf buffer) { StatsPacketCodec.writeJob(buffer, packet.job); }
    public static RowStatsJobS2C decode(FriendlyByteBuf buffer) { return new RowStatsJobS2C(StatsPacketCodec.readJob(buffer)); }
    public static void handle(RowStatsJobS2C packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.getNetworkManager(),
                () -> com.ctux.ae2craftingtime.mc1201.ClientStatsRequests.receiveJob(packet.job)));
        context.setPacketHandled(true);
    }
}
