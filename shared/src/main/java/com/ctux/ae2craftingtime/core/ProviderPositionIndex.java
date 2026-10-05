package com.ctux.ae2craftingtime.core;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Provider identities are resolved once per grid during one server tick. */
public final class ProviderPositionIndex<G, P, L> {
    private final Map<G, Map<P, L>> grids = new IdentityHashMap<>();

    public L find(G grid, P provider, Supplier<Map<P, L>> load) {
        return grids.computeIfAbsent(grid, ignored -> load.get()).get(provider);
    }

    public void clear() {
        grids.clear();
    }
}
