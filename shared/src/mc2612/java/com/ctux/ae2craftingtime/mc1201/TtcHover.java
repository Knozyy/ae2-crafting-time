package com.ctux.ae2craftingtime.mc1201;

import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/** The Minecraft 26 hover API adapter. */
public final class TtcHover {
    public static Style decorate(Style style, UnaryOperator<Component> displayCopy) {
        return style.getHoverEvent() instanceof HoverEvent.ShowText show
                ? style.withHoverEvent(new HoverEvent.ShowText(displayCopy.apply(show.value()))) : style;
    }

    public static Component showText(Style style) {
        return style.getHoverEvent() instanceof HoverEvent.ShowText show ? show.value() : null;
    }

    private TtcHover() { }
}
