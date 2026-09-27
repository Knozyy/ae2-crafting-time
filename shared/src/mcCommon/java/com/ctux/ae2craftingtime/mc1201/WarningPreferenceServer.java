package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.WarningPreferences;
import net.minecraft.server.level.ServerPlayer;

/** Kept on the logical server thread; client packets may change only their sender's choice. */
public final class WarningPreferenceServer {
    private static final WarningPreferences PREFERENCES = new WarningPreferences();

    public static void set(ServerPlayer sender, boolean enabled) {
        PREFERENCES.set(sender.getUUID(), enabled);
    }

    public static boolean canSend(ServerPlayer recipient, boolean serverEnabled) {
        return PREFERENCES.canSend(recipient.getUUID(), serverEnabled, StatsNetwork.canSend(recipient));
    }

    public static void clear(ServerPlayer player) {
        PREFERENCES.clear(player.getUUID());
    }

    public static void clearAll() {
        PREFERENCES.clearAll();
    }

    private WarningPreferenceServer() {
    }
}
