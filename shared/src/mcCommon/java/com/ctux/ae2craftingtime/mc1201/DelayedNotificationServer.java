package com.ctux.ae2craftingtime.mc1201;

import appeng.api.stacks.AEKey;
import appeng.api.networking.IGrid;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class DelayedNotificationServer {
    public static void maybeNotify(Object scope, IGrid grid, long tick, MinecraftServer server) {
        maybeNotify(scope, grid, tick, server, defaultHighlightSender());
    }

    public static void maybeNotify(Object scope, IGrid grid, long tick, MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (scope == null || server == null) {
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
            ProfilerBridge.persistProviderState();
            return;
        }
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            ProfilerBridge.persistProviderState();
            return;
        }
        var dimension = ProfilerBridge.dimensionId(grid);
        var chatEnabled = ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NOTIFY_ON_DELAYED);
        for (var event : newlyDelayed) {
            notify(player, scope, grid, dimension, owner, event.key(),
                    event.diagnostic().idleTicks(), event.diagnostic().typicalDurationTicks(), highlightSender,
                    chatEnabled);
        }
        for (var key : resolved) {
            // Another scope may still track the same output (identical outputs
            // on two CPUs/networks): only clear when no scope still needs it.
            if (key != null && ProfilerBridge.isStillDelayed(key)) {
                continue;
            }
            pushClearHighlight(player, key, highlightSender);
        }
        ProfilerBridge.persistProviderState();
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
                    player.level().getGameTime()).id();
        }
        ProfilerBridge.replaceProviderStart(key, owner, dimension, positions, name, displayKey);
        pushAutoHighlight(player, dimension, key, positions, displayKey, highlightSender);
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
