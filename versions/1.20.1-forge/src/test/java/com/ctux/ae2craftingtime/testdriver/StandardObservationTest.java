package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.TtcSymbols;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardObservationTest {
    private final Rect cell = new Rect(10, 30, 60, 22);
    private final Rect textBounds = new Rect(12, 35, 20, 5);

    @Test void missingIngredientsMustPrecedeEveryAvailableIngredient() {
        var missing = row(1);
        var available = row(0);
        assertFalse(StandardAe2Scenario.missingFirst(List.of()));
        assertFalse(StandardAe2Scenario.missingFirst(List.of(available)));
        assertTrue(StandardAe2Scenario.missingFirst(List.of(missing)));
        assertTrue(StandardAe2Scenario.missingFirst(List.of(missing, missing, available)));
        assertFalse(StandardAe2Scenario.missingFirst(List.of(available, missing)));
        assertFalse(StandardAe2Scenario.missingFirst(List.of(missing, available, missing)));
    }

    @Test void translatedRowTextMustBelongToTheRequestedOutputAndCell() {
        var translated = text("text.ae2craftingtime.waiting", "Waiting", textBounds);
        assertSame(translated, StandardAe2Scenario.rowText(snapshot(List.of(translated), List.of()),
                "minecraft:stone", translated.key()));
        assertNull(StandardAe2Scenario.rowText(snapshot(List.of(translated), List.of()),
                "minecraft:other", translated.key()));
        for (var bounds : new Rect[]{null, new Rect(90, 90, 5, 5)}) {
            assertNull(StandardAe2Scenario.rowText(snapshot(List.of(text(translated.key(), "Waiting", bounds)),
                    List.of()), "minecraft:stone", translated.key()));
        }
        assertNull(StandardAe2Scenario.rowText(snapshot(List.of(translated), List.of()),
                "minecraft:stone", "unknown"));
    }

    @Test void flattenedNativeLabelsRequireTheMatchingValueAndContainingBadge() {
        var values = new LinkedHashMap<String, String>();
        values.put("text.ae2craftingtime.waiting", TtcText.waiting().getString());
        values.put("text.ae2craftingtime.ttc_delayed", TtcText.ttcDelayed().getString());
        values.put("text.ae2craftingtime.ttc", TtcSymbols.Symbol.TIME.glyph() + " ~5s");
        values.forEach((key, rendered) -> {
            var nativeText = text("native-status-text", rendered, textBounds);
            assertSame(nativeText, StandardAe2Scenario.rowText(snapshot(List.of(nativeText), List.of(cell)),
                    "minecraft:stone", key));
            assertNull(StandardAe2Scenario.rowText(snapshot(List.of(nativeText), List.of()),
                    "minecraft:stone", key));
            assertNull(StandardAe2Scenario.rowText(snapshot(List.of(nativeText),
                    List.of(new Rect(90, 90, 5, 5))), "minecraft:stone", key));
            assertNull(StandardAe2Scenario.rowText(snapshot(List.of(text("native-status-text", "wrong", textBounds)),
                    List.of(cell)), "minecraft:stone", key));
            for (var bounds : new Rect[]{null, new Rect(90, 90, 5, 5)}) {
                assertNull(StandardAe2Scenario.rowText(snapshot(List.of(text("native-status-text", rendered, bounds)),
                        List.of(cell)), "minecraft:stone", key));
            }
        });
    }

    @Test void layoutRejectsEscapingBadgesAndHeadersAndHonorsTheBackgroundSwitch() {
        var features = ClientOptionsRuntime.current().features();
        boolean previous = features.enabled(OptionFeature.BADGE_BACKGROUND);
        try {
            features.setEnabled(OptionFeature.BADGE_BACKGROUND, false);
            assertDoesNotThrow(() -> StandardAe2Scenario.validateLayout(snapshot(List.of(), List.of())));
            assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateLayout(
                    snapshot(List.of(), List.of(new Rect(90, 90, 5, 5)))));
            for (var header : List.of(text("other", "other", textBounds),
                    text("native-title", "TTC", null), text("native-title", "TTC", textBounds),
                    text("native-title", "TTC", new Rect(10, 10, 30, 9)))) {
                assertDoesNotThrow(() -> StandardAe2Scenario.validateLayout(snapshot(List.of(header), List.of())));
            }
            var failure = assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateLayout(
                    snapshot(List.of(text("native-title", "TTC", new Rect(-1, 10, 30, 9))), List.of())));
            assertEquals("Standard status header escapes GUI", failure.getMessage());
            features.setEnabled(OptionFeature.BADGE_BACKGROUND, true);
            assertDoesNotThrow(() -> StandardAe2Scenario.validateLayout(snapshot(List.of(), List.of(cell))));
            failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateLayout(snapshot(List.of(), List.of())));
            assertEquals("Invalid standard status badge layout", failure.getMessage());
        } finally {
            features.setEnabled(OptionFeature.BADGE_BACKGROUND, previous);
        }
    }

    @Test void checkUpdatesNeverInventUnrequestedChecks() {
        var checks = new LinkedHashMap<String, Boolean>();
        checks.put("requested", false);
        StandardAe2Scenario.mark(checks, "other", true);
        assertEquals(java.util.Map.of("requested", false), checks);
        StandardAe2Scenario.mark(checks, "requested", true);
        assertEquals(java.util.Map.of("requested", true), checks);
        StandardAe2Scenario.mark(checks, "requested", false);
        assertEquals(java.util.Map.of("requested", false), checks);
    }

    @Test void suspensionCapturesWaitForBothMenuAndRenderedState() {
        var job = java.util.UUID.randomUUID();
        var disabled = new com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot(1, 1L << 32, job, true, false, false);
        var running = new com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot(1, 1L << 32, job, true, true, false);
        var paused = new com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot(1, 1L << 32, job, true, true, true);
        var complete = new com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot(1, 1L << 32,
                com.ctux.ae2craftingtime.core.CraftingSuspension.NO_JOB, true, true, false);
        var empty = suspensionFrame(List.of(), List.of());
        var stale = suspensionFrame(List.of(text("gui.ae2craftingtime.suspended", "Suspended", textBounds)), List.of());
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(null, empty, false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(paused, empty, false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(disabled, null, false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(disabled, snapshot(List.of(), List.of()), false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(disabled, stale, false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(running, empty, false));
        assertTrue(StandardAe2Scenario.suspensionCaptureReady(disabled, empty, false));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(running, empty, true));
        assertFalse(StandardAe2Scenario.suspensionCaptureReady(complete,
                suspensionFrame(List.of(), List.of(row(0))), true));
        assertTrue(StandardAe2Scenario.suspensionCaptureReady(complete, empty, true));
    }

    @Test void cpuCardTotalsIgnoreOtherLabelsMissingBoundsAndTheHeader() {
        var total = "text.ae2craftingtime.ttc";
        var card = text(total, "~5s", new Rect(10, 29, 20, 5));
        var values = List.of(text("other", "other", new Rect(10, 30, 20, 5)),
                text(total, "no bounds", null), text(total, "header", new Rect(10, 28, 20, 5)), card);
        var frame = new UiSnapshot("screen", "menu", new Rect(0, 10, 80, 80),
                100, 100, 1, 1, 0, List.of(), values, List.of(), List.of(), List.of(), List.of());
        assertEquals(List.of(card), StandardAe2Scenario.cpuCardTotals(frame));
        assertTrue(StandardAe2Scenario.cpuCardTotals(snapshot(List.of(), List.of())).isEmpty());
    }
    private UiSnapshot suspensionFrame(List<UiSnapshot.ObservedText> text, List<UiSnapshot.Row> rows) {
        return new UiSnapshot("appeng.client.gui.me.crafting.CraftingCPUScreen", "menu", cell,
                100, 100, 1, 1, 0, rows, text, List.of(), List.of(), List.of(), List.of());
    }

    private UiSnapshot.Row row(long missing) {
        return new UiSnapshot.Row("minecraft:stone", 1, missing, cell, List.of());
    }

    private UiSnapshot.ObservedText text(String key, String rendered, Rect bounds) {
        return new UiSnapshot.ObservedText(key, rendered, List.of(), bounds);
    }

    private UiSnapshot snapshot(List<UiSnapshot.ObservedText> text, List<Rect> badges) {
        return new UiSnapshot("screen", "menu", new Rect(0, 0, 80, 80), 100, 100, 1, 1, 0,
                List.of(row(0)), text, badges, List.of(), List.of(), List.of());
    }
}

