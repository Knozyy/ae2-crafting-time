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
}
