package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerRequestRateLimitTest {
    @Test
    void limitsPacketCountIndependentlyAndClearsLifecycleState() {
        var limit = new PlayerRequestRateLimit();
        var player = UUID.randomUUID();
        for (int i = 0; i < 4; i++) assertTrue(limit.allow(player, 1, 0));
        assertFalse(limit.allow(player, 1, 999));
        assertTrue(limit.allow(player, 1, 1000));
        limit.clear(player);
        assertTrue(limit.allow(player, 512, 1000));
        limit.clear();
        assertTrue(limit.allow(player, 512, 1000));
    }
    @Test
    void isolatesPlayersAndKeepsWindowAnchoredDespiteRejectedRequests() {
        var limit = new PlayerRequestRateLimit();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertTrue(limit.allow(first, 512, 1000));
        assertTrue(limit.allow(second, 512, 1500));
        assertFalse(limit.allow(first, 1, 1999));
        assertTrue(limit.allow(first, 512, 2000));
        assertFalse(limit.allow(second, 1, 2000));
        assertTrue(limit.allow(second, 512, 2500));
    }
    @Test
    void permitsNormalBurstAndRejectsExcessUntilNextWindow() {
        var limit = new PlayerRequestRateLimit();
        var player = UUID.randomUUID();

        assertTrue(limit.allow(player, 256, 1000));
        assertTrue(limit.allow(player, 256, 1500));
        assertFalse(limit.allow(player, 1, 1501));
        assertTrue(limit.allow(player, 1, 2000));
    }

    @Test
    void rejectsInvalidAndEmptyRequests() {
        var limit = new PlayerRequestRateLimit();
        var player = UUID.randomUUID();

        assertFalse(limit.allow(player, -1, 0));
        assertFalse(limit.allow(player, PlayerRequestRateLimit.MAX_KEYS_PER_SECOND + 1, 0));
        assertFalse(limit.allow(player, 0, 0));
        assertTrue(limit.allow(player, PlayerRequestRateLimit.MAX_KEYS_PER_SECOND - 1, 0));
        assertFalse(limit.allow(player, 0, 0));
    }
}
