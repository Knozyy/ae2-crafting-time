package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class PlanTextObservationTest {
    @Test void attributesDrawnPlanAndStatusLabelsAfterProductionAppendsThem() {
        var nativeLabel = Component.literal("To Craft: 1");
        List<Component> lines = new ArrayList<>(List.of(nativeLabel));
        Map<Object, List<Component>> plans = Map.of(new Object(), lines);
        var estimate = Component.translatable("text.ae2craftingtime.ttc", "~2s");
        assertNull(UiObservationStore.semanticText(Map.of(), plans, estimate.getString()));
        lines.add(estimate);
        var observed = UiObservationStore.semanticText(Map.of(), plans, estimate.getString());
        assertNotNull(observed);
        assertEquals("text.ae2craftingtime.ttc", observed.key());
        assertEquals(observed, UiObservationStore.semanticText(Map.of("stone", lines), Map.of(), estimate.getString()));
        assertEquals(observed, UiObservationStore.semanticText(Map.of("stone", lines), Map.of(), "⚠ " + estimate.getString()));
        assertNull(UiObservationStore.semanticText(Map.of(), plans, nativeLabel.getString()));
        assertNull(UiObservationStore.semanticText(Map.of(), plans, "unrelated draw"));
    }
}
