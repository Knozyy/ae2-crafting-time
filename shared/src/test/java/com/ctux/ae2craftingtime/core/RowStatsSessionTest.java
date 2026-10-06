package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RowStatsSessionTest {
    @Test
    void returningToTheSameCpuCannotAcceptThePreviousVisitsResponse() {
        var session = new RowStatsSession();
        var firstA = session.next(7, RowStatsJob.NO_JOB);
        session.clear();
        var b = session.next(8, RowStatsJob.NO_JOB);
        session.clear();
        var secondA = session.next(7, RowStatsJob.NO_JOB);
        assertNotEquals(firstA.session(), secondA.session());
        assertFalse(session.accept(firstA, 7, 7));
        assertFalse(session.accept(b, 7, 8));
        assertTrue(session.accept(secondA, 7, 7));
    }

    @Test
    void resetRejectsInFlightResponsesEvenWhenContainerAndCpuIdsAreReused() {
        var session = new RowStatsSession();
        var old = session.next(0x123456789L, RowStatsJob.NO_JOB);
        session.clear();
        assertFalse(session.accept(old, old.cpuContext(), old.cpuContext()));
        var reopened = session.next(old.cpuContext(), RowStatsJob.NO_JOB);
        assertEquals(1, reopened.sequence());
        assertTrue(session.accept(reopened, reopened.cpuContext(), reopened.cpuContext()));
    }

    @Test
    void acceptsOrderedBatchesButRejectsDuplicatesUnsentAndReorderedResponses() {
        var session = new RowStatsSession();
        assertFalse(session.accept(new RowStatsRequestId(1, 1, 7, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB), 7, 7));
        var first = session.next(7, RowStatsJob.NO_JOB);
        var second = session.next(7, RowStatsJob.NO_JOB);
        assertFalse(session.accept(new RowStatsRequestId(first.session(), 3, 7, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB), 7, 7));
        assertTrue(session.accept(first, 7, 7));
        assertFalse(session.accept(first, 7, 7));
        assertTrue(session.accept(second, 7, 7));
        var third = session.next(7, RowStatsJob.NO_JOB);
        var fourth = session.next(7, RowStatsJob.NO_JOB);
        assertTrue(session.accept(fourth, 7, 7));
        assertFalse(session.accept(third, 7, 7));
    }

    @Test
    void mismatchedRequestOrServerContextDoesNotConsumeAValidResponse() {
        var session = new RowStatsSession();
        var request = session.next(7, RowStatsJob.NO_JOB);
        assertFalse(session.accept(request, 8, 8));
        assertFalse(session.accept(request, 7, 8));
        assertTrue(session.accept(request, 7, 7));
    }

    @Test
    void serverCanRejectRequestsFromAnotherMenuBeforeCollectingStats() {
        var request = new RowStatsRequestId(3, 4, 0x123456789L, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB);
        assertTrue(request.matchesContext(0x123456789L));
        assertFalse(request.matchesContext(0x223456789L));
        assertFalse(request.matchesContext(0x123456788L));
    }

    @Test
    void rejectsNonpositiveCorrelationIds() {
        assertThrows(IllegalArgumentException.class, () -> new RowStatsRequestId(0, 1, -1, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB));
        assertThrows(IllegalArgumentException.class, () -> new RowStatsRequestId(-1, 1, -1, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB));
        assertThrows(IllegalArgumentException.class, () -> new RowStatsRequestId(1, 0, -1, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB));
        assertThrows(IllegalArgumentException.class, () -> new RowStatsRequestId(1, -1, -1, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB));
        assertEquals(-1, new RowStatsRequestId(Long.MAX_VALUE, Long.MAX_VALUE, -1, com.ctux.ae2craftingtime.core.RowStatsJob.NO_JOB).cpuContext());
    }
}
