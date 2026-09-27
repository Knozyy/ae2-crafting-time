package com.ctux.ae2craftingtime.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Client-owned values for the in-game options screen and local config file. */
public final class ClientConfig {
    public enum Color {
        FAST(0x55FF55), MIDDLE(0xFFFF55), SLOW(0xFF5555),
        WAITING(0xE0E0E0), DELAYED(0xFF5555), COLLECTING(0xE0E0E0),
        TOTAL(0xE0E0E0), BADGE(0x000000);

        private final int defaultRgb;

        Color(int defaultRgb) { this.defaultRgb = defaultRgb; }
        public int defaultRgb() { return defaultRgb; }
    }

    private static final List<Color> APPEARANCE_COLORS = List.of(Color.FAST, Color.MIDDLE, Color.SLOW,
            Color.DELAYED, Color.COLLECTING, Color.TOTAL, Color.BADGE);

    public static List<Color> appearanceColors() { return APPEARANCE_COLORS; }

    private final FeatureOptions features = new FeatureOptions(OptionFeature.Owner.CLIENT);
    private final EnumMap<Color, Integer> colors = new EnumMap<>(Color.class);
    private int badgeOpacity = 176;
    private int planSort = 2;
    private int statusSort = 2;

    public ClientConfig() { reset(); }

    public FeatureOptions features() { return features; }

    public boolean textShadow(boolean modText, boolean nativeShadow) {
        return modText ? features.enabled(OptionFeature.TEXT_SHADOW) : nativeShadow;
    }

    public boolean badgeBackground() { return features.enabled(OptionFeature.BADGE_BACKGROUND); }

    public static int appearanceRowCount(int featureRows) {
        return featureRows + APPEARANCE_COLORS.size() + 1;
    }

    public static int appearanceRowsPerPage(int screenHeight) {
        return Math.max(2, (screenHeight - 145) / 28);
    }

    public static int appearanceInputIndex(int firstVisibleRow, int featureRows, int inputOffset) {
        return Math.max(firstVisibleRow, featureRows) - featureRows + inputOffset;
    }

    public int color(Color color) { return colors.get(Objects.requireNonNull(color)); }

    public void setColor(Color color, int rgb) {
        Objects.requireNonNull(color);
        if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("RGB must be 0-FFFFFF");
        colors.put(color, rgb);
    }

    public int badgeOpacity() { return badgeOpacity; }

    public void setBadgeOpacity(int opacity) {
        if (opacity < 0 || opacity > 255) throw new IllegalArgumentException("Opacity must be 0-255");
        badgeOpacity = opacity;
    }

    public int planSort() { return planSort; }
    public int statusSort() { return statusSort; }

    public void setPlanSort(int mode) { planSort = validateSort(mode); }
    public void setStatusSort(int mode) { statusSort = validateSort(mode); }

    public void reset() {
        features.reset();
        for (var color : Color.values()) colors.put(color, color.defaultRgb());
        badgeOpacity = 176;
        planSort = 2;
        statusSort = 2;
    }

    public ClientConfig copy() {
        var copy = new ClientConfig();
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT) {
                copy.features.setEnabled(feature, features.enabled(feature));
            }
        }
        copy.colors.putAll(colors);
        copy.badgeOpacity = badgeOpacity;
        copy.planSort = planSort;
        copy.statusSort = statusSort;
        return copy;
    }

    private static int validateSort(int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Sort mode must be 0-2");
        return mode;
    }
}
