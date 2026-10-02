package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecurrentProviderBoundaryTest {
    @Test void aDiagnosticProviderNeverDispatchesWorkOrInventsPatterns() {
        var fixture = new RecurrentPlanFixture(null);
        assertEquals(List.of(), fixture.getAvailablePatterns());
        assertEquals(Set.of(), fixture.getEmitableItems());
        assertFalse(fixture.isBusy());
        assertFalse(fixture.recurrent());
        assertFalse(fixture.reported());
        assertEquals(1, fixture.requestedAmount());
        var failure = assertThrows(IllegalStateException.class, () -> fixture.pushPattern(null, null));
        assertEquals("Recurrence fixture must never submit a craft", failure.getMessage());
    }

    @Test void cancellationMustBeRequestedAndObserved() {
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateCancellation(true, true));
        for (var values : List.of(new boolean[]{false, false}, new boolean[]{false, true}, new boolean[]{true, false})) {
            var error = assertThrows(IllegalStateException.class,
                    () -> RecurrentPlanFixture.validateCancellation(values[0], values[1]));
            assertEquals("Cancellation was not observed", error.getMessage());
        }
    }

    @Test void calculationFuturesRetainSuccessfulResultsAndFailureCauses() {
        assertEquals("result", RecurrentPlanFixture.await(java.util.concurrent.CompletableFuture.completedFuture("result")));
        var cause = new IllegalArgumentException("failed calculation");
        var error = assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.await(
                java.util.concurrent.CompletableFuture.failedFuture(cause)));
        assertInstanceOf(java.util.concurrent.ExecutionException.class, error.getCause());
        assertSame(cause, error.getCause().getCause());
        var cancelled = new java.util.concurrent.CompletableFuture<String>();
        assertTrue(cancelled.cancel(true));
        assertThrows(java.util.concurrent.CancellationException.class, () -> RecurrentPlanFixture.await(cancelled));
        try {
            Thread.currentThread().interrupt();
            error = assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.await(
                    new java.util.concurrent.FutureTask<String>(() -> "pending")));
            assertInstanceOf(InterruptedException.class, error.getCause());
        } finally {
            Thread.interrupted();
        }
    }

    @Test void craftLessAndFullAttemptsMustRemainIndependent() {
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateAttempts(false, 1, Set.of(), true, Set.of("stone"), "stone"));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateAttempts(true, 1, Set.of(), true, Set.of("stone"), "stone"));
        for (var amount : List.of(0L, 2L))
            assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateAttempts(false, amount, Set.of(), true, Set.of("stone"), "stone"));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateAttempts(false, 1, Set.of("leaked"), true, Set.of("stone"), "stone"));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateAttempts(false, 1, Set.of(), false, Set.of("stone"), "stone"));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateAttempts(false, 1, Set.of(), true, Set.of("other"), "stone"));
    }

    @Test void outcomesRequireTheExactMissingKeysAndExpectedSimulationMode() {
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateOutcome("ordinary", Set.of(), true, Set.of(), false));
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateOutcome("cycle", Set.of("stone"), false, Set.of("stone"), true));
        var error = assertThrows(IllegalStateException.class,
                () -> RecurrentPlanFixture.validateOutcome("cycle", Set.of("stone"), false, Set.of("other"), true));
        assertEquals("cycle expected recurrence=[stone] success=false actual recurrence=[other] simulation=true", error.getMessage());
        for (var simulation : List.of(false, true))
            assertThrows(IllegalStateException.class,
                    () -> RecurrentPlanFixture.validateOutcome("wrong mode", Set.of(), simulation, Set.of(), simulation));
    }

    @Test void summariesMustCrossTheChunkBoundaryAndPreserveReportedQuantities() {
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateSummary("ordinary", 1, false, 1));
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateSummary("large", 257, false, 1));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateSummary("large", 256, true, 1));
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateSummary("reported-100", 1, true, 100));
        var error = assertThrows(IllegalStateException.class,
                () -> RecurrentPlanFixture.validateSummary("reported-100", 1, false, 100));
        assertEquals("Reported regression lost missing quantity 100", error.getMessage());
        assertTrue(RecurrentPlanFixture.requestedMissing(Set.of("stone"), "stone", 100, 100));
        assertFalse(RecurrentPlanFixture.requestedMissing(Set.of("stone"), "stone", 1, 100));
        assertFalse(RecurrentPlanFixture.requestedMissing(Set.of("stone"), "other", 100, 100));
    }

    @Test void summaryFlagsOnlyIntersectPositiveMissingRowsAndExpectedKeys() {
        for (var missing : List.of(-1L, 0L)) {
            assertDoesNotThrow(() -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "stone", missing, false));
            assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "stone", missing, true));
        }
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "other", 1, false));
        assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "other", 1, true));
        assertDoesNotThrow(() -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "stone", 1, true));
        var error = assertThrows(IllegalStateException.class, () -> RecurrentPlanFixture.validateFlag(Set.of("stone"), "stone", 1, false));
        assertEquals("Summary diagnosis or positive-missing intersection differs", error.getMessage());
    }
}
