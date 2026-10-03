package com.ctux.ae2craftingtime.nativetests;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.ctux.ae2craftingtime.testdriver.Rect;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/** Observation DTO boundaries with native registered keys and actual initialized widgets. */
final class NativeObservationBoundary {
    static void verify(Minecraft minecraft, List<String> passed) throws Exception {
        assertInstanceOf(TitleScreen.class, minecraft.screen);
        var screen = minecraft.screen;
        try {
            var items = java.util.stream.StreamSupport.stream(BuiltInRegistries.ITEM.spliterator(), false)
                    .filter(item -> item != Items.AIR).limit(22).toList();
            assertEquals(22, items.size());
            var entries = new ArrayList<CraftingPlanSummaryEntry>();
            for (int i = 0; i < items.size(); i++)
                entries.add(new CraftingPlanSummaryEntry(AEItemKey.of(items.get(i)), i, 0, i + 1));
            for (int scroll : new int[]{0, 1, 8}) {
                frame(minecraft);
                UiObservationStore.rows(entries, scroll);
                UiObservationStore.finish(minecraft);
                var snapshot = UiObservationStore.latest();
                var visible = entries.subList(Math.min(scroll * 3, entries.size()),
                        Math.min(scroll * 3 + 15, entries.size()));
                assertEquals(scroll, snapshot.scroll());
                assertEquals(visible.size(), snapshot.rows().size());
                assertEquals(visible.size(), snapshot.itemCells().size());
                for (int i = 0; i < visible.size(); i++) {
                    var row = snapshot.rows().get(i);
                    assertEquals(visible.get(i).getWhat().getId().toString(), row.outputId());
                    assertEquals(visible.get(i).getCraftAmount(), row.craftAmount());
                    assertEquals(visible.get(i).getMissingAmount(), row.missingAmount());
                    assertEquals(new Rect(19 + i % 3 * 68, 39 + i / 3 * 23, 67, 22), row.cell());
                    assertEquals(new Rect(row.cell().x() + 48, row.cell().y() + 3, 16, 16),
                            snapshot.itemCells().get(i));
                }
            }
            passed.add("Native plan DTO observations retain exactly the scrolled fifteen cells and omit clipped entries");
            var widget = screen.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(value -> value.visible).findFirst().orElseThrow();
            var bounds = new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            try {
                widget.visible = false;
                frame(minecraft);
                UiObservationStore.finish(minecraft);
                assertTrue(UiObservationStore.latest().widgets().stream().noneMatch(value -> value.bounds().equals(bounds)));
            } finally { widget.visible = true; }
            frame(minecraft);
            UiObservationStore.finish(minecraft);
            assertTrue(UiObservationStore.latest().widgets().stream().anyMatch(value -> value.bounds().equals(bounds)));
            passed.add("Actual title widget disappears from observations while hidden and returns after restoring visibility");
        } finally {
            UiObservationStore.reset();
            minecraft.setScreen(screen);
        }
    }

    private static void frame(Minecraft minecraft) throws Exception {
        // Frame is an observation DTO, not a replacement screen or Minecraft client.
        var type = Class.forName(UiObservationStore.class.getName() + "$Frame");
        var constructor = type.getDeclaredConstructor(String.class, String.class, Rect.class,
                int.class, int.class, double.class);
        constructor.setAccessible(true);
        NativeOptionsBoundaryMod.set(UiObservationStore.class, "active", null,
                constructor.newInstance(minecraft.screen.getClass().getName(), "native-observation-dto",
                        new Rect(10, 20, 240, 184), minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(), minecraft.getWindow().getGuiScale()));
    }
}
