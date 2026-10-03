package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RenderedTtcObservationTest {
    @Test void onlyTheNativeStatusHeaderReceivesTheTitleIdentity() {
        var gui = new Rect(10, 20, 200, 180);
        var bounds = new Rect(15, 25, 70, 9);
        var text = new UiSnapshot.ObservedText("literal", "TTC: 5s", List.of(), bounds, 0x404040, false);
        var title = UiSnapshot.nativeStatusTitle("appeng.client.gui.me.crafting.CraftingStatusScreen", gui, text);
        assertEquals("native-title", title.key());
        assertEquals(text.rendered(), title.rendered());
        assertEquals(bounds, title.bounds());
        assertEquals(text.color(), title.color());
        assertFalse(title.bold());
        assertNull(UiSnapshot.nativeStatusTitle("CraftConfirmScreen", gui, text));
        assertNull(UiSnapshot.nativeStatusTitle("CraftingStatusScreen", gui,
                new UiSnapshot.ObservedText("literal", "TTC: 5s", List.of(), null)));
        assertNull(UiSnapshot.nativeStatusTitle("CraftingStatusScreen", gui,
                new UiSnapshot.ObservedText("literal", "TTC: 5s", List.of(), new Rect(15, 39, 70, 9))));
        assertNull(UiSnapshot.nativeStatusTitle("CraftingStatusScreen", gui,
                new UiSnapshot.ObservedText("literal", "Crafting Status", List.of(), bounds)));
    }

    @Test void flattenedTextRecoversOnlyTheMatchingTtcDescription() {
        var nativeLabel = new UiSnapshot.ObservedText("gui.ae2.ToCraft", "TTC: 5s", List.of(), null);
        var estimate = new UiSnapshot.ObservedText("text.ae2craftingtime.ttc", "TTC: 5s", List.of("5s"), null);
        var other = new UiSnapshot.ObservedText("text.ae2craftingtime.ttc", "TTC: 2s", List.of("2s"), null);
        assertSame(estimate, UiSnapshot.matchingRenderedText(Stream.of(nativeLabel, other, estimate), "TTC: 5s", false));
        assertNull(UiSnapshot.matchingRenderedText(Stream.empty(), "TTC: 5s", false));
        assertNull(UiSnapshot.matchingRenderedText(Stream.of(nativeLabel, estimate), "unrelated", false));
        assertNull(UiSnapshot.matchingRenderedText(Stream.of(estimate), "⚠ TTC: 5s", false));
        assertSame(estimate, UiSnapshot.matchingRenderedText(Stream.of(estimate), "⚠ TTC: 5s", true));
        assertSame(estimate, UiSnapshot.matchingRenderedText(Stream.of(estimate), "TTC: 5s", true));
        assertNull(UiSnapshot.matchingRenderedText(Stream.of(estimate), "⚠ other", true));
    }
}
