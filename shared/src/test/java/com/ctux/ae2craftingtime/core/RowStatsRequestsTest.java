package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RowStatsRequestsTest {
    private static final java.util.UUID A = new java.util.UUID(1, 1);
    private static final java.util.UUID B = new java.util.UUID(1, 2);
    private static final java.util.UUID C = new java.util.UUID(1, 3);
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
    void equalSizedReplacementRejectsPreviousJobBeforeCacheMutation() {
        var requests = new RowStatsRequests();
        assertTrue(requests.prepare(SCREEN, CPU));
        assertTrue(requests.observeJob(A));
        var jobA = send(requests, 0);

        // Counts and elapsed time are deliberately absent: only the server job UUID matters.
        assertTrue(requests.observeJob(B));
        assertFalse(requests.observeJob(B));
        assertFalse(requests.prepare(SCREEN, CPU));
        var cached = new java.util.concurrent.atomic.AtomicBoolean(false);
        if (requests.accept(jobA, CPU, CPU)) cached.set(true);
        assertFalse(cached.get());
        var forged = new RowStatsRequestId(jobA.session(), jobA.sequence(), CPU, B);
        assertFalse(requests.accept(forged, CPU, CPU));

        var jobB = send(requests, 1000);
        assertTrue(requests.accept(jobB, CPU, CPU));
    }

    @Test
    void preparingAfterAReplacementKeepsTheNewJobAsBaseline() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(A);
        send(requests, 0);

        assertTrue(requests.observeJob(B));
        var jobB = send(requests, 1000);

        // A second rapid replacement must not be mistaken for a first observation.
        assertTrue(requests.observeJob(C));
        assertFalse(requests.prepare(SCREEN, CPU));
        assertFalse(requests.accept(jobB, CPU, CPU));
    }

    @Test
    void replacementDoesNotBypassTheSendInterval() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(A);
        send(requests, 0);

        assertTrue(requests.observeJob(B));
        requests.request(KEY, true, 100);
        assertEquals(List.of(), requests.drain(100));
        assertEquals(List.of(KEY), requests.drain(500));
    }

    @Test
    void clearDropsTheContextSessionAndJobBaseline() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(A);
        var old = send(requests, 0);

        requests.clear();
        assertFalse(requests.accept(old, CPU, CPU));
        assertTrue(requests.observeJob(B));
        assertTrue(requests.prepare(SCREEN, CPU));
    }

    @Test
    void completionAndFirstObservationRetireEarlierResponses() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        var unknown = send(requests, 0);
        assertTrue(requests.observeJob(A));
        assertFalse(requests.accept(unknown, CPU, CPU));
        var running = send(requests, 500);
        assertTrue(requests.observeJob(RowStatsJob.NO_JOB));
        assertFalse(requests.accept(running, CPU, CPU));
        assertFalse(requests.observeJob(RowStatsJob.NO_JOB));
        assertThrows(NullPointerException.class, () -> requests.observeJob(null));
    }

    @Test
    void screenChangeStillResetsTheJobBaseline() {
        var requests = new RowStatsRequests();
        requests.prepare(SCREEN, CPU);
        requests.observeJob(A);
        assertTrue(requests.prepare(new Object(), CPU));
        assertTrue(requests.observeJob(B));
    }
}
