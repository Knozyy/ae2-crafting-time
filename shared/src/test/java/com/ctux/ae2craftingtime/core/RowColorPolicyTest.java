package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RowColorPolicyTest {
    @Test
    void ordinaryRowsUseNativeOnlyWhenBothDecorationSwitchesAreOff() {
        for (boolean badge : new boolean[] {false, true}) {
            for (boolean colors : new boolean[] {false, true}) {
                assertEquals(!badge && !colors, RowColorPolicy.inheritNative(badge, colors, true));
                assertEquals(false, RowColorPolicy.inheritNative(badge, colors, false));
            }
        }
    }

    @Test
    void onlyStringValuedTtcIsAnOrdinaryEstimate() {
        assertEquals(true, RowColorPolicy.isNumericEstimate("text.ae2craftingtime.ttc", new Object[] {"~1s"}));
        assertEquals(false, RowColorPolicy.isNumericEstimate("text.ae2craftingtime.waiting", new Object[] {"~1s"}));
        assertEquals(false, RowColorPolicy.isNumericEstimate("text.ae2craftingtime.ttc", new Object[] {}));
        assertEquals(false, RowColorPolicy.isNumericEstimate("text.ae2craftingtime.ttc", new Object[] {"~1s", "extra"}));
        assertEquals(false, RowColorPolicy.isNumericEstimate("text.ae2craftingtime.ttc", new Object[] {new Object()}));
    }
}
