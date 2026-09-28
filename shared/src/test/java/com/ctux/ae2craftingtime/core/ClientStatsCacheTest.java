package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

class ClientStatsCacheTest {
    @Test
    void chanceEvidenceIsCpuScopedAndReplacedByFreshSnapshots() {
        var cache = new ClientStatsCache();
        var key = new ProfileKey("mekanism:sawdust");
        assertTrue(cache.chanceOutput(key, 1).isEmpty());
        cache.replaceChanceOutputs(List.of(key), Map.of(key, 5000), 1);
        assertEquals(5000, cache.chanceOutput(key, 1).orElseThrow());
        assertTrue(cache.chanceOutput(key, 2).isEmpty());
        cache.replaceChanceOutputs(List.of(key), Map.of(), 1);
        assertTrue(cache.chanceOutput(key, 1).isEmpty());
        cache.replaceChanceOutputs(List.of(key), Map.of(key, 5000), 1);
        cache.replaceBlockReasons(List.of(key), Map.of(), 2);
        assertTrue(cache.chanceOutput(key, 2).isEmpty());
        cache.replaceBlockReasons(List.of(key), Map.of(key, CraftingBlockReason.NO_TARGET), 2);
        cache.replaceChanceOutputs(List.of(key), Map.of(key, 5000), 3);
        assertEquals(null, cache.blockReason(key, 3));
        cache.remove(key);
        assertTrue(cache.chanceOutput(key, 3).isEmpty());
        cache.replaceChanceOutputs(List.of(key), Map.of(key, 5000), 3);
        cache.clearCpuState();
        assertTrue(cache.chanceOutput(key, 3).isEmpty());
    }

    @Test
    void channelReasonWithoutSamplesIsReplacedAndCannotCrossCpuOrNetwork() {
        var cache = new ClientStatsCache();
        var key = new ProfileKey("grid", "minecraft:diamond");
        cache.replaceBlockReasons(List.of(key), Map.of(key, CraftingBlockReason.NO_CHANNEL), 7);
        assertEquals(CraftingBlockReason.NO_CHANNEL, cache.blockReason(key, 7));
        assertTrue(cache.get(key).isEmpty());
        assertEquals(null, cache.blockReason(key, 8));
        assertEquals(null, cache.blockReason(new ProfileKey("other-grid", key.outputId()), 7));
        cache.replaceBlockReasons(List.of(key), Map.of(), 7);
        assertEquals(null, cache.blockReason(key, 7));
    }

    @Test
    void totalTtcIsScopedToTheSelectedCpu() {
        var cache = new ClientStatsCache();

        cache.replaceTotalTtcSeconds(OptionalLong.of(2_445), 7);

        assertEquals(2_445, cache.totalTtcSeconds(7).orElseThrow());
        assertFalse(cache.totalTtcSeconds(8).isPresent());
        cache.clearCpuState();
        assertFalse(cache.totalTtcSeconds(7).isPresent());
    }

    @Test
    void missingProvidersReplaceWithoutStatsAndClearWithCpuState() {
        var cache = new ClientStatsCache();
        var iron = new ProfileKey("minecraft:iron_ingot");
        var gold = new ProfileKey("minecraft:gold_ingot");
        assertEquals(null, cache.blockReason(iron, 7));
        cache.replaceBlockReasons(List.of(iron, gold), Map.of(iron, CraftingBlockReason.NO_PROVIDER, gold, CraftingBlockReason.NO_PROVIDER), 7);
        assertEquals(CraftingBlockReason.NO_PROVIDER, cache.blockReason(iron, 7));
        assertEquals(null, cache.blockReason(iron, 8));
        assertTrue(cache.get(iron).isEmpty());
        cache.replaceBlockReasons(List.of(iron), Map.of(), 7);
        assertEquals(null, cache.blockReason(iron, 7));
        assertEquals(CraftingBlockReason.NO_PROVIDER, cache.blockReason(gold, 7));
        cache.remove(gold);
        assertEquals(null, cache.blockReason(gold, 7));
        cache.replaceBlockReasons(List.of(iron), Map.of(iron, CraftingBlockReason.NO_PROVIDER), 7);
        cache.clearCpuState();
        assertEquals(null, cache.blockReason(iron, 7));
        cache.replaceBlockReasons(List.of(iron), Map.of(iron, CraftingBlockReason.NO_PROVIDER), 7);
        cache.clear();
        assertEquals(null, cache.blockReason(iron, 7));
        cache.replaceBlockReasons(List.of(iron), Map.of(iron, CraftingBlockReason.NO_PROVIDER), 7);
        cache.replaceBlockReasons(List.of(gold), Map.of(gold, CraftingBlockReason.NO_PROVIDER), 8);
        assertEquals(null, cache.blockReason(iron, 8));
        assertEquals(CraftingBlockReason.NO_PROVIDER, cache.blockReason(gold, 8));
        assertEquals(null, cache.blockReason(gold, 7));
    }

