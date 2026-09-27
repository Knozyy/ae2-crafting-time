package com.ctux.ae2craftingtime.core;

/** Foreground policy for ordinary Crafting Plan and Crafting Status row text. */
public final class RowColorPolicy {
    public static boolean inheritNative(boolean badgeBackground, boolean ttcColors) {
        return !badgeBackground && !ttcColors;
    }

    private RowColorPolicy() {
    }
}
