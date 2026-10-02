package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import appeng.menu.me.crafting.CraftingStatusEntry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiObservationLifecycleTest {
    @AfterEach void reset() { UiObservationStore.reset(); }

    @Test void callbacksAfterClosingTheObservedScreenCannotRetainAnotherFrame() {
        UiObservationStore.reset();
        assertDoesNotThrow(() -> {
            UiObservationStore.rows(null, 0);
            UiObservationStore.description((CraftingPlanSummaryEntry) null, null);
            UiObservationStore.description((CraftingStatusEntry) null, null);
            UiObservationStore.tooltip(null, null);
            UiObservationStore.text(null, null, 0, 0, 0, 0);
            UiObservationStore.nativeTitle(null, "TTC: 1s", 0, 0, 0, 0, null, false);
            UiObservationStore.fill(null, 0, 0, 0, 0, 0);
            UiObservationStore.beginCpuCards(null, 0);
            UiObservationStore.cpuCard(null, 0, 0, 0, 0, 0);
            UiObservationStore.finish(null);
            UiObservationStore.treeNode(null, null, 0, 0);
            UiObservationStore.clearWirelessTooltip();
        });
        assertNull(UiObservationStore.latest());
        assertEquals(List.of(), UiObservationStore.wirelessTooltip());
    }

    @Test void unrelatedRowsAreIgnoredInsteadOfInventingCraftingCells() throws Exception {
        // Initialize the observation frame without creating a Minecraft window.
        var frameType = Class.forName(UiObservationStore.class.getName() + "$Frame");
        var constructor = frameType.getDeclaredConstructor(String.class, String.class, Rect.class,
                int.class, int.class, double.class);
        constructor.setAccessible(true);
        var frame = constructor.newInstance("screen", "menu", new Rect(10, 20, 100, 100), 200, 200, 1.0);
        var active = UiObservationStore.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(null, frame);
        UiObservationStore.rows(List.of("unrelated", new Object()), 3);
        for (var name : List.of("rows", "itemCells")) {
            var field = frameType.getDeclaredField(name);
            field.setAccessible(true);
            assertEquals(List.of(), field.get(frame), name);
        }
        var scroll = frameType.getDeclaredField("scroll");
        scroll.setAccessible(true);
        assertEquals(3, scroll.getInt(frame));
    }

    @Test void anEmptyRecordedDescriptionFallsBackToTextInsideTheRow() {
        var cell = new Rect(10, 20, 60, 22);
        var text = new UiSnapshot.ObservedText("literal", "1s", List.of(), new Rect(12, 22, 8, 6));
        assertEquals(List.of(text), UiObservationStore.rowDescription(
                java.util.Map.of("output", List.of()), List.of(text), "output", cell));
    }
}
