package com.ctux.ae2craftingtime.core;

/** Foreground policy for ordinary Crafting Plan and Crafting Status row text. */
public final class RowColorPolicy {
    public static boolean isNumericEstimate(String translationKey, Object[] arguments) {
        return "text.ae2craftingtime.ttc".equals(translationKey)
                && arguments.length == 1 && arguments[0] instanceof String;
    }

    public static boolean inheritNative(boolean badgeBackground, boolean ttcColors, boolean ordinary) {
        return !badgeBackground && !ttcColors && ordinary;
    }

    private RowColorPolicy() {
    }
}