    @Test
    void replacesStatsByOutputKey() {
        var cache = new ClientStatsCache();
        var key = new ProfileKey("minecraft:iron_plate");

        cache.replace(List.of(new StatsEntry(key, new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.ITEM))));
        cache.replace(List.of(new StatsEntry(key, new ProfileStats(2, 10, 0.1, 2, 8, ProfileUnit.ITEM))));

        var stats = cache.get(key).orElseThrow();
        assertEquals(2, stats.sampleCount());
        assertEquals(10, stats.averageDurationTicks());
    }

    @Test
    void missingStatsStayEmpty() {
        var cache = new ClientStatsCache();

        assertFalse(cache.get(new ProfileKey("minecraft:copper_plate")).isPresent());
    }

    @Test
    void clearDropsCachedStats() {
        var cache = new ClientStatsCache();
        var key = new ProfileKey("minecraft:iron_plate");

        cache.replace(List.of(new StatsEntry(key, new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.ITEM))));
        cache.clear();

        assertFalse(cache.get(key).isPresent());
    }

    @Test
    void removeDropsOneCachedStat() {
        var cache = new ClientStatsCache();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");

        cache.replace(List.of(
                new StatsEntry(iron, new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.ITEM)),
                new StatsEntry(copper, new ProfileStats(1, 10, 0.1, 2, 10, ProfileUnit.ITEM))));
        cache.remove(iron);

        assertFalse(cache.get(iron).isPresent());
        assertEquals(1, cache.get(copper).orElseThrow().sampleCount());
    }

    @Test
    void replaceDropsRequestedStatsMissingFromResponse() {
        var cache = new ClientStatsCache();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");

        cache.replace(List.of(
                new StatsEntry(iron, new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.ITEM)),
                new StatsEntry(copper, new ProfileStats(1, 10, 0.1, 2, 10, ProfileUnit.ITEM))));
        cache.replace(List.of(iron), List.of());

        assertFalse(cache.get(iron).isPresent());
        assertEquals(1, cache.get(copper).orElseThrow().sampleCount());
    }

    @Test
    void exposesOptionalDiagnostics() {
        var key = new ProfileKey("minecraft:iron_plate");
        var accuracy = new TtcAccuracyStats(1, 1, 1, 0, 0, 1, 10, 10, 10, 1, 1);
        var stall = new StallDiagnostic(600, 20, 1, 0, 0);
        var cache = new ClientStatsCache();

        cache.replace(List.of(new StatsEntry(key,
                new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.ITEM), Optional.of(accuracy), Optional.of(stall))));

        assertEquals(accuracy, cache.accuracy(key).orElseThrow());
        assertEquals(stall, cache.stall(key).orElseThrow());
        assertFalse(cache.accuracy(new ProfileKey("minecraft:missing")).isPresent());
        assertFalse(cache.stall(new ProfileKey("minecraft:missing")).isPresent());
    }

    @Test
    void waitingValuesReplaceOnlyRequestedKeysAndClearWithTheCache() {
        var cache = new ClientStatsCache();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");

        cache.replaceWaiting(List.of(iron, copper), Map.of(iron, 20L, copper, 40L));
        cache.replaceWaiting(List.of(iron), Map.of());

        assertFalse(cache.waitingTicks(iron).isPresent());
        assertEquals(40L, cache.waitingTicks(copper).orElseThrow());
        cache.remove(copper);
        assertFalse(cache.waitingTicks(copper).isPresent());
        cache.replaceWaiting(List.of(iron), Map.of(iron, 60L));
        cache.clearCpuState();
        assertFalse(cache.waitingTicks(iron).isPresent());
        cache.replaceWaiting(List.of(iron), Map.of(iron, 80L));
        cache.clear();
        assertFalse(cache.waitingTicks(iron).isPresent());
    }
}
