package com.ctux.ae2craftingtime.testdriver;

import java.util.List;

public record UiSnapshot(
        String screen,
        String menu,
        Rect gui,
        int screenWidth,
        int screenHeight,
        double guiScale,
        long frame,
        int scroll,
        List<Row> rows,
        List<ObservedText> text,
        List<Rect> badges,
        List<Widget> widgets,
        List<Rect> itemCells,
        List<ObservedText> tooltip,
        List<CpuCard> cpuCards,
        List<Integer> rawCpuSerials) {
    public UiSnapshot(String screen, String menu, Rect gui, int screenWidth, int screenHeight, double guiScale,
            long frame, int scroll, List<Row> rows, List<ObservedText> text, List<Rect> badges,
            List<Widget> widgets, List<Rect> itemCells, List<ObservedText> tooltip, List<CpuCard> cpuCards) {
        this(screen, menu, gui, screenWidth, screenHeight, guiScale, frame, scroll, rows, text, badges, widgets,
                itemCells, tooltip, cpuCards, List.of());
    }

    public UiSnapshot(String screen, String menu, Rect gui, int screenWidth, int screenHeight, double guiScale,
            long frame, int scroll, List<Row> rows, List<ObservedText> text, List<Rect> badges,
            List<Widget> widgets, List<Rect> itemCells, List<ObservedText> tooltip) {
        this(screen, menu, gui, screenWidth, screenHeight, guiScale, frame, scroll, rows, text, badges, widgets,
                itemCells, tooltip, List.of());
    }

    public UiSnapshot {
        rows = List.copyOf(rows);
        text = List.copyOf(text);
        badges = List.copyOf(badges);
        widgets = List.copyOf(widgets);
        itemCells = List.copyOf(itemCells);
        tooltip = List.copyOf(tooltip);
        cpuCards = List.copyOf(cpuCards);
        rawCpuSerials = List.copyOf(rawCpuSerials);
    }

    public record Row(String outputId, long craftAmount, long missingAmount, Rect cell, List<ObservedText> description,
            long storedAmount, long activeAmount, long pendingAmount) {
        public Row(String outputId, long craftAmount, long missingAmount, Rect cell, List<ObservedText> description) {
            this(outputId, craftAmount, missingAmount, cell, description, 0, 0, 0);
        }
        public Row {
            description = List.copyOf(description);
        }
    }

    public record ObservedText(String key, String rendered, List<String> arguments, Rect bounds, Integer color, boolean bold) {
        public ObservedText(String key, String rendered, List<String> arguments, Rect bounds) {
            this(key, rendered, arguments, bounds, null, false);
        }
        public ObservedText {
            arguments = List.copyOf(arguments);
        }
    }

    static ObservedText matchingRenderedText(java.util.stream.Stream<ObservedText> descriptions,
            String rendered, boolean warningPrefix) {
        return descriptions.filter(line -> line.key().startsWith("text.ae2craftingtime.")
                        && (line.rendered().equals(rendered)
                                || warningPrefix && rendered.equals("⚠ " + line.rendered())))
                .findFirst().orElse(null);
    }

    static ObservedText nativeStatusTitle(String screen, Rect gui, ObservedText text) {
        if (!screen.endsWith("CraftingStatusScreen") || text.bounds() == null
                || text.bounds().y() >= gui.y() + 19 || !text.rendered().startsWith("TTC:")) return null;
        return new ObservedText("native-title", text.rendered(), text.arguments(), text.bounds(), text.color(), text.bold());
    }

    public record Widget(String type, String state, Rect bounds, List<ObservedText> tooltip) {
        public Widget {
            tooltip = List.copyOf(tooltip);
        }
    }

    public record CpuCard(int serial, String name, String jobId, long amount, long elapsedNanos, boolean selected,
            Rect bounds, Rect nameArea, Rect infoArea, Rect progressArea, ObservedText ttc, Rect badge) {
    }
}
