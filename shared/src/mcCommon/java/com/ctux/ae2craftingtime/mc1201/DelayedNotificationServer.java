package com.ctux.ae2craftingtime.mc1201;

import appeng.api.stacks.AEKey;
import appeng.api.networking.IGrid;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.PacketLimits;
import com.ctux.ae2craftingtime.core.ProviderPlateState;
import com.ctux.ae2craftingtime.core.ProviderWarningKeys;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class DelayedNotificationServer {
    private static final ProviderPlateState<BlockPos, AEKey> PLATES =
            new ProviderPlateState<>(PacketLimits.MAX_HIGHLIGHT_POSITIONS);

    public static void tick(Object scope, IGrid grid, Object logic, long tick, MinecraftServer server) {
        if (!ProfilerBridge.trackingEnabled(scope)) {
            ProfilerBridge.discardDisabledScope(scope, tick, server);
            clearScope(scope, server);
            return;
        }
        if (ProfilerBridge.isSuspended(scope)) {
            clearScope(scope, server);
            return;
        }
        maybeNotify(scope, grid, tick, server);
        BlockReasonNotifier.maybeNotifyPower(scope, grid, tick, server);
        BlockReasonNotifier.maybeNotifySpace(scope, grid, logic, server);
        reconcile(scope, grid, logic, tick, server, defaultHighlightSender());
    }

    public static void reconcile(Object scope, IGrid grid, Object logic, long tick, MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> sender) {
        if (scope == null || server == null || sender == null) return;
        var current = new java.util.HashMap<ProviderPlateState.Recipient,
                ProviderPlateState.Contribution<BlockPos, AEKey>>();
        if (grid != null && !ProfilerBridge.isSuspended(scope)
                && !ProfilerBridge.discardDisabledScope(scope, tick, server)) {
            var noSpace = new LinkedHashSet<ProfileKey>();
            var network = ProfilerBridge.networkId(grid);
            if (ServerOptionsRuntime.enabled(OptionFeature.NO_SPACE_DETECTION)) {
                for (var output : NoSpaceProbe.stuckKeys(logic)) {
                    if (output != null) noSpace.add(ProfilerBridge.key(network, output));
                }
            }
            var keys = ProviderWarningKeys.combine(ProfilerBridge.liveDelayedKeys(scope, tick),
                    ProfilerBridge.liveBlockReasons(scope, grid, tick), noSpace);
            var owner = ownerOf(scope, List.copyOf(keys));
            var dimension = ProfilerBridge.dimensionId(grid);
            if (owner != null && !dimension.isBlank()) {
                for (var key : keys) {
                    var positions = ProfilerBridge.locatePositions(scope, grid, key).stream()
                            .filter(pos -> ProviderBlockTargets.keepForHighlight(grid.getPivot().getLevel(), pos))
                            .limit(PacketLimits.MAX_HIGHLIGHT_POSITIONS).toList();
                    if (!positions.isEmpty()) {
                        current.put(new ProviderPlateState.Recipient(owner, key, dimension),
                                new ProviderPlateState.Contribution<>(positions, ProfilerBridge.displayKey(scope, key)));
                    }
                }
            }
        }
        PLATES.update(scope, current);
    }

    /** Called once after all CPU contributions have been collected for this tick. */
    public static void flush(MinecraftServer server) {
        sync(server, defaultHighlightSender());
    }

    private static void sync(MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> sender) {
        for (var change : PLATES.pending()) {
            var recipient = change.recipient();
            var player = server.getPlayerList().getPlayer(recipient.owner());
            if (player == null) continue;
            if (change.plate() == null) {
                sender.accept(player, new ProviderHighlightCodec.Highlight(recipient.key().networkId(),
                        recipient.dimension(), List.of(), recipient.key().outputId(), 0, true));
            } else {
                pushAutoHighlight(player, recipient.dimension(), recipient.key(),
                        change.plate().positions(), change.plate().displayKey(), sender);
            }
            PLATES.delivered(change);
        }
    }

    public static void clearScope(Object scope, MinecraftServer server) {
        PLATES.update(scope, Map.of());
    }

    public static void clearKey(Object scope, ProfileKey key, MinecraftServer server) {
        PLATES.clearKey(scope, key);
    }

    public static void resync(ServerPlayer player) {
        PLATES.forgetOwner(player.getUUID());
        sync(player.level().getServer(), defaultHighlightSender());
    }

    public static void clearAll() {
        PLATES.clearAll();
    }
    public static void maybeNotify(Object scope, IGrid grid, long tick, MinecraftServer server) {
        maybeNotify(scope, grid, tick, server, defaultHighlightSender());
    }

    public static void maybeNotify(Object scope, IGrid grid, long tick, MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (scope == null || server == null || ProfilerBridge.isSuspended(scope)) {
            return;
        }
        if (ProfilerBridge.discardDisabledScope(scope, tick, server)) return;
        // Preserve the once-per-episode while the live owner is offline: skip
        // polling so the transition still fires on reconnect instead of being
        // consumed with no client to receive the plate.
        var liveOwner = ProfilerBridge.jobOwner(scope).orElse(null);
        if (liveOwner != null && server.getPlayerList().getPlayer(liveOwner) == null) {
            return;
        }
        var newlyDelayed = ProfilerBridge.pollNewlyDelayed(scope, tick);
        var resolved = ProfilerBridge.pollResolvedDelayed(scope);
        if (newlyDelayed.isEmpty() && resolved.isEmpty()) {
            return;
        }
        var keys = new ArrayList<>(resolved);
        keys.addAll(newlyDelayed.stream().map(event -> event.key()).toList());
        var owner = ownerOf(scope, keys);
        if (owner == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        var dimension = ProfilerBridge.dimensionId(grid);
        var chatEnabled = ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NOTIFY_ON_DELAYED);
        for (var event : newlyDelayed) {
            notify(player, scope, grid, dimension, owner, event.key(),
                    event.diagnostic().idleTicks(), event.diagnostic().typicalDurationTicks(), highlightSender,
                    chatEnabled);
        }
    }

    static UUID ownerOf(Object scope, List<ProfileKey> keys) {
        var live = ProfilerBridge.jobOwner(scope);
        if (live.isPresent()) {
            return live.get();
        }
        for (var key : keys) {
            var remembered = ProviderLocateRecords.startFor(key)
                    .map(ProviderLocateRecords.ProviderStartInfo::owner)
                    .orElse(null);
            if (remembered != null) {
                return remembered;
            }
        }
        return null;
    }

    private static void notify(ServerPlayer player, Object scope, IGrid grid, String dimension, UUID owner,
            ProfileKey key, long idleTicks, double typicalTicks,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender, boolean chatEnabled) {
        var positions = ProfilerBridge.locatePositions(scope, grid, key);
        var name = ProfilerBridge.displayName(key);
        var displayKey = ProfilerBridge.displayKey(scope, key);
        UUID recordId = null;
        if (!positions.isEmpty()) {
            recordId = ProviderLocateRecords.create(owner, dimension, positions, name, key.outputId(),
                    player.level().getGameTime(), key.networkId()).id();
        }
        ProfilerBridge.replaceProviderStart(key, owner, dimension, positions, name, displayKey);
        // Automatic plates are reconciled after every warning probe on the CPU tick.
        if (WarningPreferenceServer.canSend(player, chatEnabled)) {
            var chance = ProfilerBridge.chanceOutput(scope, key);
            player.sendSystemMessage(chance.isPresent()
                    ? DelayedChatText.chanceMessage(name, recordId, idleTicks, chance.getAsInt())
                    : DelayedChatText.delayedMessage(name, recordId, idleTicks, typicalTicks));
        }
    }

    /**
     * Loader-agnostic highlight sender: shared code builds the
     * {@link ProviderHighlightCodec.Highlight} from already-resolved notify
     * state, while each loader's {@code StatsNetwork} delivers its own
     * {@code ProviderHighlightS2C} packet. Never requires an open menu.
     */
    public static BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> defaultHighlightSender() {
        return (player, highlight) -> StatsNetwork.sendTo(player, new ProviderHighlightS2C(
                highlight.networkId(), highlight.dimensionId(), highlight.positions(), highlight.outputId(),
                highlight.durationSeconds(), highlight.plateOnly(), highlight.displayKey()));
    }

    static void pushAutoHighlight(ServerPlayer player, String dimension, ProfileKey key,
            List<BlockPos> positions, BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        pushAutoHighlight(player, dimension, key, positions, null, highlightSender);
    }

    static void pushAutoHighlight(ServerPlayer player, String dimension, ProfileKey key,
            List<BlockPos> positions, AEKey displayKey,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (player == null || key == null || positions == null || positions.isEmpty()
                || highlightSender == null) {
            return;
        }
        highlightSender.accept(player, new ProviderHighlightCodec.Highlight(key.networkId(), dimension, positions,
                key.outputId(), ProviderLocateCommand.HIGHLIGHT_SECONDS, true, displayKey));
    }

    /**
     * Tells one player to drop one red plate: the empty highlight routes to
     * {@code ProviderHighlightClient.clearFor}, so the plate vanishes even
     * with a closed screen and no snapshot while any rainbow survives. Used
     * when a stall resolves while the craft still runs.
     */
    static void pushClearHighlight(ServerPlayer player, ProfileKey key,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (player == null || key == null || highlightSender == null) {
            return;
        }
        highlightSender.accept(player,
                new ProviderHighlightCodec.Highlight(key.networkId(), "", List.of(), key.outputId(), 0));
    }

    private DelayedNotificationServer() {
    }
}
