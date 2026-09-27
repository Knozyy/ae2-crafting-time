package com.ctux.ae2craftingtime.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Rejects work queued by a previous connection. */
public final class ConnectionEpoch {
    private final AtomicLong value = new AtomicLong();

    public long current() { return value.get(); }
    public void advance() { value.incrementAndGet(); }
    public boolean isCurrent(long captured) { return value.get() == captured; }

    public Runnable guard(BooleanSupplier receivingConnectionActive, Runnable work) {
        long captured = current();
        return () -> { if (isCurrent(captured) && receivingConnectionActive.getAsBoolean()) work.run(); };
    }
}
