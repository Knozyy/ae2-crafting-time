package com.ctux.ae2craftingtime.core;

/** A job on one CPU keeps its start item count and its elapsed time never decreases. */
public final class JobTransitionDetector {
    private boolean seen;
    private long startItems;
    private long elapsed;

    /** Returns true when this status belongs to a different job than the previous one. */
    public boolean observe(long startItemCount, long elapsedTime) {
        var changed = seen && (startItemCount != startItems || elapsedTime < elapsed);
        seen = true;
        startItems = startItemCount;
        elapsed = elapsedTime;
        return changed;
    }

    public void clear() {
        seen = false;
    }
}
