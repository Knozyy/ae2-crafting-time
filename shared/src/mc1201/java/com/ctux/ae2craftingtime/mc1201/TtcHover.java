package com.ctux.ae2craftingtime.mc1201;

import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/** The 1.20.1/1.21.1 hover API adapter. */
public final class TtcHover {
    public static Style decorate(Style style, UnaryOperator<Component> displayCopy) {
        var hover = style.getHoverEvent();
        if (hover == null || hover.getAction() != HoverEvent.Action.SHOW_TEXT) return style;
        var text = hover.getValue(HoverEvent.Action.SHOW_TEXT);
        return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, displayCopy.apply(text)));
    }

    public static Component showText(Style style) {
        var hover = style.getHoverEvent();
        return hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT
                ? hover.getValue(HoverEvent.Action.SHOW_TEXT) : null;
    }

    private TtcHover() { }
}
