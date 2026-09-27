package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctux.ae2craftingtime.core.ServerConfig;
import com.ctux.ae2craftingtime.core.ServerOptionsWire;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class StatsNetworkCapabilityTest {
    @Test
    void advertisedChannelNeedsValidSnapshotFromCurrentConnection() {
        ClientServerOptions.clear();
        var queried = new AtomicInteger();
        BooleanSupplier advertised = () -> {
            queried.incrementAndGet();
            return true;
        };
        try {
            assertFalse(StatsNetwork.canSendChannel(advertised));
            assertEquals(0, queried.get());
            ClientServerOptions.receive(new byte[3]);
            assertFalse(StatsNetwork.canSendChannel(advertised));

            ClientServerOptions.receive(ServerOptionsWire.encode(
                    new ServerOptionsWire.Snapshot(0, false, new ServerConfig())));
            assertTrue(StatsNetwork.canSendChannel(advertised));
            assertFalse(StatsNetwork.canSendChannel(() -> false));

            ClientServerOptions.clear();
            assertFalse(StatsNetwork.canSendChannel(advertised));
            assertEquals(1, queried.get());
        } finally {
            ClientServerOptions.clear();
        }
    }
}
