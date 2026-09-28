package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.core.TtcAccuracyStats;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class StatsPacketCodec {
    private StatsPacketCodec() {
    }

    public static void writeKeys(FriendlyByteBuf buffer, List<String> keys) {
        buffer.writeVarInt(keys.size());
        keys.forEach(key -> buffer.writeUtf(key, PacketLimits.MAX_OUTPUT_ID_LENGTH));
    }

    public static List<String> readKeys(FriendlyByteBuf buffer, String label) {
        var size = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, label);
        var keys = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) {
            keys.add(buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH));
        }
        return keys;
    }

    public static void writeSnapshot(FriendlyByteBuf buffer, Snapshot snapshot) {
        writeKeys(buffer, snapshot.requestedKeys());
        buffer.writeVarInt(snapshot.networkAmounts().size());
        snapshot.networkAmounts().forEach((key, amount) -> {
            buffer.writeUtf(key, PacketLimits.MAX_OUTPUT_ID_LENGTH);
            buffer.writeVarLong(amount);
        });
        buffer.writeVarInt(snapshot.waitingTicks().size());
        snapshot.waitingTicks().forEach((key, ticks) -> {
            buffer.writeUtf(key, PacketLimits.MAX_OUTPUT_ID_LENGTH);
            buffer.writeVarLong(ticks);
        });
        buffer.writeVarInt(snapshot.blockReasons().size());
        snapshot.blockReasons().forEach((key, reason) -> {
            buffer.writeUtf(key, PacketLimits.MAX_OUTPUT_ID_LENGTH);
            buffer.writeEnum(reason);
        });
        buffer.writeVarInt(snapshot.chanceOutputs().size());
        snapshot.chanceOutputs().forEach((key, chance) -> {
            buffer.writeUtf(key, PacketLimits.MAX_OUTPUT_ID_LENGTH);
            buffer.writeVarInt(chance);
        });
        buffer.writeVarInt(snapshot.entries().size());
        for (var entry : snapshot.entries()) {
            var stats = entry.stats();
            buffer.writeUtf(entry.key().outputId(), PacketLimits.MAX_OUTPUT_ID_LENGTH);
            buffer.writeEnum(stats.unit());
            buffer.writeVarInt(stats.sampleCount());
            buffer.writeDouble(stats.averageDurationTicks());
            buffer.writeDouble(stats.amountPerTick());
            buffer.writeDouble(stats.amountPerSecond());
            buffer.writeVarLong(stats.lastDurationTicks());
            buffer.writeBoolean(stats.reliableEstimate());
            buffer.writeVarInt(stats.usedSampleCount());
            buffer.writeDouble(stats.outlierMultiplier());
            buffer.writeVarInt(stats.sampleDurationTicks().size());
            stats.sampleDurationTicks().forEach(buffer::writeVarLong);
            stats.sampleAmounts().forEach(buffer::writeVarLong);
            buffer.writeBoolean(entry.accuracy().isPresent());
            entry.accuracy().ifPresent(value -> TtcAccuracyPacketCodec.write(buffer, value));
            buffer.writeBoolean(entry.stall().isPresent());
            entry.stall().ifPresent(value -> StallDiagnosticPacketCodec.write(buffer, value));
        }
        buffer.writeBoolean(snapshot.totalTtcSeconds().isPresent());
        snapshot.totalTtcSeconds().ifPresent(buffer::writeVarLong);
        buffer.writeLong(snapshot.cpuContext());
    }

    public static Snapshot readSnapshot(FriendlyByteBuf buffer) {
        var requestedKeys = readKeys(buffer, "requested keys");
        var amountSize = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, "network amounts");
        var networkAmounts = new HashMap<String, Long>(amountSize);
        for (int i = 0; i < amountSize; i++) {
            networkAmounts.put(buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH), buffer.readVarLong());
        }
        var waitingSize = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, "waiting ticks");
        var waitingTicks = new HashMap<String, Long>(waitingSize);
        for (int i = 0; i < waitingSize; i++) {
            var key = PacketLimits.checkedOutputId(buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH));
            var ticks = buffer.readVarLong();
            if (ticks < 0) {
                throw new IllegalArgumentException("waiting ticks must not be negative");
            }
            waitingTicks.put(key, ticks);
        }
        var reasonCount = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, "block reasons");
        var blockReasons = new HashMap<String, CraftingBlockReason>();
        for (int i = 0; i < reasonCount; i++) {
            var key = buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH);
            PacketLimits.checkedSubset(requestedKeys, List.of(key));
            blockReasons.put(key, buffer.readEnum(CraftingBlockReason.class));
        }
        var chanceCount = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, "chance outputs");
        var chanceOutputs = new HashMap<String, Integer>();
        for (int i = 0; i < chanceCount; i++) {
            var key = buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH);
            PacketLimits.checkedSubset(requestedKeys, List.of(key));
            var chance = buffer.readVarInt();
            if (chance <= 0 || chance >= 10000) throw new IllegalArgumentException("invalid output chance");
            chanceOutputs.put(key, chance);
        }
        var size = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_KEYS, "entries");
        var entries = new ArrayList<StatsEntry>(size);
        for (int i = 0; i < size; i++) {
            var key = new ProfileKey(buffer.readUtf(PacketLimits.MAX_OUTPUT_ID_LENGTH));
            var unit = buffer.readEnum(ProfileUnit.class);
            var sampleCount = buffer.readVarInt();
            var averageDurationTicks = buffer.readDouble();
            var amountPerTick = buffer.readDouble();
            var amountPerSecond = buffer.readDouble();
            var lastDurationTicks = buffer.readVarLong();
            var reliableEstimate = buffer.readBoolean();
            var usedSampleCount = buffer.readVarInt();
            var outlierMultiplier = buffer.readDouble();
            var durationCount = PacketLimits.checkedSize(buffer.readVarInt(), PacketLimits.MAX_SAMPLES, "samples");
            var durations = new ArrayList<Long>(durationCount);
            for (int j = 0; j < durationCount; j++) {
                durations.add(buffer.readVarLong());
            }
            var amounts = new ArrayList<Long>(durationCount);
            for (int j = 0; j < durationCount; j++) {
                amounts.add(buffer.readVarLong());
            }
            var accuracy = buffer.readBoolean()
                    ? Optional.of(TtcAccuracyPacketCodec.read(buffer))
                    : Optional.<TtcAccuracyStats>empty();
            var stall = buffer.readBoolean()
                    ? Optional.of(StallDiagnosticPacketCodec.read(buffer))
                    : Optional.<com.ctux.ae2craftingtime.core.StallDiagnostic>empty();
            entries.add(new StatsEntry(key, new ProfileStats(sampleCount, averageDurationTicks, amountPerTick,
                    amountPerSecond, lastDurationTicks, unit, reliableEstimate, usedSampleCount, outlierMultiplier,
                    durations, amounts), accuracy, stall));
        }
        var totalTtcSeconds = buffer.readBoolean() ? OptionalLong.of(buffer.readVarLong()) : OptionalLong.empty();
        if (totalTtcSeconds.isPresent() && totalTtcSeconds.getAsLong() < 0) {
            throw new IllegalArgumentException("total TTC must not be negative");
        }
        return new Snapshot(requestedKeys, entries, networkAmounts, waitingTicks, blockReasons, chanceOutputs, totalTtcSeconds,
                buffer.readLong());
    }

    public record Snapshot(List<String> requestedKeys, List<StatsEntry> entries, Map<String, Long> networkAmounts,
            Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons,
            Map<String, Integer> chanceOutputs,
            OptionalLong totalTtcSeconds, long cpuContext) {
    }
}
