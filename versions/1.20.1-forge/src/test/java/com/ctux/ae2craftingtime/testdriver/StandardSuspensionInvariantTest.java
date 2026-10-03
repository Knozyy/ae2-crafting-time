package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StandardSuspensionInvariantTest {
    private static StandardCraftFixture.SuspensionState state(String id, boolean busy, boolean suspended,
            boolean profilerSuspended, long undispatched, long waiting, int input, int output) {
        return new StandardCraftFixture.SuspensionState(id, busy, suspended, profilerSuspended,
                undispatched, waiting, 62, 2, 0, input, output);
    }

    @Test void acknowledgementRequiresTheSameJobAndBothSuspensionFlags() {
        var paused = state("large", true, true, true, 60, 1, 1, 0);
        assertTrue(StandardAe2Scenario.suspensionPausedJobReady(paused, "large"));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionPausedJob(paused, "large", 60));
        for (var changed : new StandardCraftFixture.SuspensionState[]{
                state("large", false, true, true, 60, 1, 1, 0),
                state("large", true, false, true, 60, 1, 1, 0),
                state("large", true, true, false, 60, 1, 1, 0),
                state("replacement", true, true, true, 60, 1, 1, 0)}) {
            assertFalse(StandardAe2Scenario.suspensionPausedJobReady(changed, "large"));
            assertEquals("Paused CPU changed before in-flight return", assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateSuspensionPausedJob(changed, "large", 60)).getMessage());
        }
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSuspensionPausedJob(paused, "large", 59));
    }

    @Test void acknowledgedPauseMustContainARealFurnaceReturnAndStopDispatching() {
        for (var invalid : new StandardCraftFixture.SuspensionState[]{
                state("large", true, true, true, 60, 0, 1, 0),
                state("large", true, true, true, 60, 1, 0, 0)}) {
            assertEquals("No in-flight furnace return at pause acknowledgement",
                    assertThrows(IllegalStateException.class,
                            () -> StandardAe2Scenario.validateSuspensionInFlightReturn(invalid)).getMessage());
        }
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionInFlightReturn(
                state("large", true, true, true, 60, 1, 1, 0)));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionInFlightReturn(
                state("large", true, true, true, 60, 1, 0, 1)));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionDispatch(60, 60));
        for (long count : new long[]{59, 61}) {
            assertEquals("Paused CPU dispatched another pattern", assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateSuspensionDispatch(count, 60)).getMessage());
        }
    }

    @Test void pausedDiagnosticsRejectWarningsAndEstimatesWithoutUnnecessaryQueries() {
        assertEquals("Paused job retained a delayed warning or total estimate",
                assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateSuspensionDiagnostics(
                        true, () -> { fail("A delayed job already fails the check"); return false; })).getMessage());
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSuspensionDiagnostics(false, () -> true));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionDiagnostics(false, () -> false));
    }

    @Test void resumePreservesTheActiveJobButAllowsCompletion() {
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionResumedJob(
                state("large", true, false, false, 0, 0, 0, 0), "large"));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionResumedJob(
                state("", false, false, false, 0, 0, 0, 0), "large"));
        assertEquals("Resume replaced the large job", assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSuspensionResumedJob(
                        state("replacement", true, false, false, 0, 0, 0, 0), "large")).getMessage());
    }

    @Test void resumedDelayStartsAtTheNewTimerAndReplacementNeedsANewUuid() {
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionDelay(false, 0,
                () -> { fail("No warning needs no threshold"); return 0; }));
        assertEquals("Resume inherited the paused delay timer", assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSuspensionDelay(true, 99, () -> 100)).getMessage());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionDelay(true, 100, () -> 100));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionDelay(true, 101, () -> 100));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSuspensionReplacement("replacement", "large"));
        assertEquals("Replacement job reused the prior UUID", assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSuspensionReplacement("large", "large")).getMessage());
    }
}
