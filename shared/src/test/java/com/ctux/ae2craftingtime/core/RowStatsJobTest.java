package com.ctux.ae2craftingtime.core;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RowStatsJobTest {
    private static final UUID A = new UUID(1, 2);
    private static final UUID B = new UUID(1, 3);

    @Test
    void readsActualJobIdentityIncludingReplacementAndCompletion() {
        var cpu = new Cpu();
        assertEquals(RowStatsJob.NO_JOB, RowStatsJob.readJobId(null));
        assertEquals(RowStatsJob.NO_JOB, RowStatsJob.readJobId(cpu));
        cpu.craftingLogic.link = new Link(A);
        assertEquals(A, RowStatsJob.readJobId(cpu));
        cpu.craftingLogic.link = new Link(B);
        assertEquals(B, RowStatsJob.readJobId(cpu));
        cpu.craftingLogic.link = null;
        assertEquals(RowStatsJob.NO_JOB, RowStatsJob.readJobId(cpu));
    }

    @Test
    void unavailableAddonIdentityCannotAuthorizeStats() {
        assertEquals(RowStatsJob.UNAVAILABLE_JOB, RowStatsJob.readJobId(new Object()));
        var cpu = new Cpu();
        cpu.craftingLogic.link = new Link(null);
        assertEquals(RowStatsJob.UNAVAILABLE_JOB, RowStatsJob.readJobId(cpu));
        cpu.craftingLogic = null;
        assertEquals(RowStatsJob.UNAVAILABLE_JOB, RowStatsJob.readJobId(cpu));
        var unavailable = new RowStatsRequestId(1, 1, 7, RowStatsJob.UNAVAILABLE_JOB);
        assertFalse(unavailable.matchesJob(RowStatsJob.UNAVAILABLE_JOB));
    }

    @Test
    void serverRejectsPreviousJobAndClientScopesIdentityToSelectedCpu() {
        var request = new RowStatsRequestId(1, 1, 7, A);
        assertTrue(request.matchesJob(A));
        assertFalse(request.matchesJob(B));
        assertFalse(request.matchesJob(RowStatsJob.NO_JOB));
        assertFalse(request.matchesJob(null));
        var job = new RowStatsJob(7, A);
        assertEquals(A, job.forContext(7));
        assertEquals(RowStatsJob.NO_JOB, job.forContext(8));
        assertThrows(NullPointerException.class, () -> new RowStatsJob(7, null));
        assertThrows(NullPointerException.class, () -> new RowStatsRequestId(1, 1, 7, null));
    }

    public static class Cpu { public Logic craftingLogic = new Logic(); }
    public static class Logic {
        Link link;
        public boolean hasJob() { return link != null; }
        public Link getLastLink() { return link; }
    }
    public record Link(UUID id) { public UUID getCraftingID() { return id; } }
}
