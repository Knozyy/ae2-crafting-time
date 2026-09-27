package com.ctux.ae2craftingtime.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Session-only recipient preferences; absence preserves existing warning delivery. */
public final class WarningPreferences {
    private final Map<UUID, Boolean> receive = new HashMap<>();

    public void set(UUID player, boolean enabled) {
        receive.put(Objects.requireNonNull(player), enabled);
    }

    public boolean canSend(UUID player, boolean serverEnabled, boolean peerSupported) {
        return peerSupported && serverEnabled && receive.getOrDefault(Objects.requireNonNull(player), true);
    }

    public void clear(UUID player) {
        receive.remove(Objects.requireNonNull(player));
    }

    public void clearAll() {
        receive.clear();
    }
}
