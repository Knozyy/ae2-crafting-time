package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CraftingRowStateTest {
    @ParameterizedTest
    @CsvSource({"ttc,true", "ttc_delayed,true", "waiting,true", "no_space,true", "no_provider,true", "no_power,true",
            "no_channel,true", "no_target,true", "input_blocked,true", "locked,true", "chance_output,true", "plan.recurrent,true",
            "plan.stored_variant,true", "plan.stored_variant.explanation,false", "plan.stored_variant.suggestion,false",
            "status.amounts,true", "status.amounts_legend,false",
            "plan.recurrent_hint,false", "no_provider.explanation,false", "details_hint,false", "unknown,false"})
    void onlyCompactStatusLinesReceiveBadges(String suffix, boolean expected) {
        assertEquals(expected, CraftingRowState.isBadge("text.ae2craftingtime." + suffix));
    }

    @ParameterizedTest
    @CsvSource({"plan.recurrent,true", "plan.stored_variant,true", "status.amounts,true", "chance_output,true", "ttc,false",
            "plan.stored_variant.explanation,false", "unknown,false"})
    void onlyLongBadgesAreWidthLimited(String suffix, boolean expected) {
        assertEquals(expected, CraftingRowState.isWidthLimited("text.ae2craftingtime." + suffix));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "89,1", "90,1", "91,0.98901099", "180,0.5", "9000,0.01"})
    void badgesFitTheirNativeTextArea(int width, float expected) {
        assertEquals(expected, CraftingRowState.badgeTextScale(width), 0.000001f);
    }

    @ParameterizedTest
    @CsvSource({
            "4,10,200,4/10/200", "0,10,200,-/10/200", "10,0,200,10/-/200",
            "4,10,0,4/10/-", "10,0,0,A10", "0,10,0,C10", "0,0,10,S10",
            "0,0,0,''", "-1,10,-1,C10"
    })
    void compactAmountsPreserveEveryCategory(long available, long crafting, long scheduled, String expected) {
        assertEquals(expected, CraftingRowState.compactAmounts(available, Long.toString(available),
                crafting, Long.toString(crafting), scheduled, Long.toString(scheduled)));
    }

    @org.junit.jupiter.api.Test
    void compactAmountsKeepFormattedFluidAndLargeValuesWithoutArithmetic() {
        assertEquals("1.5 mB/9.22 E/-", CraftingRowState.compactAmounts(1, "1.5 mB",
                Long.MAX_VALUE, "9.22 E", -1, null));
    }

    @ParameterizedTest
    @CsvSource({"4,10,4/10", "4,0,A4", "0,10,C10", "0,0,''", "-1,10,C10"})
    void compactPlanAmountsKeepAvailableAndToCraftSeparate(long available, long crafting, String expected) {
        assertEquals(expected, CraftingRowState.compactPlanAmounts(available, Long.toString(available),
                crafting, Long.toString(crafting)));
    }

    @org.junit.jupiter.api.Test
    void compactPlanAmountsKeepNativeFormattedUnits() {
        assertEquals("1.5 mB/9.22 E", CraftingRowState.compactPlanAmounts(1, "1.5 mB",
                Long.MAX_VALUE, "9.22 E"));
    }

    @ParameterizedTest
    @CsvSource({"1,true,true", "1,false,false", "0,true,false", "-1,true,false",
            "9223372036854775807,true,true"})
    void missingProviderRequiresPendingWorkRegardlessOfActiveBatches(long pending, boolean missing,
            boolean expected) {
        for (var reason : CraftingBlockReason.values()) {
            assertEquals(expected ? reason : null, CraftingRowState.blockReason(pending, missing ? reason : null));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true, 1, 0, 0, true",
            "false, 1, 0, 0, false",
            "true, 0, 0, 0, false",
            "true, -1, 0, 0, false",
            "true, 1, 1, 0, false",
            "true, 1, 0, 1, false",
            "true, 1, -1, 0, false",
            "true, 1, 0, -1, false",
            "true, 9223372036854775807, 0, 0, true"
    })
    void requiresRejectedStorageAndOnlyStoredItems(boolean rejected, long stored, long active, long pending,
            boolean expected) {
        assertEquals(expected, CraftingRowState.noSpace(rejected, stored, active, pending));
    }
}
