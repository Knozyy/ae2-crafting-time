package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.StatsRequestHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record StatsRequestC2S(List<String> keys) implements CustomPacketPayload {
    public StatsRequestC2S {
        keys = PacketLimits.checkedKeys(keys);
    }

    public static final Type<StatsRequestC2S> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("ae2craftingtime", "stats_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsRequestC2S> STREAM_CODEC = StreamCodec.ofMember(
            StatsRequestC2S::encode,
            StatsRequestC2S::decode);

    @Override
    public Type<StatsRequestC2S> type() {
        return TYPE;
    }

    public static void encode(StatsRequestC2S packet, FriendlyByteBuf buffer) {
        StatsPacketCodec.writeKeys(buffer, packet.keys);
    }

    public static StatsRequestC2S decode(FriendlyByteBuf buffer) {
        return new StatsRequestC2S(StatsPacketCodec.readKeys(buffer, "keys"));
    }

    public static void handle(StatsRequestC2S packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var response = StatsRequestHandler.collect(player, packet.keys);
            if (response != null) {
                StatsNetwork.sendTo(player,
                        new StatsSnapshotS2C(packet.keys, response.entries(), response.networkAmounts(),
                                response.waitingTicks(), response.blockReasons(), response.chanceOutputs(), response.totalTtcSeconds(),
                                response.cpuContext()));
            }
        });
    }
}
