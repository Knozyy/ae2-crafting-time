package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.RowStatsJob;
import net.minecraft.network.FriendlyByteBuf;

public record RowStatsJobS2C(RowStatsJob job) {
    public static void encode(RowStatsJobS2C packet, FriendlyByteBuf buffer) { StatsPacketCodec.writeJob(buffer, packet.job); }
    public static RowStatsJobS2C decode(FriendlyByteBuf buffer) { return new RowStatsJobS2C(StatsPacketCodec.readJob(buffer)); }
    public void handle() { com.ctux.ae2craftingtime.mc1201.ClientStatsRequests.receiveJob(job); }
}
