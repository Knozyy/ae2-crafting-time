package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ConnectionEpochTest {
    @Test void queuedWorkOnlyRunsInItsOwnConnection() {
        var epoch = new ConnectionEpoch();
        var calls = new AtomicInteger();
        long first = epoch.current();
        var old = epoch.guard(() -> true, calls::incrementAndGet);
        assertTrue(epoch.isCurrent(first));
        old.run();
        epoch.advance();
        assertFalse(epoch.isCurrent(first));
        old.run();
        assertEquals(1, calls.get());
        epoch.guard(() -> true, calls::incrementAndGet).run();
        assertEquals(2, calls.get());
        // A loader may queue the handler itself before we capture the epoch.
        epoch.guard(() -> false, calls::incrementAndGet).run();
        assertEquals(2, calls.get());
    }
}
