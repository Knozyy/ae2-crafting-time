package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctux.ae2craftingtime.core.CraftProfiler;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.StuckEpisodeTracker;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec.Highlight;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Auto pings remember the red plate only, manual locates show the rainbow edge
 * only, and the two lifetimes stay independent: craft-state clears drop plates
 * while rainbows survive until expiry or provider break.
 */
class ProviderHighlightTriggerTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String IRON = "minecraft:iron_ingot";

    @AfterEach
    void clearState() {
        ProviderHighlightClient.clearPlates();
        ClientStats.CACHE.clear();
    }

    @Test
    void autoPlateOnlyShowsPlateWithoutEdge() {
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);

        assertNull(ProviderHighlightClient.live());
        assertEquals(1, ProviderHighlightClient.plates().size());
        assertEquals(IRON, ProviderHighlightClient.plates().get(0).outputId());
    }

    @Test
    void plateOnlyFlagRoundTripsAndLegacyPacketsDefaultToFull() {
        var plateOnly = new Highlight(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON, 15, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        ProviderHighlightCodec.write(buffer, plateOnly);
        assertEquals(plateOnly, ProviderHighlightCodec.read(buffer));
        assertEquals(0, buffer.readableBytes());

        var legacy = new FriendlyByteBuf(Unpooled.buffer());
        legacy.writeUtf(DIMENSION);
        legacy.writeVarInt(1);
        legacy.writeBlockPos(new BlockPos(1, 2, 3));
        legacy.writeUtf(IRON);
        legacy.writeVarInt(15);
        assertFalse(ProviderHighlightCodec.read(legacy).plateOnly());
    }

    @Test
    void networkIdRoundTripsAndLegacyDefaultsToEmpty() {
        var network = "minecraft:overworld|1,2,3";
        var highlight = new Highlight(network, DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON, 15, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        ProviderHighlightCodec.write(buffer, highlight);
        assertEquals(highlight, ProviderHighlightCodec.read(buffer));
        assertEquals(0, buffer.readableBytes());

        var legacyPlateOnly = new FriendlyByteBuf(Unpooled.buffer());
        legacyPlateOnly.writeUtf(DIMENSION);
        legacyPlateOnly.writeVarInt(1);
        legacyPlateOnly.writeBlockPos(new BlockPos(1, 2, 3));
        legacyPlateOnly.writeUtf(IRON);
        legacyPlateOnly.writeVarInt(15);
        legacyPlateOnly.writeBoolean(true);
        assertEquals("", ProviderHighlightCodec.read(legacyPlateOnly).networkId());
    }

    @Test
    void manualShowSetsEdgeOnly() {
        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(1, 2, 3)), 15, IRON);

        assertNotNull(ProviderHighlightClient.live());
        assertTrue(ProviderHighlightClient.plates().isEmpty());
    }

    @Test
    void chatBeamSharesEdgeExpiryAndRowReplacesItsProvenance() {
        var shared = new BlockPos(1, 2, 3);
        var other = new BlockPos(4, 5, 6);
        ProviderHighlightClient.show("net", DIMENSION, List.of(shared, other), 15, IRON, true);
        ProviderHighlightClient.show("other", DIMENSION, List.of(shared), 15, "minecraft:gold_ingot", true);
        assertEquals(List.of(new ProviderHighlightClient.RenderBeam(DIMENSION, shared),
                new ProviderHighlightClient.RenderBeam(DIMENSION, other)), ProviderHighlightClient.renderBeams());
        var expires = ProviderHighlightClient.live().expiresAtMillis();
        ProviderHighlightClient.trimPositions(DIMENSION, pos -> !pos.equals(other));
        assertEquals(List.of(new ProviderHighlightClient.RenderBeam(DIMENSION, shared)),
                ProviderHighlightClient.renderBeams());
        assertEquals(expires, ProviderHighlightClient.live().expiresAtMillis());
        ProviderHighlightClient.show("net", DIMENSION, List.of(shared), 15, IRON, false);
        assertEquals(List.of(new ProviderHighlightClient.RenderBeam(DIMENSION, shared)),
                ProviderHighlightClient.renderBeams());
        ProviderHighlightClient.clearEdgeFor("other", "minecraft:gold_ingot");
        assertTrue(ProviderHighlightClient.renderBeams().isEmpty());
        assertEquals(1, ProviderHighlightClient.liveEdges().size());
        ProviderHighlightClient.onSessionEnd();
        assertTrue(ProviderHighlightClient.renderBeams().isEmpty());
    }

    @Test
    void chatProvenanceRoundTripsWithoutPlateOrIcon() {
        var chat = new Highlight("net", DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON,
                15, false, null, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        ProviderHighlightCodec.write(buffer, chat);
        assertEquals(chat, ProviderHighlightCodec.read(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rowReplacesOnlyItsVerifiedNetworkEdge() {
        var target = new BlockPos(1, 2, 3);
        var unrelated = new BlockPos(4, 5, 6);
        ProviderHighlightClient.show("net", DIMENSION, List.of(target), 15, IRON, true);
        ProviderHighlightClient.show("other", DIMENSION, List.of(unrelated), 15, IRON, true);

        ProviderHighlightClient.show("net", DIMENSION, List.of(target), 15, IRON, false);

        assertEquals(List.of(new ProviderHighlightClient.RenderBeam(DIMENSION, unrelated)),
                ProviderHighlightClient.renderBeams());
        assertEquals(2, ProviderHighlightClient.liveEdges().size());
    }

    @Test
    void manualShowNeverCreatesOrExtendsPlate() {
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);
        assertNull(ProviderHighlightClient.live());

        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(4, 5, 6)), 15, IRON);

        assertNotNull(ProviderHighlightClient.live());
        assertEquals(List.of(new BlockPos(4, 5, 6)), ProviderHighlightClient.live().positions());
        assertEquals(1, ProviderHighlightClient.plates().size());
        assertEquals(List.of(new BlockPos(1, 2, 3)), ProviderHighlightClient.plates().get(0).positions());
    }

    @Test
    void trimPositionsRemovesBoth() {
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);
        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(1, 2, 3)), 15, IRON);

        ProviderHighlightClient.trimPositions(DIMENSION, pos -> false);

        assertNull(ProviderHighlightClient.live());
        assertTrue(ProviderHighlightClient.plates().isEmpty());
    }

    @Test
    void clearForRemovesPlateOnlyKeepsRainbow() {
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);
        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(1, 2, 3)), 15, IRON);
        ProviderHighlightClient.clearFor(IRON);
        assertTrue(ProviderHighlightClient.plates().isEmpty());
        assertNotNull(ProviderHighlightClient.live());
    }

    @Test
    void emptyManualShowClearsEdgeOnlyKeepsPlate() {
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);
        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(1, 2, 3)), 15, IRON);
        ProviderHighlightClient.show(DIMENSION, List.of(), 0, IRON);
        assertNull(ProviderHighlightClient.live());
        assertEquals(1, ProviderHighlightClient.plates().size());
    }

    @Test
    void delayedToCraftingClearRemovesPlateKeepsRainbow() {
        var profiler = new CraftProfiler(10);
        var key = new ProfileKey(IRON);
        var cpu = new Object();
        seedTypical(profiler, key);
        profiler.start(key, cpu, 10, ProfileUnit.ITEM, 100);
        profiler.setJobOwner(cpu, UUID.randomUUID());

        assertEquals(1, profiler.pollNewlyDelayed(cpu, 800).size());
        assertTrue(profiler.pollResolvedDelayed(cpu).isEmpty());
        ProviderHighlightClient.showPlate(DIMENSION, List.of(new BlockPos(1, 2, 3)), IRON);
        ProviderHighlightClient.show(DIMENSION, List.of(new BlockPos(1, 2, 3)), 15, IRON);
        assertNotNull(ProviderHighlightClient.live());
        assertEquals(1, ProviderHighlightClient.plates().size());

        // Partial progress resolves the stall while the craft still runs.
        profiler.complete(key, cpu, 1, 860);
        profiler.flushCompletedSamples();
        assertTrue(profiler.pollNewlyDelayed(cpu, 900).isEmpty());
        assertEquals(List.of(key), profiler.pollResolvedDelayed(cpu));
        assertTrue(profiler.pollResolvedDelayed(cpu).isEmpty());

        // The explicit server clear drops the plate only; the rainbow survives.
        ProviderHighlightClient.clearFor(key.outputId());
        assertTrue(ProviderHighlightClient.plates().isEmpty());
        assertNotNull(ProviderHighlightClient.live());
    }

    @Test
    void resolvedDelayedIsNullSafe() {
        var profiler = new CraftProfiler(10);
        assertTrue(profiler.pollResolvedDelayed(null).isEmpty());
        assertTrue(profiler.pollResolvedDelayed(new Object()).isEmpty());
    }

    @Test
    void stuckEpisodeResolvedDrainsOnce() {
        var tracker = new StuckEpisodeTracker();
        var scope = new Object();
        var key = new ProfileKey(IRON);

        assertEquals(List.of(key), tracker.pollNewlyStuck(scope, Set.of(key)));
        assertTrue(tracker.pollNewlyStuck(scope, Set.of()).isEmpty());
        assertEquals(List.of(key), tracker.pollResolved(scope));
        assertTrue(tracker.pollResolved(scope).isEmpty());
        assertTrue(tracker.pollResolved(null).isEmpty());
    }

    private static void seedTypical(CraftProfiler profiler, ProfileKey key) {
        var seed = new Object();
        profiler.start(key, seed, 1, ProfileUnit.ITEM, 0);
        profiler.complete(key, seed, 1, 200);
        profiler.flushCompletedSamples();
    }
}
