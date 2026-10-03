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

    @Test void quantitySummariesRequireExactlyOneNormalAmountLineForNonemptyRows() {
        var key = "text.ae2craftingtime.status.amounts";
        var summary = new UiSnapshot.ObservedText(key, "4/10/200", List.of("4/10/200"), textBounds, null, false);
        var populated = new UiSnapshot.Row("minecraft:stone", 1, 0, cell,
                List.of(text("unrelated", "other", textBounds), summary));
        assertEquals(List.of(summary), StandardAe2Scenario.quantityAmountLines(populated, false, 0));
        assertTrue(StandardAe2Scenario.quantityAmountLines(row(0), true, 7).isEmpty());
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.quantityAmountLines(populated, true, 7));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.quantityAmountLines(row(0), false, 0));
        var duplicate = new UiSnapshot.Row("minecraft:stone", 1, 0, cell, List.of(summary, summary));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.quantityAmountLines(duplicate, false, 0));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityText(summary, "4/10/200", 0));
        var bold = new UiSnapshot.ObservedText(key, "4/10/200", List.of("4/10/200"), textBounds, null, true);
        for (var invalid : List.of(bold, text(key, "4/10/200", textBounds))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateQuantityText(invalid, "4/10/200", 0));
            assertTrue(failure.getMessage().startsWith("Synthetic native amount case 0 text "));
        }
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityLayout(snapshot(List.of(), List.of()), summary, null, 0));
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateQuantityLayout(snapshot(List.of(), List.of()), summary, 0xFFFFFF, 0));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateQuantityLayout(
                        snapshot(List.of(), List.of(new Rect(90, 90, 5, 5))), summary, null, 0));
        assertTrue(failure.getMessage().startsWith("Synthetic native amount case 0 color/layout "));
    }

    @Test void quantityTooltipsRequireTheLegendAndExactNativeFullAmounts() {
        var legend = text("text.ae2craftingtime.status.amounts_legend", "Stored / crafting / scheduled", null);
        var irrelevant = text("unrelated", "other", null);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityLegend(List.of(irrelevant, legend), false, 0));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityLegend(List.of(irrelevant), true, 7));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateQuantityLegend(List.of(legend), true, 7));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateQuantityLegend(List.of(), false, 0));
        assertEquals("Synthetic native amount case 0 lost tooltip legend", failure.getMessage());
        var key = "gui.ae2.FromStorage";
        var nativeAmount = new UiSnapshot.ObservedText(key, "Stored: 4", List.of("4"), null);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityTooltipCategory(List.of(irrelevant, nativeAmount), key, "4", 0, 0));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityTooltipCategory(List.of(nativeAmount), key, null, 0, 7));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateQuantityTooltipCategory(List.of(), key, null, 0, 7));
        for (var tooltip : List.of(List.<UiSnapshot.ObservedText>of(), List.of(irrelevant), List.of(text(key, "Stored: 4", null)))) {
            failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateQuantityTooltipCategory(tooltip, key, "4", 0, 0));
            assertEquals("Synthetic native amount case 0 lost full tooltip category 0=4", failure.getMessage());
        }
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

    @Test void addonSummariesRetainNativeAmountsNormalFontAndCellBounds() {
        var slot = List.of("4", "10", "200");
        var summary = new UiSnapshot.ObservedText("text.ae2craftingtime.status.amounts", "4/10/200",
                List.of("4/10/200"), textBounds, null, false);
        var frame = snapshot(List.of(), List.of());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAddonSummary(frame, summary, slot, "gas"));
        var bold = new UiSnapshot.ObservedText(summary.key(), summary.rendered(), summary.arguments(), textBounds, null, true);
        for (var invalid : java.util.Arrays.asList(null, text(summary.key(), summary.rendered(), textBounds), bold)) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAddonSummary(frame, invalid, slot, "gas"));
            assertEquals("Addon gas lost native SLOT amounts or badge bounds", failure.getMessage());
        }
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAddonSummary(
                snapshot(List.of(), List.of(new Rect(90, 90, 5, 5))), summary, slot, "gas"));
        var irrelevant = text("other", "other", null);
        var legend = text("text.ae2craftingtime.status.amounts_legend", "Stored / crafting / scheduled", null);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAddonLegend(List.of(irrelevant, legend), "gas"));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateAddonLegend(List.of(irrelevant), "gas"));
        assertEquals("Addon gas lost amount legend", failure.getMessage());
        var nativeAmount = new UiSnapshot.ObservedText("gui.ae2.FromStorage", "Stored: 4", List.of("4"), null);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAddonTooltip(List.of(irrelevant, nativeAmount), nativeAmount.key(), "4", "gas"));
        for (var tooltip : List.of(List.<UiSnapshot.ObservedText>of(), List.of(irrelevant), List.of(text(nativeAmount.key(), "Stored: 4", null)))) {
            failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAddonTooltip(tooltip, nativeAmount.key(), "4", "gas"));
            assertEquals("Addon gas lost native FULL tooltip gui.ae2.FromStorage", failure.getMessage());
        }
    }

    @Test void fontAndScaleChecksRejectUnreadableOrEscapingAmountText() {
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountFont(0, 10, 10));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountFont(2, 10, 10));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountFont(1, 11, 10));
        for (int width : new int[]{9, 10}) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAmountFont(1, width, 10));
            assertEquals("Uniform status font is not wider than the default font", failure.getMessage());
        }
        var frame = snapshot(List.of(), List.of());
        var amount = text("text.ae2craftingtime.status.amounts", "1G/2G/3G", textBounds);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateScaledAmount(frame, amount, amount));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateScaledAmount(frame, null, amount));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateScaledAmount(frame, amount, null));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateScaledAmount(frame, amount,
                text(amount.key(), "1G/2G", textBounds)));
        var failure = assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateScaledAmount(
                snapshot(List.of(), List.of(new Rect(90, 90, 5, 5))), amount, amount));
        assertTrue(failure.getMessage().startsWith("Scaled amount badge escapes its native cell:"));
    }

    @Test void amountOptionsRequireSavedFlagsAndTheMatchingNativeRowRepresentation() {
        var features = new com.ctux.ae2craftingtime.core.FeatureOptions(OptionFeature.Owner.CLIENT);
        var keys = List.of("gui.ae2.FromStorage", "gui.ae2.Crafting", "gui.ae2.Scheduled");
        var summary = text("text.ae2craftingtime.status.amounts", "4/10/200", textBounds);
        var unrelated = text("other", "other", textBounds);
        for (boolean compact : new boolean[]{false, true}) {
            for (boolean time : new boolean[]{false, true}) {
                features.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, compact);
                features.setEnabled(OptionFeature.STATUS_ROWS, time);
                assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountOptionFeatures(features, compact, time, 0));
                assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAmountOptionFeatures(features, !compact, time, 0));
                var failure = assertThrows(IllegalStateException.class,
                        () -> StandardAe2Scenario.validateAmountOptionFeatures(features, compact, !time, 0));
                assertEquals("Saved amount option combination 0 differs", failure.getMessage());
                var description = compact ? List.of(unrelated, summary)
                        : List.of(unrelated, text(keys.get(0), "Stored: 4", textBounds), text(keys.get(1), "Crafting: 10", textBounds), text(keys.get(2), "Scheduled: 200", textBounds));
                var row = new UiSnapshot.Row("minecraft:stone", 0, 0, cell, description, 4, 10, 200);
                assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountOptionSummary(row, compact, 0));
                assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAmountOptionSummary(row, !compact, 0));
                assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountOptionCategories(row, keys, compact, time, 0));
                assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAmountOptionCategories(row, keys, !compact, time, 0));
            }
        }
        var empty = new UiSnapshot.Row("minecraft:stone", 0, 0, cell, List.of(unrelated));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountOptionCategories(empty, keys, false, false, 0));
        var extra = new UiSnapshot.Row("minecraft:stone", 0, 0, cell, List.of(text(keys.get(0), "Stored: 0", textBounds)));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAmountOptionCategories(extra, keys, false, true, 0));
        var duplicate = new UiSnapshot.Row("minecraft:stone", 0, 0, cell,
                List.of(text(keys.get(0), "Stored: 4", textBounds), text(keys.get(0), "Stored: 4", textBounds)), 4, 0, 0);
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAmountOptionCategories(duplicate, keys, false, true, 0));
        var forbidden = new UiSnapshot.Row("minecraft:stone", 0, 0, cell,
                List.of(unrelated, summary, text("text.ae2craftingtime.waiting", "Waiting", textBounds)), 4, 10, 200);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountOptionCategories(forbidden, keys, true, true, 0));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateAmountOptionCategories(forbidden, keys, true, false, 0));
        assertEquals("TTC-off amount option rendered a status badge", failure.getMessage());
    }

    @Test void badgeChangesPreserveNativeRowsTextAndSavedAppearance() {
        var rows = List.of("minecraft:stone:10", "minecraft:cobblestone:20");
        var text = List.of("Waiting:12,35", "TTC:12,40");
        assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgeContent(rows, text, rows, text));
        for (var changed : List.of(List.<String>of(), List.of(rows.get(1), rows.get(0)))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateBadgeContent(changed, text, rows, text));
            assertEquals("Badge switch changed native rows or mod text", failure.getMessage());
        }
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateBadgeContent(rows, List.of("Waiting:13,35"), rows, text));
        var config = new com.ctux.ae2craftingtime.core.ClientConfig();
        config.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0x245A7D);
        config.setBadgeOpacity(96);
        for (int step : new int[]{1, 3, 5}) {
            config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, step != 3);
            assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgeAppearance(config, step, "ACTIVE"));
            config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, step == 3);
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateBadgeAppearance(config, step, "ACTIVE"));
            assertEquals("Badge background state differs at ACTIVE step " + step, failure.getMessage());
        }
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
        config.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0);
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateBadgeAppearance(config, 1, "ACTIVE"));
        config.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0x245A7D);
        config.setBadgeOpacity(176);
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateBadgeAppearance(config, 1, "ACTIVE"));
        assertEquals("Custom badge appearance was lost", failure.getMessage());
    }

    @Test void appearanceResetRequiresEveryExpectedInputAndExactDefaultValues() {
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAppearanceInput("Badge color", "#000000", "#000000"));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAppearanceInput("Badge opacity", "176", "176"));
        for (var expected : java.util.Arrays.asList(null, "#000000")) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAppearanceInput("Badge color", expected, "#245A7D"));
            assertEquals("Appearance reset value differs: Badge color", failure.getMessage());
        }
        int expectedCount = com.ctux.ae2craftingtime.core.ClientConfig.appearanceColors().size() + 1;
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAppearanceInputCount(expectedCount));
        for (int count : new int[]{0, expectedCount - 1, expectedCount + 1}) {
            var failure = assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateAppearanceInputCount(count));
            assertEquals("Appearance reset did not expose every input", failure.getMessage());
        }
    }

    @Test void storedVariantTransitionsPreserveAllAmountsAndNativeButtonStates() {
        var amounts = List.of(List.of(1L, 2L, 3L), List.of(4L, 5L, 6L));
        var buttons = List.of(true, false);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateVariantContent(amounts, amounts, buttons, buttons));
        for (var changed : List.of(List.<List<Long>>of(), List.of(amounts.get(1), amounts.get(0)))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateVariantContent(amounts, changed, buttons, buttons));
            assertEquals("Stored-variant transition changed quantities or native button state", failure.getMessage());
        }
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateVariantContent(amounts, amounts, buttons, List.of(false, false)));
    }

    @Test void delayedWarningsAndStatusHeadersKeepTheirNativeStyleAndGeometry() {
        var warning = new UiSnapshot.ObservedText("text.ae2craftingtime.ttc_delayed", "Delayed",
                List.of(), textBounds, 0xFF5555, false);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateDelayedWarning(warning));
        for (var invalid : List.of(
                new UiSnapshot.ObservedText(warning.key(), warning.rendered(), List.of(), textBounds, 0xFF5555, true),
                new UiSnapshot.ObservedText(warning.key(), warning.rendered(), List.of(), textBounds, 0xFFFFFF, false),
                text(warning.key(), warning.rendered(), textBounds))) {
            var failure = assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateDelayedWarning(invalid));
            assertEquals("DELAYED must be normal red on the active stone row", failure.getMessage());
        }
        var frame = snapshot(List.of(), List.of());
        var header = text("native-title", "Crafting Status", new Rect(10, 10, 30, 9));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateStatusHeader(frame, header));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateStatusHeader(frame,
                text(header.key(), header.rendered(), new Rect(-1, 10, 30, 9))));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validateStatusHeader(
                snapshot(List.of(), List.of(new Rect(90, 90, 5, 5))), header));
    }

    @Test void completedLifecycleMustClearTheTotalWithoutRejectingRetainedRowText() {
        var key = "text.ae2craftingtime.ttc";
        var header = text(key, "TTC: 5s", new Rect(10, 10, 30, 9));
        var active = snapshot(List.of(header), List.of());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateCompletedStatus(active, "running-status"));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateCompletedStatus(active, "craft-lifecycle"));
        assertEquals("completed crafting status still shows total TTC", failure.getMessage());
        for (var retained : List.of(text("other", "other", textBounds), text(key, "TTC: 5s", null),
                text(key, "TTC: 5s", new Rect(10, 19, 30, 9)))) {
            assertDoesNotThrow(() -> StandardAe2Scenario.validateCompletedStatus(snapshot(List.of(retained), List.of()), "craft-lifecycle"));
        }
        assertDoesNotThrow(() -> StandardAe2Scenario.validateCompletedStatus(snapshot(List.of(), List.of()), "craft-lifecycle"));
    }

    @Test void nativeSortAndPlanChecksRejectChangedOrderMissingBadgesAndInvalidFills() {
        var missing = row(1);
        var available = row(0);
        var frame = new UiSnapshot("screen", "menu", new Rect(0, 0, 80, 80), 100, 100, 1, 1, 0,
                List.of(missing, available), List.of(), List.of(), List.of(), List.of(), List.of());
        assertDoesNotThrow(() -> StandardAe2Scenario.validatePlanMissingOrder(frame, true));
        assertDoesNotThrow(() -> StandardAe2Scenario.validatePlanMissingOrder(snapshot(List.of(), List.of()), false));
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validatePlanMissingOrder(snapshot(List.of(), List.of()), true));
        var total = text("text.ae2craftingtime.total_ttc", "TTC: 5s", textBounds);
        assertDoesNotThrow(() -> StandardAe2Scenario.validatePlanTotal(frame, total, false));
        assertDoesNotThrow(() -> StandardAe2Scenario.validatePlanTotal(snapshot(List.of(), List.of(cell)), total, true));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validatePlanTotal(frame, total, true));
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validatePlanTotal(frame, text(total.key(), total.rendered(), null), false));
        assertThrows(IllegalStateException.class, () -> StandardAe2Scenario.validatePlanTotal(
                snapshot(List.of(), List.of(new Rect(90, 90, 5, 5))), total, false));
        var expected = List.of("minecraft:stone", "minecraft:smooth_stone");
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSortedRows(expected, expected, "plan", 3));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSortedRows(
                List.of(expected.get(0), expected.get(1), "minecraft:dirt"), expected, "plan", 3));
        var wrong = List.of(expected.get(1), expected.get(0));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateSortedRows(wrong, expected, "plan", 3));
        assertEquals("plan sort 3 row order is " + wrong + ", expected " + expected, failure.getMessage());
    }

    @Test void amountResetChecksTheNativeInputAtEveryOperation() {
        for (int optionCase : new int[]{4, 5, 6}) {
            for (int step = 0; step <= 10; step++) {
                int operation = step;
                boolean expected = switch (step) {
                    case 0 -> optionCase != 6;
                    case 1 -> optionCase == 6;
                    case 3, 4, 9 -> true;
                    default -> false;
                };
                assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountResetInput(
                        optionCase, operation, "Compact amounts", expected));
                if (step == 6 || step == 10) {
                    assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountResetInput(
                            optionCase, operation, "Compact amounts", !expected));
                } else {
                    String message = switch (step) {
                        case 0 -> "Wrong pre-reset compact value: case=" + optionCase;
                        case 1 -> "Compact edit did not apply: case=" + optionCase;
                        case 2, 4 -> "Wrong pre-reset option value: Compact amounts";
                        case 3, 5 -> "Option edit did not apply: Compact amounts";
                        default -> "Reset did not restore model default: Compact amounts";
                    };
                    var failure = assertThrows(IllegalStateException.class,
                            () -> StandardAe2Scenario.validateAmountResetInput(
                                    optionCase, operation, "Compact amounts", !expected));
                    assertEquals(message, failure.getMessage());
                }
            }
        }
        for (int step : new int[]{-1, 11}) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAmountResetInput(4, step, "Compact amounts", false));
            assertEquals("Unexpected reset step " + step, failure.getMessage());
        }
    }

    @Test void amountResetMustNotChangeSavedConfigBeforeDone() {
        var features = new com.ctux.ae2craftingtime.core.FeatureOptions(OptionFeature.Owner.CLIENT);
        for (int optionCase : new int[]{4, 5, 6}) {
            features.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, optionCase == 5);
            assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountResetSaved(features, optionCase));
            features.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, optionCase != 5);
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAmountResetSaved(features, optionCase));
            assertEquals("Reset changed live compact option before Done: case=" + optionCase, failure.getMessage());
        }
    }

    @Test void badgePersistencePreservesCustomAppearanceAndRequiresAChangedSavedFileOnRestore() {
        var config = new com.ctux.ae2craftingtime.core.ClientConfig();
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, false);
        config.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0x245A7D);
        config.setBadgeOpacity(96);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgeBeforeRelaunch(config));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateSavedBadgeOff(config));
        for (int change = 0; change < 3; change++) {
            var invalid = config.copy();
            if (change == 0) invalid.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
            if (change == 1) invalid.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0);
            if (change == 2) invalid.setBadgeOpacity(176);
            var before = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateBadgeBeforeRelaunch(invalid));
            assertEquals("Badge Off/custom appearance was not saved for relaunch", before.getMessage());
            var after = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateSavedBadgeOff(invalid));
            assertEquals("Saved Off/custom badge settings changed after relaunch", after.getMessage());
        }
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateRestoredBadge(config, "new-hash", "old-hash"));
        for (int change = 0; change < 4; change++) {
            var invalid = config.copy();
            if (change == 0) invalid.features().setEnabled(OptionFeature.BADGE_BACKGROUND, false);
            if (change == 1) invalid.setColor(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE, 0);
            if (change == 2) invalid.setBadgeOpacity(176);
            String hash = change == 3 ? "old-hash" : "new-hash";
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateRestoredBadge(invalid, hash, "old-hash"));
            assertEquals("Badge On/custom appearance did not restore after relaunch", failure.getMessage());
        }
    }

    @Test void relaunchRequiresTheCompletePredecessorChecksAndTheSavedConfig() {
        var required = List.of("header", "layout");
        var badge = new StandardAe2Scenario.BadgeContinuation(1, "world", "campaign", "hash", required, List.of());
        var amount = new StandardAe2Scenario.AmountContinuation(1, "world", "campaign", "hash", required, List.of());
        assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgePredecessorChecks(badge, required));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgePredecessorChecks(badge, List.of("layout", "header")));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateBadgePredecessorConfig(badge, "hash"));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountPredecessorChecks(amount, required));
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountPredecessorChecks(amount, List.of("layout", "header")));
        for (var invalid : List.of(List.<String>of(), List.of("header"), List.of("header", "layout", "extra"))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateBadgePredecessorChecks(badge, invalid));
            assertEquals("Badge relaunch predecessor or saved config differs", failure.getMessage());
            failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.validateAmountPredecessorChecks(amount, invalid));
            assertEquals("Status relaunch predecessor omitted required checks", failure.getMessage());
        }
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateBadgePredecessorConfig(badge, "changed"));
        var features = new com.ctux.ae2craftingtime.core.FeatureOptions(OptionFeature.Owner.CLIENT);
        features.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, false);
        assertDoesNotThrow(() -> StandardAe2Scenario.validateAmountPredecessorConfig(amount, "hash", features));
        var failure = assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateAmountPredecessorConfig(amount, "changed", features));
        assertEquals("Saved compact-off config changed before relaunch check", failure.getMessage());
        features.setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
        assertThrows(IllegalStateException.class,
                () -> StandardAe2Scenario.validateAmountPredecessorConfig(amount, "hash", features));
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
