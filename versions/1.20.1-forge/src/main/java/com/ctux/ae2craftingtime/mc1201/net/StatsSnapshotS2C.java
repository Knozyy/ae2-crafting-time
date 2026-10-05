package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.RowStatsRequestId;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Supplier;

public record StatsSnapshotS2C(List<String> requestedKeys, List<StatsEntry> entries,
        Map<String, Long> networkAmounts, Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons, Map<String, Integer> chanceOutputs,
        OptionalLong totalTtcSeconds, long cpuContext, RowStatsRequestId requestId) {
    public StatsSnapshotS2C(List<StatsEntry> entries, RowStatsRequestId requestId) {
        this(entries.stream().map(entry -> entry.key().outputId()).toList(), entries, Map.of(), Map.of(), Map.of(), Map.of(),
                OptionalLong.empty(), requestId.cpuContext(), requestId);
    }

    public static void encode(StatsSnapshotS2C packet, FriendlyByteBuf buffer) {
        StatsPacketCodec.writeSnapshot(buffer,
                new StatsPacketCodec.Snapshot(packet.requestedKeys, packet.entries, packet.networkAmounts,
                        packet.waitingTicks, packet.blockReasons, packet.chanceOutputs, packet.totalTtcSeconds, packet.cpuContext, packet.requestId));
    }

    public static StatsSnapshotS2C decode(FriendlyByteBuf buffer) {
        var snapshot = StatsPacketCodec.readSnapshot(buffer);
        return new StatsSnapshotS2C(snapshot.requestedKeys(), snapshot.entries(), snapshot.networkAmounts(),
                snapshot.waitingTicks(), snapshot.blockReasons(), snapshot.chanceOutputs(), snapshot.totalTtcSeconds(), snapshot.cpuContext(), snapshot.requestId());
    }

    public static void handle(StatsSnapshotS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.getNetworkManager(), () -> {
            if (!com.ctux.ae2craftingtime.mc1201.ClientStatsRequests.acceptSnapshot(packet.requestId, packet.cpuContext)) return;
            ClientStats.CACHE.replace(packet.requestedKeys.stream().map(ProfileKey::new).toList(), packet.entries);
            ProviderHighlightClient.prunePlates(packet.requestedKeys);
            ClientStats.replaceNetworkAmounts(packet.requestedKeys, packet.networkAmounts);
            ClientStats.replaceWaitingTicks(packet.requestedKeys, packet.waitingTicks);
            ClientStats.replaceBlockReasons(packet.requestedKeys, packet.blockReasons, packet.cpuContext);
            ClientStats.replaceChanceOutputs(packet.requestedKeys, packet.chanceOutputs, packet.cpuContext);
            ClientStats.replaceTotalTtcSeconds(packet.totalTtcSeconds, packet.cpuContext);
        }));
        context.setPacketHandled(true);
    }
}
