package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavedSampleHistoryTest {
    @Test
    void oneChangedOutputReusesAllUnchangedHistoriesAndSnapshotsStayStable() {
        var profiler = new CraftProfiler(10);
        var saved = new SavedSampleHistory();
        var first = new ProfileKey("test:first");
        var second = new ProfileKey("test:second");
        for (var key : List.of(first, second)) {
            profiler.start(key, 2, ProfileUnit.ITEM, 0);
            profiler.complete(key, 1, 10);
        }
        profiler.flushCompletedSamples();
        saved.replace(profiler.takeChangedSamples());
        var before = saved.snapshot();
        assertTrue(profiler.takeChangedSamples().isEmpty());
        profiler.complete(first, 1, 10);
        var changed = profiler.takeChangedSamples();
        assertEquals(1, changed.size());
        assertEquals(2, changed.get(0).samples().get(0).amount());
        saved.update(changed);
        var oldSecond = before.stream().filter(s -> s.key().equals(second)).findFirst().orElseThrow();
        assertSame(oldSecond, saved.snapshot().stream().filter(s -> s.key().equals(second)).findFirst().orElseThrow());
        assertEquals(1, before.get(0).samples().get(0).amount());
        profiler.clearSamples(first);
        saved.update(profiler.takeChangedSamples());
        assertEquals(List.of(oldSecond), saved.snapshot());
        profiler.loadSamples(List.of());
        saved.update(profiler.takeChangedSamples());
        assertTrue(saved.snapshot().isEmpty());
        saved.replace(before);
        assertEquals(2, saved.snapshot().size());
        saved.replace(List.of());
        assertTrue(saved.snapshot().isEmpty());
    }

    @Test
    void unconsumedChangesSurviveHistoryOffConfigurationAndOverflow() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey("test:output");
        profiler.start(key, Long.MAX_VALUE, ProfileUnit.ITEM, 0);
        profiler.complete(key, Long.MAX_VALUE, 10);
        profiler.flushCompletedSamples();
        profiler.start(key, 1, ProfileUnit.ITEM, 10);
        profiler.complete(key, 1, 10);
        assertTrue(profiler.takeChangedSamples().get(0).samples().isEmpty());
        profiler.loadSamples(List.of(new PersistedOutputSamples(key, ProfileUnit.ITEM,
                List.of(new PersistedCraftSample(1, 10), new PersistedCraftSample(2, 20)))));
        profiler.takeChangedSamples();
        var config = new ServerConfig();
        config.setMaxSamples(1);
        profiler.configure(config);
        profiler.flushCompletedSamples();
        assertEquals(List.of(new PersistedCraftSample(2, 20)), profiler.takeChangedSamples().get(0).samples());
        assertTrue(profiler.takeChangedSamples().isEmpty());
    }
}
