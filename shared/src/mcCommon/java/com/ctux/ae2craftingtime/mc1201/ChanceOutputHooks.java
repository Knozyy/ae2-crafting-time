package com.ctux.ae2craftingtime.mc1201;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.Map;

/** An empty result means the provider or its effective recipe cannot be proved. */
public final class ChanceOutputHooks {
    @FunctionalInterface
    public interface Detector {
        Map<AEKey, Integer> detect(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs);
    }

    private static Detector detector = (provider, pattern, inputs) -> Map.of();

    public static void install(Detector installed) {
        detector = installed;
    }

    public static Map<AEKey, Integer> detect(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs) {
        return detector.detect(provider, pattern, inputs);
    }

    private ChanceOutputHooks() { }
}
