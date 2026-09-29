package com.ctux.ae2craftingtime.core;

public final class ProviderBeamHeight {
    public static int top(int providerY, int upperBuildBoundary, int renderDistanceChunks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max((long) upperBuildBoundary,
                (long) providerY + 1 + Math.max(1, renderDistanceChunks) * 16L));
    }

    private ProviderBeamHeight() {
    }
}
