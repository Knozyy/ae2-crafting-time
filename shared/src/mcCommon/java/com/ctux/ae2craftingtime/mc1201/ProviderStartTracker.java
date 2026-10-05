package com.ctux.ae2craftingtime.mc1201;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.core.DisplayKeySelection;
import appeng.me.InWorldGridNode;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.ProfileKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Server-side only. Remembers which patterns each crafting CPU dispatched per
 * output so a later DELAYED warning can resolve the providers that ran the
 * craft. Positions resolve at notify time through the live grid, never from
 * stale dispatch data.
 */
public final class ProviderStartTracker {
    private static final Map<Object, Map<ProfileKey, Set<IPatternDetails>>> PATTERNS = new IdentityHashMap<>();
    private static final Map<Object, Map<ProfileKey, Set<BlockPos>>> CANDIDATES = new IdentityHashMap<>();

    private static final com.ctux.ae2craftingtime.core.ProviderPositionIndex<IGrid, ICraftingProvider, BlockPos> POSITIONS =
            new com.ctux.ae2craftingtime.core.ProviderPositionIndex<>();

    public static void endTick() { POSITIONS.clear(); }

    public static void noteCandidate(IGrid grid, Object scope, String networkId, IPatternDetails pattern,
            ICraftingProvider provider) {
        if (grid == null || scope == null || pattern == null || provider == null) return;
        if (!ProfilerBridge.trackingEnabled(scope)) return;
        var position = locate(grid, provider);
        if (position.isEmpty()) return;
        var scoped = CANDIDATES.computeIfAbsent(scope, ignored -> new HashMap<>());
        for (var output : pattern.getOutputs()) {
            if (output != null && output.what() != null) {
                var key = new ProfileKey(networkId, output.what().getId().toString());
                var positions = scoped.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
                if (positions.size() < PacketLimits.MAX_HIGHLIGHT_POSITIONS) positions.add(position.get());
            }
        }
    }

    public static void noteDispatch(Object scope, IPatternDetails pattern, Map<ProfileKey, Long> outputs) {
        if (scope == null || pattern == null || outputs == null || outputs.isEmpty()) {
            return;
        }
        var scoped = PATTERNS.computeIfAbsent(scope, ignored -> new HashMap<>());
        for (var entry : outputs.entrySet()) {
            if (entry.getKey() != null && entry.getValue() > 0) {
                scoped.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).add(pattern);
            }
        }
    }

    public static void clear(Object scope) {
        if (scope != null) {
            PATTERNS.remove(scope);
            CANDIDATES.remove(scope);
        }
    }

    public static void clearAll() {
        POSITIONS.clear();
        PATTERNS.clear();
        CANDIDATES.clear();
    }

    /** Selects a typed output from retained live patterns without guessing from its id. */
    public static DisplayKeySelection<AEKey> displayKey(Object scope, ProfileKey key) {
        if (scope == null || key == null) {
            return DisplayKeySelection.from(List.of());
        }
        var scoped = PATTERNS.get(scope);
        if (scoped == null) {
            return DisplayKeySelection.from(List.of());
        }
        var candidates = new ArrayList<AEKey>();
        for (var pattern : scoped.getOrDefault(key, Set.of())) {
            if (pattern == null) {
                continue;
            }
            for (var output : pattern.getOutputs()) {
                if (output != null && output.what() != null
                        && key.outputId().equals(output.what().getId().toString())) {
                    candidates.add(output.what());
                }
            }
        }
        return DisplayKeySelection.from(candidates);
    }

    /**
     * Resolves distinct world positions of providers currently offering the
     * output's dispatched patterns, capped for packets. Empty when nothing is
     * locatable.
     */
    public static List<BlockPos> positions(IGrid grid, Object scope, ProfileKey key) {
        if (grid == null || scope == null || key == null) {
            return List.of();
        }
        var scoped = PATTERNS.get(scope);
        if (scoped == null) {
            return candidatePositions(scope, key);
        }
        var patterns = scoped.getOrDefault(key, Set.of());
        if (patterns.isEmpty()) {
            return candidatePositions(scope, key);
        }
        CraftingService crafting;
        try {
            crafting = (CraftingService) grid.getCraftingService();
        } catch (Exception ignored) {
            return candidatePositions(scope, key);
        }
        var positions = new ArrayList<BlockPos>();
        for (var pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            Iterable<ICraftingProvider> providers;
            try {
                providers = crafting.getProviders(pattern);
            } catch (Exception ignored) {
                continue;
            }
            for (var provider : providers) {
                if (provider == null) {
                    continue;
                }
                locate(grid, provider).ifPresent(pos -> {
                    if (!positions.contains(pos)) {
                        positions.add(pos);
                    }
                });
                if (positions.size() >= PacketLimits.MAX_HIGHLIGHT_POSITIONS) {
                    return List.copyOf(positions);
                }
            }
        }
        return positions.isEmpty() ? candidatePositions(scope, key) : List.copyOf(positions);
    }

    private static List<BlockPos> candidatePositions(Object scope, ProfileKey key) {
        return List.copyOf(CANDIDATES.getOrDefault(scope, Map.of()).getOrDefault(key, Set.of()));
    }

    private static Optional<BlockPos> locate(IGrid grid, ICraftingProvider provider) {
        return Optional.ofNullable(POSITIONS.find(grid, provider, () -> {
            var positions = new IdentityHashMap<ICraftingProvider, BlockPos>();
            try {
                for (var node : grid.getNodes()) {
                    try {
                        if (node instanceof InWorldGridNode inWorld) {
                            var service = node.getService(ICraftingProvider.class);
                            if (service != null) positions.putIfAbsent(service, inWorld.getLocation());
                        }
                    } catch (Exception ignored) {
                        // One unreadable node must not hide the rest.
                    }
                }
            } catch (Exception ignored) {
                // An unavailable grid yields the existing fallback positions.
            }
            return positions;
        }));
    }

    private ProviderStartTracker() {
    }
}
