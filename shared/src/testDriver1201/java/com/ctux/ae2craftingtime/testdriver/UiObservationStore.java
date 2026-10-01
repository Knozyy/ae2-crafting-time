package com.ctux.ae2craftingtime.testdriver;

import appeng.client.gui.AEBaseScreen;
import com.ctux.ae2craftingtime.testdriver.mixin.ContainerScreenAccessor;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.ctux.ae2craftingtime.mc1201.TtcSortButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import appeng.api.stacks.AEKey;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UiObservationStore {
    private static final int TABLE_X = 9;
    private static final int TABLE_Y = 19;
    private static final int CELL_WIDTH = 67;
    private static final int CELL_HEIGHT = 22;
    private static final int PITCH_X = 68;
    private static final int PITCH_Y = 23;
    private static final Set<String> WIRELESS_SCREENS = Set.of(
            "appeng.client.gui.me.common.MEStorageScreen",
            "com.lhy.wcwt.client.WirelessComprehensiveWorkTerminalScreen",
            "de.mari_023.ae2wtlib.wct.WCTScreen");
    private static Frame active;
    private static volatile UiSnapshot latest;
    private static volatile List<UiSnapshot.ObservedText> wirelessTooltip = List.of();
    private static long sequence;

    public static void reset() {
        active = null;
        latest = null;
        wirelessTooltip = List.of();
    }

    public static void begin(Minecraft minecraft) {
        if (!(minecraft.screen instanceof AEBaseScreen<?> screen)
                || (!(screen instanceof CraftConfirmScreen)
                && !(screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?>)
                && !screen.getClass().getName().equals(Ae2NetworkAnalyserFixture.SCREEN)
                && !screen.getClass().getName().equals(RequesterFixture.SCREEN)
                && !CraftingTreeScenario.isScreen(screen.getClass().getName()))) {
            active = null;
            return;
        }
        active = new Frame(screen.getClass().getName(), screen.getMenu().getClass().getName(),
                new Rect(screen.getGuiLeft(), screen.getGuiTop(), ((ContainerScreenAccessor) screen).ae2craftingtime_test_driver$width(),
                        ((ContainerScreenAccessor) screen).ae2craftingtime_test_driver$height()),
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                minecraft.getWindow().getGuiScale());
    }

    public static void rows(List<?> entries, int scroll) {
        if (active == null) {
            return;
        }
        active.scroll = scroll;
        active.rows.clear();
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof CraftingPlanSummaryEntry entry)) {
                continue;
            }
            var visibleIndex = i - scroll * 3;
            if (visibleIndex < 0 || visibleIndex >= 15) {
                continue;
            }
            var cell = new Rect(active.gui.x() + TABLE_X + visibleIndex % 3 * PITCH_X,
                    active.gui.y() + TABLE_Y + visibleIndex / 3 * PITCH_Y, CELL_WIDTH, CELL_HEIGHT);
            active.rows.add(new PendingRow(entry.getWhat().getId().toString(), entry.getCraftAmount(),
                    entry.getMissingAmount(), cell, entry));
            active.itemCells.add(new Rect(cell.x() + CELL_WIDTH - 19, cell.y() + 3, 16, 16));
        }
    }

    public static void description(CraftingPlanSummaryEntry entry, List<Component> components) {
        if (active != null) {
            // Production appends TTC at the same RETURN point; observe the list after all injectors finish.
            active.planDescriptions.put(entry, components);
        }
    }

    public static void description(CraftingStatusEntry entry, List<Component> components) {
        if (active != null) {
            active.descriptions.put(entry.getWhat().getId().toString(), observed(components, null));
        }
    }

    public static void tooltip(CraftingPlanSummaryEntry entry, List<Component> components) {
        if (active != null) {
            active.tooltip.clear();
            active.tooltip.addAll(observed(components, null));
        }
    }

    public static void text(GuiGraphics graphics, Component component, int x, int y, int width, int height) {
        if (active == null) {
            return;
        }
        var observed = CraftingTreeScenario.nodeTtcText(active.screen,
                observed(component, transformed(graphics, x, y, x + width, y + height)));
        if (recordStatusText(component.getString(), observed.bounds())) return;
        var total = planTotalText(active.screen, active.gui, observed);
        if (total != null) {
            active.text.add(total);
            return;
        }
        if (observed.key().startsWith("text.ae2craftingtime.")
                || observed.key().equals("gui.ae2craftingtime.suspended")
                || active.screen.contains("CraftingStatusScreen")
                || active.cpuCards.stream().anyMatch(card -> observed.bounds().overlaps(card.nameArea))) {
            active.text.add(observed);
        }
    }

    public static void nativeTitle(GuiGraphics graphics, String text, int x, int y, int width, int height,
            Integer color, boolean bold) {
        if (active != null) {
            var bounds = transformed(graphics, x, y, x + width, y + height);
            if (recordStatusText(text, bounds)) return;
            // Newer AE2 flattens styled row labels into formatted characters.
            if (active.screen.contains("CraftingStatusScreen") && statusRowText(bounds)) {
                active.text.add(new UiSnapshot.ObservedText("native-status-text", text, List.of(), bounds,
                        color, bold));
                return;
            }
        }
        if (active != null && active.screen.contains("CraftingStatusScreen") && text.startsWith("TTC:")) {
            active.text.add(new UiSnapshot.ObservedText("native-title", text, List.of(),
                    transformed(graphics, x, y, x + width, y + height)));
        }
        if (active != null && active.screen.contains("CraftingCPUScreen")
                && text.equals(net.minecraft.client.resources.language.I18n.get("gui.ae2craftingtime.suspended"))) {
            active.text.add(new UiSnapshot.ObservedText("gui.ae2craftingtime.suspended", text, List.of(),
                    transformed(graphics, x, y, x + width, y + height)));
        }
    }

    private static boolean statusRowText(Rect bounds) {
        for (int index = 0; index < 18; index++) {
            var cell = new Rect(active.gui.x() + TABLE_X + index % 3 * PITCH_X,
                    active.gui.y() + TABLE_Y + index / 3 * PITCH_Y, CELL_WIDTH, CELL_HEIGHT);
            if (bounds.inside(cell)) return true;
        }
        return false;
    }

    private static boolean recordStatusText(String rendered, Rect bounds) {
        var semantic = semanticText(active.descriptions, active.planDescriptions, rendered);
        if (semantic == null) return false;
        if (active.text.stream().noneMatch(line -> line.key().equals(semantic.key()) && bounds.equals(line.bounds())))
            active.text.add(new UiSnapshot.ObservedText(semantic.key(), semantic.rendered(), semantic.arguments(),
                    bounds, semantic.color(), semantic.bold()));
        return true;
    }

    static UiSnapshot.ObservedText semanticText(Map<String, List<UiSnapshot.ObservedText>> descriptions,
            Map<Object, List<Component>> planDescriptions, String rendered) {
        return java.util.stream.Stream.concat(descriptions.values().stream(),
                planDescriptions.values().stream().map(lines -> observed(lines, null))).flatMap(List::stream)
                .filter(line -> line.key().startsWith("text.ae2craftingtime.")
                        && line.rendered().equals(rendered))
                .findFirst().orElse(null);
    }

    static List<UiSnapshot.ObservedText> planDescription(Map<Object, List<Component>> descriptions, Object identity) {
        return observed(descriptions.getOrDefault(identity, List.of()), null);
    }

    static UiSnapshot.ObservedText planTotalText(String screen, Rect gui, UiSnapshot.ObservedText text) {
        // The production footer flattens its translated component to recolor symbols before drawing it.
        if (!screen.endsWith("CraftConfirmScreen") || text.bounds() == null
                || text.bounds().y() != gui.y() + 178 || !text.rendered().startsWith("TTC:")) return null;
        return new UiSnapshot.ObservedText("text.ae2craftingtime.total_ttc", text.rendered(),
                text.arguments(), text.bounds(), text.color(), text.bold());
    }

    public static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (active != null && color == com.ctux.ae2craftingtime.mc1201.TtcBadge.BACKGROUND) {
            active.badges.add(transformed(graphics, x1, y1, x2, y2));
        }
    }

    public static void beginCpuCards(List<appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry> entries,
            int scroll) {
        if (active == null) return;
        active.cpuCards.clear();
        active.scroll = scroll;
        active.rawCpuSerials = entries.stream().map(
                appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry::serial).toList();
    }

    public static void cpuCard(appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry cpu,
            int selectedSerial, int x, int y, int width, int height) {
        if (active == null) return;
        var job = cpu.currentJob();
        var name = cpu.name() == null ? "CPU #" + cpu.serial() : cpu.name().getString();
        var bounds = new Rect(x, y, width, height);
        active.cpuCards.add(new PendingCpuCard(cpu.serial(), name,
                job == null ? null : job.what().getId().toString(), job == null ? 0 : job.amount(),
                cpu.elapsedTimeNanos(), cpu.serial() == selectedSerial, bounds,
                new Rect(bounds.x() + 2, bounds.y() + 2, Math.max(0, width - 4), 10),
                new Rect(bounds.x() + 2, bounds.y() + height - 12, Math.max(0, width - 4), 10),
                new Rect(bounds.x() + 1, bounds.y() + height - 2, Math.max(0, width - 2), 1)));
    }

    public static void finish(Minecraft minecraft) {
        if (active == null || minecraft.screen == null) {
            return;
        }
        if (active.screen.equals(RequesterFixture.SCREEN)) {
            var screen = (AEBaseScreen<?>) minecraft.screen;
            screen.getMenu().slots.stream().filter(net.minecraft.world.inventory.Slot::isActive)
                    .map(slot -> new Rect(active.gui.x() + slot.x, active.gui.y() + slot.y, 16, 16))
                    .forEach(active.itemCells::add);
        }
        if (minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?> screen) {
            var accessor = (com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) screen;
            var status = accessor.ae2craftingtime_test_driver$status();
            if (status != null) {
                active.rows.clear();
                int scroll = accessor.ae2craftingtime_test_driver$scrollbar().getCurrentScroll();
                var entries = status.getEntries();
                for (int i = scroll * 3; i < Math.min(entries.size(), scroll * 3 + 18); i++) {
                    var entry = entries.get(i);
                    int visible = i - scroll * 3;
                    var cell = new Rect(active.gui.x() + 9 + visible % 3 * 68,
                            active.gui.y() + 19 + visible / 3 * 23, 67, 22);
                    active.rows.add(new PendingRow(entry.getWhat().getId().toString(),
                            entry.getActiveAmount() + entry.getPendingAmount(), 0, cell, null,
                            entry.getStoredAmount(), entry.getActiveAmount(), entry.getPendingAmount()));
                    active.itemCells.add(new Rect(cell.x() + CELL_WIDTH - 19, cell.y() + 3, 16, 16));
                }
            }
        }
        for (var child : minecraft.screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                var state = widget instanceof TtcSortButton button
                        ? button.getTooltipMessage().stream().map(Component::getString).reduce((a, b) -> a + " | " + b)
                                .orElse("")
                        : "";
                active.widgets.add(new UiSnapshot.Widget(widget.getClass().getName(), state,
                        new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()), List.of()));
            }
        }
        var rows = active.rows.stream().map(row -> new UiSnapshot.Row(row.outputId, row.craftAmount,
                row.missingAmount, row.cell,
                row.identity == null ? rowDescription(active.descriptions, active.text, row.outputId, row.cell)
                        : planDescription(active.planDescriptions, row.identity),
                row.storedAmount, row.activeAmount, row.pendingAmount)).toList();
        var mergedBadges = merge(active.badges);
        var cpuCards = active.cpuCards.stream().map(card -> {
            var ttc = active.text.stream().filter(text -> text.key().equals("text.ae2craftingtime.ttc")
                    && text.bounds() != null && text.bounds().inside(card.bounds)).findFirst().orElse(null);
            var badge = mergedBadges.stream().filter(rect -> rect.inside(card.bounds)).findFirst().orElse(null);
            return new UiSnapshot.CpuCard(card.serial, card.name, card.jobId, card.amount, card.elapsedNanos,
                    card.selected, card.bounds, card.nameArea, card.infoArea, card.progressArea, ttc, badge);
        }).toList();
        latest = new UiSnapshot(active.screen, active.menu, active.gui, active.screenWidth, active.screenHeight,
                active.guiScale, ++sequence, active.scroll, rows, active.text, mergedBadges, active.widgets,
                active.itemCells, active.tooltip, cpuCards, active.rawCpuSerials);
        active = null;
    }

    public static UiSnapshot latest() {
        return latest;
    }

    public static void wirelessTooltip(List<Component> components) {
        if (active != null && (CraftingTreeScenario.isScreen(active.screen)
                || Minecraft.getInstance().screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?>)) {
            active.tooltip.clear();
            active.tooltip.addAll(observed(components, null));
        }
        if (Minecraft.getInstance().screen != null
                && isWirelessScreen(Minecraft.getInstance().screen.getClass().getName())) {
            wirelessTooltip = observed(components, null);
        }
    }

    static boolean isWirelessScreen(String className) {
        return WIRELESS_SCREENS.contains(className);
    }

    static List<UiSnapshot.ObservedText> rowDescription(
            Map<String, List<UiSnapshot.ObservedText>> descriptions,
            List<UiSnapshot.ObservedText> text, String outputId, Rect cell) {
        var recorded = descriptions.get(outputId);
        if (recorded != null && !recorded.isEmpty()) return recorded;
        return text.stream().filter(value -> value.bounds() != null && value.bounds().inside(cell)).toList();
    }

    public static void treeNode(GuiGraphics graphics, AEKey key, int x, int y) {
        if (active != null && CraftingTreeScenario.isScreen(active.screen)) {
            var bounds = transformed(graphics, x, y, x + 16, y + 16);
            active.rows.add(new PendingRow(key.getId().toString(), 0, 0, bounds));
            active.itemCells.add(bounds);
        }
    }

    private static Rect transformed(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        var pose = graphics.pose().last().pose();
        var from = pose.transformPosition(new org.joml.Vector3f(x1, y1, 0));
        var to = pose.transformPosition(new org.joml.Vector3f(x2, y2, 0));
        return new Rect((int) Math.floor(from.x), (int) Math.floor(from.y),
                (int) Math.ceil(to.x - from.x), (int) Math.ceil(to.y - from.y));
    }

    public static List<UiSnapshot.ObservedText> wirelessTooltip() {
        return wirelessTooltip;
    }

    public static void clearWirelessTooltip() {
        wirelessTooltip = List.of();
    }

    static List<UiSnapshot.ObservedText> observed(List<Component> components, Rect bounds) {
        var result = new ArrayList<UiSnapshot.ObservedText>();
        for (var component : components) {
            collect(component, bounds, result, true);
        }
        return List.copyOf(result);
    }

    private static void collect(Component component, Rect bounds, List<UiSnapshot.ObservedText> result,
            boolean includeSiblings) {
        result.add(observed(component, bounds));
        var wrapped = !(component.getContents() instanceof TranslatableContents)
                && com.ctux.ae2craftingtime.mc1201.TtcComponents.translation(component) != null;
        var siblings = component.getSiblings();
        if (includeSiblings || wrapped) {
            for (int i = wrapped ? 2 : 0; i < siblings.size(); i++) {
                collect(siblings.get(i), bounds, result, false);
            }
        }
    }

    private static UiSnapshot.ObservedText observed(Component component, Rect bounds) {
        var translated = com.ctux.ae2craftingtime.mc1201.TtcComponents.translation(component);
        if (translated != null) {
            var style = component.getContents() instanceof TranslatableContents
                    ? component.getStyle() : component.getSiblings().get(1).getStyle().applyTo(component.getStyle());
            var arguments = new ArrayList<String>();
            for (var argument : translated.getArgs()) {
                if (argument instanceof Component nested) {
                    var nestedTranslation = com.ctux.ae2craftingtime.mc1201.TtcComponents.translation(nested);
                    arguments.add(nestedTranslation == null ? nested.getString() : nestedTranslation.getKey());
                } else {
                    arguments.add(String.valueOf(argument));
                }
            }
            return new UiSnapshot.ObservedText(translated.getKey(), component.getString(), arguments, bounds,
                    style.getColor() == null ? null : style.getColor().getValue(), style.isBold());
        }
        return new UiSnapshot.ObservedText("literal", component.getString(), List.of(), bounds);
    }

    private static List<Rect> merge(List<Rect> rectangles) {
        var merged = new ArrayList<Rect>();
        for (var rectangle : rectangles) {
            var match = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).overlaps(rectangle)) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                merged.add(rectangle);
            } else {
                var old = merged.get(match);
                var left = Math.min(old.x(), rectangle.x());
                var top = Math.min(old.y(), rectangle.y());
                var right = Math.max(old.x() + old.width(), rectangle.x() + rectangle.width());
                var bottom = Math.max(old.y() + old.height(), rectangle.y() + rectangle.height());
                merged.set(match, new Rect(left, top, right - left, bottom - top));
            }
        }
        return List.copyOf(merged);
    }

    private record PendingRow(String outputId, long craftAmount, long missingAmount, Rect cell, Object identity,
            long storedAmount, long activeAmount, long pendingAmount) {
        private PendingRow(String outputId, long craftAmount, long missingAmount, Rect cell, Object identity) {
            this(outputId, craftAmount, missingAmount, cell, identity, 0, 0, 0);
        }
        private PendingRow(String outputId, long craftAmount, long missingAmount, Rect cell) {
            this(outputId, craftAmount, missingAmount, cell, null);
        }
    }

    private record PendingCpuCard(int serial, String name, String jobId, long amount, long elapsedNanos,
            boolean selected, Rect bounds, Rect nameArea, Rect infoArea, Rect progressArea) {
    }

    private static final class Frame {
        private final String screen;
        private final String menu;
        private final Rect gui;
        private final int screenWidth;
        private final int screenHeight;
        private final double guiScale;
        private int scroll;
        private final List<PendingRow> rows = new ArrayList<>();
        private final Map<String, List<UiSnapshot.ObservedText>> descriptions = new LinkedHashMap<>();
        private final Map<Object, List<Component>> planDescriptions = new java.util.IdentityHashMap<>();
        private final List<UiSnapshot.ObservedText> text = new ArrayList<>();
        private final List<Rect> badges = new ArrayList<>();
        private final List<UiSnapshot.Widget> widgets = new ArrayList<>();
        private final List<Rect> itemCells = new ArrayList<>();
        private final List<UiSnapshot.ObservedText> tooltip = new ArrayList<>();
        private final List<PendingCpuCard> cpuCards = new ArrayList<>();
        private List<Integer> rawCpuSerials = List.of();

        private Frame(String screen, String menu, Rect gui, int screenWidth, int screenHeight, double guiScale) {
            this.screen = screen;
            this.menu = menu;
            this.gui = gui;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.guiScale = guiScale;
        }
    }

    private UiObservationStore() {
    }
}
