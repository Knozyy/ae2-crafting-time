package com.ctux.ae2craftingtime.core;

import java.nio.ByteBuffer;
import java.util.Objects;

/** One fixed, versioned server-options payload for snapshots and edit requests. */
public final class ServerOptionsWire {
    public static final int LENGTH = 38;
    private static final int VERSION = 2;

    public record Snapshot(int revision, boolean editable, ServerConfig config) { }

    public static byte[] encode(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.revision() < 0) throw new IllegalArgumentException("Invalid revision");
        var config = Objects.requireNonNull(snapshot.config());
        long switches = 0;
        int bit = 0;
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER) {
                if (config.features().enabled(feature)) switches |= 1L << bit;
                bit++;
            }
        }
        return ByteBuffer.allocate(LENGTH).put((byte) VERSION).putInt(snapshot.revision())
                .put((byte) (snapshot.editable() ? 1 : 0)).putLong(switches)
                .putInt(config.maxSamples()).putDouble(config.outlierMultiplier())
                .putInt(config.minimumNoProgressSeconds()).putDouble(config.typicalDurationMultiplier()).array();
    }

    public static Snapshot decode(byte[] bytes) {
        if (bytes == null || bytes.length != LENGTH) throw new IllegalArgumentException("Invalid options length");
        var buffer = ByteBuffer.wrap(bytes);
        if (buffer.get() != VERSION) throw new IllegalArgumentException("Invalid options version");
        int revision = buffer.getInt();
        if (revision < 0) throw new IllegalArgumentException("Invalid revision");
        byte editable = buffer.get();
        if (editable != 0 && editable != 1) throw new IllegalArgumentException("Invalid edit flag");
        long switches = buffer.getLong();
        var config = new ServerConfig();
        int bit = 0;
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER)
                config.features().setEnabled(feature, (switches & 1L << bit++) != 0);
        }
        if (switches >>> bit != 0) throw new IllegalArgumentException("Unknown option bits");
        config.setMaxSamples(buffer.getInt());
        config.setOutlierMultiplier(buffer.getDouble());
        config.setMinimumNoProgressSeconds(buffer.getInt());
        config.setTypicalDurationMultiplier(buffer.getDouble());
        return new Snapshot(revision, editable == 1, config);
    }

    private ServerOptionsWire() { }
}
