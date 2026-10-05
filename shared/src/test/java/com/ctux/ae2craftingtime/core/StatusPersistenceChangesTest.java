package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusPersistenceChangesTest {
    @Test
    void unchangedPollsDoNotRebuildStatusesAndChangesCoalesce() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("test:output");
        var cpu = new Object();
        assertEquals(List.of(), profiler.takeChangedStatuses().orElseThrow());
        profiler.rememberBlockReason(key, CraftingBlockReason.NO_POWER, 10);
        assertEquals(10, profiler.takeChangedStatuses().orElseThrow().get(0).acceptedAtTick());
        for (int tick = 11; tick < 100; tick++) {
            profiler.rememberBlockReason(key, CraftingBlockReason.NO_POWER, tick);
            profiler.pollNewlyDelayed(cpu, tick);
            assertTrue(profiler.takeChangedStatuses().isEmpty());
        }
        profiler.rememberBlockReason(key, CraftingBlockReason.NO_PROVIDER, 100);
        profiler.rememberBlockReason(key, CraftingBlockReason.NO_TARGET, 101);
        assertEquals(StatusKind.NO_PROVIDER, profiler.takeChangedStatuses().orElseThrow().get(0).kind());
        profiler.startWaiting(cpu, List.of(key), 110);
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 120);
        assertEquals(List.of(), profiler.takeChangedStatuses().orElseThrow());
        assertTrue(profiler.takeChangedStatuses().isEmpty());
        profiler.clearPending(cpu);
        assertTrue(profiler.takeChangedStatuses().isEmpty());
        profiler.startWaiting(cpu, List.of(key), 130);
        profiler.takeChangedStatuses();
        profiler.clearPending(cpu);
        assertEquals(List.of(), profiler.takeChangedStatuses().orElseThrow());
        profiler.setEnabled(false);
        profiler.takeChangedStatuses();
        profiler.setEnabled(false);
        assertTrue(profiler.takeChangedStatuses().isEmpty());
        profiler.restoreStatuses(null);
        assertEquals(List.of(), profiler.takeChangedStatuses().orElseThrow());
    }
}
