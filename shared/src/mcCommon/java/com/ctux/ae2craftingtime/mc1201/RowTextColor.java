package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.RowColorPolicy;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/** Applies row-only foregrounds before AE2 renders descriptions with its palette. */
public final class RowTextColor {
    public static Component estimate(String eta, OptionalInt color, ClientConfig config) {
        var line = TtcText.ttc(eta);
        if (!RowColorPolicy.inheritNative(config.badgeBackground(),
                config.features().enabled(OptionFeature.TTC_COLORS)) && color.isPresent()) {
            line.withStyle(style -> style.withColor(TextColor.fromRgb(color.getAsInt())));
        }
        return line;
    }

    public static Component neutral(MutableComponent line, ClientConfig config) {
        var color = config.badgeBackground()
                ? TextColor.fromRgb(config.color(ClientConfig.Color.TOTAL)) : null;
        return line.copy().withStyle(style -> style.withColor(color));
    }

    public static int planTotalColor(ClientConfig config) {
        return config.badgeBackground() ? config.color(ClientConfig.Color.TOTAL) : 0x404040;
    }

    public static void amounts(MutableComponent amounts, Component status, ClientConfig config) {
        var color = status == null ? null : status.getStyle().getColor();
        if (color == null && config.badgeBackground())
            color = TextColor.fromRgb(config.color(ClientConfig.Color.TOTAL));
        if (color != null) amounts.setStyle(amounts.getStyle().withColor(color));
    }

    private RowTextColor() {
    }
}
