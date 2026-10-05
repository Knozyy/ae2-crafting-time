package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileStatsCacheTest {
    private final ProfileKey key = new ProfileKey("test:output");

    @Test
    void unchangedHistoryReusesStatsWhileTimeAndCapacityStayLive() {
        var profiler = new CraftProfiler(10);
        var cpu = new Object();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, cpu, 1, 20);
        profiler.flushCompletedSamples();
        var stats = profiler.stats(key).orElseThrow();
        profiler.start(key, cpu, 1, ProfileUnit.ITEM, 30);
        profiler.updateCapacity(cpu, 1, 2, 220);
        assertTrue(profiler.stall(key, cpu, 229).isEmpty());
        assertEquals(1, profiler.stall(key, cpu, 230).orElseThrow().usedParallelSlots());
        assertEquals(0, profiler.stall(key, cpu, 241).orElseThrow().usedParallelSlots());
        assertSame(stats, profiler.stats(key).orElseThrow());
        profiler.complete(key, cpu, 1, 250);
        profiler.flushCompletedSamples();
        assertNotSame(stats, profiler.stats(key).orElseThrow());
    }

    @Test
    void amendmentOverflowResetLoadAndConfigurationInvalidate() {
        var profiler = new CraftProfiler(10);
        profiler.start(key, Long.MAX_VALUE, ProfileUnit.ITEM, 0);
        profiler.complete(key, Long.MAX_VALUE, 10);
        profiler.flushCompletedSamples();
        var old = profiler.stats(key).orElseThrow();
        profiler.start(key, 1, ProfileUnit.ITEM, 10);
        profiler.complete(key, 1, 10);
        assertTrue(profiler.stats(key).isEmpty());
        profiler.start(key, 2, ProfileUnit.ITEM, 20);
        profiler.complete(key, 1, 30);
        profiler.flushCompletedSamples();
        var first = profiler.stats(key).orElseThrow();
        profiler.complete(key, 1, 30);
        assertEquals(2, profiler.stats(key).orElseThrow().sampleAmounts().get(0));
        assertNotSame(first, profiler.stats(key).orElseThrow());
        var config = new ServerConfig();
        config.setMaxSamples(1);
        config.setOutlierMultiplier(2);
        profiler.configure(config);
        assertEquals(2, profiler.stats(key).orElseThrow().outlierMultiplier());
        profiler.loadSamples(List.of());
        assertTrue(profiler.stats(key).isEmpty());
        profiler.start(key, 1, ProfileUnit.ITEM, 50);
        profiler.complete(key, 1, 60);
        profiler.flushCompletedSamples();
        assertNotSame(old, profiler.stats(key).orElseThrow());
        profiler.clearSamples(key);
        assertTrue(profiler.stats(key).isEmpty());
    }
}
