package com.ctux.ae2craftingtime.mc1201;

import appeng.api.networking.IGrid;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.StuckEpisodeTracker;
import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side only. Warns the craft owner once per stuck episode about NO
 * POWER and NO SPACE rows, with the same private clickable message shape as
 * delayed warnings. Reasons that clear re-arm a later transition.
 *
 * <p>This class owns chat episodes only. {@link DelayedNotificationServer}
 * reconciles red plates from all current warning reasons after the CPU probes.
 */
public final class BlockReasonNotifier {
    private static final StuckEpisodeTracker NO_POWER = new StuckEpisodeTracker();
    private static final StuckEpisodeTracker NO_SPACE = new StuckEpisodeTracker();

    public static void maybeNotifyPower(Object scope, IGrid grid, long tick, MinecraftServer server) {
        maybeNotifyPower(scope, grid, tick, server, DelayedNotificationServer.defaultHighlightSender());
    }

    public static void maybeNotifyPower(Object scope, IGrid grid, long tick, MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (ProfilerBridge.isSuspended(scope) || !armed(server) || !ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NO_POWER_DETECTION)) {
            return;
        }
        var reasons = ProfilerBridge.blockReasons(scope, grid, tick);
        var keys = new HashSet<ProfileKey>();
        for (var entry : reasons.entrySet()) {
            if (entry.getValue() == CraftingBlockReason.NO_POWER) {
                keys.add(entry.getKey());
            }
        }
        notifyAll(scope, grid, keys, server, "text.ae2craftingtime.chat.no_power.word",
                Component.translatable("text.ae2craftingtime.no_power.explanation"), NO_POWER, highlightSender);
    }

    public static void maybeNotifySpace(Object scope, IGrid grid, Object logic, MinecraftServer server) {
        maybeNotifySpace(scope, grid, logic, server, DelayedNotificationServer.defaultHighlightSender());
    }

    public static void maybeNotifySpace(Object scope, IGrid grid, Object logic, MinecraftServer server,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (ProfilerBridge.isSuspended(scope) || !armed(server) || !ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NO_SPACE_DETECTION)
                || scope == null || grid == null) {
            return;
        }
        var networkId = ProfilerBridge.networkId(grid);
        var keys = new HashSet<ProfileKey>();
        for (var aeKey : NoSpaceProbe.stuckKeys(logic)) {
            if (aeKey != null) {
                keys.add(ProfilerBridge.key(networkId, aeKey));
            }
        }
        notifyAll(scope, grid, keys, server, "text.ae2craftingtime.chat.no_space.word",
                Component.translatable("text.ae2craftingtime.no_space.explanation"), NO_SPACE, highlightSender);
    }

    private static void notifyAll(Object scope, IGrid grid, Set<ProfileKey> keys, MinecraftServer server,
            String wordKey, Component detail, StuckEpisodeTracker tracker,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender) {
        if (scope == null || server == null) {
            return;
        }
        if (!ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING)) {
            return;
        }
        // Poll even when nothing is currently stuck: an empty set ends the
        // episode so a later transition warns again. Plate changes are handled
        // by the shared warning reconciliation after this chat probe.
        var newly = tracker.pollNewlyStuck(scope, keys);
        tracker.pollResolved(scope);
        if (newly.isEmpty()) {
            return;
        }
        var owner = DelayedNotificationServer.ownerOf(scope, List.copyOf(newly));
        if (owner == null) {
            return;
        }
        var player = server.getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        var dimension = ProfilerBridge.dimensionId(grid);
        var chatEnabled = ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.NOTIFY_ON_DELAYED);
        for (var key : newly) {
            notify(player, scope, grid, dimension, owner, key, wordKey, detail, highlightSender, chatEnabled);
        }
    }

    private static void notify(ServerPlayer player, Object scope, IGrid grid, String dimension, UUID owner,
            ProfileKey key, String wordKey, Component detail,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> highlightSender, boolean chatEnabled) {
        var positions = ProfilerBridge.locatePositions(scope, grid, key);
        var name = ProfilerBridge.displayName(key);
        UUID recordId = null;
        if (!positions.isEmpty()) {
            recordId = ProviderLocateRecords.create(owner, dimension, positions, name, key.outputId(),
                    player.level().getGameTime(), key.networkId()).id();
        }
        // Chat records stay separate from the plate reconciliation.
        if (WarningPreferenceServer.canSend(player, chatEnabled)) {
            player.sendSystemMessage(DelayedChatText.blockedMessage(name, recordId, wordKey, detail));
        }
    }

    private static boolean armed(MinecraftServer server) {
        return server != null && ServerOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING);
    }

    public static void clear(Object scope) {
        NO_POWER.clear(scope);
        NO_SPACE.clear(scope);
    }

    public static void clearAll() {
        NO_POWER.clearAll();
        NO_SPACE.clearAll();
    }

    private BlockReasonNotifier() {
    }
}
