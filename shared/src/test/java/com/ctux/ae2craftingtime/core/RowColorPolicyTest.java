package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RowColorPolicyTest {
    @Test
    void ordinaryRowsUseNativeOnlyWhenBothDecorationSwitchesAreOff() {
        for (boolean badge : new boolean[] {false, true}) {
            for (boolean colors : new boolean[] {false, true}) {
                assertEquals(!badge && !colors, RowColorPolicy.inheritNative(badge, colors));
            }
        }
    }

}
