package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DelayedNotificationTest {
    @Test
    void oneLiveDiagnosticSnapshotFeedsPlatesAndEpisodeTransitions() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var output = key("minecraft:iron_plate");
        seedTypical(profiler, output, new Object());
        profiler.start(output, cpu, 2, ProfileUnit.ITEM, 300);
        assertTrue(profiler.delayedDiagnostics(cpu, 400).isEmpty());
        var live = profiler.delayedDiagnostics(cpu, 800);
        assertEquals(Set.of(output), live.keySet());
        var newly = profiler.pollNewlyDelayed(cpu, 800, live);
        assertEquals(1, newly.size());
        assertSame(live.get(output), newly.get(0).diagnostic());
        assertTrue(profiler.pollNewlyDelayed(cpu, 800, live).isEmpty());
        profiler.complete(output, cpu, 1, 801);
        var recovered = profiler.delayedDiagnostics(cpu, 802);
        assertTrue(recovered.isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 802, recovered).isEmpty());
        assertEquals(List.of(output), profiler.pollResolvedDelayed(cpu));
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 1300, profiler.delayedDiagnostics(cpu, 1300)).size());
        profiler.setSuspended(cpu, true, 1301);
        assertTrue(profiler.delayedDiagnostics(cpu, 2000).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 2000, live).isEmpty());
        assertTrue(profiler.delayedDiagnostics(null, 2000).isEmpty());
        profiler.setEnabled(false);
        assertTrue(profiler.delayedDiagnostics(cpu, 2000).isEmpty());
    }

    @Test
    void activeOutputCleanupSeparatesOwnersAndKeepsWaitingJobs() {
        var profiler = new CraftProfiler(10);
        var output = key("minecraft:iron_plate");
        var first = new Object();
        var second = new Object();
        var firstOwner = UUID.randomUUID();
        var secondOwner = UUID.randomUUID();
        profiler.setJobOwner(first, firstOwner);
        profiler.setJobOwner(second, secondOwner);
        profiler.start(output, first, 1, ProfileUnit.ITEM, 100);
        profiler.startWaiting(second, Set.of(output), 100);
        assertFalse(profiler.hasActiveOutput(key("minecraft:copper_plate"), firstOwner));
        assertFalse(profiler.hasActiveOutput(output, UUID.randomUUID()));
        assertTrue(profiler.hasActiveOutput(output, null));
        assertTrue(profiler.hasActiveOutput(output, firstOwner));
        assertTrue(profiler.hasActiveOutput(output, secondOwner));
        profiler.complete(output, first, 1, 120);
        assertFalse(profiler.hasActiveOutput(output, firstOwner));
        assertFalse(profiler.hasActiveOutput(key("minecraft:copper_plate"), secondOwner));
        assertTrue(profiler.hasActiveOutput(output, null));
        profiler.clearPending(second);
        assertFalse(profiler.hasActiveOutput(output, null));
        assertFalse(profiler.hasActiveOutput(null, firstOwner));
    }

    @Test
    void immediateCompletionRetainsPlateKeyUntilFinishCleanup() {
        var profiler = new CraftProfiler(10);
        var output = key("minecraft:iron_plate");
        var cpu = new Object();
        seedTypical(profiler, output, new Object());
        profiler.start(output, cpu, 1, ProfileUnit.ITEM, 300);
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());

        assertTrue(profiler.complete(output, cpu, 1, 801));
        assertFalse(profiler.hasPending(output));
        assertEquals(java.util.Set.of(output), profiler.scopedKeys(cpu));

        profiler.clearPending(cpu);
        assertTrue(profiler.scopedKeys(cpu).isEmpty());
        assertFalse(profiler.isDelayed(output));
        assertTrue(profiler.snapshotStatuses().isEmpty());
    }

    @Test
    void completionPollQueuesClearUntilDrainedEvenWithoutPendingOutputs() {
        var profiler = new CraftProfiler(10);
        var output = key("minecraft:iron_plate");
        var cpu = new Object();
        seedTypical(profiler, output, new Object());
        profiler.start(output, cpu, 1, ProfileUnit.ITEM, 300);
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        profiler.complete(output, cpu, 1, 801);

        assertTrue(profiler.pollNewlyDelayed(cpu, 802).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 803).isEmpty());
        assertFalse(profiler.isDelayed(output));
        assertEquals(java.util.Set.of(output), profiler.scopedKeys(cpu));
        assertEquals(List.of(output), profiler.pollResolvedDelayed(cpu));
        assertTrue(profiler.pollResolvedDelayed(cpu).isEmpty());
        assertTrue(profiler.scopedKeys(cpu).isEmpty());
    }

    @Test
    void discardedScopeCannotWarnOrLearnFromLaterCompletion() {
        var profiler = new CraftProfiler(10);
        var output = key("minecraft:iron_plate");
        var cpu = new Object();
        seedTypical(profiler, output, new Object());
        var samples = profiler.snapshotSamples();
        profiler.start(output, cpu, 1, ProfileUnit.ITEM, 300);
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());

        profiler.clearPending(cpu);
        assertFalse(profiler.complete(output, cpu, 1, 801));
        assertFalse(profiler.hasPending(output));
        assertTrue(profiler.pollNewlyDelayed(cpu, 1_000).isEmpty());
        assertEquals(samples, profiler.snapshotSamples());
        assertTrue(profiler.snapshotStatuses().isEmpty());
    }

    private static ProfileKey key(String id) {
        return new ProfileKey(id);
    }

    private static void seedTypical(CraftProfiler profiler, ProfileKey key, Object seedScope) {
        profiler.start(key, seedScope, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, seedScope, 1, 200);
        profiler.flushCompletedSamples();
    }

    @Test
    void notifiesOncePerDelayedEpisode() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();
        var owner = UUID.randomUUID();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 300);
        profiler.setJobOwner(cpu, owner);

        assertTrue(profiler.pollNewlyDelayed(cpu, 400).isEmpty());
        var first = profiler.pollNewlyDelayed(cpu, 800);
        assertEquals(1, first.size());
        assertEquals(key, first.get(0).key());
        // Repeated polls do not duplicate.
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 1_000).isEmpty());
    }

    @Test
    void liveDelayedPlateEvidenceDoesNotWaitForOfflineChatPoll() {
        var profiler = new CraftProfiler(10);
        var output = key("minecraft:iron_plate");
        var cpu = new Object();
        seedTypical(profiler, output, new Object());
        profiler.start(output, cpu, 1, ProfileUnit.ITEM, 300);

        assertTrue(profiler.liveDelayedKeys(cpu, 400).isEmpty());
        assertTrue(profiler.liveDelayedKeys(null, 400).isEmpty());
        assertTrue(profiler.delayedKeys(cpu).isEmpty());
        assertEquals(Set.of(output), profiler.liveDelayedKeys(cpu, 800));
        profiler.complete(output, cpu, 1, 801);
        assertTrue(profiler.liveDelayedKeys(cpu, 802).isEmpty());
        profiler.setEnabled(false);
        assertTrue(profiler.liveDelayedKeys(cpu, 802).isEmpty());
    }

    @Test
    void progressClearsEpisodeAndRearms() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        var first = profiler.pollNewlyDelayed(cpu, 800);
        assertEquals(1, first.size());
        assertTrue(profiler.pollNewlyDelayed(cpu, 850).isEmpty());

        // Partial progress resets the delay clock and clears the notified episode.
        profiler.complete(key, cpu, 1, 860);
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());

        var second = profiler.pollNewlyDelayed(cpu, 1_300);
        assertEquals(1, second.size());
    }

    @Test
    void nullScopeAndDisabledAreSafe() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:gear");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);

        assertTrue(profiler.pollNewlyDelayed(null, 1_000).isEmpty());
        assertTrue(profiler.jobOwner(null).isEmpty());

        profiler.setJobOwner(null, UUID.randomUUID());
        profiler.setJobOwner(cpu, null);
        assertTrue(profiler.jobOwner(cpu).isEmpty());

        profiler.setJobOwner(cpu, UUID.randomUUID());
        profiler.setEnabled(false);
        assertTrue(profiler.pollNewlyDelayed(cpu, 1_000).isEmpty());
        assertTrue(profiler.jobOwner(cpu).isEmpty());
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertTrue(profiler.jobOwner(cpu).isEmpty());
        profiler.setEnabled(true);
    }

    @Test
    void ownerLifecycle() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var owner = UUID.randomUUID();

        assertTrue(profiler.jobOwner(cpu).isEmpty());
        profiler.setJobOwner(cpu, owner);
        assertEquals(owner, profiler.jobOwner(cpu).orElseThrow());
        profiler.setJobOwner(cpu, null);
        assertTrue(profiler.jobOwner(cpu).isEmpty());

        profiler.setJobOwner(cpu, owner);
        profiler.clearPending(cpu);
        assertTrue(profiler.jobOwner(cpu).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 10_000).isEmpty());
    }

    @Test
    void newJobClearsPreviousEpisode() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());

        // A new job (new waiting set + owner) starts a fresh episode.
        profiler.startWaiting(cpu, List.of(key), 900);
        profiler.setJobOwner(cpu, UUID.randomUUID());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 901);
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 1_500).size());
    }

    @Test
    void multipleOutputsNotifyOnceEach() {
        var profiler = new CraftProfiler(10);
        var iron = key("minecraft:iron_plate");
        var copper = key("minecraft:copper_plate");
        var cpu = new Object();
        var seed = new Object();

        seedTypical(profiler, iron, seed);
        seedTypical(profiler, copper, seed);
        profiler.start(iron, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.start(copper, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        var first = profiler.pollNewlyDelayed(cpu, 800);
        assertEquals(2, first.size());
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());
    }

    @Test
    void clearingStateDropsPendingNotifications() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        profiler.clearSamples(key);
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 2_000);
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 2_800).size());
        profiler.loadSamples(List.of());
        assertTrue(profiler.jobOwner(cpu).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 3_000).isEmpty());

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 4_000);
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertEquals(1, profiler.pollNewlyDelayed(cpu, 4_800).size());
        profiler.setEnabled(false);
        profiler.setEnabled(true);
        assertTrue(profiler.jobOwner(cpu).isEmpty());
        assertTrue(profiler.pollNewlyDelayed(cpu, 5_000).isEmpty());
    }

    @Test
    void noPendingMeansNoNotification() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        profiler.setJobOwner(cpu, UUID.randomUUID());
        assertTrue(profiler.pollNewlyDelayed(cpu, 1_000).isEmpty());
        assertFalse(profiler.jobOwner(cpu).isEmpty());
        profiler.clearPending(cpu);
        assertTrue(profiler.jobOwner(cpu).isEmpty());
    }

    @Test
    void resolvedDelayedDrainsOnceWhileCraftRuns() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        assertTrue(profiler.pollResolvedDelayed(cpu).isEmpty());

        // Partial progress resolves the stall while the craft still runs.
        profiler.complete(key, cpu, 1, 860);
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());
        assertEquals(List.of(key), profiler.pollResolvedDelayed(cpu));
        assertTrue(profiler.pollResolvedDelayed(cpu).isEmpty());
    }

    @Test
    void resolvedDelayedIsNullSafe() {
        var profiler = new CraftProfiler(10);

        assertTrue(profiler.pollResolvedDelayed(null).isEmpty());
        assertTrue(profiler.pollResolvedDelayed(new Object()).isEmpty());
    }

    @Test
    void delayedStatusesPersistForLoginResync() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        var statuses = profiler.snapshotStatuses();
        assertEquals(1, statuses.stream().filter(status -> status.key().equals(key)
                && status.kind() == StatusKind.DELAYED).count());
    }

    @Test
    void resolvedStatusesLeaveNoStaleDelayedForReload() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var cpu = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        profiler.complete(key, cpu, 1, 860);
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());
        assertEquals(List.of(key), profiler.pollResolvedDelayed(cpu));

        assertTrue(profiler.snapshotStatuses().stream()
                .noneMatch(status -> status.key().equals(key) && status.kind() == StatusKind.DELAYED));
    }

    @Test
    void isDelayedIsNullAndEmptySafe() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");

        assertFalse(profiler.isDelayed(null));
        assertFalse(profiler.isDelayed(key));
        assertTrue(profiler.delayedKeys(null).isEmpty());
        assertTrue(profiler.delayedKeys(new Object()).isEmpty());
    }

    @Test
    void isDelayedKeepsPlateWhileAnotherScopeStillNeedsRed() {
        var profiler = new CraftProfiler(10);
        var key = key("minecraft:iron_plate");
        var other = key("minecraft:copper_plate");
        var first = new Object();
        var second = new Object();

        seedTypical(profiler, key, new Object());
        profiler.start(key, first, 10, ProfileUnit.ITEM, 100);
        profiler.start(key, second, 10, ProfileUnit.ITEM, 100);

        assertEquals(1, profiler.pollNewlyDelayed(first, 800).size());
        assertEquals(1, profiler.pollNewlyDelayed(second, 800).size());
        assertEquals(Set.of(key), profiler.delayedKeys(first));
        assertEquals(Set.of(key), profiler.delayedKeys(second));
        assertTrue(profiler.isDelayed(key));
        assertFalse(profiler.isDelayed(other));
        assertFalse(profiler.isDelayed(null));

        profiler.complete(key, first, 1, 860);
        assertTrue(profiler.pollNewlyDelayed(first, 900).isEmpty());
        assertEquals(List.of(key), profiler.pollResolvedDelayed(first));
        assertTrue(profiler.delayedKeys(first).isEmpty());
        assertEquals(Set.of(key), profiler.delayedKeys(second));
        assertTrue(profiler.isDelayed(key));

        profiler.clearPending(second);
        assertFalse(profiler.isDelayed(key));
    }
}
