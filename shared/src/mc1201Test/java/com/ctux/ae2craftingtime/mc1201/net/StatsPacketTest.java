package com.ctux.ae2craftingtime.mc1201.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.StallDiagnostic;
import com.ctux.ae2craftingtime.core.StatsChatAction;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.core.TtcAccuracyStats;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

class StatsPacketTest {
    @Test
    void snapshotRejectsInvalidChanceEvidence() {
        for (var chance : new int[] {0, 10000}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            StatsPacketCodec.writeKeys(buffer, List.of("mekanism:sawdust"));
            buffer.writeVarInt(0); // Network amounts.
            buffer.writeVarInt(0); // Waiting ticks.
            buffer.writeVarInt(0); // Block reasons.
            buffer.writeVarInt(1);
            buffer.writeUtf("mekanism:sawdust");
            buffer.writeVarInt(chance);
            assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(buffer));
        }
        var unrequested = new FriendlyByteBuf(Unpooled.buffer());
        StatsPacketCodec.writeKeys(unrequested, List.of());
        unrequested.writeVarInt(0);
        unrequested.writeVarInt(0);
        unrequested.writeVarInt(0);
        unrequested.writeVarInt(1);
        unrequested.writeUtf("mekanism:sawdust");
        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(unrequested));
    }

    @Test
    void snapshotRejectsUnknownBlockReason() {
        for (var reason : new int[] {-1, CraftingBlockReason.values().length}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            StatsPacketCodec.writeKeys(buffer, List.of("minecraft:iron_ingot"));
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeUtf("minecraft:iron_ingot");
            buffer.writeVarInt(reason);
            assertThrows(RuntimeException.class, () -> StatsSnapshotS2C.decode(buffer));
        }
    }

    @Test
    void snapshotRoundTripsEveryBlockReason() {
        for (var reason : CraftingBlockReason.values()) {
            var packet = new StatsSnapshotS2C(List.of("minecraft:iron_ingot"), List.of(), Map.of(), Map.of(),
                    Map.of("minecraft:iron_ingot", reason), Map.of(), OptionalLong.empty(), 7);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            StatsSnapshotS2C.encode(packet, buffer);
            assertEquals(packet, StatsSnapshotS2C.decode(buffer));
        }
    }

    @Test
    void snapshotRoundTripsMissingProvidersWithoutLearnedStatsAtBothSizeLimits() {
        for (var count : new int[] {0, PacketLimits.MAX_KEYS}) {
            var keys = java.util.stream.IntStream.range(0, count).mapToObj(i -> "test:output_" + i).toList();
            var packet = new StatsSnapshotS2C(keys, List.of(), Map.of(), Map.of(), keys.stream().collect(java.util.stream.Collectors.toMap(key -> key, key -> CraftingBlockReason.NO_POWER)), Map.of(), OptionalLong.empty(), 0x123456789L);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            StatsSnapshotS2C.encode(packet, buffer);
            assertEquals(packet, StatsSnapshotS2C.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    @Test
    void snapshotRejectsInvalidMissingProviderKeysAndCounts() {
        for (var count : new int[] {-1, PacketLimits.MAX_KEYS + 1}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeVarInt(0); // Requested keys.
            buffer.writeVarInt(0); // Network amounts.
            buffer.writeVarInt(0); // Waiting ticks.
            buffer.writeVarInt(count);
            assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(buffer));
        }
        for (var key : List.of("invalid", "minecraft:unrequested",
                "minecraft:" + "a".repeat(PacketLimits.MAX_OUTPUT_ID_LENGTH))) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeUtf(key);
            assertThrows(RuntimeException.class, () -> StatsSnapshotS2C.decode(buffer));
        }
    }

    @Test
    void requestRoundTripsVisibleOutputKeys() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var packet = new StatsRequestC2S(List.of("minecraft:iron_plate", "ae2:printed_silicon"));

        StatsRequestC2S.encode(packet, buffer);

        assertEquals(packet, StatsRequestC2S.decode(buffer));
    }

    @Test
    void requestRejectsOversizedCollectionBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(PacketLimits.MAX_KEYS + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsRequestC2S.decode(buffer));
    }

    @Test
    void snapshotRoundTripsServerCalculatedStats() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var packet = new StatsSnapshotS2C(List.of("minecraft:water", "minecraft:lava"), List.of(
                new StatsEntry(new ProfileKey("minecraft:water"),
                        new ProfileStats(3, 40.5, 25.0, 500.0, 42, ProfileUnit.MILLIBUCKET, false,
                                2, 4.0, List.of(10L, 20L, 42L), List.of(250L, 500L, 1000L)),
                        java.util.Optional.of(new TtcAccuracyStats(4, 3, 0.9, 2.5, 12.5, 1.1,
                                30, 33.0, 31.0, 4, 4)),
                        java.util.Optional.of(new StallDiagnostic(960, 240, 1, 1, 4))),
                new StatsEntry(new ProfileKey("minecraft:lava"),
                        new ProfileStats(1, 20, 50, 1000, 20, ProfileUnit.MILLIBUCKET))),
                Map.of("minecraft:water", 8_000L, "minecraft:lava", 0L),
                Map.of("minecraft:water", 40L), Map.of("minecraft:lava", CraftingBlockReason.NO_PROVIDER),
                Map.of("minecraft:water", 5000), OptionalLong.of(2_445), 0x123456789L);

        StatsSnapshotS2C.encode(packet, buffer);

        assertEquals(packet, StatsSnapshotS2C.decode(buffer));
    }

    @Test
    void snapshotRoundTripsRawManaUnit() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var packet = new StatsSnapshotS2C(List.of(new StatsEntry(new ProfileKey("botania:mana"),
                new ProfileStats(1, 20, 0.05, 1, 20, ProfileUnit.MANA))));
        StatsSnapshotS2C.encode(packet, buffer);
        assertEquals(packet, StatsSnapshotS2C.decode(buffer));
    }

    @Test
    void chatRoundTripsServerValidatedIntent() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var packet = new StatsChatC2S("minecraft:iron_ingot", 1509, StatsChatAction.SHOW);

        StatsChatC2S.encode(packet, buffer);

        assertEquals(packet, StatsChatC2S.decode(buffer));
        assertThrows(IllegalArgumentException.class,
                () -> new StatsChatC2S("arbitrary chat text", 1, StatsChatAction.SHOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StatsChatC2S("minecraft:iron_ingot", -1, StatsChatAction.SHOW));
    }

    @Test
    void snapshotConvenienceConstructorDerivesRequestedKeys() {
        var entries = List.of(new StatsEntry(new ProfileKey("minecraft:iron_ingot"),
                new ProfileStats(1, 20, 1, 20, 20, ProfileUnit.ITEM)));

        var packet = new StatsSnapshotS2C(entries);

        assertEquals(List.of("minecraft:iron_ingot"), packet.requestedKeys());
        assertEquals(entries, packet.entries());
        assertEquals(Map.of(), packet.networkAmounts());
        assertEquals(Map.of(), packet.waitingTicks());
        assertEquals(Map.of(), packet.blockReasons());
        assertEquals(Map.of(), packet.chanceOutputs());
        assertFalse(packet.totalTtcSeconds().isPresent());
        assertEquals(-1, packet.cpuContext());
    }

    @Test
    void snapshotRejectsNegativeTotalTtc() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeBoolean(true);
        buffer.writeVarLong(-1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(buffer));
    }

    @Test
    void snapshotRejectsOversizedCollectionsBeforeAllocation() {
        var oversizedKeys = new FriendlyByteBuf(Unpooled.buffer());
        oversizedKeys.writeVarInt(PacketLimits.MAX_KEYS + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(oversizedKeys));

        var oversizedAmounts = new FriendlyByteBuf(Unpooled.buffer());
        oversizedAmounts.writeVarInt(0);
        oversizedAmounts.writeVarInt(PacketLimits.MAX_KEYS + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(oversizedAmounts));

        var oversizedEntries = new FriendlyByteBuf(Unpooled.buffer());
        oversizedEntries.writeVarInt(0);
        oversizedEntries.writeVarInt(0);
        oversizedEntries.writeVarInt(0);
        oversizedEntries.writeVarInt(0);
        oversizedEntries.writeVarInt(0);
        oversizedEntries.writeVarInt(PacketLimits.MAX_KEYS + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(oversizedEntries));

        var oversizedWaiting = new FriendlyByteBuf(Unpooled.buffer());
        oversizedWaiting.writeVarInt(0);
        oversizedWaiting.writeVarInt(0);
        oversizedWaiting.writeVarInt(PacketLimits.MAX_KEYS + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(oversizedWaiting));
    }

    @Test
    void snapshotRejectsInvalidWaitingValues() {
        var negative = new FriendlyByteBuf(Unpooled.buffer());
        negative.writeVarInt(0);
        negative.writeVarInt(0);
        negative.writeVarInt(1);
        negative.writeUtf("minecraft:iron_ingot");
        negative.writeVarLong(-1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(negative));

        var longId = new FriendlyByteBuf(Unpooled.buffer());
        longId.writeVarInt(0);
        longId.writeVarInt(0);
        longId.writeVarInt(1);
        longId.writeUtf("minecraft:" + "a".repeat(PacketLimits.MAX_OUTPUT_ID_LENGTH));

        assertThrows(RuntimeException.class, () -> StatsSnapshotS2C.decode(longId));
    }

    @Test
    void snapshotRejectsOversizedSampleHistoryBeforeAllocation() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeUtf("minecraft:iron_ingot");
        buffer.writeEnum(ProfileUnit.ITEM);
        buffer.writeVarInt(1);
        buffer.writeDouble(20);
        buffer.writeDouble(1);
        buffer.writeDouble(20);
        buffer.writeVarLong(20);
        buffer.writeBoolean(true);
        buffer.writeVarInt(1);
        buffer.writeDouble(4);
        buffer.writeVarInt(PacketLimits.MAX_SAMPLES + 1);

        assertThrows(IllegalArgumentException.class, () -> StatsSnapshotS2C.decode(buffer));
    }
}
