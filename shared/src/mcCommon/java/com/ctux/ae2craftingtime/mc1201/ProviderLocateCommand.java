package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightCodec;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Shared locate command shape. Each loader registers the built tree on its
 * own command event and passes its own highlight sender. The tree is open to
 * every command source; the handler itself validates the player and record
 * ownership, and non-player sources get no answer.
 */
public final class ProviderLocateCommand {
    public static final String ROOT = "ae2craftingtime";
    public static final String LOCATE = "locate";
    public static final int HIGHLIGHT_SECONDS = 15;

    public static LiteralArgumentBuilder<CommandSourceStack> build(BiConsumer<CommandSourceStack, UUID> locate) {
        return Commands.literal(ROOT)
                .requires(source -> true)
                .then(Commands.literal(LOCATE)
                        .then(Commands.argument("record", StringArgumentType.string())
                                .executes(context -> {
                                    UUID id;
                                    try {
                                        id = UUID.fromString(StringArgumentType.getString(context, "record"));
                                    } catch (IllegalArgumentException ignored) {
                                        expired(context.getSource());
                                        return 0;
                                    }
                                    locate.accept(context.getSource(), id);
                                    return 1;
                                })));
    }

    /**
     * Resolves the clicking player against the owning active job and valid
     * provider targets. Missing or foreign records expire. Active-craft links
     * survive reload via persisted records and per-output fallbacks; finished,
     * cancelled, and broken links expire without recreating red or targeting
     * a replacement block. Manual locates send rainbow edges only, never
     * plates.
     */
    public static void locate(CommandSourceStack source, UUID id,
            BiConsumer<ServerPlayer, ProviderHighlightCodec.Highlight> sender) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            return;
        }
        var record = ProviderLocateRecords.ownedBy(player.getUUID(), id);
        if (record.isEmpty()) {
            player.sendSystemMessage(Component.translatable("text.ae2craftingtime.chat.delayed.expired"));
            return;
        }
        var located = record.get();
        var resolved = resolveValidTargets(source, located);
        if (resolved.isEmpty()) {
            // Broken, finished, or cancelled: forget the click so it never
            // returns after reload, then expire with no highlight.
            ProviderLocateRecords.removeRecord(located.id());
            player.sendSystemMessage(Component.translatable("text.ae2craftingtime.chat.delayed.expired"));
            return;
        }
        var highlight = resolved.get();
        sender.accept(player, highlight);
        player.sendSystemMessage(DelayedChatText.highlightingMessage(
                providerName(levelFor(source, highlight.dimensionId()), highlight.positions()),
                highlight.positions(), highlight.dimensionId()));
    }

    /**
     * Uses only the record's verified network. An empty initial start can use
     * captured blocked-warning positions; a later empty replacement removes
     * its click record, so invalidation cannot revive those positions.
     */
    static java.util.Optional<ProviderHighlightCodec.Highlight> resolveValidTargets(CommandSourceStack source,
            ProviderLocateRecords.LocateRecord record) {
        if (source == null || record == null) {
            return java.util.Optional.empty();
        }
        var fallbacks = ProviderLocateRecords.startsForOutput(record.owner(), record.outputId()).stream()
                .filter(start -> record.networkId().isBlank()
                        || record.networkId().equals(start.key().networkId())).toList();
        // Old saved records have no network identity. Only an unambiguous
        // owner-bound start can authenticate them; never choose another grid.
        if (fallbacks.size() != 1) return java.util.Optional.empty();
        var fallback = fallbacks.get(0);
        if (record.networkId().isBlank() && !ProviderLocateRecords.matchesLegacyStart(record, fallback))
            return java.util.Optional.empty();
        var dimension = fallback.dimensionId() != null && !fallback.dimensionId().isBlank()
                ? fallback.dimensionId() : record.dimensionId();
        var positions = fallback.positions().isEmpty() ? record.positions() : fallback.positions();
        var kept = filterValid(source, dimension, positions);
        if (kept.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new ProviderHighlightCodec.Highlight(fallback.key().networkId(),
                dimension, kept, record.outputId(), HIGHLIGHT_SECONDS, false, null, true));
    }

    private static List<BlockPos> filterValid(CommandSourceStack source, String dimensionId,
            List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        try {
            var serverLevel = source.getLevel();
            var server = serverLevel.getServer();
            if (server != null && dimensionId != null && !dimensionId.isBlank()) {
                for (var level : server.getAllLevels()) {
                    if (!dimensionId.equals(ProfilerBridge.dimensionId(level))) {
                        continue;
                    }
                    var kept = new java.util.ArrayList<BlockPos>();
                    for (var pos : positions) {
                        if (pos != null && ProviderBlockTargets.keepForHighlight(level, pos)) {
                            kept.add(pos);
                        }
                    }
                    return List.copyOf(kept);
                }
                return List.of();
            }
            var kept = new java.util.ArrayList<BlockPos>();
            for (var pos : positions) {
                if (pos != null && ProviderBlockTargets.keepForHighlight(serverLevel, pos)) {
                    kept.add(pos);
                }
            }
            return List.copyOf(kept);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Level levelFor(CommandSourceStack source, String dimensionId) {
        try {
            var serverLevel = source.getLevel();
            var server = serverLevel.getServer();
            if (server != null && dimensionId != null && !dimensionId.isBlank()) {
                for (var level : server.getAllLevels()) {
                    if (dimensionId.equals(ProfilerBridge.dimensionId(level))) {
                        return level;
                    }
                }
            }
            return serverLevel;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Names the provider block at the first resolved position for the
     * highlight message. Anything unresolvable falls back to the generic
     * provider name so the message never breaks.
     */
    public static Component providerName(Level level, List<BlockPos> positions) {
        if (level != null && positions != null && !positions.isEmpty()) {
            try {
                return level.getBlockState(positions.get(0)).getBlock().getName();
            } catch (Exception ignored) {
                // Fall through to the generic provider name.
            }
        }
        return Component.translatable("text.ae2craftingtime.chat.provider");
    }

    private static void expired(CommandSourceStack source) {
        try {
            source.getPlayerOrException().sendSystemMessage(
                    Component.translatable("text.ae2craftingtime.chat.delayed.expired"));
        } catch (Exception ignored) {
            // Non-player sources get no answer.
        }
    }

    private ProviderLocateCommand() {
    }
}
