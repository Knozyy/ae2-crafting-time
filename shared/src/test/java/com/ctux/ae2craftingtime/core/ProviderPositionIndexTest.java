package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderPositionIndexTest {
    @Test
    void oneEnumerationPerGridAndTickIncludingMissingProviders() {
        var index = new ProviderPositionIndex<Object, Object, Integer>();
        var grid = new Object();
        var provider = new Object();
        var loads = new AtomicInteger();
        java.util.function.Supplier<java.util.Map<Object, Integer>> load = () -> {
            var map = new IdentityHashMap<Object, Integer>();
            map.put(provider, loads.incrementAndGet());
            return map;
        };
        for (int i = 0; i < 1000; i++) assertEquals(1, index.find(grid, provider, load));
        assertNull(index.find(grid, new Object(), load));
        assertEquals(1, loads.get());
        assertEquals(2, index.find(new Object(), provider, load));
        index.clear();
        assertEquals(3, index.find(grid, provider, load));
    }
}
