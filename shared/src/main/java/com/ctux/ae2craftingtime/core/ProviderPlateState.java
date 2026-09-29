package com.ctux.ae2craftingtime.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runtime contributions and last delivered plates; no chat or persisted status participates. */
public final class ProviderPlateState<P, K> {
    public record Recipient(UUID owner, ProfileKey key, String dimension) {
    }

    public record Contribution<P, K>(List<P> positions, K displayKey) {
        public Contribution {
            positions = positions == null ? List.of() : List.copyOf(positions);
        }
    }

    public record Change<P, K>(Recipient recipient, Contribution<P, K> plate) {
    }

    private final int maxPositions;
    private final Map<Object, Map<Recipient, Contribution<P, K>>> scopes = new IdentityHashMap<>();
    private final Map<Recipient, Contribution<P, K>> sent = new HashMap<>();

    public ProviderPlateState(int maxPositions) {
        this.maxPositions = maxPositions;
    }

    public void update(Object scope, Map<Recipient, Contribution<P, K>> current) {
        if (scope == null) return;
        if (current == null || current.isEmpty()) scopes.remove(scope);
        else scopes.put(scope, Map.copyOf(current));
    }

    public void clearKey(Object scope, ProfileKey key) {
        var current = scopes.get(scope);
        if (current == null) return;
        var kept = new HashMap<>(current);
        kept.keySet().removeIf(recipient -> recipient.key().equals(key));
        update(scope, kept);
    }

    public void forgetOwner(UUID owner) {
        sent.keySet().removeIf(recipient -> recipient.owner().equals(owner));
    }

    public List<Change<P, K>> pending() {
        var union = new HashMap<Recipient, Contribution<P, K>>();
        for (var current : scopes.values()) {
            current.forEach((recipient, contribution) -> union.merge(recipient, contribution, (left, right) -> {
                var positions = new LinkedHashSet<>(left.positions());
                positions.addAll(right.positions());
                return new Contribution<>(positions.stream().limit(maxPositions).toList(),
                        left.displayKey() != null ? left.displayKey() : right.displayKey());
            }));
        }
        var changes = new ArrayList<Change<P, K>>();
        sent.keySet().stream().filter(recipient -> !union.containsKey(recipient))
                .forEach(recipient -> changes.add(new Change<>(recipient, null)));
        union.forEach((recipient, plate) -> {
            if (!plate.equals(sent.get(recipient))) changes.add(new Change<>(recipient, plate));
        });
        return List.copyOf(changes);
    }

    /** Call only after a packet was delivered to the online owner. */
    public void delivered(Change<P, K> change) {
        if (change.plate() == null) sent.remove(change.recipient());
        else sent.put(change.recipient(), change.plate());
    }

    public void clearAll() {
        scopes.clear();
        sent.clear();
    }
}
