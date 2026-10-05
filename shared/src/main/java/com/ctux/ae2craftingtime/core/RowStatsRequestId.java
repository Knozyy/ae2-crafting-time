package com.ctux.ae2craftingtime.core;

/** Echoed unchanged by the server; the CPU context is checked before collection. */
public record RowStatsRequestId(long session, long sequence, long cpuContext) {
    public RowStatsRequestId {
        if (session <= 0 || sequence <= 0) {
            throw new IllegalArgumentException("row stats session and sequence must be positive");
        }
    }

    public boolean matchesContext(long activeCpuContext) {
        return cpuContext == activeCpuContext;
    }
}
