package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileScopeIndexTest {
    private static final ProfileKey KEY = new ProfileKey("network", "test:output");
    private static final ProfileKey OTHER = new ProfileKey("other-network", "test:output");

    @Test
    void pendingScopesUseIdentityAndRetainPartialAndSharedWork() {
        var profiler = new CraftProfiler(10);
        // Equal values are still different CPU identities.
        var first = new String("cpu");
        var second = new String("cpu");
        var owner = UUID.randomUUID();
        profiler.setJobOwner(first, owner);
        profiler.start(KEY, first, 2, ProfileUnit.ITEM, 0);
        profiler.start(KEY, first, 1, ProfileUnit.ITEM, 0);
        profiler.start(KEY, second, 1, ProfileUnit.ITEM, 0);
        assertFalse(profiler.completeUniquePending(KEY, 1, 1));
        profiler.complete(KEY, first, 2, 1);
        assertTrue(profiler.hasPending(KEY));
        assertTrue(profiler.hasActiveOutput(KEY, owner));
        profiler.clearPending(second);
        assertTrue(profiler.completeUniquePending(KEY, 1, 2));
        assertFalse(profiler.hasPending(KEY));
        assertFalse(profiler.hasActiveOutput(KEY, owner));
        assertFalse(profiler.completeUniquePending(KEY, 1, 3));
        assertFalse(profiler.hasPending(null));
    }

    @Test
    void waitingReplacementsOwnerChangesAndCleanupStayIndexed() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        var owner = UUID.randomUUID();
        profiler.startWaiting(cpu, Set.of(KEY, OTHER), 0);
        assertTrue(profiler.hasActiveOutput(KEY, null));
        assertFalse(profiler.hasActiveOutput(KEY, owner));
        profiler.setJobOwner(cpu, owner);
        assertTrue(profiler.hasActiveOutput(KEY, owner));
        profiler.startWaiting(cpu, Set.of(OTHER), 1);
        assertFalse(profiler.hasActiveOutput(KEY, null));
        profiler.start(OTHER, cpu, 2, ProfileUnit.ITEM, 2);
        profiler.setSuspended(cpu, true, 3);
        assertTrue(profiler.hasActiveOutput(OTHER, owner));
        profiler.setSuspended(cpu, false, 4);
        profiler.clearSamples(OTHER);
        assertFalse(profiler.hasPending(OTHER));
        assertFalse(profiler.hasActiveOutput(OTHER, owner));
        profiler.clearPending(cpu);
        profiler.startWaiting(cpu, Set.of(KEY), 5);
        profiler.startWaiting(cpu, Set.of(), 6);
        assertFalse(profiler.hasActiveOutput(KEY, null));
        profiler.startWaiting(cpu, Set.of(KEY), 7);
        profiler.start(OTHER, cpu, 1, ProfileUnit.ITEM, 7);
        profiler.setEnabled(false);
        assertFalse(profiler.hasActiveOutput(KEY, null));
        assertFalse(profiler.hasPending(OTHER));
        profiler.setEnabled(true);
        profiler.startWaiting(cpu, Set.of(KEY), 8);
        profiler.start(OTHER, cpu, 1, ProfileUnit.ITEM, 8);
        profiler.loadSamples(List.of());
        assertFalse(profiler.hasActiveOutput(KEY, null));
        assertFalse(profiler.hasPending(OTHER));
    }

    @Test
    void unrelatedJobsAndNullScopeDoNotChangeUniqueCompletion() {
        var profiler = new CraftProfiler(10);
        for (int i = 0; i < 128; i++) {
            var key = new ProfileKey("network-" + i, "test:output");
            var cpu = new Object();
            profiler.startWaiting(cpu, Set.of(key), 0);
            profiler.start(key, cpu, 1, ProfileUnit.ITEM, 1);
        }
        assertFalse(profiler.hasActiveOutput(KEY, null));
        profiler.start(KEY, null, 1, ProfileUnit.ITEM, 0);
        // Preserve the reload fallback's existing refusal of a null CPU scope.
        assertFalse(profiler.completeUniquePending(KEY, 1, 1));
        assertTrue(profiler.complete(KEY, null, 1, 1));
        assertFalse(profiler.hasPending(KEY));
    }
}
