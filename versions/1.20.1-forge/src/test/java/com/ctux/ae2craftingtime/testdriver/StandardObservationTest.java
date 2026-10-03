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

    @Test void storedVariantLabelsAndGeometryPreserveNeutralNativeRows() {
        var key = "text.ae2craftingtime.plan.stored_variant";
        var valid = new UiSnapshot.ObservedText(key, "Stored variant", List.of(), textBounds, 0xE0E0E0, false);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStoredVariantLabel(null, false, 0xE0E0E0));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStoredVariantLabel(valid, true, 0xE0E0E0));
        var nativeColor = new UiSnapshot.ObservedText(key, "Stored variant", List.of(), textBounds, null, false);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStoredVariantLabel(nativeColor, true, null));
        for (var invalid : List.of(
                new UiSnapshot.ObservedText(key, "Stored variant", List.of(), textBounds, 0xE0E0E0, true),
                new UiSnapshot.ObservedText(key, "Stored variant", List.of(), textBounds, null, false),
                new UiSnapshot.ObservedText(key, "Stored variant", List.of(), textBounds, 0xFF5555, false))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateStoredVariantLabel(invalid, true, 0xE0E0E0));
            assertEquals("Stored-variant label is not normal neutral text", failure.getMessage());
        }
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStoredVariantText(row(1), valid));
        var outsideText = new UiSnapshot.ObservedText(key, "Stored variant", List.of(), new Rect(90, 90, 5, 5));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateStoredVariantText(row(1), outsideText));
        assertEquals("Stored-variant text exceeds its row at narrow layout", failure.getMessage());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStoredVariantRow(snapshot(List.of(), List.of()), row(1)));
        var outsideRow = new UiSnapshot.Row("minecraft:iron_pickaxe", 1, 1, new Rect(90, 90, 5, 5), List.of());
        failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateStoredVariantRow(snapshot(List.of(), List.of()), outsideRow));
        assertEquals("Variant row escapes native plan layout", failure.getMessage());
    }

    @Test void recurrenceTextMustStayInsideAnObservedTableCell() {
        var key = "text.ae2craftingtime.plan.recurrent";
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceBounds(snapshot(List.of(), List.of())));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceBounds(
                snapshot(List.of(text("unrelated", "other", null)), List.of())));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceBounds(
                snapshot(List.of(text(key, "Recurrent: 1", textBounds)), List.of())));
        for (var bounds : new Rect[]{null, new Rect(90, 90, 5, 5)}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateRecurrenceBounds(
                            snapshot(List.of(text(key, "Recurrent: 1", bounds)), List.of())));
            assertEquals("Recurrent text escapes its native table cell", error.getMessage());
        }
        var emptyRows = new UiSnapshot("screen", "menu", cell, 100, 100, 1, 1, 0,
                List.of(), List.of(text(key, "Recurrent: 1", textBounds)), List.of(), List.of(), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateRecurrenceBounds(emptyRows));
    }

    @Test void recurrenceLabelsPreserveWarningStyleRequestedQuantityAndVisibleBackgrounds() {
        var key = "text.ae2craftingtime.plan.recurrent";
        var valid = new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, 0xFF5555, false);
        var frame = snapshot(List.of(text("unrelated", "other", null), valid), List.of());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), null, false, false, false, 1));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), valid, false, false, false, 1));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), valid, false, true, true, 100));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), valid, false, false, true, 1));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), null, false, false, true, 100));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRecurrenceLabel(
                snapshot(List.of(valid), List.of(cell)), row(1), valid, true, false, false, 1));
        var error = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), valid, false, false, true, 100));
        assertEquals("Recurrence label lost requested quantity 100", error.getMessage());
        for (var invalid : List.of(
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, 0xFF5555, true),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, null, false),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, 0xFFFFFF, false),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of(), textBounds, 0xFF5555, false),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1", "2"), textBounds, 0xFF5555, false),
                new UiSnapshot.ObservedText(key, "Recurrent: 2", List.of("1"), textBounds, 0xFF5555, false))) {
            error = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateRecurrenceLabel(frame, row(1), invalid, false, false, false, 1));
            assertEquals("Recurrence label lost its red warning style or amount", error.getMessage());
        }
        for (var drawn : List.of(
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, 0xFF5555, true),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), textBounds, 0xFFFFFF, false),
                new UiSnapshot.ObservedText(key, "Recurrent: 1", List.of("1"), null, 0xFF5555, false))) {
            error = assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateRecurrenceLabel(
                    snapshot(List.of(drawn), List.of()), row(1), valid, false, false, false, 1));
            assertEquals("Recurrent label or badge differs from client options", error.getMessage());
        }
        for (var background : List.of(false, true)) {
            var badges = background ? List.<Rect>of() : List.of(cell);
            assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateRecurrenceLabel(
                    snapshot(List.of(valid), badges), row(1), valid, background, false, false, 1));
        }
        var outside = new UiSnapshot.Row("minecraft:stone", 1, 1, new Rect(90, 90, 5, 5), List.of());
        error = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateRecurrenceLabel(frame, outside, null, false, false, false, 1));
        assertEquals("Recurrence row escapes plan layout", error.getMessage());
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
