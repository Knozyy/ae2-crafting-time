package com.ctux.ae2craftingtime.core;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Job-local suspension policy and fixed Forge packet payloads. */
public final class CraftingSuspension {
    public static final int LENGTH = 29;
    public static final UUID NO_JOB = new UUID(0, 0);
    private boolean suspended;

    public boolean suspended() { return suspended; }

    public boolean set(boolean desired, boolean enabled, boolean hasJob) {
        if (!enabled || !hasJob || suspended == desired) return false;
        suspended = desired;
        return true;
    }

    public boolean reconcile(boolean enabled, boolean hasJob) {
        if (enabled && hasJob || !suspended) return false;
        suspended = false;
        return true;
    }

    public void read(boolean saved, boolean hasJob) { suspended = saved && hasJob; }

    public static boolean masksSelectedCard(int serial, int selectedSerial, boolean suspended) {
        return suspended && serial == selectedSerial;
    }

    public record Request(int containerId, long cpuContext, UUID jobId, boolean desired) {
        public Request {
            validateContext(containerId, cpuContext);
            if (jobId == null || NO_JOB.equals(jobId)) throw new IllegalArgumentException("Missing job");
        }
    }

    public record Snapshot(int containerId, long cpuContext, UUID jobId, boolean supported,
                           boolean enabled, boolean suspended) {
        public Snapshot {
            validateContext(containerId, cpuContext);
            if (jobId == null) throw new IllegalArgumentException("Missing job ID");
            if (suspended && (!supported || !enabled || NO_JOB.equals(jobId)))
                throw new IllegalArgumentException("Invalid suspended state");
            if (enabled && !supported || !supported && !NO_JOB.equals(jobId))
                throw new IllegalArgumentException("Unsupported job state");
        }

        public boolean hasJob() { return !NO_JOB.equals(jobId); }
    }

    public static byte[] encode(Request request) {
        return ByteBuffer.allocate(LENGTH).putInt(request.containerId()).putLong(request.cpuContext())
                .putLong(request.jobId().getMostSignificantBits()).putLong(request.jobId().getLeastSignificantBits())
                .put((byte) (request.desired() ? 1 : 0)).array();
    }

    public static Request decodeRequest(byte[] bytes) {
        var b = buffer(bytes);
        int container = b.getInt();
        long context = b.getLong();
        var job = new UUID(b.getLong(), b.getLong());
        byte desired = b.get();
        if (desired != 0 && desired != 1) throw new IllegalArgumentException("Invalid desired state");
        return new Request(container, context, job, desired == 1);
    }

    public static byte[] encode(Snapshot snapshot) {
        int flags = (snapshot.supported() ? 1 : 0) | (snapshot.enabled() ? 2 : 0)
                | (snapshot.hasJob() ? 4 : 0) | (snapshot.suspended() ? 8 : 0);
        return ByteBuffer.allocate(LENGTH).putInt(snapshot.containerId()).putLong(snapshot.cpuContext())
                .put((byte) flags).putLong(snapshot.jobId().getMostSignificantBits())
                .putLong(snapshot.jobId().getLeastSignificantBits()).array();
    }

    public static Snapshot decodeSnapshot(byte[] bytes) {
        var b = buffer(bytes);
        int container = b.getInt();
        long context = b.getLong();
        int flags = Byte.toUnsignedInt(b.get());
        var job = new UUID(b.getLong(), b.getLong());
        if ((flags & ~15) != 0 || ((flags & 4) != 0) != !NO_JOB.equals(job))
            throw new IllegalArgumentException("Invalid snapshot flags");
        return new Snapshot(container, context, job, (flags & 1) != 0,
                (flags & 2) != 0, (flags & 8) != 0);
    }

    private static ByteBuffer buffer(byte[] bytes) {
        if (bytes == null || bytes.length != LENGTH) throw new IllegalArgumentException("Invalid packet length");
        return ByteBuffer.wrap(bytes);
    }

    private static void validateContext(int containerId, long context) {
        if (containerId < 0 || (int) (context >>> 32) != containerId)
            throw new IllegalArgumentException("Invalid CPU context");
    }
}
