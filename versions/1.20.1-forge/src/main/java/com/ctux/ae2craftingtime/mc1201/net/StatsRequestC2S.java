package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.RowStatsRequestId;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.StatsRequestHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record StatsRequestC2S(List<String> keys, RowStatsRequestId requestId) {
    public StatsRequestC2S {
        keys = PacketLimits.checkedKeys(keys);
    }

    public static void encode(StatsRequestC2S packet, FriendlyByteBuf buffer) {
        StatsPacketCodec.writeKeys(buffer, packet.keys);
        StatsPacketCodec.writeRequestId(buffer, packet.requestId);
    }

    public static StatsRequestC2S decode(FriendlyByteBuf buffer) {
        return new StatsRequestC2S(StatsPacketCodec.readKeys(buffer, "keys"), StatsPacketCodec.readRequestId(buffer));
    }

    public static void handle(StatsRequestC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            var response = StatsRequestHandler.collect(player, packet.keys, packet.requestId);
            if (response != null) {
                StatsNetwork.sendTo(player,
                        new StatsSnapshotS2C(packet.keys, response.entries(), response.networkAmounts(),
                                response.waitingTicks(), response.blockReasons(), response.chanceOutputs(), response.totalTtcSeconds(),
                                response.cpuContext(), packet.requestId));
            }
        });
        context.setPacketHandled(true);
    }
}
