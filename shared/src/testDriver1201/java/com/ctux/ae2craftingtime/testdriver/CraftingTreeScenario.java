package com.ctux.ae2craftingtime.testdriver;

import java.util.Set;

final class CraftingTreeScenario {
    static final String SCENARIO = "crafting-tree-screen";
    static final String RECOVERY = "crafting-tree-read-recovery";

    static boolean supports(String scenario) { return SCENARIO.equals(scenario) || RECOVERY.equals(scenario); }
    private static final Set<String> SCREENS = Set.of(
            "com.neuvillette.ae2ct.gui.CraftingTreeScreen", "com.vcwdfca.ae2ct.gui.CraftingTreeScreen");

    static boolean isScreen(String name) {
        return SCREENS.contains(name);
    }

    static boolean tooltipReady(UiSnapshot snapshot) {
        return Set.of("text.ae2craftingtime.ttc", "text.ae2craftingtime.details_hint", "text.ae2craftingtime.reset_hint")
                .stream().allMatch(key -> snapshot.tooltip().stream().filter(text -> text.key().equals(key)).count() == 1);
    }

    static UiSnapshot.ObservedText nodeTtcText(String screen, UiSnapshot.ObservedText text) {
        if (!isScreen(screen) || !text.key().equals("literal")
                || !text.rendered().matches("(?:⏱ )?~\\d+(?:s|:\\d{2}(?::\\d{2})?)")) return text;
        return new UiSnapshot.ObservedText("text.ae2craftingtime.tree_ttc", text.rendered(), text.arguments(),
                text.bounds(), text.color(), text.bold());
    }

    static boolean nodeTtcDrawn(UiSnapshot snapshot, UiSnapshot.Row node) {
        return snapshot.text().stream().anyMatch(text -> text.bounds() != null
                && text.key().equals("text.ae2craftingtime.tree_ttc")
                // The centered label starts 21 pixels below the 16-pixel icon, before the Tree pose scales it.
                && Math.abs(text.bounds().centerX() - node.cell().centerX()) <= 2
                && Math.abs(text.bounds().y() - node.cell().y() - node.cell().height() * 21.0 / 16) <= 2);
    }

    private CraftingTreeScenario() {
    }
}
