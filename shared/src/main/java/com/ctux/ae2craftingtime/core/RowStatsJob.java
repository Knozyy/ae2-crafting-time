package com.ctux.ae2craftingtime.core;

import java.util.Objects;
import java.util.UUID;

/** Server-owned identity, independent of item counts and elapsed time. */
public record RowStatsJob(long cpuContext, UUID jobId) {
    public static final UUID NO_JOB = new UUID(0, 0);
    public static final UUID UNAVAILABLE_JOB = new UUID(-1, -1);

    public RowStatsJob {
        Objects.requireNonNull(jobId);
    }

    /** AE2 and supported addon CPUs expose the same crafting-link lifecycle. */
    public static UUID readJobId(Object cpu) {
        if (cpu == null) return NO_JOB;
        try {
            var logic = Objects.requireNonNull(IntegrationRead.field(cpu, "craftingLogic", Object.class));
            if (!Boolean.TRUE.equals(IntegrationRead.invoke(logic, "hasJob", Boolean.class))) return NO_JOB;
            var link = IntegrationRead.invoke(logic, "getLastLink", Object.class);
            return Objects.requireNonNull(IntegrationRead.invoke(link, "getCraftingID", UUID.class));
        } catch (IntegrationRead.Failure | NullPointerException unavailable) {
            // Fail closed if an addon no longer exposes its crafting link.
            return UNAVAILABLE_JOB;
        }
    }

    public UUID forContext(long activeCpuContext) {
        return cpuContext == activeCpuContext ? jobId : NO_JOB;
    }
}
