package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ServerOptionsWireTest {
    @Test
    void roundTripAllServerValuesAndAuthorityFlag() {
        var config = new ServerConfig();
        config.features().setEnabled(OptionFeature.PROFILING, false);
        config.features().setEnabled(OptionFeature.NO_POWER_DETECTION, false);
        config.setMaxSamples(100);
        config.setOutlierMultiplier(1000);
        config.setMinimumNoProgressSeconds(3600);
        config.setTypicalDurationMultiplier(1);
        var encoded = ServerOptionsWire.encode(new ServerOptionsWire.Snapshot(7, true, config));
        assertEquals(ServerOptionsWire.LENGTH, encoded.length);
        assertEquals(3, Byte.toUnsignedInt(encoded[0]));
        var decoded = ServerOptionsWire.decode(encoded);
        assertEquals(7, decoded.revision());
        assertTrue(decoded.editable());
        assertFalse(decoded.config().features().enabled(OptionFeature.PROFILING));
        assertFalse(decoded.config().features().enabled(OptionFeature.NO_POWER_DETECTION));
        assertTrue(decoded.config().features().enabled(OptionFeature.NO_SPACE_DETECTION));
        assertFalse(decoded.config().features().enabled(OptionFeature.CRAFTING_SUSPENSION));
        assertEquals(100, decoded.config().maxSamples());
        assertEquals(1000, decoded.config().outlierMultiplier());
        assertEquals(3600, decoded.config().minimumNoProgressSeconds());
        assertEquals(1, decoded.config().typicalDurationMultiplier());
        assertFalse(ServerOptionsWire.decode(ServerOptionsWire.encode(
                new ServerOptionsWire.Snapshot(0, false, new ServerConfig()))).editable());
    }

    @Test
    void appendedSuspensionBitDoesNotMoveExistingOptions() {
        var config = new ServerConfig();
        var before = ServerOptionsWire.encode(new ServerOptionsWire.Snapshot(0, false, config));
        config.features().setEnabled(OptionFeature.CRAFTING_SUSPENSION, true);
        var after = ServerOptionsWire.encode(new ServerOptionsWire.Snapshot(0, false, config));
        long difference = ByteBuffer.wrap(before).getLong(6) ^ ByteBuffer.wrap(after).getLong(6);
        assertEquals(1L << 19, difference);
        assertTrue(ServerOptionsWire.decode(after).config().features().enabled(OptionFeature.CRAFTING_SUSPENSION));
    }

    @Test
    void rejectsMalformedOrOutOfRangePayloads() {
        var good = ServerOptionsWire.encode(new ServerOptionsWire.Snapshot(0, false, new ServerConfig()));
        assertThrows(NullPointerException.class, () -> ServerOptionsWire.encode(null));
        assertThrows(NullPointerException.class, () -> ServerOptionsWire.encode(
                new ServerOptionsWire.Snapshot(0, false, null)));
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.encode(
                new ServerOptionsWire.Snapshot(-1, false, new ServerConfig())));
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(null));
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(new byte[3]));
        var badVersion = Arrays.copyOf(good, good.length);
        badVersion[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badVersion));
        var badRevision = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badRevision).putInt(1, -1);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badRevision));
        var badFlag = Arrays.copyOf(good, good.length);
        badFlag[5] = 2;
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badFlag));
        var badBits = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badBits).putLong(6, -1L);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badBits));
        var badSamples = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badSamples).putInt(14, 0);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badSamples));
        var badOutlier = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badOutlier).putDouble(18, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badOutlier));
        var badDelay = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badDelay).putInt(26, 0);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badDelay));
        var badTypical = Arrays.copyOf(good, good.length);
        ByteBuffer.wrap(badTypical).putDouble(30, Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> ServerOptionsWire.decode(badTypical));
    }
}
