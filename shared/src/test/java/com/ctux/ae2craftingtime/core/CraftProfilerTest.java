package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftProfilerTest {
    @Test
    void suspensionKeepsPendingAndOtherCpuWhileRebasingDelay() {
        var profiler = new CraftProfiler(10);
        var paused = new Object();
        var other = new Object();
        var output = new ProfileKey("test:shared-output");
        var waiting = new ProfileKey("test:waiting");
        var options = new ServerConfig();
        options.setMinimumNoProgressSeconds(1);
        profiler.configure(options);
        profiler.start(output, paused, 1, ProfileUnit.ITEM, 0);
        profiler.complete(output, paused, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(output, paused, 2, ProfileUnit.ITEM, 30);
        profiler.start(output, other, 2, ProfileUnit.ITEM, 30);
        profiler.startWaiting(paused, List.of(waiting), 30);
        profiler.setJobEstimate(paused, new CraftingJobEstimate(output, Map.of(output, 2L), Map.of()));
        assertTrue(profiler.setSuspended(paused, true, 40));
        assertFalse(profiler.setSuspended(paused, true, 41));
        assertTrue(profiler.isSuspended(paused));
        assertTrue(profiler.ignoreRemembered(paused));
        profiler.updateCapacity(paused, 1, 2, 40);
        assertTrue(profiler.rememberedReasons(paused).isEmpty());
        assertEquals(Set.of(output, waiting), profiler.scopedKeys(paused));
        assertTrue(profiler.waitingTicks(waiting, paused, 100).isEmpty());
        assertTrue(profiler.stall(output, paused, 100).isEmpty());
        assertTrue(profiler.liveDelayedKeys(paused, 100).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(paused, 100).isEmpty());
        assertTrue(profiler.blockReasons(paused, 100, Set.of(output)).isEmpty());
        assertTrue(profiler.remainingJobSeconds(paused, (key, amount) -> OptionalLong.of(1)).isEmpty());
        assertTrue(profiler.hasActiveOutput(output, null));
        assertTrue(profiler.stall(output, other, 100).isPresent());
        assertTrue(profiler.setSuspended(paused, false, 100));
        assertFalse(profiler.setSuspended(paused, false, 101));
        assertTrue(profiler.rememberedReasons(paused).isEmpty());
        assertEquals(1, profiler.waitingTicks(waiting, paused, 101).orElseThrow());
        assertTrue(profiler.stall(output, paused, 101).isEmpty());
        assertEquals(1, profiler.remainingJobSeconds(paused, (key, amount) -> OptionalLong.of(1)).orElseThrow());
        assertEquals(1, profiler.stats(output).orElseThrow().sampleCount());
        profiler.clearPending(paused);
        assertFalse(profiler.ignoreRemembered(paused));
        assertFalse(profiler.isSuspended(paused));
        assertTrue(profiler.hasActiveOutput(output, null));
        assertFalse(profiler.setSuspended(null, true, 0));
        var emptyScope = new Object();
        assertTrue(profiler.setSuspended(emptyScope, true, 0));
        assertTrue(profiler.setSuspended(emptyScope, false, 1));
        assertTrue(profiler.setSuspended(emptyScope, true, 2));
        profiler.loadSamples(List.of());
        assertFalse(profiler.isSuspended(emptyScope));
        assertFalse(profiler.ignoreRemembered(emptyScope));
        assertFalse(profiler.isSuspended(paused));
    }
    @Test
    void serverOptionsReconfigureRetainedSamplesAndDelayDetection() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("test:option-output");
        var cpu = new Object();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 30);
        profiler.complete(key, cpu, 1, 70);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);

        var options = new ServerConfig();
        options.setMaxSamples(1);
        options.setMinimumNoProgressSeconds(1);
        options.setTypicalDurationMultiplier(1);
        profiler.configure(options);
        assertEquals(1, profiler.stats(key).orElseThrow().sampleCount());
        assertFalse(profiler.stall(key, cpu, 139).isPresent());
        assertTrue(profiler.stall(key, cpu, 140).isPresent());
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 140).size());

        options.features().setEnabled(OptionFeature.DELAYED_DETECTION, false);
        profiler.configure(options);
        assertFalse(profiler.stall(key, cpu, 200).isPresent());
        assertTrue(profiler.pollNewlyDelayed(cpu, 200).isEmpty());
        assertEquals(List.of(key), profiler.pollResolvedDelayed(cpu));
    }
    @Test
    void jobEstimateCountsDownAndClearsWithItsCpu() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var root = new ProfileKey("test:root");
        var leaf = new ProfileKey("test:leaf");
        profiler.setJobEstimate(cpu,
                new CraftingJobEstimate(root, Map.of(root, 5L, leaf, 20L), Map.of(root, Set.of(leaf))));

        assertEquals(25, profiler.remainingJobSeconds(cpu, (key, amount) -> OptionalLong.of(amount)).orElseThrow());
        profiler.complete(leaf, cpu, 12, 1);
        assertEquals(13, profiler.remainingJobSeconds(cpu, (key, amount) -> OptionalLong.of(amount)).orElseThrow());
        profiler.clearPending(cpu);
        assertFalse(profiler.remainingJobSeconds(cpu, (key, amount) -> OptionalLong.of(amount)).isPresent());
    }

    @Test
    void jobEstimateFollowsAReplacementCpuScope() {
        var profiler = new CraftProfiler(10);
        var previousCpu = new Object();
        var currentCpu = new Object();
        var output = new ProfileKey("test:output");
        profiler.setJobEstimate(previousCpu,
                new CraftingJobEstimate(output, Map.of(output, 12L), Map.of()));

        profiler.rebindJobEstimate(previousCpu, currentCpu);

        assertFalse(profiler.remainingJobSeconds(previousCpu, (key, amount) -> OptionalLong.of(amount)).isPresent());
        assertEquals(12, profiler.remainingJobSeconds(currentCpu,
                (key, amount) -> OptionalLong.of(amount)).orElseThrow());

        profiler.rebindJobEstimate(null, currentCpu);
        profiler.rebindJobEstimate(previousCpu, null);
        profiler.rebindJobEstimate(currentCpu, currentCpu);
        profiler.rebindJobEstimate(new Object(), currentCpu);

        assertEquals(12, profiler.remainingJobSeconds(currentCpu,
                (key, amount) -> OptionalLong.of(amount)).orElseThrow());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new CraftProfiler(0));
        assertThrows(IllegalArgumentException.class, () -> new CraftProfiler(1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CraftProfiler(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CraftProfiler(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new CraftProfiler(1, Double.NEGATIVE_INFINITY));

        assertDoesNotThrow(() -> new CraftProfiler(1, 1.0));
        assertDoesNotThrow(() -> new CraftProfiler(1, 4.0));
        assertDoesNotThrow(() -> new CraftProfiler(1, 1000.5));
    }

    @Test
    void averagesLatestSamplesForSameOutputKey() {
        var profiler = new CraftProfiler(2);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 10);
        profiler.complete(ironPlate, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 30);
        profiler.complete(ironPlate, 1, 50);
        profiler.flushCompletedSamples();
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 60);
        profiler.complete(ironPlate, 1, 100);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(2, stats.sampleCount());
        assertEquals(30.0, stats.averageDurationTicks());
        assertEquals(3.0 / 100.0, stats.amountPerTick());
        assertEquals(20.0 * 3.0 / 100.0, stats.amountPerSecond());
        assertEquals(40, stats.lastDurationTicks());
    }

    @Test
    void weightsRecentSamplesMoreForThroughput() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 0);
        profiler.complete(ironPlate, 1, 100);
        profiler.flushCompletedSamples();
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 100);
        profiler.complete(ironPlate, 1, 110);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(3.0 / 120.0, stats.amountPerTick());
        assertEquals(20.0 * 3.0 / 120.0, stats.amountPerSecond());
    }

    @Test
    void ignoresExtremeDurationOutliersForThroughput() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 0);
        profiler.complete(ironPlate, 1, 1);
        profiler.flushCompletedSamples();
        for (var i = 0; i < 4; i++) {
            profiler.start(ironPlate, 1, ProfileUnit.ITEM, 20 + i * 20L);
            profiler.complete(ironPlate, 1, 30 + i * 20L);
            profiler.flushCompletedSamples();
        }
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 120);
        profiler.complete(ironPlate, 1, 1_120);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(6, stats.sampleCount());
        assertEquals(2.0, stats.amountPerSecond());
        assertFalse(stats.reliableEstimate());
    }

    @Test
    void fallbackMultiplierKeepsFourAndFiveSampleEstimatesFinite() {
        var multiplier = ConfigNumbers.parseDouble("NaN", 4.0, 1.0, 1000.0);
        var profiler = new CraftProfiler(10, multiplier);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        for (var i = 0; i < 4; i++) {
            profiler.start(ironPlate, 1, ProfileUnit.ITEM, i * 20L);
            profiler.complete(ironPlate, 1, i * 20L + 10);
            profiler.flushCompletedSamples();
        }

        var fourSamples = profiler.stats(ironPlate).orElseThrow();
        assertEquals(2.0, fourSamples.amountPerSecond());
        assertEquals(4, fourSamples.usedSampleCount());
        assertTrue(fourSamples.reliableEstimate());
        assertEquals(5, TimeEstimate.seconds(10, fourSamples).orElseThrow());

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 100);
        profiler.complete(ironPlate, 1, 1_100);
        profiler.flushCompletedSamples();

        var fiveSamples = profiler.stats(ironPlate).orElseThrow();
        assertEquals(2.0, fiveSamples.amountPerSecond());
        assertEquals(4, fiveSamples.usedSampleCount());
        assertFalse(fiveSamples.reliableEstimate());
        assertEquals(5, TimeEstimate.seconds(10, fiveSamples).orElseThrow());
    }

    @Test
    void outlierMultiplierCanBeTuned() {
        var profiler = new CraftProfiler(10, 200.0);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        for (var i = 0; i < 4; i++) {
            profiler.start(ironPlate, 1, ProfileUnit.ITEM, i * 20L);
            profiler.complete(ironPlate, 1, i * 20L + 10);
            profiler.flushCompletedSamples();
        }
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 100);
        profiler.complete(ironPlate, 1, 1_100);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(15.0 / 5_100.0 * 20.0, stats.amountPerSecond());
    }

    @Test
    void statsExistAfterOneCompletedCraft() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 10);
        profiler.complete(ironPlate, 1, 30);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(1, stats.sampleCount());
        assertEquals(20.0, stats.averageDurationTicks());
    }

    @Test
    void tenSampleWindowDropsOlderCrafts() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        for (var i = 1; i <= 11; i++) {
            profiler.start(ironPlate, 1, ProfileUnit.ITEM, 0);
            profiler.complete(ironPlate, 1, i);
            profiler.flushCompletedSamples();
        }

        var stats = profiler.stats(ironPlate).orElseThrow();

        assertEquals(10, stats.sampleCount());
        assertEquals(6.5, stats.averageDurationTicks());
    }

    @Test
    void exportsAndImportsCompletedSamples() {
        var key = new ProfileKey("minecraft:iron_plate");
        var profiler = new CraftProfiler(10);
        profiler.start(key, 2, ProfileUnit.ITEM, 5);
        profiler.complete(key, 2, 25);
        profiler.flushCompletedSamples();

        var restored = new CraftProfiler(10);
        restored.loadSamples(profiler.snapshotSamples());

        var stats = restored.stats(key).orElseThrow();
        assertEquals(1, stats.sampleCount());
        assertEquals(20.0, stats.averageDurationTicks());
        assertEquals(0.1, stats.amountPerTick());
    }

    @Test
    void importKeepsLatestTenSamples() {
        var key = new ProfileKey("minecraft:iron_plate");
        var imported = new CraftProfiler(10);

        imported.loadSamples(List.of(new PersistedOutputSamples(key, ProfileUnit.ITEM, List.of(
                new PersistedCraftSample(1, 1),
                new PersistedCraftSample(1, 2),
                new PersistedCraftSample(1, 3),
                new PersistedCraftSample(1, 4),
                new PersistedCraftSample(1, 5),
                new PersistedCraftSample(1, 6),
                new PersistedCraftSample(1, 7),
                new PersistedCraftSample(1, 8),
                new PersistedCraftSample(1, 9),
                new PersistedCraftSample(1, 10),
                new PersistedCraftSample(1, 11)))));

        var stats = imported.stats(key).orElseThrow();
        assertEquals(10, stats.sampleCount());
        assertEquals(6.5, stats.averageDurationTicks());
    }

    @Test
    void newProfilerStartsWithoutPersistedSessionStats() {
        var key = new ProfileKey("minecraft:iron_plate");
        var oldSession = new CraftProfiler(10);
        oldSession.start(key, 1, ProfileUnit.ITEM, 1);
        oldSession.complete(key, 1, 2);
        oldSession.flushCompletedSamples();

        var newSession = new CraftProfiler(10);

        assertFalse(newSession.stats(key).isPresent());
    }

    @Test
    void outputIdentityMergesSamplesFromSameOutput() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("minecraft:iron_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 0);
        profiler.complete(ironPlate, 1, 10);
        profiler.flushCompletedSamples();
        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 100);
        profiler.complete(ironPlate, 1, 130);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(ironPlate).orElseThrow();
        assertEquals(2, stats.sampleCount());
        assertEquals(20.0, stats.averageDurationTicks());
    }

    @Test
    void networkScopedIdentityKeepsSamplesSeparate() {
        var profiler = new CraftProfiler(10);
        var networkA = new ProfileKey("net-a", "minecraft:iron_plate");
        var networkB = new ProfileKey("net-b", "minecraft:iron_plate");

        profiler.start(networkA, 1, ProfileUnit.ITEM, 0);
        profiler.complete(networkA, 1, 10);
        profiler.flushCompletedSamples();

        assertEquals(1, profiler.stats(networkA).orElseThrow().sampleCount());
        assertFalse(profiler.stats(networkB).isPresent());
    }

    @Test
    void importKeepsScopedNetworkFragmentsSeparate() {
        var imported = new CraftProfiler(10);

        imported.loadSamples(List.of(
                new PersistedOutputSamples(new ProfileKey("net-a", "minecraft:iron_plate"), ProfileUnit.ITEM,
                        List.of(new PersistedCraftSample(1, 10))),
                new PersistedOutputSamples(new ProfileKey("net-b", "minecraft:iron_plate"), ProfileUnit.ITEM,
                        List.of(new PersistedCraftSample(1, 20)))));

        var networkA = imported.stats(new ProfileKey("net-a", "minecraft:iron_plate")).orElseThrow();
        var networkB = imported.stats(new ProfileKey("net-b", "minecraft:iron_plate")).orElseThrow();

        assertEquals(1, networkA.sampleCount());
        assertEquals(10.0, networkA.averageDurationTicks());
        assertEquals(1, networkB.sampleCount());
        assertEquals(20.0, networkB.averageDurationTicks());
        assertEquals(List.of(
                new ProfileKey("net-a", "minecraft:iron_plate"),
                new ProfileKey("net-b", "minecraft:iron_plate")),
                imported.snapshotSamples().stream().map(PersistedOutputSamples::key).sorted((a, b) -> a.networkId()
                        .compareTo(b.networkId())).toList());
    }

    @Test
    void importSkipsInvalidSamples() {
        var key = new ProfileKey("net-a", "minecraft:iron_plate");
        var profiler = new CraftProfiler(10);

        profiler.loadSamples(List.of(new PersistedOutputSamples(key, ProfileUnit.ITEM, List.of(
                new PersistedCraftSample(0, 10),
                new PersistedCraftSample(1, 0),
                new PersistedCraftSample(2, 20)))));

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(1, stats.sampleCount());
        assertEquals(20.0, stats.averageDurationTicks());
    }

    @Test
    void importSkipsInvalidOutputs() {
        var key = new ProfileKey("minecraft:iron_plate");
        var profiler = new CraftProfiler(10);

        profiler.loadSamples(Arrays.asList(
                null,
                new PersistedOutputSamples(null, ProfileUnit.ITEM, List.of(new PersistedCraftSample(1, 1))),
                new PersistedOutputSamples(key, null, List.of(new PersistedCraftSample(1, 1))),
                new PersistedOutputSamples(key, ProfileUnit.ITEM, List.of(new PersistedCraftSample(2, 20)))));

        assertEquals(1, profiler.stats(key).orElseThrow().sampleCount());
    }

    @Test
    void clearsOnlyRequestedOutputSamples() {
        var profiler = new CraftProfiler(10);
        var ironPlate = new ProfileKey("net-a", "minecraft:iron_plate");
        var copperPlate = new ProfileKey("net-a", "minecraft:copper_plate");

        profiler.start(ironPlate, 1, ProfileUnit.ITEM, 0);
        profiler.complete(ironPlate, 1, 10);
        profiler.start(copperPlate, 1, ProfileUnit.ITEM, 0);
        profiler.complete(copperPlate, 1, 20);
        profiler.flushCompletedSamples();

        profiler.clearSamples(ironPlate);

        assertFalse(profiler.stats(ironPlate).isPresent());
        assertEquals(1, profiler.stats(copperPlate).orElseThrow().sampleCount());
    }

    @Test
    void recordsCompletedIntervalsWhileWorkRemains() {
        var profiler = new CraftProfiler(10);
        var fluid = new ProfileKey("minecraft:water");

        profiler.start(fluid, 1000, ProfileUnit.MILLIBUCKET, 5);
        profiler.complete(fluid, 250, 10);
        assertTrue(profiler.flushCompletedSamples());

        var first = profiler.stats(fluid).orElseThrow();
        assertEquals(List.of(250L), first.sampleAmounts());
        assertEquals(List.of(5L), first.sampleDurationTicks());

        profiler.complete(fluid, 750, 25);
        assertTrue(profiler.flushCompletedSamples());

        var stats = profiler.stats(fluid).orElseThrow();
        assertEquals(ProfileUnit.MILLIBUCKET, stats.unit());
        assertEquals(List.of(250L, 750L), stats.sampleAmounts());
        assertEquals(List.of(5L, 15L), stats.sampleDurationTicks());
    }

    @Test
    void combinesParallelCpuBatchesIntoNetworkThroughput() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cpuA = new Object();
        var cpuB = new Object();

        profiler.start(key, cpuA, 64, ProfileUnit.ITEM, 0);
        profiler.start(key, cpuB, 64, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpuA, 64, 20);

        assertFalse(profiler.stats(key).isPresent());

        profiler.complete(key, cpuB, 64, 20);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(128.0, stats.amountPerSecond());
        assertEquals(List.of(128L), stats.sampleAmounts());
        assertEquals(List.of(20L), stats.sampleDurationTicks());
    }

    @Test
    void flushesRealProgressBeforeContinuousProductionBecomesIdle() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cpu = new Object();

        profiler.start(key, cpu, 3, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);

        assertFalse(profiler.stats(key).isPresent());
        assertTrue(profiler.flushCompletedSamples());
        var retained = profiler.stats(key).orElseThrow();
        assertEquals(1.0, retained.amountPerSecond());
        assertFalse(retained.reliableEstimate());
        assertEquals(List.of(1L), retained.sampleAmounts());

        var sameTickKey = new ProfileKey("minecraft:copper_ingot");
        profiler.start(sameTickKey, cpu, 2, ProfileUnit.ITEM, 20);
        profiler.complete(sameTickKey, cpu, 1, 20);
        profiler.flushCompletedSamples();
        assertEquals(20.0, profiler.stats(sameTickKey).orElseThrow().amountPerSecond());
    }

    @Test
    void combinesOneReturnAcrossSeveralDispatchedBatches() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cpu = new Object();

        profiler.start(key, cpu, 32, ProfileUnit.ITEM, 0);
        profiler.start(key, cpu, 32, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 64, 20);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(64.0, stats.amountPerSecond());
        assertEquals(List.of(64L), stats.sampleAmounts());
    }

    @Test
    void cancelledCpuDoesNotPoisonFutureSamples() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cancelledCpu = new Object();
        var nextCpu = new Object();

        profiler.start(key, cancelledCpu, 64, ProfileUnit.ITEM, 0);
        profiler.clearPending(cancelledCpu);
        profiler.start(key, nextCpu, 64, ProfileUnit.ITEM, 1_000);
        profiler.complete(key, nextCpu, 64, 1_020);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(20.0, stats.averageDurationTicks());
        assertEquals(64.0, stats.amountPerSecond());
    }

    @Test
    void sameTickBatchUsesOneTickMinimum() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");

        profiler.start(key, 64, ProfileUnit.ITEM, 10);
        profiler.complete(key, 64, 10);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(1.0, stats.averageDurationTicks());
        assertEquals(1_280.0, stats.amountPerSecond());
    }

    @Test
    void clearingSamplesAlsoClearsPendingWork() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");

        profiler.start(key, 64, ProfileUnit.ITEM, 0);
        profiler.clearSamples(key);
        profiler.complete(key, 64, 1_000);

        assertFalse(profiler.stats(key).isPresent());
    }

    @Test
    void disabledProfilerIgnoresStartsAndCompletions() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:gear");

        profiler.setEnabled(false);
        profiler.updateCapacity(new Object(), 1, 1, 0);
        profiler.start(key, 1, ProfileUnit.ITEM, 1);
        profiler.complete(key, 1, 10);

        assertFalse(profiler.stats(key).isPresent());
        profiler.setEnabled(true);
    }

    @Test
    void ignoresInvalidOrUnmatchedRuntimeEvents() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:gear");
        var scope = new Object();

        profiler.start(key, scope, 0, ProfileUnit.ITEM, 0);
        assertFalse(profiler.complete(key, scope, 0, 1));
        assertFalse(profiler.complete(key, scope, 1, 1));
        profiler.clearPending(scope);
        assertFalse(profiler.clearSamples(key));
        assertFalse(profiler.stall(key, scope, 1_000).isPresent());
        profiler.start(key, scope, 1, ProfileUnit.ITEM, 0);
        assertFalse(profiler.stall(key, scope, 1_000).isPresent());
        profiler.updateCapacity(null, 1, 1, 0);
        profiler.updateCapacity(scope, 1, 0, 0);
    }

    @Test
    void clearingOneCpuRebuildsTheSharedBusyWindow() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:gear");
        var cancelled = new Object();
        var retained = new Object();
        var unrelated = new Object();
        var otherKey = new ProfileKey("minecraft:plate");

        profiler.start(key, cancelled, 1, ProfileUnit.ITEM, 0);
        profiler.start(key, retained, 1, ProfileUnit.ITEM, 10);
        profiler.start(key, retained, 1, ProfileUnit.ITEM, 0);
        profiler.start(key, retained, 1, ProfileUnit.ITEM, 20);
        profiler.start(otherKey, unrelated, 1, ProfileUnit.ITEM, 0);
        profiler.clearPending(cancelled);
        assertTrue(profiler.complete(key, retained, 3, 30));
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(30.0, stats.averageDurationTicks());
    }

    @Test
    void completingOneOutputKeepsOtherWorkInTheSameScope() {
        var profiler = new CraftProfiler(10);
        var scope = new Object();
        var gear = new ProfileKey("minecraft:gear");
        var plate = new ProfileKey("minecraft:plate");

        profiler.start(gear, scope, 1, ProfileUnit.ITEM, 0);
        profiler.start(plate, scope, 1, ProfileUnit.ITEM, 0);

        assertTrue(profiler.complete(gear, scope, 2, 20));
        assertTrue(profiler.complete(plate, scope, 1, 20));
    }

    @Test
    void capacityUsageIsClampedToItsValidRange() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:gear");
        var scope = new Object();

        profiler.start(key, scope, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, scope, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, scope, 1, ProfileUnit.ITEM, 100);
        profiler.updateCapacity(scope, 10, 4, 700);

        assertEquals(4, profiler.stall(key, scope, 700).orElseThrow().usedParallelSlots());
        profiler.updateCapacity(scope, -1, 4, 700);
        assertEquals(0, profiler.stall(key, scope, 700).orElseThrow().usedParallelSlots());
    }

    @Test
    void reportsDelayedPendingOutputAfterTypicalThreshold() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 200);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 300);
        profiler.updateCapacity(cpu, 1, 4, 300);

        assertFalse(profiler.stall(key, cpu, 699).isPresent());
        profiler.updateCapacity(cpu, 1, 4, 700);
        var diagnostic = profiler.stall(key, cpu, 700).orElseThrow();

        assertEquals(400, diagnostic.idleTicks());
        assertEquals(200.0, diagnostic.typicalDurationTicks());
        assertEquals(1, diagnostic.activeBatches());
        assertEquals(1, diagnostic.usedParallelSlots());
        assertEquals(4, diagnostic.totalParallelSlots());
    }

    @Test
    void reportsDelayedPendingOutputAtTenSecondMinimum() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);

        assertFalse(profiler.stall(key, cpu, 299).isPresent());
        assertEquals(200, profiler.stall(key, cpu, 300).orElseThrow().idleTicks());
    }

    @Test
    void partialOutputResetsTheDelayClock() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 100);
        profiler.complete(key, cpu, 1, 650);

        assertFalse(profiler.stall(key, cpu, 849).isPresent());
        assertEquals(200, profiler.stall(key, cpu, 850).orElseThrow().idleTicks());
    }

    @Test
    void staleCapacityIsNotPresentedAsCurrent() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.updateCapacity(cpu, 4, 4, 100);

        var diagnostic = profiler.stall(key, cpu, 300).orElseThrow();

        assertEquals(0, diagnostic.usedParallelSlots());
        assertEquals(0, diagnostic.totalParallelSlots());
    }

    @Test
    void tracksWaitingTimeUntilEachOutputsFirstDispatch() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");
        var unrelated = new ProfileKey("minecraft:gear");

        profiler.startWaiting(cpu, List.of(iron, copper), 100);

        assertEquals(0, profiler.waitingTicks(iron, cpu, 90).orElseThrow());
        assertEquals(20, profiler.waitingTicks(iron, cpu, 120).orElseThrow());
        assertFalse(profiler.waitingTicks(null, cpu, 120).isPresent());
        assertFalse(profiler.waitingTicks(unrelated, cpu, 120).isPresent());
        assertFalse(profiler.waitingTicks(iron, new Object(), 120).isPresent());

        profiler.start(unrelated, cpu, 1, ProfileUnit.ITEM, 121);
        assertTrue(profiler.waitingTicks(iron, cpu, 121).isPresent());
        profiler.start(iron, cpu, 1, ProfileUnit.ITEM, 122);
        assertFalse(profiler.waitingTicks(iron, cpu, 122).isPresent());
        assertTrue(profiler.waitingTicks(copper, cpu, 122).isPresent());
        profiler.start(copper, cpu, 1, ProfileUnit.ITEM, 123);
        profiler.start(copper, cpu, 1, ProfileUnit.ITEM, 124);
        assertFalse(profiler.waitingTicks(copper, cpu, 124).isPresent());
    }

    @Test
    void replacesAndClearsWaitingRuntimeState() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");

        profiler.startWaiting(cpu, List.of(iron), 10);
        profiler.startWaiting(cpu, List.of(copper), 20);
        assertFalse(profiler.waitingTicks(iron, cpu, 30).isPresent());
        assertEquals(10, profiler.waitingTicks(copper, cpu, 30).orElseThrow());

        profiler.clearPending(cpu);
        assertFalse(profiler.waitingTicks(copper, cpu, 30).isPresent());
        profiler.startWaiting(cpu, List.of(iron), 40);
        profiler.loadSamples(List.of());
        assertFalse(profiler.waitingTicks(iron, cpu, 50).isPresent());
        profiler.startWaiting(cpu, List.of(iron), 60);
        profiler.setEnabled(false);
        assertFalse(profiler.waitingTicks(iron, cpu, 70).isPresent());
    }

    @Test
    void ignoresInvalidWaitingRegistrationsAndDropsEmptyReplacement() {        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var key = new ProfileKey("minecraft:iron_plate");

        profiler.startWaiting(null, List.of(key), 0);
        profiler.startWaiting(cpu, null, 0);
        profiler.startWaiting(cpu, Arrays.asList(null, key), 0);
        assertTrue(profiler.waitingTicks(key, cpu, 0).isPresent());
        profiler.startWaiting(cpu, Arrays.asList((ProfileKey) null), 1);
        assertFalse(profiler.waitingTicks(key, cpu, 1).isPresent());
        profiler.setEnabled(false);
        profiler.startWaiting(cpu, List.of(key), 2);
        assertFalse(profiler.waitingTicks(key, cpu, 2).isPresent());
    }

    @Test
    void rejectsStatusWithoutKeyOrKind() {
        var key = new ProfileKey("minecraft:iron_plate");
        assertThrows(NullPointerException.class, () -> new PersistedOutputStatus(null, StatusKind.DELAYED, 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new PersistedOutputStatus(key, null, 0, 0, 0));
    }

    @Test
    void ignoresNullRememberedStatusAndRestoresNullAsEmpty() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");

        profiler.rememberStatus(null);
        assertTrue(profiler.snapshotStatuses().isEmpty());
        assertFalse(profiler.rememberedStall(null).isPresent());
        assertFalse(profiler.rememberedStall(key).isPresent());
        assertFalse(profiler.rememberedWaitingTicks(null, 100).isPresent());
        assertFalse(profiler.rememberedWaitingTicks(new ProfileKey("minecraft:copper_plate"), 100).isPresent());
        assertTrue(profiler.rememberedReasons().isEmpty());
        assertFalse(profiler.hasPending(null));
        assertFalse(profiler.hasPending(key));

        profiler.rememberStatus(new PersistedOutputStatus(key, StatusKind.DELAYED, 300, 100.0, 50));
        profiler.restoreStatuses(null);
        assertTrue(profiler.snapshotStatuses().isEmpty());
    }

    @Test
    void delayedStallSurvivesReloadUntilFreshDispatch() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 400).size());

        // World save, then a reload clears live state but keeps the snapshot.
        var snapshot = profiler.snapshotStatuses();
        profiler.loadSamples(List.of());
        assertFalse(profiler.rememberedStall(key).isPresent());
        profiler.restoreStatuses(snapshot);

        var diagnostic = profiler.rememberedStall(key).orElseThrow();
        assertEquals(300, diagnostic.idleTicks());
        assertEquals(20.0, diagnostic.typicalDurationTicks());

        // A resumed craft reports live values again instead of the memory.
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 1000);
        assertTrue(profiler.hasPending(key));
        assertFalse(profiler.rememberedStall(key).isPresent());
    }

    @Test
    void livePendingSuppressesRememberedStall() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var cpu = new Object();

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.rememberStatus(new PersistedOutputStatus(key, StatusKind.DELAYED, 300, 20.0, 0));
        assertFalse(profiler.rememberedStall(key).isPresent());

        profiler.rememberStatus(new PersistedOutputStatus(key, StatusKind.WAITING, 0, 0, 0));
        assertFalse(profiler.rememberedWaitingTicks(key, 100).isPresent());

        profiler.complete(key, cpu, 1, 20);
        assertFalse(profiler.rememberedStall(key).isPresent());
    }

    @Test
    void clearingOneCpuKeepsStatusesForWorkStillPendingElsewhere() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");
        var first = new Object();
        var second = new Object();

        profiler.start(key, first, 1, ProfileUnit.ITEM, 0);
        profiler.start(key, second, 1, ProfileUnit.ITEM, 0);
        profiler.rememberStatus(new PersistedOutputStatus(key, StatusKind.NO_POWER, 0, 0, 0));

        profiler.clearPending(first);
        assertEquals(CraftingBlockReason.NO_POWER, profiler.rememberedReasons().get(key));

        profiler.clearPending(second);
        assertTrue(profiler.rememberedReasons().isEmpty());
    }

    @Test
    void finalOutputAllowsPlateCleanupOnlyAfterEveryScopeHasCompleted() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:overworld|60,80,1026", "minecraft:water");
        var reloadedCpu = new Object();
        var parallelCpu = new Object();

        profiler.start(key, reloadedCpu, 1000, ProfileUnit.MILLIBUCKET, 0);
        profiler.start(key, parallelCpu, 1000, ProfileUnit.MILLIBUCKET, 0);
        assertTrue(profiler.complete(key, reloadedCpu, 1000, 20));
        assertTrue(profiler.hasPending(key));

        assertTrue(profiler.complete(key, parallelCpu, 1000, 21));
        assertFalse(profiler.hasPending(key));
    }

    @Test
    void reloadedCpuCompletesOnlyAnUnambiguousPendingOutput() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:overworld|60,80,1026", "minecraft:water");
        var oldCpu = new Object();
        var newCpu = new Object();
        var parallelCpu = new Object();
        profiler.start(new ProfileKey("minecraft:overworld|60,80,1026", "minecraft:lava"),
                new Object(), 1000, ProfileUnit.MILLIBUCKET, 0);

        assertFalse(profiler.completeUniquePending(null, 1000, 1));
        assertFalse(profiler.completeUniquePending(key, 0, 1));
        assertFalse(profiler.completeUniquePending(key, 1000, 1));
        profiler.start(key, oldCpu, 1000, ProfileUnit.MILLIBUCKET, 1);
        profiler.start(key, parallelCpu, 1000, ProfileUnit.MILLIBUCKET, 1);
        assertFalse(profiler.complete(key, newCpu, 1000, 20));
        assertFalse(profiler.completeUniquePending(key, 1000, 20));
        assertTrue(profiler.hasPending(key));

        assertTrue(profiler.complete(key, parallelCpu, 1000, 21));
        assertTrue(profiler.completeUniquePending(key, 1000, 22));
        assertFalse(profiler.hasPending(key));
    }

    @Test
    void waitingKeysSnapshotAndRestoreForDisplay() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var iron = new ProfileKey("minecraft:iron_plate");
        var copper = new ProfileKey("minecraft:copper_plate");

        profiler.startWaiting(cpu, List.of(iron, copper), 100);
        profiler.rememberStatus(new PersistedOutputStatus(copper, StatusKind.DELAYED, 300, 20.0, 100));

        var snapshot = profiler.snapshotStatuses();
        assertEquals(2, snapshot.size());

        profiler.loadSamples(List.of());
        profiler.restoreStatuses(snapshot);
        assertEquals(20, profiler.rememberedWaitingTicks(iron, 120).orElseThrow());
        // The delayed memory is not a waiting row for the same key.
        assertFalse(profiler.rememberedWaitingTicks(copper, 130).isPresent());
        // The delayed memory wins over the live waiting row for the same key.
        assertTrue(profiler.rememberedStall(copper).isPresent());

        profiler.restoreStatuses(Arrays.asList(null,
                new PersistedOutputStatus(iron, StatusKind.NO_PROVIDER, 0, 0, 0)));
        assertEquals(CraftingBlockReason.NO_PROVIDER, profiler.rememberedReasons().get(iron));
        assertFalse(profiler.rememberedStall(iron).isPresent());
    }

    @Test
    void rememberedReasonsOnlyCoverBlockReasons() {
        var profiler = new CraftProfiler(10);
        var delayed = new ProfileKey("minecraft:iron_plate");
        var waiting = new ProfileKey("minecraft:copper_plate");
        var power = new ProfileKey("minecraft:gear");
        var provider = new ProfileKey("minecraft:stick");

        profiler.rememberStatus(new PersistedOutputStatus(delayed, StatusKind.DELAYED, 1, 1.0, 0));
        profiler.rememberStatus(new PersistedOutputStatus(waiting, StatusKind.WAITING, 0, 0, 0));
        profiler.rememberStatus(new PersistedOutputStatus(power, StatusKind.NO_POWER, 0, 0, 0));
        profiler.rememberStatus(new PersistedOutputStatus(provider, StatusKind.NO_PROVIDER, 0, 0, 0));

        var reasons = profiler.rememberedReasons();
        assertEquals(2, reasons.size());
        assertEquals(CraftingBlockReason.NO_POWER, reasons.get(power));
        assertEquals(CraftingBlockReason.NO_PROVIDER, reasons.get(provider));
    }

    @Test
    void livePendingSuppressesRememberedBlockReasons() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var provider = new ProfileKey("minecraft:iron_plate");
        var power = new ProfileKey("minecraft:copper_plate");
        var restored = new ProfileKey("minecraft:gold_plate");

        profiler.start(provider, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.start(power, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.rememberStatus(new PersistedOutputStatus(provider, StatusKind.NO_PROVIDER, 0, 0, 0));
        profiler.rememberStatus(new PersistedOutputStatus(power, StatusKind.NO_POWER, 0, 0, 0));
        profiler.rememberStatus(new PersistedOutputStatus(restored, StatusKind.NO_PROVIDER, 0, 0, 0));

        assertEquals(Map.of(restored, CraftingBlockReason.NO_PROVIDER), profiler.rememberedReasons(cpu));
        assertEquals(Map.of(provider, CraftingBlockReason.NO_PROVIDER),
                profiler.blockReasons(cpu, 1, Set.of(provider)));
        profiler.observeDispatchPower(cpu, "pattern", Map.of(power, 1L), 10, 0, 1);
        assertEquals(Map.of(power, CraftingBlockReason.NO_POWER),
                profiler.blockReasons(cpu, 1, Set.of()));
    }

    @Test
    void disablingDropsRememberedStatuses() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_plate");

        profiler.rememberStatus(new PersistedOutputStatus(key, StatusKind.DELAYED, 300, 20.0, 0));
        profiler.setEnabled(false);
        assertTrue(profiler.snapshotStatuses().isEmpty());
    }

    @Test
    void scopedKeysListsWaitingAndPendingOutputs() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var iron = new ProfileKey("minecraft:iron_plate");
        var gear = new ProfileKey("minecraft:gear");

        assertTrue(profiler.scopedKeys(null).isEmpty());
        assertTrue(profiler.scopedKeys(cpu).isEmpty());

        profiler.startWaiting(cpu, List.of(iron), 100);
        assertEquals(Set.of(iron), profiler.scopedKeys(cpu));

        profiler.start(gear, cpu, 1, ProfileUnit.ITEM, 101);
        assertEquals(Set.of(iron, gear), profiler.scopedKeys(cpu));

        // Dispatching the waiting key leaves it tracked as pending work.
        profiler.start(iron, cpu, 1, ProfileUnit.ITEM, 102);
        assertEquals(Set.of(iron, gear), profiler.scopedKeys(cpu));

        profiler.clearPending(cpu);
        assertTrue(profiler.scopedKeys(cpu).isEmpty());
    }

    @Test
    void scopedKeysListsPendingWithoutWaiting() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var copper = new ProfileKey("minecraft:copper_plate");

        profiler.start(copper, cpu, 1, ProfileUnit.ITEM, 0);

        assertEquals(Set.of(copper), profiler.scopedKeys(cpu));
    }

    @Test
    void batchesSameTickReturnsAndAdvancesTheIntervalCursor() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var first = new Object();
        var second = new Object();

        profiler.start(key, first, 10, ProfileUnit.ITEM, 0);
        profiler.start(key, second, 10, ProfileUnit.ITEM, 0);
        profiler.complete(key, first, 4, 90);
        profiler.complete(key, second, 5, 90);
        assertTrue(profiler.flushCompletedSamples());
        assertFalse(profiler.flushCompletedSamples());

        profiler.complete(key, first, 1, 190);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(List.of(9L, 1L), stats.sampleAmounts());
        assertEquals(List.of(90L, 100L), stats.sampleDurationTicks());
    }

    @Test
    void earlyFlushAmendsTheSameTickTailWithoutDuplicatingIt() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cpu = new Object();

        profiler.start(key, cpu, 9, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 4, 90);
        profiler.flushCompletedSamples();
        profiler.complete(key, cpu, 5, 90);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(1, stats.sampleCount());
        assertEquals(List.of(9L), stats.sampleAmounts());
        assertEquals(List.of(90L), stats.sampleDurationTicks());
    }

    @Test
    void cancellationKeepsAcceptedEvidenceButResetAndReloadDropPendingBuckets() {
        var key = new ProfileKey("minecraft:iron_ingot");
        var profiler = new CraftProfiler(10);
        var cpu = new Object();

        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 100);
        profiler.clearPending(cpu);
        profiler.flushCompletedSamples();
        assertEquals(List.of(1L), profiler.stats(key).orElseThrow().sampleAmounts());

        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 200);
        profiler.complete(key, cpu, 1, 300);
        assertTrue(profiler.clearSamples(key));
        assertFalse(profiler.flushCompletedSamples());
        assertFalse(profiler.stats(key).isPresent());

        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 400);
        profiler.complete(key, cpu, 1, 500);
        profiler.loadSamples(List.of());
        assertFalse(profiler.flushCompletedSamples());
        assertFalse(profiler.stats(key).isPresent());

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 600);
        profiler.clearPending(cpu);
        assertFalse(profiler.flushCompletedSamples());
    }

    @Test
    void newerEventsDefensivelyFinalizeOlderReturnsAndClockRegressionStaysPositive() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var cpu = new Object();

        profiler.start(key, cpu, 2, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 100);
        profiler.complete(key, cpu, 1, 300);
        profiler.flushCompletedSamples();
        assertEquals(List.of(100L, 200L), profiler.stats(key).orElseThrow().sampleDurationTicks());

        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 500);
        profiler.complete(key, cpu, 1, 400);
        profiler.flushCompletedSamples();
        assertEquals(1L, profiler.stats(key).orElseThrow().lastDurationTicks());
    }

    @Test
    void checkedSameTickOverflowDiscardsTheCorruptInterval() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var finishedKey = new ProfileKey("minecraft:gold_ingot");
        var first = new Object();
        var second = new Object();
        var third = new Object();

        profiler.start(key, first, Long.MAX_VALUE, ProfileUnit.ITEM, 0);
        profiler.start(key, second, 1, ProfileUnit.ITEM, 0);
        profiler.start(key, third, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, first, Long.MAX_VALUE, 20);
        profiler.flushCompletedSamples();
        profiler.complete(key, second, 1, 20);
        profiler.flushCompletedSamples();
        profiler.complete(key, third, 1, 40);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(List.of(1L), stats.sampleAmounts());
        assertEquals(List.of(20L), stats.sampleDurationTicks());

        profiler.start(finishedKey, third, 1, ProfileUnit.ITEM, 0);
        profiler.complete(finishedKey, third, 1, 10);
        profiler.flushCompletedSamples();
        profiler.start(finishedKey, first, Long.MAX_VALUE, ProfileUnit.ITEM, 50);
        profiler.start(finishedKey, second, 1, ProfileUnit.ITEM, 50);
        profiler.complete(finishedKey, first, Long.MAX_VALUE, 70);
        profiler.flushCompletedSamples();
        profiler.complete(finishedKey, second, 1, 70);
        profiler.flushCompletedSamples();
        assertEquals(List.of(1L), profiler.stats(finishedKey).orElseThrow().sampleAmounts());
    }

    @Test
    void clockRegressionAfterAnUnflushedReturnStartsFreshEvidence() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("minecraft:iron_ingot");
        var first = new Object();
        var second = new Object();

        profiler.start(key, first, 2, ProfileUnit.ITEM, 0);
        profiler.complete(key, first, 1, 100);
        profiler.start(key, second, 1, ProfileUnit.ITEM, 50);
        profiler.complete(key, first, 1, 60);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(key).orElseThrow();
        assertEquals(List.of(1L), stats.sampleAmounts());
        assertEquals(List.of(10L), stats.sampleDurationTicks());
    }

    @Test
    void checkedIntervalOverflowResetsWhilePendingAndDropsWhenFinished() {
        var profiler = new CraftProfiler(10);
        var pendingKey = new ProfileKey("minecraft:iron_ingot");
        var finishedKey = new ProfileKey("minecraft:gold_ingot");
        var first = new Object();
        var second = new Object();
        var third = new Object();

        profiler.start(pendingKey, first, Long.MAX_VALUE, ProfileUnit.ITEM, 0);
        profiler.start(pendingKey, second, 1, ProfileUnit.ITEM, 0);
        profiler.start(pendingKey, third, 1, ProfileUnit.ITEM, 0);
        profiler.complete(pendingKey, first, Long.MAX_VALUE, 20);
        profiler.complete(pendingKey, second, 1, 20);
        profiler.complete(pendingKey, third, 1, 40);

        profiler.start(finishedKey, first, Long.MAX_VALUE, ProfileUnit.ITEM, 50);
        profiler.start(finishedKey, second, 1, ProfileUnit.ITEM, 50);
        profiler.complete(finishedKey, first, Long.MAX_VALUE, 70);
        profiler.complete(finishedKey, second, 1, 70);
        profiler.flushCompletedSamples();

        var stats = profiler.stats(pendingKey).orElseThrow();
        assertEquals(List.of(1L), stats.sampleAmounts());
        assertEquals(List.of(20L), stats.sampleDurationTicks());
        assertFalse(profiler.stats(finishedKey).isPresent());
    }
}
