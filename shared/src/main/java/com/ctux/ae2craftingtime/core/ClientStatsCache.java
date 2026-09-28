package com.ctux.ae2craftingtime.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class ClientStatsCache {
    private final Map<ProfileKey, StatsEntry> stats = new HashMap<>();
    private final Map<ProfileKey, Long> waitingTicks = new HashMap<>();
    private long blockContext = -1;
    private long totalTtcContext = -1;
    private OptionalLong totalTtcSeconds = OptionalLong.empty();
    private final Map<ProfileKey, CraftingBlockReason> blockReasons = new HashMap<>();
    private final Map<ProfileKey, Integer> chanceOutputs = new HashMap<>();

    public void replace(List<StatsEntry> entries) {
        for (var entry : entries) {
            stats.put(entry.key(), entry);
        }
    }

    public void replace(List<ProfileKey> requestedKeys, List<StatsEntry> entries) {
        for (var key : requestedKeys) {
            stats.remove(key);
        }
        replace(entries);
    }

    public Optional<ProfileStats> get(ProfileKey key) {
        return Optional.ofNullable(stats.get(key)).map(StatsEntry::stats);
    }

    public Optional<TtcAccuracyStats> accuracy(ProfileKey key) {
        return Optional.ofNullable(stats.get(key)).flatMap(StatsEntry::accuracy);
    }

    public Optional<StallDiagnostic> stall(ProfileKey key) {
        return Optional.ofNullable(stats.get(key)).flatMap(StatsEntry::stall);
    }

    public OptionalLong waitingTicks(ProfileKey key) {
        var ticks = waitingTicks.get(key);
        return ticks == null ? OptionalLong.empty() : OptionalLong.of(ticks);
    }

    public void replaceWaiting(List<ProfileKey> requestedKeys, Map<ProfileKey, Long> values) {
        requestedKeys.forEach(waitingTicks::remove);
        waitingTicks.putAll(values);
    }

    public CraftingBlockReason blockReason(ProfileKey key, long cpuContext) {
        return blockContext == cpuContext ? blockReasons.get(key) : null;
    }

    public void replaceBlockReasons(List<ProfileKey> requestedKeys, Map<ProfileKey, CraftingBlockReason> values, long cpuContext) {
        if (blockContext != cpuContext) {
            blockReasons.clear();
            chanceOutputs.clear();
            blockContext = cpuContext;
        }
        requestedKeys.forEach(blockReasons::remove);
        blockReasons.putAll(values);
    }

    public OptionalLong chanceOutput(ProfileKey key, long cpuContext) {
        var value = blockContext == cpuContext ? chanceOutputs.get(key) : null;
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public void replaceChanceOutputs(List<ProfileKey> requestedKeys, Map<ProfileKey, Integer> values, long cpuContext) {
        if (blockContext != cpuContext) {
            blockReasons.clear();
            chanceOutputs.clear();
            blockContext = cpuContext;
        }
        requestedKeys.forEach(chanceOutputs::remove);
        chanceOutputs.putAll(values);
    }

    public OptionalLong totalTtcSeconds(long cpuContext) {
        return totalTtcContext == cpuContext ? totalTtcSeconds : OptionalLong.empty();
    }

    public void replaceTotalTtcSeconds(OptionalLong value, long cpuContext) {
        totalTtcContext = cpuContext;
        totalTtcSeconds = value;
    }

    public void clearCpuState() {
        waitingTicks.clear();
        blockReasons.clear();
        chanceOutputs.clear();
        blockContext = -1;
        totalTtcContext = -1;
        totalTtcSeconds = OptionalLong.empty();
    }

    public void remove(ProfileKey key) {
        stats.remove(key);
        waitingTicks.remove(key);
        blockReasons.remove(key);
        chanceOutputs.remove(key);
    }

    public void clear() {
        stats.clear();
        clearCpuState();
    }
}
