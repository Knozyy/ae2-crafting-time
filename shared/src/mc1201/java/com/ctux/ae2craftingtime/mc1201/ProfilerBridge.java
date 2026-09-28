package com.ctux.ae2craftingtime.mc1201;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.core.CraftProfiler;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.CraftingJobEstimate;
import com.ctux.ae2craftingtime.core.PersistedOutputStatus;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.StallDiagnostic;
import com.ctux.ae2craftingtime.core.StatsEntry;
import com.ctux.ae2craftingtime.core.StatusKind;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.TtcAccuracyStats;
import com.ctux.ae2craftingtime.core.TtcAccuracyTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class ProfilerBridge {
    private static CraftProfiler PROFILER = new CraftProfiler(Ae2CraftingTimeConfig.MAX_SAMPLES.get(),
            Ae2CraftingTimeConfig.OUTLIER_MULTIPLIER.get());
    private static final com.ctux.ae2craftingtime.core.ChanceOutputTracker CHANCE = new com.ctux.ae2craftingtime.core.ChanceOutputTracker();
    private static TtcAccuracyTracker ACCURACY = new TtcAccuracyTracker(Ae2CraftingTimeConfig.MAX_SAMPLES.get());
    private static final Map<ProfileKey, String> DISPLAY_NAMES = new ConcurrentHashMap<>();
    private static Ae2CraftingTimeSavedData savedData;

    public static void observeProviders(String networkId, Object scope, IPatternDetails pattern,
            boolean hasProvider) {
        isEnabled();
        var outputs = new HashMap<ProfileKey, Long>();
        for (var output : pattern.getOutputs()) {
            outputs.merge(key(networkId, output.what()), output.amount(), Long::sum);
        }
        if (isEnabled()) {
            ProviderStartTracker.noteDispatch(scope, pattern, outputs);
        }
        if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NO_PROVIDER_DETECTION))
            PROFILER.observeProviders(scope, pattern, outputs, hasProvider);
    }

    public static void observeChanceOutput(String networkId, Object scope, IPatternDetails pattern,
            Map<AEKey, Integer> verifiedChance) {
        if (!isEnabled() || scope == null || pattern == null) return;
        var outputs = new HashSet<ProfileKey>();
        var chances = new HashMap<ProfileKey, Integer>();
        for (var output : pattern.getOutputs()) {
            var key = key(networkId, output.what());
            outputs.add(key);
            var chance = verifiedChance.get(output.what());
            if (chance != null) chances.put(key, chance);
        }
        CHANCE.observe(scope, outputs, chances);
    }

    public static void clearChanceEvidence() { CHANCE.clearAll(); }

    public static java.util.OptionalInt chanceOutput(Object scope, ProfileKey key) {
        return scope == null || !isEnabled() || !ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.CHANCE_OUTPUT_DETECTION)
                ? java.util.OptionalInt.empty() : CHANCE.chance(scope, key);
    }
    public static void observeDispatchPower(String networkId, Object scope, IPatternDetails pattern,
            double required, double extracted, long tick) {
        isEnabled();
        var outputs = new HashMap<ProfileKey, Long>();
        for (var output : pattern.getOutputs()) {
            outputs.merge(key(networkId, output.what()), output.amount(), Long::sum);
        }
        if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NO_POWER_DETECTION))
            PROFILER.observeDispatchPower(scope, pattern, outputs, required, extracted, tick);
    }

    public static void observeProviderDispatch(String networkId, Object scope, IPatternDetails pattern,
            CraftingBlockReason reason, long tick) {
        isEnabled();
        var outputs = new HashMap<ProfileKey, Long>();
        for (var output : pattern.getOutputs()) {
            outputs.merge(key(networkId, output.what()), output.amount(), Long::sum);
        }
        if (reason == null || reasonEnabled(reason)) PROFILER.observeProviderDispatch(scope, pattern, outputs, reason, tick);
    }

    public static java.util.Map<ProfileKey, com.ctux.ae2craftingtime.core.CraftingBlockReason> blockReasons(
            Object scope, IGrid grid, long tick) {
        if (grid == null) {
            return java.util.Map.of();
        }
        var live = PROFILER.blockReasons(scope, tick, missingProviders(scope, grid));
        live.entrySet().removeIf(entry -> !reasonEnabled(entry.getValue()));
        for (var entry : live.entrySet()) {
            PROFILER.rememberBlockReason(entry.getKey(), entry.getValue(), tick);
        }
        var merged = new java.util.HashMap<>(live);
        PROFILER.rememberedReasons(scope).forEach((key, reason) -> {
            if (reasonEnabled(reason)) merged.putIfAbsent(key, reason);
        });
        return merged;
    }

    public static Set<ProfileKey> missingProviders(Object scope, IGrid grid) {
        isEnabled();
        return grid == null || !ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NO_PROVIDER_DETECTION)
                ? Set.of() : PROFILER.missingProviderOutputs(scope,
                pattern -> ((CraftingService) grid.getCraftingService())
                        .getProviders((IPatternDetails) pattern).iterator().hasNext());
    }

    public static void start(String networkId, GenericStack output, long tick) {
        if (output == null || !isEnabled()) {
            return;
        }
        start(networkId, output.what(), output.amount(), tick);
    }

    public static void start(String networkId, AEKey what, long amount, long tick) {
        start(networkId, ProfilerBridge.class, what, amount, tick);
    }

    public static void start(String networkId, Object scope, AEKey what, long amount, long tick) {
        if (what == null || amount <= 0 || !isEnabled() || !ServerOptionsRuntime.scopeEnabled(scope)
                || !ServerOptionsRuntime.keyEnabled(what)) {
            return;
        }
        var profileKey = key(networkId, what);
        try {
            DISPLAY_NAMES.put(profileKey, what.getDisplayName().getString());
        } catch (Exception ignored) {
            // Display name is best-effort; output id remains the fallback.
        }
        PROFILER.start(profileKey, scope, normalizeAmount(what, amount), unit(what), tick);
    }

    public static void complete(String networkId, AEKey what, long amount, long tick) {
        complete(networkId, ProfilerBridge.class, what, amount, tick);
    }

    public static void complete(String networkId, Object scope, AEKey what, long amount, long tick) {
        complete(networkId, scope, what, amount, tick, null);
    }

    public static void complete(String networkId, Object scope, AEKey what, long amount, long tick,
            net.minecraft.server.MinecraftServer server) {
        if (what == null || !isEnabled()) {
            return;
        }
        if (!ServerOptionsRuntime.scopeEnabled(scope)) {
            discardDisabledScope(scope, tick, server);
            return;
        }
        if (!ServerOptionsRuntime.keyEnabled(what)) {
            return;
        }
        var profileKey = key(networkId, what);
        var normalizedAmount = normalizeAmount(what, amount);
        if (!PROFILER.complete(profileKey, scope, normalizedAmount, tick)) {
            PROFILER.completeUniquePending(profileKey, normalizedAmount, tick);
        }
        // A reloaded CPU can accept its final output without invoking finishJob.
        // The last completed output must still clear its persistent plate.
        if (server != null && !PROFILER.hasPending(profileKey)) {
            ProviderLocateRecords.startFor(profileKey).ifPresent(start -> {
                clearHighlights(server, start.owner(), Set.of(profileKey));
                ProviderLocateRecords.removeStarts(Set.of(profileKey));
                ProviderLocateRecords.removeRecordsForKeys(Set.of(profileKey), start.owner());
                persistProviderState();
            });
        }
    }

    public static boolean flushCompletedSamples() {
        if (!isEnabled() || !PROFILER.flushCompletedSamples()) {
            return false;
        }
        if (savedData != null) {
            if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.SAVE_HISTORY))
                savedData.replaceFrom(PROFILER.snapshotSamples());
            persistStatuses();
        }
        return true;
    }

    public static void startJob(String networkId, Object scope, ICraftingPlan plan, long tick, long nanoTime) {
        startJob(networkId, scope, plan, tick, nanoTime, null);
    }

    public static void startJob(String networkId, Object scope, ICraftingPlan plan, long tick, long nanoTime,
            UUID owner) {
        if (plan == null || plan.finalOutput() == null || !isEnabled() || !ServerOptionsRuntime.scopeEnabled(scope)) {
            return;
        }

        var craftedAmounts = new KeyCounter();
        craftedAmounts.addAll(plan.emittedItems());
        for (var entry : plan.patternTimes().entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                craftedAmounts.add(output.what(), output.amount() * entry.getValue());
            }
        }

        int knownRows = 0;
        int totalRows = 0;
        var waitingKeys = new HashSet<ProfileKey>();
        var remainingAmounts = new HashMap<ProfileKey, Long>();
        for (var crafted : craftedAmounts) {
            if (crafted.getLongValue() <= 0) {
                continue;
            }
            totalRows++;
            var key = key(networkId, crafted.getKey());
            waitingKeys.add(key);
            remainingAmounts.put(key, normalizeAmount(crafted.getKey(), crafted.getLongValue()));
            var stats = PROFILER.stats(key);
            var estimate = stats.isEmpty() ? java.util.OptionalLong.empty()
                    : TimeEstimate.seconds(normalizeAmount(crafted.getKey(), crafted.getLongValue()), stats.get());
            if (estimate.isPresent()) {
                knownRows++;
            }
        }

        var jobEstimate = new CraftingJobEstimate(key(networkId, plan.finalOutput().what()), remainingAmounts,
                dependencies(networkId, plan, craftedAmounts));
        PROFILER.startWaiting(scope,
                ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.WAITING_TRACKING)
                        ? waitingKeys : Set.of(), tick);
        PROFILER.setJobOwner(scope, owner);
        PROFILER.setJobEstimate(scope, jobEstimate);
        ProviderStartTracker.clear(scope);
        CHANCE.plan(scope, plan.patternTimes().keySet().stream().map(pattern -> {
            Set<ProfileKey> keys = new HashSet<>();
            for (var output : pattern.getOutputs()) keys.add(key(networkId, output.what()));
            return keys;
        }).toList());
        for (var crafted : craftedAmounts) {
            if (crafted.getLongValue() <= 0) {
                continue;
            }
            ProviderLocateRecords.noteStart(key(networkId, crafted.getKey()), owner, "", null,
                    displayNameOf(crafted.getKey()), crafted.getKey());
        }
        persistProviderState();
        var predictedSeconds = jobEstimate.remainingSeconds((key, amount) -> estimateSeconds(key, amount)).orElse(0);
        if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.ACCURACY_RECORDING))
            ACCURACY.start(key(networkId, plan.finalOutput().what()), scope, predictedSeconds, knownRows, totalRows, tick,
                    nanoTime);
    }

    private static Map<ProfileKey, Set<ProfileKey>> dependencies(String networkId, ICraftingPlan plan,
            KeyCounter craftedAmounts) {
        var crafted = new HashSet<ProfileKey>();
        for (var stack : craftedAmounts) {
            if (stack.getLongValue() > 0) {
                crafted.add(key(networkId, stack.getKey()));
            }
        }

        var patterns = new ArrayList<CraftingJobEstimate.Pattern>();
        for (var entry : plan.patternTimes().entrySet()) {
            var inputs = new ArrayList<Set<ProfileKey>>();
            for (var input : entry.getKey().getInputs()) {
                var candidates = new HashSet<ProfileKey>();
                for (var possible : input.getPossibleInputs()) {
                    candidates.add(key(networkId, possible.what()));
                }
                inputs.add(candidates);
            }
            var outputs = new ArrayList<ProfileKey>();
            for (var output : entry.getKey().getOutputs()) {
                outputs.add(key(networkId, output.what()));
            }
            patterns.add(new CraftingJobEstimate.Pattern(entry.getValue(), outputs, inputs));
        }
        return CraftingJobEstimate.dependencies(crafted, patterns);
    }

    private static OptionalLong estimateSeconds(ProfileKey key, long amount) {
        if (amount <= 0) {
            return OptionalLong.empty();
        }
        var stats = PROFILER.stats(key);
        return stats.isEmpty() ? OptionalLong.empty() : TimeEstimate.seconds(amount, stats.get());
    }

    public static OptionalLong remainingJobSeconds(Object scope) {
        return scope == null || !isEnabled() || !ServerOptionsRuntime.scopeEnabled(scope)
                ? OptionalLong.empty()
                : PROFILER.remainingJobSeconds(scope, ProfilerBridge::estimateSeconds);
    }

    public static void rebindJobEstimate(Object previousScope, Object currentScope) {
        if (isEnabled()) {
            PROFILER.rebindJobEstimate(previousScope, currentScope);
        }
    }

    public static UUID jobOwner(appeng.api.networking.security.IActionSource source) {
        if (source == null) {
            return null;
        }
        try {
            var player = source.player();
            if (player.isEmpty() || player.get() == null) {
                return null;
            }
            return player.get().getUUID();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Optional<UUID> jobOwner(Object scope) {
        if (scope == null || !isEnabled() || !ServerOptionsRuntime.scopeEnabled(scope)) {
            return Optional.empty();
        }
        return PROFILER.jobOwner(scope);
    }

    public static List<CraftProfiler.DelayedEvent> pollNewlyDelayed(Object scope, long tick) {
        if (scope == null || !isEnabled()) {
            return List.of();
        }
        return PROFILER.pollNewlyDelayed(scope, tick);
    }

    public static boolean discardDisabledScope(Object scope, long tick, net.minecraft.server.MinecraftServer server) {
        if (scope == null || ServerOptionsRuntime.scopeEnabled(scope)) return false;
        if (!PROFILER.scopedKeys(scope).isEmpty()) finishJob(scope, false, tick, 0, server);
        return true;
    }

    /**
     * Outputs whose stall cleared while the scope still runs, drained once
     * per key. The notify path turns these into explicit highlight clears so
     * plates vanish even with a closed screen and no snapshot.
     */
    public static List<ProfileKey> pollResolvedDelayed(Object scope) {
        if (scope == null || !isEnabled()) {
            return List.of();
        }
        return PROFILER.pollResolvedDelayed(scope);
    }

    public static String displayName(ProfileKey key) {
        if (key == null) {
            return "?";
        }
        var name = DISPLAY_NAMES.get(key);
        return name != null && !name.isBlank() ? name : key.outputId();
    }

    public static String dimensionId(IGrid grid) {
        if (grid == null || grid.getPivot() == null) {
            return "";
        }
        try {
            return grid.getPivot().getLevel().dimension().location().toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * Provider positions for a delayed output: freshly resolved through the
     * live grid first, persisted fallback second, empty when not locatable.
     */
    public static List<BlockPos> locatePositions(Object scope, IGrid grid, ProfileKey key) {
        if (scope == null || key == null) {
            return List.of();
        }
        var live = ProviderStartTracker.positions(grid, scope, key);
        if (!live.isEmpty()) {
            return live;
        }
        return ProviderLocateRecords.startFor(key)
                .map(ProviderLocateRecords.ProviderStartInfo::positions)
                .orElse(List.of());
    }

    public static AEKey displayKey(Object scope, ProfileKey key) {
        var live = ProviderStartTracker.displayKey(scope, key);
        return live.orFallback(ProviderLocateRecords.startFor(key)
                .map(ProviderLocateRecords.ProviderStartInfo::displayKey).orElse(null));
    }

    public static void replaceProviderStart(ProfileKey key, UUID owner,
            List<BlockPos> positions, String outputName) {
        ProviderLocateRecords.replaceStart(key, owner, positions, outputName);
    }

    public static void replaceProviderStart(ProfileKey key, UUID owner, String dimensionId,
            List<BlockPos> positions, String outputName, AEKey displayKey) {
        ProviderLocateRecords.replaceStart(key, owner, dimensionId, positions, outputName, displayKey);
    }

    /**
     * Whether any live scope still reports the key as delayed. Keeps an
     * identical output's red plate when one CPU/network recovers or finishes
     * while another still needs it.
     */
    public static boolean isStillDelayed(ProfileKey key) {
        if (key == null || !isEnabled()) {
            return false;
        }
        return PROFILER.isDelayed(key);
    }

    public static boolean hasPending(ProfileKey key) {
        return PROFILER.hasPending(key);
    }

    public static void persistProviderState() {
        if (savedData != null && ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.SAVE_HISTORY)) {
            savedData.replaceProviderStarts(ProviderLocateRecords.snapshotStarts());
            savedData.replaceProviderRecords(ProviderLocateRecords.snapshotRecords());
        }
        persistStatuses();
    }

    public static void persistStatuses() {
        if (savedData != null && ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.SAVE_HISTORY)) {
            savedData.replaceStatuses(PROFILER.snapshotStatuses());
        }
    }

    public static void finishJob(Object scope, boolean success, long tick, long nanoTime) {
        finishJob(scope, success, tick, nanoTime, null);
    }

    public static void finishJob(Object scope, boolean success, long tick, long nanoTime,
            net.minecraft.server.MinecraftServer server) {
        var highlightKeys = scope == null ? Set.<ProfileKey>of() : PROFILER.scopedKeys(scope);
        var highlightOwner = scope == null ? Optional.<UUID>empty() : PROFILER.jobOwner(scope);
        ACCURACY.finish(scope, success && isEnabled()
                && ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.ACCURACY_RECORDING), tick, nanoTime);
        PROFILER.clearPending(scope);
        ProviderStartTracker.clear(scope);
        CHANCE.clear(scope);
        BlockReasonNotifier.clear(scope);
        // Identical outputs on another CPU/network still need red: only clear
        // and forget keys with no remaining live tracking.
        var releasable = highlightKeys.stream().filter(key -> key != null && !PROFILER.hasPending(key)).toList();
        clearHighlights(server, highlightOwner.orElse(null), Set.copyOf(releasable));
        // Finished and cancelled targets must never return after a reload:
        // forget their provider fallback and their click records as well as
        // their statuses, so stale chat links expire instead of highlighting
        // a replacement block or recreating red.
        if (!releasable.isEmpty()) {
            ProviderLocateRecords.removeStarts(releasable);
            ProviderLocateRecords.removeRecordsForKeys(releasable, highlightOwner.orElse(null));
        }
        persistProviderState();
    }

    /**
     * Tells the craft owner to drop every highlight plate for the finished
     * scope. Empty positions with duration zero is the clear signal the
     * client routes to {@code ProviderHighlightClient.clearFor}, so plates
     * vanish even with a closed screen and no snapshot. Best-effort: a
     * missing server or offline owner only skips the send.
     */
    private static void clearHighlights(net.minecraft.server.MinecraftServer server, UUID owner,
            Set<ProfileKey> keys) {
        if (server == null || owner == null || keys == null || keys.isEmpty()) {
            return;
        }
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        for (var key : keys) {
            if (key == null) {
                continue;
            }
            try {
                StatsNetwork.sendTo(player,
                        new com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C(key.networkId(), "",
                                List.of(), key.outputId(), 0, false));
            } catch (Exception ignored) {
                // One unsendable plate must not hide the rest.
            }
        }
    }

    /**
     * Re-sends every remembered delayed plate owned by the joining player, so
     * a craft that became delayed while they were offline still shows red
     * without opening any window, including after singleplayer reload,
     * dedicated reconnect, server restart, and full client restart. Chat is
     * never re-sent here: login syncs plates only, once-per-episode warnings
     * fire on live transitions while online and honor the chat setting there.
     * Broken provider targets (air, missing block entity, replacement block
     * entity, or surviving host without provider service) are skipped and
     * forgotten so they never return; unloaded chunks stay unknown and keep
     * their plates. Best-effort: missing positions or dimension simply skip
     * that output.
     */
    public static void resyncPlatesForPlayer(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || !isEnabled()) {
            return;
        }
        var owner = player.getUUID();
        var broken = new java.util.ArrayList<ProfileKey>();
        var pruned = false;
        for (var status : PROFILER.snapshotStatuses()) {
            if (status == null || status.kind() != StatusKind.DELAYED || status.key() == null) {
                continue;
            }
            var key = status.key();
            var start = ProviderLocateRecords.startFor(key).orElse(null);
            if (start == null || !owner.equals(start.owner())) {
                continue;
            }
            var positions = start.positions();
            if (positions == null || positions.isEmpty()) {
                continue;
            }
            var storedDimension = start.dimensionId() != null ? start.dimensionId() : "";
            var dimension = !storedDimension.isBlank() ? storedDimension
                    : dimensionFromNetworkId(key.networkId());
            if (dimension.isBlank()) {
                continue;
            }
            var kept = filterUnbroken(player, dimension, positions);
            if (kept.isEmpty()) {
                broken.add(key);
                continue;
            }
            if (kept.size() != positions.size() || !dimension.equals(storedDimension)) {
                ProviderLocateRecords.replaceStart(key, owner, dimension, kept, start.outputName(),
                        start.displayKey());
                pruned = true;
            }
            try {
                StatsNetwork.sendTo(player,
                        new com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C(key.networkId(), dimension,
                                kept, key.outputId(), ProviderLocateCommand.HIGHLIGHT_SECONDS, true,
                                start.displayKey()));
            } catch (Exception ignored) {
                // One unsendable plate must not hide the rest.
            }
        }
        if (!broken.isEmpty()) {
            ProviderLocateRecords.removeStarts(broken);
            pruned = true;
        }
        if (pruned) {
            persistProviderState();
        }
    }

    private static List<BlockPos> filterUnbroken(net.minecraft.server.level.ServerPlayer player,
            String dimensionId, List<BlockPos> positions) {
        try {
            var server = player.serverLevel().getServer();
            for (var level : server.getAllLevels()) {
                String levelDimension;
                try {
                    levelDimension = level.dimension().location().toString();
                } catch (Exception ignored) {
                    continue;
                }
                if (!dimensionId.equals(levelDimension)) {
                    continue;
                }
                var kept = new java.util.ArrayList<BlockPos>();
                for (var pos : positions) {
                    if (pos == null) {
                        continue;
                    }
                    // Shared provider-target check: replacement block entities
                    // and surviving hosts without provider service drop, while
                    // unloaded chunks and unreadable grid stay unknown (kept)
                    // so reload never clears intact red server-side either.
                    if (ProviderBlockTargets.keepForHighlight(level, pos)) {
                        kept.add(pos);
                    }
                }
                return List.copyOf(kept);
            }
        } catch (Exception ignored) {
            // Unknown dimension or unloaded world: send as-is, client trim handles it.
        }
        return positions;
    }

    static String dimensionFromNetworkId(String networkId) {
        return GridNetworkIds.dimensionFromNetworkId(networkId);
    }

    public static Optional<ProfileStats> stats(AEKey what) {
        if (what == null || !isEnabled()) {
            return Optional.empty();
        }
        return PROFILER.stats(key(what));
    }

    public static Optional<ProfileStats> stats(ProfileKey key) {
        if (key == null || !isEnabled()) {
            return Optional.empty();
        }
        return PROFILER.stats(key);
    }

    public static Optional<TtcAccuracyStats> accuracy(ProfileKey key) {
        if (key == null || !isEnabled()) {
            return Optional.empty();
        }
        return ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.ACCURACY_RECORDING)
                ? ACCURACY.stats(key) : Optional.empty();
    }

    public static OptionalLong waitingTicks(ProfileKey key, Object scope, long tick) {
        if (key == null || !isEnabled()
                || !ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.WAITING_TRACKING)) {
            return OptionalLong.empty();
        }
        var live = PROFILER.waitingTicks(key, scope, tick);
        return live.isPresent() ? live : PROFILER.rememberedWaitingTicks(key, tick);
    }

    public static void updateCapacity(Object scope, int usedParallelSlots, int totalParallelSlots, long tick) {
        if (isEnabled()) {
            PROFILER.updateCapacity(scope, usedParallelSlots, totalParallelSlots, tick);
        }
    }

    public static Optional<StatsEntry> entry(ProfileKey lookupKey, ProfileKey displayKey) {
        return entry(lookupKey, displayKey, null, 0);
    }

    public static Optional<StatsEntry> entry(ProfileKey lookupKey, ProfileKey displayKey, Object scope, long tick) {
        return stats(lookupKey).map(stats -> {
            var stall = scope == null ? Optional.<StallDiagnostic>empty()
                    : PROFILER.stall(lookupKey, scope, tick);
            if (stall.isEmpty()) {
                stall = PROFILER.rememberedStall(lookupKey);
            }
            return new StatsEntry(displayKey, stats, accuracy(lookupKey), stall);
        });
    }

    public static boolean clearStats(ProfileKey key) {
        if (key == null || !isEnabled()) {
            return false;
        }
        var cleared = PROFILER.clearSamples(key);
        ACCURACY.clear(key);
        if (cleared && savedData != null) {
            if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.SAVE_HISTORY))
                savedData.replaceFrom(PROFILER.snapshotSamples());
            persistStatuses();
        }
        return cleared;
    }

    private static boolean isEnabled() {
        var enabled = ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING);
        PROFILER.setEnabled(enabled);
        return enabled;
    }

    public static ProfileKey key(AEKey key) {
        return new ProfileKey(key.getId().toString());
    }

    public static ProfileKey key(String networkId, AEKey key) {
        return new ProfileKey(networkId, key.getId().toString());
    }

    public static String networkId(IGrid grid) {
        if (grid == null || grid.getPivot() == null) {
            return "";
        }
        var dimensionId = grid.getPivot().getLevel().dimension().location().toString();
        var controllerAnchors = new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for (var controller : grid.getMachines(ControllerBlockEntity.class)) {
            controllerAnchors.add(controller.getBlockPos());
        }
        return GridNetworkIds.fromControllers(dimensionId, controllerAnchors);
    }

    public static void load(Ae2CraftingTimeSavedData data) {
        savedData = data;
        var config = ServerOptionsRuntime.current();
        PROFILER = new CraftProfiler(config.maxSamples(), config.outlierMultiplier());
        ACCURACY = new TtcAccuracyTracker(config.maxSamples());
        PROFILER.configure(config);
        PROFILER.loadSamples(data.samples());
        ProviderStartTracker.clearAll();
        CHANCE.clearAll();
        ProviderLocateRecords.clearAll();
        BlockReasonNotifier.clearAll();
        ProviderLocateRecords.restoreStarts(data.providerStarts());
        ProviderLocateRecords.restoreRecords(data.providerRecords());
        PROFILER.restoreStatuses(data.statuses());
        var migrated = PROFILER.snapshotSamples();
        if (!migrated.equals(data.samples())) {
            if (ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.SAVE_HISTORY))
                savedData.replaceFrom(migrated);
        }
    }

    private static boolean reasonEnabled(CraftingBlockReason reason) {
        if (reason == null) return false;
        var feature = switch (reason) {
            case NO_PROVIDER -> com.ctux.ae2craftingtime.core.OptionFeature.NO_PROVIDER_DETECTION;
            case NO_POWER -> com.ctux.ae2craftingtime.core.OptionFeature.NO_POWER_DETECTION;
            case NO_TARGET -> com.ctux.ae2craftingtime.core.OptionFeature.NO_TARGET_DETECTION;
            case NO_CHANNEL -> com.ctux.ae2craftingtime.core.OptionFeature.NO_CHANNEL_DETECTION;
            case INPUT_BLOCKED, LOCKED -> com.ctux.ae2craftingtime.core.OptionFeature.INPUT_BLOCKED_DETECTION;
        };
        return ServerOptionsRuntime.enabled(feature);
    }

    public static void configure(com.ctux.ae2craftingtime.core.ServerConfig config) {
        PROFILER.configure(config);
        ACCURACY.configure(config.maxSamples());
        isEnabled();
    }

    private static String displayNameOf(AEKey key) {
        try {
            return key.getDisplayName().getString();
        } catch (Exception ignored) {
            return key.getId().toString();
        }
    }

    private static ProfileUnit unit(AEKey key) {
        return AeKeyAmounts.unit(key);
    }

    private static long normalizeAmount(AEKey key, long amount) {
        return AeKeyAmounts.normalize(key, amount);
    }

    private ProfilerBridge() {
    }
}
