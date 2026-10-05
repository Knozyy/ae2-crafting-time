package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RowStatsRequestsTest {
    private static final Object SCREEN = new Object();
    private static final long CPU = 7;
    private static final ProfileKey KEY = new ProfileKey("minecraft:iron_ingot");

    private static RowStatsRequestId send(RowStatsRequests requests, long now) {
        assertFalse(requests.prepare(SCREEN, CPU));
        requests.request(KEY, true, now);
        assertEquals(List.of(KEY), requests.drain(now));
        return requests.next(CPU);
    }

    @Test
    void lateResponseForAReplacedJobIsRejected() {
        var requests = new RowStatsRequests();
        assertTrue(requests.prepare(SCREEN, CPU));
        assertFalse(requests.observeJob(100, 20));
        var jobA = send(requests, 0);

        assertTrue(requests.observeJob(64, 0));
        assertFalse(requests.prepare(SCREEN, CPU));
        assertFalse(requests.accept(jobA, CPU, CPU));

        var jobB = send(requests, 1000);
        assertTrue(requests.accept(jobB, CPU, CPU));
    }

    @Test
    void preparingAfterAReplacementKeepsTheNewJobAsBaseline() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(100, 20);
        send(requests, 0);

        assertTrue(requests.observeJob(64, 0));
        var jobB = send(requests, 1000);

        // A second rapid replacement must not be mistaken for a first observation.
        assertTrue(requests.observeJob(32, 0));
        assertFalse(requests.prepare(SCREEN, CPU));
        assertFalse(requests.accept(jobB, CPU, CPU));
    }

    @Test
    void replacementDoesNotBypassTheSendInterval() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(100, 20);
        send(requests, 0);

        assertTrue(requests.observeJob(64, 0));
        requests.request(KEY, true, 100);
        assertEquals(List.of(), requests.drain(100));
        assertEquals(List.of(KEY), requests.drain(500));
    }

    @Test
    void screenChangeStillResetsTheJobBaseline() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(100, 20);
        assertTrue(requests.prepare(new Object(), CPU));
        assertFalse(requests.observeJob(64, 0));
    }
}
