package com.ctux.ae2craftingtime.mc1201;

import appeng.api.stacks.GenericStack;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.TtcColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Point;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;

public final class CraftingTreeTtc {
    public static final float TEXT_SCALE = 0.5f;
    public static final int LABEL_PADDING = 2;
    public static final int LABEL_TOP_OFFSET = 19;

    private CraftingTreeTtc() {
    }

    public static void drawBadge(GuiGraphics guiGraphics, int outputX, int outputY, int spacingX, int spacingY,
            Point point, long seconds, int color) {
        var text = TimeEstimate.formatTotal(List.of(OptionalLong.of(seconds))).orElse(null);
        if (text == null) {
            return;
        }

        var font = Minecraft.getInstance().font;
        var display = TtcComponents.time(text).withStyle(style -> style.withColor(color));
        var scaledTextWidth = font.width(display) * TEXT_SCALE;
        var labelWidth = (int) Math.ceil(scaledTextWidth) + LABEL_PADDING * 2;
        var labelHeight = (int) Math.ceil(font.lineHeight * TEXT_SCALE) + LABEL_PADDING * 2;
        var x = point.x * spacingX + outputX;
        var y = point.y * spacingY + outputY;
        var labelLeft = x + 8 - labelWidth / 2.0f;
        var labelTop = y + LABEL_TOP_OFFSET;
        var textX = (int) ((x + 8 - scaledTextWidth / 2) / TEXT_SCALE);
        var textY = (int) ((labelTop + LABEL_PADDING) / TEXT_SCALE);

        TtcBadge.fillRoundedRect(guiGraphics, (int) Math.floor(labelLeft), (int) Math.floor(labelTop),
                (int) Math.ceil(labelLeft + labelWidth), (int) Math.ceil(labelTop + labelHeight),
                TtcBadge.BACKGROUND);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        guiGraphics.drawString(font, display, textX, textY, color, ClientOptionsRuntime.textShadow());
        pose.popPose();
    }

    public static IdentityHashMap<Object, Long> computeSeconds(List<Object> roots,
            Function<Object, GenericStack> stackOf,
            Function<Object, Long> craftAmountOf,
            Function<Object, List<Object>> childrenOf) {
        var seconds = new IdentityHashMap<Object, Long>();
        if (roots != null) {
            for (var root : roots) {
                addSeconds(root, seconds, stackOf, craftAmountOf, childrenOf);
            }
        }
        return seconds;
    }

    private static void addSeconds(Object data, IdentityHashMap<Object, Long> cache,
            Function<Object, GenericStack> stackOf,
            Function<Object, Long> craftAmountOf,
            Function<Object, List<Object>> childrenOf) {
        if (data == null || cache.containsKey(data)) {
            return;
        }

        long total = 0;
        var stack = stackOf.apply(data);
        var craftAmount = craftAmountOf.apply(data);
        if (stack != null && craftAmount != null && craftAmount > 0) {
            var key = new ProfileKey(stack.what().getId().toString());
            var stats = ClientStats.CACHE.get(key);
            if (stats.isEmpty()) {
                ClientStatsRequests.request(key);
            } else {
                var self = TimeEstimate.seconds(AeKeyAmounts.normalize(stack.what(), craftAmount), stats.get());
                if (self.isPresent()) {
                    total += self.getAsLong();
                }
            }
        }

        for (var child : childrenOf.apply(data)) {
            addSeconds(child, cache, stackOf, craftAmountOf, childrenOf);
            var childSeconds = cache.get(child);
            if (childSeconds != null) {
                total += childSeconds;
            }
        }

        cache.put(data, total);
    }

    public static IdentityHashMap<Object, Integer> computeColors(IdentityHashMap<Object, Long> seconds) {
        var colors = new IdentityHashMap<Object, Integer>();
        if (seconds == null || seconds.isEmpty()) {
            return colors;
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (var value : seconds.values()) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        for (var entry : seconds.entrySet()) {
            colors.put(entry.getKey(), ClientOptionsRuntime.ttcColor(entry.getValue(), min, max));
        }
        return colors;
    }

}
