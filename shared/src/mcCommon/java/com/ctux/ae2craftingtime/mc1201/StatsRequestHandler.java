package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import appeng.api.networking.IGrid;
import com.ctux.ae2craftingtime.core.PlayerRequestRateLimit;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.StatsEntry;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public final class StatsRequestHandler {
    private static final PlayerRequestRateLimit RATE_LIMIT = new PlayerRequestRateLimit();

    private StatsRequestHandler() {
    }

    public static Response collect(ServerPlayer player, List<String> keys) {
        if (!RATE_LIMIT.allow(player.getUUID(), keys.size(), System.currentTimeMillis())) {
            return null;
        }
        var entries = new ArrayList<StatsEntry>();
        var waitingTicks = new HashMap<String, Long>();
        var context = StatsRequestContext.current(player);
        var networkId = ProfilerBridge.networkId(context.grid());
        var gameTick = player.level().getGameTime();
        if (context.craftingCpu() != null) {
            // Backup path: tick notifications already cover closed screens, but a status
            // request must never duplicate them and must only notify the job owner.
            DelayedNotificationServer.maybeNotify(context.craftingCpu(), context.grid(), gameTick,
                    player.level().getServer());
            BlockReasonNotifier.maybeNotifyPower(context.craftingCpu(), context.grid(), gameTick,
                    player.level().getServer());
        }
        var missing = ProfilerBridge.blockReasons(context.craftingCpu(), context.grid(), gameTick);
        var blockReasons = new HashMap<String, CraftingBlockReason>();
        var chanceOutputs = new HashMap<String, Integer>();
        for (var key : keys) {
            var profileKey = new ProfileKey(networkId, key);
            if (missing.containsKey(profileKey)) {
                blockReasons.put(key, missing.get(profileKey));
            }
            ProfilerBridge.chanceOutput(context.craftingCpu(), profileKey)
                    .ifPresent(value -> chanceOutputs.put(key, value));
            ProfilerBridge.entry(profileKey, new ProfileKey(key), context.craftingCpu(), gameTick)
                    .ifPresent(entries::add);
            ProfilerBridge.waitingTicks(profileKey, context.craftingCpu(), gameTick)
                    .ifPresent(value -> waitingTicks.put(key, value));
        }
        return new Response(entries, networkAmounts(context.grid(), keys), waitingTicks, blockReasons, chanceOutputs,
                ProfilerBridge.remainingJobSeconds(context.craftingCpu()),
                StatsRequestContext.cpuContext(player.containerMenu));
    }

    private static Map<String, Long> networkAmounts(IGrid grid, List<String> keys) {
        var amounts = new HashMap<String, Long>();
        if (grid == null) {
            return amounts;
        }
        var requested = new HashSet<>(keys);
        keys.forEach(key -> amounts.put(key, 0L));
        for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
            var id = entry.getKey().getId().toString();
            if (requested.contains(id)) {
                amounts.merge(id, entry.getLongValue(), Long::sum);
            }
        }
        return amounts;
    }

    public record Response(List<StatsEntry> entries, Map<String, Long> networkAmounts,
            Map<String, Long> waitingTicks, Map<String, CraftingBlockReason> blockReasons,
            Map<String, Integer> chanceOutputs,
            OptionalLong totalTtcSeconds, long cpuContext) {
    }
}
