package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardReconnectTest {
    @TempDir Path directory;

    @Test void rejectsUnknownLeavesAndReportsTheChosenScenario() {
        assertFalse(StandardAe2Scenario.supports("unknown"));
        var error = assertThrows(IllegalArgumentException.class,
                () -> new StandardAe2Scenario("unknown", "world", directory, false));
        assertEquals("Unknown standard leaf: unknown", error.getMessage());
        for (var leaf : StandardAe2Scenario.CHECKS.keySet()) {
            assertTrue(StandardAe2Scenario.supports(leaf));
            var scenario = new StandardAe2Scenario(leaf, "world", directory, false);
            assertTrue(scenario.checkpoint().startsWith("phase=PREPARE "), leaf);
            assertFalse(scenario.reconnectRequested(), leaf);
        }
    }

    @Test void recurrenceAndVariantReconnectionsClearOnlyTheirPendingRequest() throws Exception {
        for (var leaf : List.of("recurrent-plan", "stored-variant-plan")) {
            var scenario = new StandardAe2Scenario(leaf, "world", directory, false);
            var request = leaf.equals("recurrent-plan") ? "recurrenceReconnectRequested" : "variantReconnectRequested";
            set(scenario, request, true);
            assertTrue(scenario.reconnectRequested());
            scenario.reconnected();
            assertFalse(scenario.reconnectRequested());
            assertTrue(scenario.checkpoint().startsWith("phase=TERMINAL "));
            if (leaf.equals("recurrent-plan")) assertEquals(true, get(scenario, "recurrenceRejoined"));
            else assertEquals(4, get(scenario, "variantLifecycle"));
        }
    }

    private static void set(StandardAe2Scenario scenario, String name, Object value) throws Exception {
        var field = StandardAe2Scenario.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(scenario, value);
    }

    private static Object get(StandardAe2Scenario scenario, String name) throws Exception {
        var field = StandardAe2Scenario.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(scenario);
    }
}
