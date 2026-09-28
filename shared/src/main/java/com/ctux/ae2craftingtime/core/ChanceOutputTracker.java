package com.ctux.ae2craftingtime.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Live, CPU-scoped evidence. Unknown or mixed producers cannot become a confirmed warning. */
public final class ChanceOutputTracker {
    private final Map<Object, Map<ProfileKey, Integer>> evidence = new IdentityHashMap<>();
    private final Map<Object, Set<ProfileKey>> uniquePlanOutputs = new IdentityHashMap<>();

    public void plan(Object scope, Iterable<Set<ProfileKey>> patternOutputs) {
        clear(scope);
        if (scope == null || patternOutputs == null) return;
        var seen = new HashSet<ProfileKey>();
        var repeated = new HashSet<ProfileKey>();
        for (var outputs : patternOutputs) {
            if (outputs == null) continue;
            for (var output : outputs) {
                if (output != null && !seen.add(output)) repeated.add(output);
            }
        }
        seen.removeAll(repeated);
        uniquePlanOutputs.put(scope, seen);
    }

    public void observe(Object scope, Set<ProfileKey> outputs, Map<ProfileKey, Integer> verifiedChance) {
        if (scope == null || outputs == null || outputs.isEmpty()) return;
        var scoped = evidence.computeIfAbsent(scope, ignored -> new HashMap<>());
        for (var output : outputs) {
            if (output == null) continue;
            var chance = verifiedChance == null ? null : verifiedChance.get(output);
            var value = chance != null && chance > 0 && chance < 10000 ? chance : 0;
            scoped.merge(output, value, (previous, current) -> previous.equals(current) ? current : 0);
        }
    }

    public OptionalInt chance(Object scope, ProfileKey output) {
        var scoped = evidence.get(scope);
        var value = scoped == null ? null : scoped.get(output);
        var unique = uniquePlanOutputs.get(scope);
        return value == null || value == 0 || unique == null || !unique.contains(output)
                ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public void clear(Object scope) {
        evidence.remove(scope);
        uniquePlanOutputs.remove(scope);
    }

    public void clearAll() {
        evidence.clear();
        uniquePlanOutputs.clear();
    }
}
