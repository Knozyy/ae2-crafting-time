package com.ctux.ae2craftingtime.core;

public final class CraftingRowState {
    // AE2 draws table descriptions at half scale: 90 font pixels occupy 45 screen pixels.
    public static final int BADGE_TEXT_WIDTH = 90;
    private static final java.util.Set<String> BADGE_KEYS = java.util.Set.of(
            "text.ae2craftingtime.ttc", "text.ae2craftingtime.ttc_delayed", "text.ae2craftingtime.waiting",
            "text.ae2craftingtime.no_space", "text.ae2craftingtime.no_provider", "text.ae2craftingtime.no_power",
            "text.ae2craftingtime.no_channel",
            "text.ae2craftingtime.chance_output",
            "text.ae2craftingtime.no_target", "text.ae2craftingtime.input_blocked",
            "text.ae2craftingtime.locked", "text.ae2craftingtime.plan.recurrent",
            "text.ae2craftingtime.plan.stored_variant",
            "text.ae2craftingtime.status.amounts");

    public static boolean isBadge(String translationKey) {
        return BADGE_KEYS.contains(translationKey);
    }

    public static float badgeTextScale(int width) {
        return width > BADGE_TEXT_WIDTH ? (float) BADGE_TEXT_WIDTH / width : 1f;
    }

    public static boolean isWidthLimited(String key) {
        return key.equals("text.ae2craftingtime.plan.recurrent")
                || key.equals("text.ae2craftingtime.plan.stored_variant")
                || key.equals("text.ae2craftingtime.status.amounts");
    }

    public static String compactAmounts(long available, String availableText, long crafting, String craftingText,
            long scheduled, String scheduledText) {
        boolean a = available > 0;
        boolean c = crafting > 0;
        boolean s = scheduled > 0;
        int shown = (a ? 1 : 0) + (c ? 1 : 0) + (s ? 1 : 0);
        if (shown == 0) return "";
        if (shown == 1) return a ? "A" + availableText : c ? "C" + craftingText : "S" + scheduledText;
        return (a ? availableText : "-") + "/" + (c ? craftingText : "-") + "/" + (s ? scheduledText : "-");
    }

    public static String compactPlanAmounts(long available, String availableText, long crafting,
            String craftingText) {
        if (available > 0 && crafting > 0) return availableText + "/" + craftingText;
        if (available > 0) return "A" + availableText;
        return crafting > 0 ? "C" + craftingText : "";
    }

    private CraftingRowState() {
    }

    public static boolean noSpace(boolean cantStoreItems, long stored, long active, long pending) {
        return cantStoreItems && stored > 0 && active == 0 && pending == 0;
    }

    public static CraftingBlockReason blockReason(long pending, CraftingBlockReason reason) {
        return pending > 0 ? reason : null;
    }
}
