package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeatureOptionsTest {
    private static final Set<OptionFeature> DEFAULT_OFF = Set.of(OptionFeature.COMPACT_STATUS_AMOUNTS,
            OptionFeature.TTC_COLORS, OptionFeature.TEXT_SHADOW, OptionFeature.BADGE_BACKGROUND,
            OptionFeature.CHANCE_OUTPUT_STATUS);
    private static final Set<OptionFeature> SERVER_DEFAULT_OFF = Set.of(OptionFeature.RECURRENT_DETECTION,
            OptionFeature.CHANCE_OUTPUT_DETECTION);

    @Test
    void blockedReasonSwitchesAreIndependent() {
        assertEquals(OptionFeature.NO_PROVIDER_STATUS, OptionFeature.statusFor(CraftingBlockReason.NO_PROVIDER));
        assertEquals(OptionFeature.NO_POWER_STATUS, OptionFeature.statusFor(CraftingBlockReason.NO_POWER));
        assertEquals(OptionFeature.NO_TARGET_STATUS, OptionFeature.statusFor(CraftingBlockReason.NO_TARGET));
        assertEquals(OptionFeature.NO_CHANNEL_STATUS, OptionFeature.statusFor(CraftingBlockReason.NO_CHANNEL));
        assertEquals(OptionFeature.INPUT_BLOCKED_STATUS, OptionFeature.statusFor(CraftingBlockReason.INPUT_BLOCKED));
        assertEquals(OptionFeature.INPUT_BLOCKED_STATUS, OptionFeature.statusFor(CraftingBlockReason.LOCKED));
    }
    @Test
    void everySwitchHasOneStableKeyAndItsExpectedDefault() {
        var keys = new HashSet<String>();
        var client = new FeatureOptions(OptionFeature.Owner.CLIENT);
        var server = new FeatureOptions(OptionFeature.Owner.SERVER);
        for (var feature : OptionFeature.values()) {
            assertTrue(keys.add(feature.key()));
            assertTrue(feature.group() != null);
            assertEquals(feature.owner() == OptionFeature.Owner.SERVER
                            ? !SERVER_DEFAULT_OFF.contains(feature) : !DEFAULT_OFF.contains(feature),
                    (feature.owner() == OptionFeature.Owner.CLIENT ? client : server).enabled(feature));
        }
        assertEquals(DEFAULT_OFF, client.disabled());
        assertEquals(SERVER_DEFAULT_OFF, server.disabled());
    }

    @Test
    void switchesAreIndependentAndWorkingCopiesDoNotShareEdits() {
        var client = new FeatureOptions(OptionFeature.Owner.CLIENT);
        client.setEnabled(OptionFeature.RECEIVE_CRAFT_WARNINGS, false);
        assertFalse(client.enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS));
        assertTrue(client.enabled(OptionFeature.DELAYED_STATUS));
        assertEquals(DEFAULT_OFF.size() + 1, client.disabled().size());

        var copy = client.copy();
        copy.setEnabled(OptionFeature.RECEIVE_CRAFT_WARNINGS, true);
        assertTrue(copy.enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS));
        assertFalse(client.enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS));
        client.reset();
        assertEquals(DEFAULT_OFF, client.disabled());
    }

    @Test
    void compactAmountsAndStatusTimesCanBeToggledIndependently() {
        var options = new FeatureOptions(OptionFeature.Owner.CLIENT);
        for (boolean compact : new boolean[] {false, true}) {
            for (boolean time : new boolean[] {false, true}) {
                options.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, compact);
                options.setEnabled(OptionFeature.STATUS_ROWS, time);
                assertEquals(compact, options.enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
                assertEquals(time, options.enabled(OptionFeature.STATUS_ROWS));
            }
        }
        options.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, false);
        var draft = options.copy();
        draft.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
        assertFalse(options.enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
        draft.reset();
        assertFalse(draft.enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
        options.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
        assertTrue(options.copy().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
    }

    @Test
    void environmentBoundaryRejectsWrongOrMissingSwitches() {
        var server = new FeatureOptions(OptionFeature.Owner.SERVER);
        assertThrows(IllegalArgumentException.class, () -> server.setEnabled(OptionFeature.PLAN_ROWS, false));
        assertThrows(IllegalArgumentException.class, () -> server.enabled(OptionFeature.PLAN_ROWS));
        assertThrows(NullPointerException.class, () -> server.enabled(null));
        assertThrows(NullPointerException.class, () -> new FeatureOptions(null));
        server.setEnabled(OptionFeature.NOTIFY_ON_DELAYED, false);
        assertFalse(server.enabled(OptionFeature.NOTIFY_ON_DELAYED));
    }
}
