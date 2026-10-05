package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RowStatsCollectionTest {
    private record Stack(String id, long amount) { }

    @Test
    void planAndStatusRequestsNeverReadInventory() {
        assertFalse(RowStatsCollection.needsInventory("appeng.menu.me.crafting.CraftConfirmMenu"));
        assertFalse(RowStatsCollection.needsInventory("appeng.menu.me.crafting.CraftingStatusMenu"));
        assertFalse(RowStatsCollection.needsInventory("other.requester.Menu"));
        for (var required : List.of(false, true)) {
            var keys = required ? List.<String>of() : List.of("minecraft:stone");
            assertEquals(Map.of(), RowStatsCollection.amounts(required, keys,
                    () -> { throw new AssertionError("inventory must not be read"); }, Stack::id, Stack::amount));
        }
    }

    @Test
    void requesterReadsOnceAndPreservesIdAggregationAndMissingZeroes() {
        assertTrue(RowStatsCollection.needsInventory("com.almostreliable.merequester.requester.RequesterMenu"));
        assertTrue(RowStatsCollection.needsInventory("com.almostreliable.merequester.terminal.RequesterTerminalMenu"));
        var reads = new AtomicInteger();
        var inventory = List.of(new Stack("minecraft:stone", 10), new Stack("minecraft:stone", 5),
                new Stack("minecraft:dirt", 7), new Stack("minecraft:grass_block", 100));
        var result = RowStatsCollection.amounts(true,
                List.of("minecraft:stone", "minecraft:dirt", "minecraft:air", "minecraft:stone"),
                () -> { reads.incrementAndGet(); return inventory; }, Stack::id, Stack::amount);
        assertEquals(1, reads.get());
        assertEquals(Map.of("minecraft:stone", 15L, "minecraft:dirt", 7L, "minecraft:air", 0L), result);
    }

    @Test
    void emptyInventoryRetainsRequestedZeroes() {
        assertEquals(Map.of("minecraft:stone", 0L), RowStatsCollection.amounts(true,
                List.of("minecraft:stone"), List::<Stack>of, Stack::id, Stack::amount));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 32, 256})
    void inventoryWorkDoesNotGrowWithTheNumberOfRequestedIds(int count) {
        var keys = IntStream.range(0, count).mapToObj(i -> "test:item_" + i).toList();
        var inventory = IntStream.range(0, count + 20)
                .mapToObj(i -> List.of(new Stack("test:item_" + i, 5), new Stack("test:item_" + i, 7)))
                .flatMap(List::stream).toList();
        var reads = new AtomicInteger();
        var result = RowStatsCollection.amounts(true, keys,
                () -> { reads.incrementAndGet(); return inventory; }, Stack::id, Stack::amount);
        assertEquals(keys.stream().collect(Collectors.toMap(key -> key, key -> 12L)), result);
        assertEquals(1, reads.get());
    }
}
