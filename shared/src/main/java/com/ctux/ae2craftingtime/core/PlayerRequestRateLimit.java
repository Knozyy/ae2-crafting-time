package com.ctux.ae2craftingtime.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerRequestRateLimit {
    public static final int MAX_KEYS_PER_SECOND = 512;
    public static final int MAX_PACKETS_PER_SECOND = 4;
    private final Map<UUID, Window> windows = new HashMap<>();

    public boolean allow(UUID playerId, int keyCount, long nowMillis) {
        if (keyCount <= 0 || keyCount > MAX_KEYS_PER_SECOND) {
            return false;
        }
        var cost = keyCount;
        var current = windows.get(playerId);
        if (current == null || nowMillis - current.startedAtMillis >= 1000) {
            windows.put(playerId, new Window(nowMillis, cost, 1));
            return true;
        }
        if (current.keyCount + cost > MAX_KEYS_PER_SECOND || current.packets >= MAX_PACKETS_PER_SECOND) {
            return false;
        }
        windows.put(playerId, new Window(current.startedAtMillis, current.keyCount + cost, current.packets + 1));
        return true;
    }

    public void clear(UUID playerId) { windows.remove(playerId); }
    public void clear() { windows.clear(); }

    private record Window(long startedAtMillis, int keyCount, int packets) {
    }
}
