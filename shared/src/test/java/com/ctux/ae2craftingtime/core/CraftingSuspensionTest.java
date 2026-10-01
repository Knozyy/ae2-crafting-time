package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CraftingSuspensionTest {
    private static final UUID JOB = UUID.fromString("aaf87931-28d5-4b51-b95d-768a9fb7d871");
    private static final long CONTEXT = 7L << 32 | 0xffffffffL;

    @Test
    void lifecycleAndDisableAreIdempotent() {
        var state = new CraftingSuspension();
        assertFalse(state.suspended());
        assertFalse(state.set(true, false, true));
        assertFalse(state.set(true, true, false));
        assertFalse(state.set(false, true, true));
        assertTrue(state.set(true, true, true));
        assertTrue(state.suspended());
        assertFalse(state.set(true, true, true));
        assertFalse(state.set(false, false, true));
        assertFalse(state.set(false, true, false));
        assertFalse(state.reconcile(true, true));
        assertTrue(state.reconcile(false, true));
        assertFalse(state.reconcile(false, true));
        state.read(true, false);
        assertFalse(state.suspended());
        state.read(true, true);
        assertTrue(state.suspended());
        assertTrue(state.reconcile(true, false));
        assertFalse(state.reconcile(true, false));
        assertTrue(state.set(true, true, true));
        assertTrue(state.set(false, true, true));
        state.read(false, true);
        assertFalse(state.suspended());
        state.read(false, false);
        assertFalse(state.suspended());
    }

    @Test
    void cachedCardMaskAffectsOnlyTheSelectedSuspendedCpu() {
        assertTrue(CraftingSuspension.masksSelectedCard(12, 12, true));
        assertFalse(CraftingSuspension.masksSelectedCard(13, 12, true));
        assertFalse(CraftingSuspension.masksSelectedCard(12, 12, false));
    }

    @Test
    void requestHasExactLayoutAndRejectsEveryMalformedBoundary() {
        var request = new CraftingSuspension.Request(7, CONTEXT, JOB, true);
        var good = CraftingSuspension.encode(request);
        assertEquals(29, good.length);
        assertEquals(request, CraftingSuspension.decodeRequest(good));
        assertFalse(CraftingSuspension.decodeRequest(CraftingSuspension.encode(
                new CraftingSuspension.Request(7, CONTEXT, JOB, false))).desired());
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeRequest(null));
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeRequest(new byte[28]));
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeRequest(new byte[30]));
        var invalid = good.clone(); invalid[28] = 2;
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeRequest(invalid));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Request(-1, CONTEXT, JOB, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Request(7, 8L << 32, JOB, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Request(7, CONTEXT, null, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Request(7, CONTEXT,
                CraftingSuspension.NO_JOB, true));
    }

    @Test
    void snapshotChecksFlagsJobAndCapability() {
        var live = new CraftingSuspension.Snapshot(7, CONTEXT, JOB, true, true, true);
        assertTrue(live.hasJob());
        assertEquals(live, CraftingSuspension.decodeSnapshot(CraftingSuspension.encode(live)));
        var empty = new CraftingSuspension.Snapshot(7, CONTEXT, CraftingSuspension.NO_JOB, false, false, false);
        assertFalse(CraftingSuspension.decodeSnapshot(CraftingSuspension.encode(empty)).hasJob());
        assertEquals(new CraftingSuspension.Snapshot(7, CONTEXT, JOB, true, false, false),
                CraftingSuspension.decodeSnapshot(CraftingSuspension.encode(
                        new CraftingSuspension.Snapshot(7, CONTEXT, JOB, true, false, false))));
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(null));
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(new byte[28]));
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(new byte[30]));
        var unknown = CraftingSuspension.encode(live); unknown[12] |= 16;
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(unknown));
        var noJobFlag = CraftingSuspension.encode(live); noJobFlag[12] &= ~4;
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(noJobFlag));
        var fakeJobFlag = CraftingSuspension.encode(empty); fakeJobFlag[12] |= 4;
        assertThrows(IllegalArgumentException.class, () -> CraftingSuspension.decodeSnapshot(fakeJobFlag));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT, null, true, true, false));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT, JOB, false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT, JOB, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT, JOB, true, false, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT, JOB, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(7, CONTEXT,
                CraftingSuspension.NO_JOB, true, true, true));
        assertThrows(IllegalArgumentException.class, () -> new CraftingSuspension.Snapshot(-1, CONTEXT,
                CraftingSuspension.NO_JOB, false, false, false));
    }
}
