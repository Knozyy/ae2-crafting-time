package com.ctux.ae2craftingtime.mc1201;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import com.ctux.ae2craftingtime.core.ProviderDispatchTracker.Evaluation;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Iterator;
import java.util.Map;

public final class ProviderDispatchObserver {
    private final String networkId;
    private final IGrid grid;
    private final Object scope;
    private final IPatternDetails pattern;
    private final long tick;
    private final Map<ProfileKey, Long> outputs;
    private final Evaluation evaluation = new Evaluation();
    private boolean presenceObserved;
    private boolean completed;

    public ProviderDispatchObserver(String networkId, IGrid grid, Object scope, IPatternDetails pattern, long tick) {
        this.networkId = networkId;
        this.grid = grid;
        this.scope = scope;
        this.pattern = pattern;
        this.tick = tick;
        outputs = ProfilerBridge.patternOutputs(networkId, pattern);
        ProviderStartTracker.noteDispatch(scope, pattern, outputs);
    }

    public String networkId() { return networkId; }

    public void power(double required, double extracted) {
        ProfilerBridge.observeDispatchPower(scope, pattern, outputs, required, extracted, tick);
    }

    public Iterator<ICraftingProvider> iterator(Iterable<ICraftingProvider> providers) {
        var delegate = providers.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                var hasNext = delegate.hasNext();
                if (hasNext) {
                    observePresence(true);
                } else {
                    observePresence(false);
                    evaluation.exhausted();
                    complete(evaluation.result());
                }
                return hasNext;
            }

            @Override
            public ICraftingProvider next() {
                var provider = delegate.next();
                observePresence(true);
                evaluation.candidate();
                ProviderStartTracker.noteCandidate(grid, scope, outputs.keySet(), provider);
                return provider;
            }

            @Override
            public void remove() {
                delegate.remove();
            }
        };
    }

    public boolean busy(ICraftingProvider provider) {
        var busy = provider.isBusy();
        evaluation.busy(busy);
        return busy;
    }

    public boolean push(ICraftingProvider provider, IPatternDetails dispatchedPattern, KeyCounter[] input,
            Operation<Boolean> original) {
        try (var context = ProviderDispatchContext.begin(provider)) {
            var accepted = original.call(provider, dispatchedPattern, input);
            evaluation.attempt(context.finish(accepted));
            if (accepted && ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.CHANCE_OUTPUT_DETECTION)) {
                ProfilerBridge.observeChanceOutput(networkId, scope, dispatchedPattern,
                        ChanceOutputHooks.detect(provider, dispatchedPattern, input));
            }
            if (evaluation.succeeded()) {
                complete(null);
            }
            return accepted;
        }
    }

    public void finish() {
        complete(null);
    }

    private void observePresence(boolean hasProvider) {
        if (!presenceObserved) {
            presenceObserved = true;
            ProfilerBridge.observeProviders(scope, pattern, outputs, hasProvider);
        }
    }

    private void complete(com.ctux.ae2craftingtime.core.CraftingBlockReason reason) {
        if (completed) {
            return;
        }
        completed = true;
        ProfilerBridge.observeProviderDispatch(scope, pattern, outputs, reason, tick);
    }
}
