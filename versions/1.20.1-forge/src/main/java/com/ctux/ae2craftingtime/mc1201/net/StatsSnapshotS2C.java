package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
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
        Map<String, Long> networkAmounts, Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons,
        OptionalLong totalTtcSeconds, long cpuContext) {
    public StatsSnapshotS2C(List<StatsEntry> entries) {
        this(entries.stream().map(entry -> entry.key().outputId()).toList(), entries, Map.of(), Map.of(), Map.of(),
                OptionalLong.empty(), -1);
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

    public static void handle(StatsSnapshotS2C packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.getNetworkManager(), () -> {
            ClientStats.CACHE.replace(packet.requestedKeys.stream().map(ProfileKey::new).toList(), packet.entries);
            ProviderHighlightClient.prunePlates(packet.requestedKeys);
            ClientStats.replaceNetworkAmounts(packet.requestedKeys, packet.networkAmounts);
            ClientStats.replaceWaitingTicks(packet.requestedKeys, packet.waitingTicks);
            ClientStats.replaceBlockReasons(packet.requestedKeys, packet.blockReasons, packet.cpuContext);
            ClientStats.replaceTotalTtcSeconds(packet.totalTtcSeconds, packet.cpuContext);
        }));
        context.setPacketHandled(true);
    }
}
