package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.RowColorPolicy;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Applies row-only foregrounds before AE2 renders descriptions with its palette. */
public final class RowTextColor {
    public static Component estimate(String eta, OptionalInt color, ClientConfig config) {
        var line = TtcText.ttc(eta);
        if (!inheritNative(config, true) && color.isPresent()) {
            line.withStyle(style -> style.withColor(TextColor.fromRgb(color.getAsInt())));
        }
        return line;
    }

    public static void amounts(MutableComponent amounts, Component status, ClientConfig config) {
        if (inheritNative(config, ordinary(status))) return;
        var color = status == null ? TextColor.fromRgb(config.color(ClientConfig.Color.TOTAL))
                : status.getStyle().getColor();
        if (color != null) amounts.setStyle(amounts.getStyle().withColor(color));
    }

    private static boolean ordinary(Component status) {
        if (status == null) return true;
        var text = TtcComponents.translation(status);
        if (text == null) return false;
        var args = text.getArgs();
        if (args.length == 1 && args[0] instanceof Component value
                && TtcComponents.translation(value) == null) {
            return RowColorPolicy.isNumericEstimate(text.getKey(), new Object[] {value.getString()});
        }
        return RowColorPolicy.isNumericEstimate(text.getKey(), args);
    }

    private static boolean inheritNative(ClientConfig config, boolean ordinary) {
        return RowColorPolicy.inheritNative(config.features().enabled(OptionFeature.BADGE_BACKGROUND),
                config.features().enabled(OptionFeature.TTC_COLORS), ordinary);
    }

    private RowTextColor() {
    }
}
