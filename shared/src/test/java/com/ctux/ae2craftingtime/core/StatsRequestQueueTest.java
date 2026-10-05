package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsRequestQueueTest {
    private static ProfileKey key(int i) { return new ProfileKey("test:key" + i); }

    @Test
    void batchesDeduplicatesAndHonorsCooldown() {
        for (int count : new int[] {1, 32, 256}) {
            var queue = new StatsRequestQueue();
            assertTrue(queue.drain(0).isEmpty());
            for (int i = 0; i < count; i++) {
                queue.request(key(i), true, 0);
                queue.request(key(i), true, 0);
            }
            assertEquals(count, queue.drain(0).size());
            queue.request(key(0), true, 499);
            assertTrue(queue.drain(499).isEmpty());
            assertTrue(queue.drain(500).isEmpty());
            queue.request(key(0), true, 1000);
            assertEquals(List.of(key(0)), queue.drain(1000));
        }
    }

    @Test
    void visiblePromotionAndReservedBackgroundShareAreFair() {
        var queue = new StatsRequestQueue();
        queue.request(key(0), false, 0);
        queue.request(key(0), false, 0);
        queue.request(key(0), true, 0);
        for (int i = 1; i < 512; i++) queue.request(key(i), false, 0);
        for (int i = 512; i < 1024; i++) queue.request(key(i), true, 0);
        var first = queue.drain(0);
        assertEquals(key(0), first.get(0));
        assertEquals(256, first.size());
        assertTrue(first.contains(key(1)));
        var seen = new HashSet<>(first);
        for (int i = 1; i < 4; i++) seen.addAll(queue.drain(i * 500L));
        assertEquals(1024, seen.size());
        assertTrue(queue.drain(2000).isEmpty());
    }

    @Test
    void boundsQueueAndDiscardsOldScreenOrCpuRequests() {
        var queue = new StatsRequestQueue();
        var screen = new Object();
        queue.context(screen, 1);
        for (int i = 0; i <= StatsRequestQueue.MAX_PENDING; i++) queue.request(key(i), false, 0);
        var seen = new HashSet<ProfileKey>();
        for (int i = 0; i < 16; i++) seen.addAll(queue.drain(i * 500L));
        assertEquals(StatsRequestQueue.MAX_PENDING, seen.size());
        assertFalse(seen.contains(key(StatsRequestQueue.MAX_PENDING)));
        queue.clear();
        assertTrue(queue.context(screen, 1));
        assertFalse(queue.context(screen, 1));
        queue.request(key(0), true, 8000);
        assertEquals(List.of(key(0)), queue.drain(8000));
        queue.request(key(1), false, 8001);
        queue.context(screen, 2);
        assertTrue(queue.drain(8500).isEmpty());
        queue.request(key(2), true, 8500);
        queue.context(new Object(), 2);
        assertTrue(queue.drain(9000).isEmpty());
    }

    @Test
    void contextResetsCannotBypassBatchPacing() {
        var queue = new StatsRequestQueue();
        queue.context(new Object(), 1);
        queue.request(key(0), true, 0);
        assertEquals(List.of(key(0)), queue.drain(0));
        for (int time = 1; time < 500; time++) {
            queue.context(new Object(), time);
            queue.request(key(0), true, time);
            assertTrue(queue.drain(time).isEmpty());
        }
        assertEquals(List.of(key(0)), queue.drain(500));
    }

    @Test
    void fullBackgroundQueueAdmitsNewVisibleRowsWithoutExceedingItsBound() {
        var queue = new StatsRequestQueue();
        for (int i = 0; i < StatsRequestQueue.MAX_PENDING; i++) queue.request(key(i), false, 0);
        var visible = key(StatsRequestQueue.MAX_PENDING);
        queue.request(visible, true, 0);
        var first = queue.drain(0);
        assertEquals(visible, first.get(0));
        assertFalse(first.contains(key(0)));
        var seen = new HashSet<>(first);
        for (int i = 1; i < 16; i++) seen.addAll(queue.drain(i * 500L));
        assertEquals(StatsRequestQueue.MAX_PENDING, seen.size());
        queue.request(key(0), false, 8000);
        assertEquals(List.of(key(0)), queue.drain(8000));
    }

    @Test
    void fullVisibleQueueRetainsExistingRowsAndAllowsOverflowToRetry() {
        var queue = new StatsRequestQueue();
        for (int i = 0; i < StatsRequestQueue.MAX_PENDING; i++) queue.request(key(i), true, 0);
        var overflow = key(StatsRequestQueue.MAX_PENDING);
        queue.request(overflow, true, 0);
        var seen = new HashSet<ProfileKey>();
        for (int i = 0; i < 16; i++) seen.addAll(queue.drain(i * 500L));
        assertEquals(StatsRequestQueue.MAX_PENDING, seen.size());
        assertFalse(seen.contains(overflow));
        queue.request(overflow, true, 8000);
        assertEquals(List.of(overflow), queue.drain(8000));
    }
}
