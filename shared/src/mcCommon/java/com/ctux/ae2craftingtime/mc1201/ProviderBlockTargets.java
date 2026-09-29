package com.ctux.ae2craftingtime.mc1201;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Shared provider-target validation for broken-provider trims (client render)
 * and login resync (server). Replacing a provider with another block entity
 * must drop its highlights; unloading or reloading an intact provider must
 * not permanently remove red.
 *
 * <p>Unloaded chunks and unreadable grid state are unknown, never broken:
 * they keep the highlight so a reload cannot permanently clear an intact
 * provider. Air, missing block entities, and non-provider block entities
 * (for example a chest placed where the provider was) are broken. A surviving
 * grid host without any {@link ICraftingProvider} service (for example the
 * provider part removed while its host block remains) is also broken. The
 * service check covers vanilla and addon provider variants without
 * hardcoding concrete block-entity classes.
 */
public final class ProviderBlockTargets {
    /**
     * Whether the highlight position should survive a broken-provider trim.
     * Returns {@code true} for unknown state (unloaded chunk, unreadable
     * grid, unexpected error) so red never disappears permanently on reload.
     */
    public static boolean keepForHighlight(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        try {
            if (!level.isLoaded(pos)) {
                return true;
            }
            // Level block reads add a short-lived server chunk ticket on every poll.
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                return true;
            }
            if (chunk.getBlockState(pos).isAir()) {
                return false;
            }
            var blockEntity = chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity == null) {
                return false;
            }
            return keepHost(blockEntity);
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * Whether a block entity still counts as a provider target. Non-grid
     * hosts are never providers. Grid hosts offering
     * {@link ICraftingProvider} on any side are providers. Grid hosts with
     * readable nodes but no provider service lost their provider part.
     * Grid hosts with no readable nodes are unknown (grid rebuilding after
     * reload) and keep their highlight.
     */
    static boolean keepHost(Object blockEntity) {
        if (!(blockEntity instanceof IInWorldGridNodeHost host)) {
            return false;
        }
        var sawNode = false;
        for (var direction : Direction.values()) {
            try {
                var node = host.getGridNode(direction);
                if (node == null) {
                    continue;
                }
                sawNode = true;
                try {
                    if (node.getService(ICraftingProvider.class) != null) {
                        return true;
                    }
                } catch (Exception ignored) {
                    // One unreadable service must not hide the remaining sides.
                }
            } catch (Exception ignored) {
                // One unreadable side must not hide the remaining sides.
            }
        }
        return !sawNode;
    }

    private ProviderBlockTargets() {
    }
}
