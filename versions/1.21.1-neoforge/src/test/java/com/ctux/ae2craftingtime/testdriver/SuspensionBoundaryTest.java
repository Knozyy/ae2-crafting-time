package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SuspensionBoundaryTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unsupportedSuspensionFailsBeforeReadingClientState(boolean connected) {
        var scenario = new StandardAe2Scenario("crafting-suspension", "world", directory, connected);
        var failure = assertThrows(IllegalStateException.class,
                () -> scenario.tick(null, null, new HashMap<>(),
                        name -> fail("Unsupported suspension must not capture evidence"),
                        (x, y) -> fail("Unsupported suspension must not interact with the client")));
        assertEquals(connected ? "Connected suspension requires Forge 1.20.1"
                : "Crafting suspension requires Forge 1.20.1", failure.getMessage());
    }

    @Test void retainedForgeRequestReportsTheUnavailableTransportOnNeoForge() throws Exception {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.ctux.ae2craftingtime.mc1201.net.CraftingSuspensionC2S"));
        var send = StandardAe2Scenario.class.getDeclaredMethod("sendStaleSuspension",
                int.class, long.class, UUID.class, boolean.class);
        send.setAccessible(true);
        var invocation = assertThrows(InvocationTargetException.class,
                () -> send.invoke(null, 1, 1L, new UUID(1, 1), true));
        var failure = assertInstanceOf(IllegalStateException.class, invocation.getCause());
        assertEquals("Cannot send retained stale Forge suspension request", failure.getMessage());
        assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    }
}
