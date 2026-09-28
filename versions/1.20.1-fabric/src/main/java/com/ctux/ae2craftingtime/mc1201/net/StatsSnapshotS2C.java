package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public record StatsSnapshotS2C(List<String> requestedKeys, List<StatsEntry> entries,
        Map<String, Long> networkAmounts, Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons, Map<String, Integer> chanceOutputs,
        OptionalLong totalTtcSeconds, long cpuContext) {
    public StatsSnapshotS2C(List<StatsEntry> entries) {
        this(entries.stream().map(entry -> entry.key().outputId()).toList(), entries, Map.of(), Map.of(), Map.of(), Map.of(),
                OptionalLong.empty(), -1);
    }

    public static void encode(StatsSnapshotS2C packet, FriendlyByteBuf buffer) {
        StatsPacketCodec.writeSnapshot(buffer,
                new StatsPacketCodec.Snapshot(packet.requestedKeys, packet.entries, packet.networkAmounts,
                        packet.waitingTicks, packet.blockReasons, packet.chanceOutputs, packet.totalTtcSeconds, packet.cpuContext));
    }

    public static StatsSnapshotS2C decode(FriendlyByteBuf buffer) {
        var snapshot = StatsPacketCodec.readSnapshot(buffer);
        return new StatsSnapshotS2C(snapshot.requestedKeys(), snapshot.entries(), snapshot.networkAmounts(),
                snapshot.waitingTicks(), snapshot.blockReasons(), snapshot.chanceOutputs(), snapshot.totalTtcSeconds(), snapshot.cpuContext());
    }

    public void handle() {
        ClientStats.CACHE.replace(requestedKeys.stream().map(ProfileKey::new).toList(), entries);
        ProviderHighlightClient.prunePlates(requestedKeys);
        ClientStats.replaceNetworkAmounts(requestedKeys, networkAmounts);
        ClientStats.replaceWaitingTicks(requestedKeys, waitingTicks);
        ClientStats.replaceBlockReasons(requestedKeys, blockReasons, cpuContext);
        ClientStats.replaceChanceOutputs(requestedKeys, chanceOutputs, cpuContext);
        ClientStats.replaceTotalTtcSeconds(totalTtcSeconds, cpuContext);
    }
}
