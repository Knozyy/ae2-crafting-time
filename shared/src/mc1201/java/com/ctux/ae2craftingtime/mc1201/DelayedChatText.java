package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.TimeEstimate;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds the delayed warning message. The status word is a separate red
 * translatable so translation order never breaks, and the output name links
 * to the locate command while its record exists.
 */
public final class DelayedChatText {
    public static MutableComponent delayedMessage(String outputName, UUID recordId, long idleTicks,
            double typicalTicks) {
        var idleSeconds = (long) Math.ceil(Math.max(0, idleTicks) / 20.0);
        return Component.translatable("text.ae2craftingtime.chat.delayed",
                name(outputName, recordId),
                Component.translatable("text.ae2craftingtime.chat.delayed.word")
                        .withStyle(ChatFormatting.RED),
                Component.translatable("text.ae2craftingtime.value.whole_seconds", idleSeconds),
                TimeEstimate.formatTicks(typicalTicks));
    }

    public static MutableComponent chanceMessage(String outputName, UUID recordId, long idleTicks,
            int chanceBasisPoints) {
        var percentage = java.math.BigDecimal.valueOf(chanceBasisPoints, 2)
                .stripTrailingZeros().toPlainString();
        var idleSeconds = (long) Math.ceil(Math.max(0, idleTicks) / 20.0);
        return Component.translatable("text.ae2craftingtime.chat.chance_output",
                name(outputName, recordId),
                Component.translatable("text.ae2craftingtime.chat.chance_output.word")
                        .withStyle(ChatFormatting.RED), percentage,
                Component.translatable("text.ae2craftingtime.value.whole_seconds", idleSeconds));
    }
    public static MutableComponent blockedMessage(String outputName, UUID recordId, String wordKey,
            Component detail) {
        return Component.translatable("text.ae2craftingtime.chat.blocked",
                name(outputName, recordId),
                Component.translatable(wordKey).withStyle(ChatFormatting.RED),
                detail);
    }

    /**
     * Private "highlighting here" notice sent after every locate, whatever
     * triggered it. Names the provider block; every coordinate is an
     * underlined literal that teleports the clicker to that position.
     */
    public static MutableComponent highlightingMessage(Component providerName, List<BlockPos> positions,
            String dimensionId) {
        return Component.translatable("text.ae2craftingtime.chat.highlighting",
                providerName == null ? Component.literal("") : providerName, teleportCoords(positions),
                dimensionId == null ? "" : dimensionId);
    }

    private static Component teleportCoords(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return Component.literal("");
        }
        var coords = Component.empty();
        var first = true;
        for (var pos : positions) {
            if (!first) {
                coords.append(Component.literal(", "));
            }
            first = false;
            var x = pos.getX();
            var y = pos.getY();
            var z = pos.getZ();
            coords.append(Component.literal("(" + x + ", " + y + ", " + z + ")")
                    .withStyle(style -> style.withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/tp @s " + x + " " + y + " " + z))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("text.ae2craftingtime.chat.teleport.hint")))));
        }
        return coords;
    }

    private static Component name(String outputName, UUID recordId) {
        var name = Component.literal(outputName);
        if (recordId == null) {
            return name;
        }
        return name.withStyle(style -> style.withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/ae2craftingtime locate " + recordId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("text.ae2craftingtime.chat.delayed.hint"))));
    }

    private DelayedChatText() {
    }
}
