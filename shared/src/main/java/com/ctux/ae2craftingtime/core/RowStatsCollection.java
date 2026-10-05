package com.ctux.ae2craftingtime.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/** Inventory is read only for Requester menus, once for all IDs in the batch. */
public final class RowStatsCollection {
    public static boolean needsInventory(String menuClass) {
        return menuClass.startsWith("com.almostreliable.merequester.");
    }

    public static <T> Map<String, Long> amounts(boolean required, List<String> keys,
            Supplier<? extends Iterable<T>> inventory, Function<T, String> id, ToLongFunction<T> amount) {
        if (!required || keys.isEmpty()) return Map.of();
        var requested = new HashSet<>(keys);
        var result = new HashMap<String, Long>();
        keys.forEach(key -> result.put(key, 0L));
        for (var entry : inventory.get()) {
            var outputId = id.apply(entry);
            if (requested.contains(outputId)) result.merge(outputId, amount.applyAsLong(entry), Long::sum);
        }
        return result;
    }

    private RowStatsCollection() { }
}
