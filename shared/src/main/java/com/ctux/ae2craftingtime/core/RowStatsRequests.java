package com.ctux.ae2craftingtime.core;

import java.util.List;

/** Client row-stats request state: pending keys, the response session and the job they belong to. */
public final class RowStatsRequests {
    private final StatsRequestQueue queue = new StatsRequestQueue();
    private final RowStatsSession session = new RowStatsSession();
    private java.util.UUID job = RowStatsJob.NO_JOB;

    /** Returns true when the screen context changed and everything for the previous one was dropped. */
    public boolean prepare(Object context, long cpuContext) {
        if (!queue.context(context, cpuContext)) return false;
        session.clear();
        job = RowStatsJob.NO_JOB;
        return true;
    }

    /**
     * Returns true when the status belongs to a new job on the same CPU. Pending keys and in-flight responses are
     * retired, while the screen context and this status stay as the baseline for the next replacement.
     */
    public boolean observeJob(java.util.UUID jobId) {
        if (job.equals(jobId)) return false;
        job = java.util.Objects.requireNonNull(jobId);
        queue.clearPending();
        session.clear();
        return true;
    }

    public void request(ProfileKey key, boolean priority, long now) {
        queue.request(key, priority, now);
    }

    public List<ProfileKey> drain(long now) {
        return queue.drain(now);
    }

    public RowStatsRequestId next(long cpuContext) {
        return session.next(cpuContext, job);
    }

    public boolean accept(RowStatsRequestId response, long activeCpuContext, long responseCpuContext) {
        return response.matchesJob(job) && session.accept(response, activeCpuContext, responseCpuContext);
    }

    public void clear() {
        queue.clear();
        session.clear();
        job = RowStatsJob.NO_JOB;
    }
}
