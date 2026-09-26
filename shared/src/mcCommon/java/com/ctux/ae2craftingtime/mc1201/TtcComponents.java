package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.TtcSymbols;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Builds a local display copy; the server and stored chat retain their original components. */
public final class TtcComponents {
    public static MutableComponent text(String key, Object... args) {
        return decorate(Component.translatable(key, args));
    }

    public static MutableComponent time(String value) {
        return ClientOptionsRuntime.current().features().enabled(OptionFeature.SHOW_EMOJI)
                ? prefix(TtcSymbols.Symbol.TIME, Component.literal(value)) : Component.literal(value);
    }

    public static MutableComponent decorate(Component source) {
        return decorate(source, ClientOptionsRuntime.current().features().enabled(OptionFeature.SHOW_EMOJI));
    }

    public static MutableComponent decorate(Component source, boolean showEmoji) {
        if (!showEmoji || alreadyDecorated(source)) return source.copy();
        var style = TtcHover.decorate(source.getStyle(), value -> decorate(value, true));
        MutableComponent result;
        if (source.getContents() instanceof TranslatableContents text) {
            var args = text.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component nested) args[i] = decorate(nested, true);
                else if (TtcSymbols.timeArgument(text.getKey(), i) && args[i] != null)
                    args[i] = prefix("?".equals(args[i].toString()) ? TtcSymbols.Symbol.INFO
                            : TtcSymbols.Symbol.TIME, Component.literal(args[i].toString()));
            }
            result = Component.translatableWithFallback(text.getKey(), text.getFallback(), args)
                    .setStyle(style);
            for (var sibling : source.getSiblings()) result.append(decorate(sibling, true));
            var symbol = TtcSymbols.heading(text.getKey());
            if (symbol != null) return prefix(symbol, result);
            return result;
        }
        result = MutableComponent.create(source.getContents()).setStyle(style);
        for (var sibling : source.getSiblings()) result.append(decorate(sibling, true));
        return result;
    }

    public static TranslatableContents translation(Component source) {
        if (source.getContents() instanceof TranslatableContents text) return text;
        if (alreadyDecorated(source) && source.getSiblings().get(1).getContents() instanceof TranslatableContents text)
            return text;
        return null;
    }

    private static boolean alreadyDecorated(Component source) {
        if (source.getSiblings().size() < 2) return false;
        var first = source.getSiblings().get(0);
        for (var symbol : TtcSymbols.Symbol.values()) {
            if ((symbol.glyph() + " ").equals(first.getString())) return true;
        }
        return false;
    }

    private static MutableComponent prefix(TtcSymbols.Symbol symbol, Component value) {
        return Component.empty().append(Component.literal(symbol.glyph() + " ")
                .withStyle(style -> style.withColor(TextColor.fromRgb(symbol.color())))).append(value);
    }

    private TtcComponents() { }
}
