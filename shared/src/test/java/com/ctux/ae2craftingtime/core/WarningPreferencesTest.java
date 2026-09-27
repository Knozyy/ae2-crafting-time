package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarningPreferencesTest {
    @Test
    void personalMuteAndGlobalSwitchAreIndependent() {
        var preferences = new WarningPreferences();
        var muted = UUID.randomUUID();
        var other = UUID.randomUUID();
        assertTrue(preferences.canSend(muted, true, true));
        assertFalse(preferences.canSend(muted, true, false));
        preferences.set(muted, false);
        assertFalse(preferences.canSend(muted, true, true));
        assertTrue(preferences.canSend(other, true, true));
        assertFalse(preferences.canSend(other, false, true));
        preferences.set(muted, true);
        assertTrue(preferences.canSend(muted, true, true));
        preferences.set(muted, false);
        preferences.clear(muted);
        assertTrue(preferences.canSend(muted, true, true));
        preferences.set(other, false);
        preferences.clearAll();
        assertTrue(preferences.canSend(other, true, true));
    }

    @Test
    void nullIdentityIsRejected() {
        var preferences = new WarningPreferences();
        assertThrows(NullPointerException.class, () -> preferences.set(null, false));
        assertThrows(NullPointerException.class, () -> preferences.canSend(null, true, true));
        assertThrows(NullPointerException.class, () -> preferences.clear(null));
    }
}
