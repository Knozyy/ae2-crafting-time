package com.ctux.ae2craftingtime.core;

/** Server-owned values; updates must be validated again on the logical server. */
public final class ServerConfig {
    private final FeatureOptions features = new FeatureOptions(OptionFeature.Owner.SERVER);
    private int maxSamples = 10;
    private double outlierMultiplier = 4.0;
    private int minimumNoProgressSeconds = 10;
    private double typicalDurationMultiplier = 2.0;

    public FeatureOptions features() { return features; }
    public int maxSamples() { return maxSamples; }
    public double outlierMultiplier() { return outlierMultiplier; }
    public int minimumNoProgressSeconds() { return minimumNoProgressSeconds; }
    public double typicalDurationMultiplier() { return typicalDurationMultiplier; }

    public void setMaxSamples(int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("Samples must be 1-100");
        maxSamples = value;
    }

    public void setOutlierMultiplier(double value) {
        outlierMultiplier = bounded(value, 1.0, 1000.0);
    }

    public void setMinimumNoProgressSeconds(int value) {
        if (value < 1 || value > 3600) throw new IllegalArgumentException("Delay must be 1-3600 seconds");
        minimumNoProgressSeconds = value;
    }

    public void setTypicalDurationMultiplier(double value) {
        typicalDurationMultiplier = bounded(value, 1.0, 1000.0);
    }

    public void reset() {
        features.reset();
        maxSamples = 10;
        outlierMultiplier = 4.0;
        minimumNoProgressSeconds = 10;
        typicalDurationMultiplier = 2.0;
    }

    public ServerConfig copy() {
        var copy = new ServerConfig();
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER) {
                copy.features.setEnabled(feature, features.enabled(feature));
            }
        }
        copy.maxSamples = maxSamples;
        copy.outlierMultiplier = outlierMultiplier;
        copy.minimumNoProgressSeconds = minimumNoProgressSeconds;
        copy.typicalDurationMultiplier = typicalDurationMultiplier;
        return copy;
    }

    private static double bounded(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Multiplier must be finite and in range");
        }
        return value;
    }
}
