package com.ctux.ae2craftingtime.core;

/** Constant-size correlation state, independent of dropped or unanswered packets. */
public final class RowStatsSession {
    private long session = 1;
    private long issued;
    private long applied;

    public RowStatsRequestId next(long cpuContext) {
        return new RowStatsRequestId(session, ++issued, cpuContext);
    }

    public boolean accept(RowStatsRequestId response, long activeCpuContext, long responseCpuContext) {
        if (response.session() != session || response.sequence() <= applied || response.sequence() > issued
                || !response.matchesContext(activeCpuContext) || responseCpuContext != activeCpuContext) {
            return false;
        }
        applied = response.sequence();
        return true;
    }

    public void clear() {
        session++;
        issued = 0;
        applied = 0;
    }
}
