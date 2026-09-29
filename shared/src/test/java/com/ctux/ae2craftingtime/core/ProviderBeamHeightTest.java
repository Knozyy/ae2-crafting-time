package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProviderBeamHeightTest {
    @Test
    void reachesRoofAndStaysAboveHighProviders() {
        assertEquals(320, ProviderBeamHeight.top(-60, 320, 8));
        assertEquals(449, ProviderBeamHeight.top(320, 320, 8));
        assertEquals(337, ProviderBeamHeight.top(320, 320, -1));
        assertEquals(Integer.MAX_VALUE,
                ProviderBeamHeight.top(Integer.MAX_VALUE - 2, 320, Integer.MAX_VALUE));
    }
}
