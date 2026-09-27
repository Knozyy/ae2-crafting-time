package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public record StatsSnapshotS2C(List<String> requestedKeys, List<StatsEntry> entries,
        Map<String, Long> networkAmounts, Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons,
        OptionalLong totalTtcSeconds, long cpuContext)
        implements CustomPacketPayload {
    public StatsSnapshotS2C(List<StatsEntry> entries) {
        this(entries.stream().map(entry -> entry.key().outputId()).toList(), entries, Map.of(), Map.of(), Map.of(),
                OptionalLong.empty(), -1);
    }

    public static final Type<StatsSnapshotS2C> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("ae2craftingtime", "stats_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSnapshotS2C> STREAM_CODEC = StreamCodec.ofMember(
            StatsSnapshotS2C::encode,
            StatsSnapshotS2C::decode);

    @Override
    public Type<StatsSnapshotS2C> type() {
        return TYPE;
    }

    public static void encode(StatsSnapshotS2C packet, FriendlyByteBuf buffer) {
        StatsPacketCodec.writeSnapshot(buffer,
                new StatsPacketCodec.Snapshot(packet.requestedKeys, packet.entries, packet.networkAmounts,
                        packet.waitingTicks, packet.blockReasons, packet.totalTtcSeconds, packet.cpuContext));
    }

    public static StatsSnapshotS2C decode(FriendlyByteBuf buffer) {
        var snapshot = StatsPacketCodec.readSnapshot(buffer);
        return new StatsSnapshotS2C(snapshot.requestedKeys(), snapshot.entries(), snapshot.networkAmounts(),
                snapshot.waitingTicks(), snapshot.blockReasons(), snapshot.totalTtcSeconds(), snapshot.cpuContext());
    }

    public static void handle(StatsSnapshotS2C packet, IPayloadContext context) {
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.connection(), () -> {
            ClientStats.CACHE.replace(packet.requestedKeys.stream().map(ProfileKey::new).toList(), packet.entries);
            ProviderHighlightClient.prunePlates(packet.requestedKeys);
            ClientStats.replaceNetworkAmounts(packet.requestedKeys, packet.networkAmounts);
            ClientStats.replaceWaitingTicks(packet.requestedKeys, packet.waitingTicks);
            ClientStats.replaceBlockReasons(packet.requestedKeys, packet.blockReasons, packet.cpuContext);
            ClientStats.replaceTotalTtcSeconds(packet.totalTtcSeconds, packet.cpuContext);
        }));
    }
}
