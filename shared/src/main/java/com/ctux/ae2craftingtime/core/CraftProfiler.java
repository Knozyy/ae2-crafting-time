package com.ctux.ae2craftingtime.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class CraftProfiler {
    private int maxSamples;
    private double outlierMultiplier;
    private static final Object DEFAULT_SCOPE = new Object();
    private long minimumDelayTicks = 200L;
    private double typicalDurationMultiplier = 2.0;
    private boolean delayedEnabled = true;
    private final Map<Object, Map<ProfileKey, ArrayDeque<PendingCraft>>> pending = new IdentityHashMap<>();
    private final Map<Object, Map<ProfileKey, Long>> lastProgressTicks = new IdentityHashMap<>();
    private final Map<Object, CapacityState> capacities = new IdentityHashMap<>();
    private final Map<Object, WaitingState> waiting = new IdentityHashMap<>();
    private final Map<Object, UUID> jobOwners = new IdentityHashMap<>();
    private final Map<Object, CraftingJobEstimate> jobEstimates = new IdentityHashMap<>();
    private final Map<Object, Set<ProfileKey>> delayedNotified = new IdentityHashMap<>();
    private final Map<Object, Set<ProfileKey>> delayedResolved = new IdentityHashMap<>();
    private final Set<Object> suspended = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> ignoreRemembered = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ProfileKey, CompletionInterval> completionIntervals = new HashMap<>();
    private final Set<ProfileKey> dirtySampleKeys = new HashSet<>();
    private final Map<ProfileKey, Long> retainedTailTicks = new HashMap<>();
    private final Map<ProfileKey, ArrayDeque<CraftSample>> samples = new HashMap<>();
    private final Map<ProfileKey, PersistedOutputStatus> rememberedStatuses = new HashMap<>();
    private final MissingProviderTracker<Object> missingProviders = new MissingProviderTracker<>();
    private final DispatchPowerTracker dispatchPower = new DispatchPowerTracker();
    private final ProviderDispatchTracker providerDispatch = new ProviderDispatchTracker();
    private boolean enabled = true;
    private long newestEventTick = Long.MIN_VALUE;

    public CraftProfiler(int maxSamples) {
        this(maxSamples, 4.0);
    }

    public CraftProfiler(int maxSamples, double outlierMultiplier) {
        if (maxSamples <= 0) {
            throw new IllegalArgumentException("maxSamples must be positive");
        }
        if (!Double.isFinite(outlierMultiplier) || outlierMultiplier < 1.0) {
            throw new IllegalArgumentException("outlierMultiplier must be finite and at least 1");
        }
        this.maxSamples = maxSamples;
        this.outlierMultiplier = outlierMultiplier;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        missingProviders.setEnabled(enabled);
        dispatchPower.setEnabled(enabled);
        providerDispatch.setEnabled(enabled);
        if (!enabled) {
            pending.clear();
            lastProgressTicks.clear();
            capacities.clear();
            waiting.clear();
            completionIntervals.clear();
            dirtySampleKeys.clear();
            retainedTailTicks.clear();
            newestEventTick = Long.MIN_VALUE;
            jobOwners.clear();
            jobEstimates.clear();
            delayedNotified.clear();
            delayedResolved.clear();
            rememberedStatuses.clear();
        }
    }

    public void configure(ServerConfig config) {
        maxSamples = config.maxSamples();
        outlierMultiplier = config.outlierMultiplier();
        minimumDelayTicks = config.minimumNoProgressSeconds() * 20L;
        typicalDurationMultiplier = config.typicalDurationMultiplier();
        delayedEnabled = config.features().enabled(OptionFeature.DELAYED_DETECTION);
        for (var queue : samples.values()) trim(queue);
    }

    public void observeProviders(Object scope, Object pattern, Map<ProfileKey, Long> outputs, boolean hasProvider) {
        missingProviders.observe(scope, pattern, outputs, hasProvider);
    }

    public Set<ProfileKey> missingProviderOutputs(Object scope, Predicate<Object> hasProvider) {
        return missingProviders.missingOutputs(scope, hasProvider);
    }

    public void observeDispatchPower(Object scope, Object pattern, Map<ProfileKey, Long> outputs,
            double required, double extracted, long tick) {
        dispatchPower.observe(scope, pattern, outputs, required, extracted, tick);
    }

    public void observeProviderDispatch(Object scope, Object pattern, Map<ProfileKey, Long> outputs,
            CraftingBlockReason reason, long tick) {
        providerDispatch.observe(scope, pattern, outputs, reason, tick);
    }

    public Map<ProfileKey, CraftingBlockReason> blockReasons(Object scope, long tick, Set<ProfileKey> missing) {
        if (suspended.contains(scope)) return Map.of();
        var reasons = new HashMap<>(providerDispatch.reasons(scope, tick));
        reasons.putAll(dispatchPower.reasons(scope, tick, missing));
        return reasons;
    }

    public void startWaiting(Object scope, Iterable<ProfileKey> keys, long tick) {
        if (!enabled || scope == null || keys == null) {
            return;
        }

        missingProviders.clear(scope);
        suspended.remove(scope);
        ignoreRemembered.remove(scope);
        dispatchPower.clear(scope);
        providerDispatch.clear(scope);
        // A new job starts a fresh delayed-notification episode.
        delayedNotified.remove(scope);
        delayedResolved.remove(scope);
        var waitingKeys = new HashSet<ProfileKey>();
        for (var key : keys) {
            if (key != null) {
                waitingKeys.add(key);
            }
        }
        if (waitingKeys.isEmpty()) {
            waiting.remove(scope);
        } else {
            waiting.put(scope, new WaitingState(tick, waitingKeys));
        }
    }

    public OptionalLong waitingTicks(ProfileKey key, Object scope, long tick) {
        if (suspended.contains(scope)) return OptionalLong.empty();
        var state = waiting.get(scope);
        return key != null && state != null && state.keys.contains(key)
                ? OptionalLong.of(Math.max(0, tick - state.acceptedAtTick))
                : OptionalLong.empty();
    }

    public void start(ProfileKey key, long amount, ProfileUnit unit, long tick) {
        start(key, DEFAULT_SCOPE, amount, unit, tick);
    }

    public void start(ProfileKey key, Object scope, long amount, ProfileUnit unit, long tick) {
        if (!enabled || amount <= 0) {
            return;
        }
        advanceTick(key, tick);
        // Fresh live observations supersede any remembered status for the key.
        rememberedStatuses.remove(key);
        var waitingState = waiting.get(scope);
        if (waitingState != null && waitingState.keys.remove(key) && waitingState.keys.isEmpty()) {
            waiting.remove(scope);
        }
        pending.computeIfAbsent(scope, ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .addLast(new PendingCraft(amount, unit, tick));
        lastProgressTicks.computeIfAbsent(scope, ignored -> new HashMap<>()).putIfAbsent(key, tick);
        completionIntervals.computeIfAbsent(key, ignored -> new CompletionInterval(unit, tick));
    }

    public boolean complete(ProfileKey key, long amount, long tick) {
        return complete(key, DEFAULT_SCOPE, amount, tick);
    }

    public boolean complete(ProfileKey key, Object scope, long amount, long tick) {
        if (!enabled || amount <= 0) {
            return false;
        }

        var jobEstimate = jobEstimates.get(scope);
        if (jobEstimate != null) {
            jobEstimate.complete(key, amount);
        }

        var scopedPending = pending.get(scope);
        var queue = scopedPending == null ? null : scopedPending.get(key);
        if (queue == null) {
            return false;
        }

        advanceTick(key, tick);
        var remaining = amount;
        long consumedTotal = 0;
        while (remaining > 0 && !queue.isEmpty()) {
            var craft = queue.peekFirst();
            var consumed = Math.min(remaining, craft.remainingAmount);
            craft.remainingAmount -= consumed;
            remaining -= consumed;
            consumedTotal = Math.addExact(consumedTotal, consumed);

            if (craft.remainingAmount == 0) {
                queue.removeFirst();
            }
        }

        if (queue.isEmpty()) {
            scopedPending.remove(key);
            removeLastProgress(scope, key);
            if (scopedPending.isEmpty()) {
                pending.remove(scope);
            }
        } else {
            lastProgressTicks.get(scope).put(key, tick);
        }

        recordCompleted(key, consumedTotal, tick);
        rememberedStatuses.remove(key);
        return true;
    }

    /** Completes an output whose CPU scope changed during a chunk reload. */
    public boolean completeUniquePending(ProfileKey key, long amount, long tick) {
        if (key == null || amount <= 0) return false;
        Object onlyScope = null;
        for (var entry : pending.entrySet()) {
            if (!entry.getValue().containsKey(key)) continue;
            if (onlyScope != null) return false;
            onlyScope = entry.getKey();
        }
        if (onlyScope == null) return false;
        return complete(key, onlyScope, amount, tick);
    }

    public boolean flushCompletedSamples() {
        if (dirtySampleKeys.isEmpty()) {
            return false;
        }
        var changed = false;
        for (var key : List.copyOf(dirtySampleKeys)) {
            var interval = completionIntervals.get(key);
            if (interval != null && interval.returnedAmount > 0) {
                finalizeInterval(key, interval);
            }
            changed = true;
        }
        dirtySampleKeys.clear();
        return changed;
    }

    /**
     * Every output the scope still tracks, including delayed notifications
     * awaiting a clear after the final output has returned.
     * Snapshot before {@link #clearPending} so finish and cancel paths can
     * tell clients to drop their highlight plates.
     */
    public Set<ProfileKey> scopedKeys(Object scope) {
        if (scope == null) {
            return Set.of();
        }
        var keys = new HashSet<ProfileKey>();
        var waitingState = waiting.get(scope);
        if (waitingState != null) {
            keys.addAll(waitingState.keys);
        }
        var scopedPending = pending.get(scope);
        if (scopedPending != null) {
            keys.addAll(scopedPending.keySet());
        }
        keys.addAll(delayedNotified.getOrDefault(scope, Set.of()));
        keys.addAll(delayedResolved.getOrDefault(scope, Set.of()));
        return Set.copyOf(keys);
    }

    /** Keep outstanding work while removing only this job's deliberate-pause diagnostics. */
    public boolean setSuspended(Object scope, boolean pause, long tick) {
        if (scope == null || suspended.contains(scope) == pause) return false;
        if (pause) {
            suspended.add(scope);
        } else {
            suspended.remove(scope);
            var progress = lastProgressTicks.get(scope);
            if (progress != null) progress.replaceAll((key, old) -> tick);
            var waitingState = waiting.get(scope);
            if (waitingState != null) waiting.put(scope, new WaitingState(tick, waitingState.keys));
        }
        ignoreRemembered.add(scope);
        missingProviders.clear(scope);
        dispatchPower.clear(scope);
        providerDispatch.clear(scope);
        capacities.remove(scope);
        delayedNotified.remove(scope);
        delayedResolved.remove(scope);
        return true;
    }

    public boolean isSuspended(Object scope) { return suspended.contains(scope); }

    public boolean ignoreRemembered(Object scope) { return ignoreRemembered.contains(scope); }

    public void clearPending(Object scope) {
        suspended.remove(scope);
        ignoreRemembered.remove(scope);
        missingProviders.clear(scope);
        dispatchPower.clear(scope);
        providerDispatch.clear(scope);
        delayedResolved.remove(scope);
        var removed = pending.remove(scope);
        lastProgressTicks.remove(scope);
        capacities.remove(scope);
        waiting.remove(scope);
        jobOwners.remove(scope);
        jobEstimates.remove(scope);
        delayedNotified.remove(scope);
        if (removed == null) {
            return;
        }
        for (var key : removed.keySet()) {
            if (!hasPending(key)) {
                rememberedStatuses.remove(key);
                var interval = completionIntervals.get(key);
                if (interval.returnedAmount == 0) {
                    completionIntervals.remove(key);
                }
            }
        }
    }

    public void updateCapacity(Object scope, int usedParallelSlots, int totalParallelSlots, long tick) {
        if (!enabled || scope == null || totalParallelSlots <= 0 || suspended.contains(scope)) {
            return;
        }
        capacities.put(scope, new CapacityState(
                Math.max(0, Math.min(usedParallelSlots, totalParallelSlots)), totalParallelSlots, tick));
    }

    public Optional<StallDiagnostic> stall(ProfileKey key, Object scope, long tick) {
        if (!delayedEnabled || suspended.contains(scope)) return Optional.empty();
        var scopedPending = pending.get(scope);
        var queue = scopedPending == null ? null : scopedPending.get(key);
        var stats = stats(key);
        if (queue == null || stats.isEmpty()) {
            return Optional.empty();
        }

        var lastProgress = lastProgressTicks.get(scope).get(key);
        var idleTicks = Math.max(0, tick - lastProgress);
        var typicalTicks = stats.get().averageDurationTicks();
        var delayedAfter = Math.max(minimumDelayTicks, (long) Math.ceil(typicalTicks * typicalDurationMultiplier));
        if (idleTicks < delayedAfter) {
            return Optional.empty();
        }

        var capacity = capacities.get(scope);
        var capacityIsFresh = capacity != null && tick - capacity.tick <= 20;
        return Optional.of(new StallDiagnostic(idleTicks, typicalTicks, queue.size(),
                capacityIsFresh ? capacity.usedParallelSlots : 0,
                capacityIsFresh ? capacity.totalParallelSlots : 0));
    }

    public void setJobOwner(Object scope, UUID owner) {
        if (scope == null || !enabled) {
            return;
        }
        if (owner == null) {
            jobOwners.remove(scope);
        } else {
            jobOwners.put(scope, owner);
        }
                // A new job starts a fresh delayed-notification episode.
        delayedNotified.remove(scope);
        delayedResolved.remove(scope);
    }

    public void setJobEstimate(Object scope, CraftingJobEstimate estimate) {
        jobEstimates.put(scope, estimate);
    }

    public void rebindJobEstimate(Object previousScope, Object currentScope) {
        if (previousScope == null || currentScope == null || previousScope == currentScope) {
            return;
        }
        var estimate = jobEstimates.remove(previousScope);
        if (estimate != null) {
            jobEstimates.put(currentScope, estimate);
        }
    }

    public OptionalLong remainingJobSeconds(Object scope,
            BiFunction<ProfileKey, Long, OptionalLong> estimate) {
        if (suspended.contains(scope)) return OptionalLong.empty();
        var jobEstimate = jobEstimates.get(scope);
        return jobEstimate == null ? OptionalLong.empty() : jobEstimate.remainingSeconds(estimate);
    }

    public Optional<UUID> jobOwner(Object scope) {        if (scope == null || !enabled) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobOwners.get(scope));
    }

    public record DelayedEvent(ProfileKey key, StallDiagnostic diagnostic) {
    }

    /**
     * Returns outputs that newly transitioned to DELAYED since the last poll.
     * Each delayed output is reported once per delayed episode; progress that
     * clears the stall makes a later transition eligible again. Keys that
     * leave the delayed set are stashed for {@link #pollResolvedDelayed} so
     * the server can explicitly clear their highlight while the craft runs.
     */
    public List<DelayedEvent> pollNewlyDelayed(Object scope, long tick) {
        if (!enabled || scope == null || suspended.contains(scope)) {
            return List.of();
        }
        var scopedPending = pending.getOrDefault(scope, Map.of());
        var notified = delayedNotified.computeIfAbsent(scope, ignored -> new HashSet<>());
        var currentlyDelayed = new HashMap<ProfileKey, StallDiagnostic>();
        for (var key : scopedPending.keySet()) {
            stall(key, scope, tick).ifPresent(diagnostic -> currentlyDelayed.put(key, diagnostic));
        }
        // Progress that clears the stall ends the episode and re-arms notification.
        // Drop remembered DELAYED for resolved keys so login resync and world
        // save never resurrect a recovered plate; a later transition re-adds it.
        var resolved = new HashSet<>(notified);
        resolved.removeAll(currentlyDelayed.keySet());
        if (!resolved.isEmpty()) {
            delayedResolved.computeIfAbsent(scope, ignored -> new HashSet<>()).addAll(resolved);
            for (var key : resolved) {
                rememberedStatuses.remove(key);
            }
        }
        notified.retainAll(currentlyDelayed.keySet());
        var newly = new ArrayList<DelayedEvent>();
        for (var entry : currentlyDelayed.entrySet()) {
            if (notified.add(entry.getKey())) {
                newly.add(new DelayedEvent(entry.getKey(), entry.getValue()));
                rememberedStatuses.put(entry.getKey(),
                        new PersistedOutputStatus(entry.getKey(), StatusKind.DELAYED,
                                entry.getValue().idleTicks(), entry.getValue().typicalDurationTicks(), tick));
            }
        }
        if (notified.isEmpty()) {
            delayedNotified.remove(scope);
        }
        return List.copyOf(newly);
    }

    /**
     * Drains outputs whose stall cleared since the last
     * {@link #pollNewlyDelayed} poll while the scope still runs. Each key is
     * reported once; a later delayed transition re-arms it. Empty when the
     * scope is unknown or nothing resolved.
     */
    public List<ProfileKey> pollResolvedDelayed(Object scope) {
        if (scope == null) {
            return List.of();
        }
        var resolved = delayedResolved.remove(scope);
        if (resolved == null) {
            return List.of();
        }
        return List.copyOf(resolved);
    }

    /**
     * Whether any scope still reports the key as delayed (not yet resolved).
     * Used to keep an identical output's plate when one CPU/network finishes
     * or recovers while another still needs red.
     */
    public boolean isDelayed(ProfileKey key) {
        if (key == null) {
            return false;
        }
        for (var notified : delayedNotified.values()) {
            if (notified.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public Set<ProfileKey> delayedKeys(Object scope) {
        return Set.copyOf(delayedNotified.getOrDefault(scope, Set.of()));
    }

    /** Current stall evidence, independent of whether chat notification was polled. */
    public Set<ProfileKey> liveDelayedKeys(Object scope, long tick) {
        if (!enabled || scope == null || suspended.contains(scope)) return Set.of();
        var live = new HashSet<ProfileKey>();
        for (var key : pending.getOrDefault(scope, Map.of()).keySet()) {
            if (stall(key, scope, tick).isPresent()) live.add(key);
        }
        return Set.copyOf(live);
    }

    public void rememberStatus(PersistedOutputStatus status) {
        if (status == null) {
            return;
        }
        rememberedStatuses.put(status.key(), status);
    }

    public void rememberBlockReason(ProfileKey key, CraftingBlockReason reason, long tick) {
        var kind = reason == CraftingBlockReason.NO_PROVIDER
                ? StatusKind.NO_PROVIDER
                : reason == CraftingBlockReason.NO_POWER ? StatusKind.NO_POWER : null;
        if (kind != null) {
            rememberStatus(new PersistedOutputStatus(key, kind, 0, 0, tick));
        }
    }

    /**
     * Snapshot remembered statuses plus currently waiting keys for the world
     * save. Live data always wins on display; this is only the fallback shown
     * after a reload until fresh observations arrive.
     */
    public List<PersistedOutputStatus> snapshotStatuses() {
        var snapshot = new HashMap<>(rememberedStatuses);
        for (var state : waiting.values()) {
            for (var key : state.keys) {
                snapshot.putIfAbsent(key,
                        new PersistedOutputStatus(key, StatusKind.WAITING, 0, 0, state.acceptedAtTick));
            }
        }
        return List.copyOf(snapshot.values());
    }

    public void restoreStatuses(List<PersistedOutputStatus> statuses) {
        rememberedStatuses.clear();
        if (statuses == null) {
            return;
        }
        for (var status : statuses) {
            if (status != null) {
                rememberedStatuses.put(status.key(), status);
            }
        }
    }

    /**
     * Remembered DELAYED diagnostic for display when no live pending exists
     * for the key. Live observations always win: any pending craft means the
     * live stall check (or its absence) is authoritative.
     */
    public Optional<StallDiagnostic> rememberedStall(ProfileKey key) {
        if (key == null) {
            return Optional.empty();
        }
        var remembered = rememberedStatuses.get(key);
        if (remembered == null || remembered.kind() != StatusKind.DELAYED || hasPending(key)) {
            return Optional.empty();
        }
        return Optional.of(new StallDiagnostic(remembered.idleTicks(), remembered.typicalDurationTicks(), 0, 0, 0));
    }

    public OptionalLong rememberedWaitingTicks(ProfileKey key, long tick) {
        if (key == null) {
            return OptionalLong.empty();
        }
        var remembered = rememberedStatuses.get(key);
        if (remembered == null || remembered.kind() != StatusKind.WAITING || hasPending(key)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0, tick - remembered.acceptedAtTick()));
    }

    public Map<ProfileKey, CraftingBlockReason> rememberedReasons() {
        return rememberedReasons(null);
    }

    public Map<ProfileKey, CraftingBlockReason> rememberedReasons(Object liveScope) {
        if (suspended.contains(liveScope) || ignoreRemembered.contains(liveScope)) return Map.of();
        var reasons = new HashMap<ProfileKey, CraftingBlockReason>();
        var livePending = liveScope == null ? null : pending.get(liveScope);
        for (var status : rememberedStatuses.values()) {
            if (livePending != null && livePending.containsKey(status.key())) {
                continue;
            }
            if (status.kind() == StatusKind.NO_POWER) {
                reasons.put(status.key(), CraftingBlockReason.NO_POWER);
            } else if (status.kind() == StatusKind.NO_PROVIDER) {
                reasons.put(status.key(), CraftingBlockReason.NO_PROVIDER);
            }
        }
        return reasons;
    }

    public boolean hasPending(ProfileKey key) {
        return key != null && pending.values().stream().anyMatch(scoped -> scoped.containsKey(key));
    }

    /** Includes jobs waiting for their first dispatch; null owner checks every job. */
    public boolean hasActiveOutput(ProfileKey key, UUID owner) {
        if (key == null) return false;
        for (var scope : pending.keySet()) {
            if ((owner == null || owner.equals(jobOwners.get(scope))) && pending.get(scope).containsKey(key))
                return true;
        }
        for (var entry : waiting.entrySet()) {
            if ((owner == null || owner.equals(jobOwners.get(entry.getKey()))) && entry.getValue().keys.contains(key))
                return true;
        }
        return false;
    }

    public Optional<ProfileStats> stats(ProfileKey key) {
        var queue = samples.get(key);
        if (queue == null) {
            return Optional.empty();
        }

        long durationTotal = 0;
        long lastDuration = 0;
        var filtered = filteredSamples(queue, outlierMultiplier);
        var sampleDurationTicks = new ArrayList<Long>(queue.size());
        var sampleAmounts = new ArrayList<Long>(queue.size());
        long weightedDurationTotal = 0;
        long weightedAmountTotal = 0;
        long weight = 1;
        ProfileUnit unit = null;

        for (var sample : queue) {
            durationTotal += sample.durationTicks;
            lastDuration = sample.durationTicks;
            sampleDurationTicks.add(sample.durationTicks);
            sampleAmounts.add(sample.amount);
            unit = sample.unit;
        }
        for (var sample : filtered) {
            weightedDurationTotal += sample.durationTicks * weight;
            weightedAmountTotal += sample.amount * weight;
            weight++;
        }

        var averageDuration = (double) durationTotal / queue.size();
        var amountPerTick = (double) weightedAmountTotal / weightedDurationTotal;
        return Optional.of(new ProfileStats(
                queue.size(),
                averageDuration,
                amountPerTick,
                amountPerTick * 20.0,
                lastDuration,
                unit,
                queue.size() >= 3 && filtered.size() == queue.size(),
                filtered.size(),
                outlierMultiplier,
                sampleDurationTicks,
                sampleAmounts));
    }

    public boolean clearSamples(ProfileKey key) {
        var cleared = samples.remove(key) != null;
        completionIntervals.remove(key);
        dirtySampleKeys.remove(key);
        retainedTailTicks.remove(key);
        rememberedStatuses.remove(key);
        pending.values().forEach(scoped -> scoped.remove(key));
        pending.values().removeIf(Map::isEmpty);
        lastProgressTicks.values().forEach(scoped -> scoped.remove(key));
        lastProgressTicks.values().removeIf(Map::isEmpty);
        delayedNotified.values().forEach(notified -> notified.remove(key));
        delayedNotified.values().removeIf(Set::isEmpty);
        return cleared;
    }

    private static List<CraftSample> filteredSamples(ArrayDeque<CraftSample> queue, double outlierMultiplier) {
        if (queue.size() < 5) {
            return List.copyOf(queue);
        }

        var samples = List.copyOf(queue);
        var durationsPerUnit = new ArrayList<Double>(samples.size());
        for (var sample : samples) {
            durationsPerUnit.add((double) sample.durationTicks / sample.amount);
        }
        durationsPerUnit.sort(Double::compare);
        var median = durationsPerUnit.get(durationsPerUnit.size() / 2);
        var filtered = new ArrayList<CraftSample>();
        for (var sample : samples) {
            var durationPerUnit = (double) sample.durationTicks / sample.amount;
            if (durationPerUnit >= median / outlierMultiplier && durationPerUnit <= median * outlierMultiplier) {
                filtered.add(sample);
            }
        }
        return filtered;
    }

    private void addSample(ProfileKey key, CraftSample sample) {
        var queue = samples.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        queue.addLast(sample);
        trim(queue);
    }

    private void trim(ArrayDeque<CraftSample> queue) {
        while (queue.size() > maxSamples) queue.removeFirst();
    }

    public List<PersistedOutputSamples> snapshotSamples() {
        var snapshot = new ArrayList<PersistedOutputSamples>();
        for (var entry : samples.entrySet()) {
            var persisted = new ArrayList<PersistedCraftSample>();
            ProfileUnit unit = null;
            for (var sample : entry.getValue()) {
                unit = sample.unit;
                persisted.add(new PersistedCraftSample(sample.amount, sample.durationTicks));
            }
            snapshot.add(new PersistedOutputSamples(entry.getKey(), unit, persisted));
        }
        return snapshot;
    }

    public void loadSamples(List<PersistedOutputSamples> persisted) {
        suspended.clear();
        ignoreRemembered.clear();
        samples.clear();
        rememberedStatuses.clear();
        missingProviders.clear();
        dispatchPower.clear();
        providerDispatch.clear();
        pending.clear();
        lastProgressTicks.clear();
        capacities.clear();
        waiting.clear();
        completionIntervals.clear();
        dirtySampleKeys.clear();
        retainedTailTicks.clear();
        newestEventTick = Long.MIN_VALUE;
        jobOwners.clear();
        jobEstimates.clear();
        delayedNotified.clear();
        delayedResolved.clear();
        for (var output : persisted) {
            if (output == null || output.key() == null || output.unit() == null) {
                continue;
            }
            output = ProfileAmounts.migrate(output);
            for (var sample : output.samples()) {
                if (sample.amount() > 0 && sample.durationTicks() > 0) {
                    addSample(output.key(), new CraftSample(sample.amount(), output.unit(), sample.durationTicks()));
                }
            }
        }
    }

    private static final class PendingCraft {
        private final ProfileUnit unit;
        private final long startedTick;
        private long remainingAmount;

        private PendingCraft(long amount, ProfileUnit unit, long startedTick) {
            this.remainingAmount = amount;
            this.unit = unit;
            this.startedTick = startedTick;
        }
    }

    private void removeLastProgress(Object scope, ProfileKey key) {
        var scoped = lastProgressTicks.get(scope);
        scoped.remove(key);
        if (scoped.isEmpty()) {
            lastProgressTicks.remove(scope);
        }
    }

    private void advanceTick(ProfileKey key, long tick) {
        if (tick > newestEventTick) {
            retainedTailTicks.clear();
            newestEventTick = tick;
        }
        var interval = completionIntervals.get(key);
        if (interval == null) {
            return;
        }
        if (tick < interval.anchorTick || interval.returnTick != null && tick < interval.returnTick) {
            completionIntervals.put(key, new CompletionInterval(interval.unit, tick));
            dirtySampleKeys.remove(key);
            retainedTailTicks.remove(key);
            return;
        }
        if (interval.returnTick != null && tick > interval.returnTick) {
            finalizeInterval(key, interval);
            dirtySampleKeys.add(key);
        }
        var tailTick = retainedTailTicks.get(key);
        if (tailTick != null && tick > tailTick) {
            retainedTailTicks.remove(key);
        }
    }

    private void recordCompleted(ProfileKey key, long amount, long tick) {
        var tailTick = retainedTailTicks.get(key);
        if (tailTick != null) {
            var queue = samples.get(key);
            var tail = queue.removeLast();
            try {
                queue.addLast(new CraftSample(Math.addExact(tail.amount, amount), tail.unit, tail.durationTicks));
            } catch (ArithmeticException ignored) {
                if (queue.isEmpty()) {
                    samples.remove(key);
                }
                retainedTailTicks.remove(key);
                if (hasPending(key)) {
                    completionIntervals.put(key, new CompletionInterval(tail.unit, tick));
                } else {
                    completionIntervals.remove(key);
                }
            }
            dirtySampleKeys.add(key);
            if (!hasPending(key)) {
                completionIntervals.remove(key);
            }
            return;
        }
        var interval = completionIntervals.get(key);
        try {
            interval.returnedAmount = Math.addExact(interval.returnedAmount, amount);
            interval.returnTick = tick;
            dirtySampleKeys.add(key);
        } catch (ArithmeticException ignored) {
            if (hasPending(key)) {
                completionIntervals.put(key, new CompletionInterval(interval.unit, tick));
            } else {
                completionIntervals.remove(key);
            }
            dirtySampleKeys.add(key);
        }
    }

    private void finalizeInterval(ProfileKey key, CompletionInterval interval) {
        var returnTick = interval.returnTick;
        addSample(key, new CraftSample(interval.returnedAmount, interval.unit,
                Math.max(1, returnTick - interval.anchorTick)));
        retainedTailTicks.put(key, returnTick);
        if (hasPending(key)) {
            interval.anchorTick = returnTick;
            interval.returnTick = null;
            interval.returnedAmount = 0;
        } else {
            completionIntervals.remove(key);
        }
    }

    private static final class CompletionInterval {
        private final ProfileUnit unit;
        private long anchorTick;
        private Long returnTick;
        private long returnedAmount;

        private CompletionInterval(ProfileUnit unit, long startedTick) {
            this.unit = unit;
            this.anchorTick = startedTick;
        }
    }

    private record CraftSample(long amount, ProfileUnit unit, long durationTicks) {
    }

    private record CapacityState(int usedParallelSlots, int totalParallelSlots, long tick) {
    }

    private record WaitingState(long acceptedAtTick, Set<ProfileKey> keys) {
    }
}
