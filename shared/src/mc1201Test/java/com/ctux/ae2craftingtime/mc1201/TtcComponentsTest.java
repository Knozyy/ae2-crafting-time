package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.StallDiagnostic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class TtcComponentsTest {
    @Test
    void localSettingsAndNestedChatKeepOriginalUntouched() {
        var raw = Component.translatable("chat.type.text", Component.literal("player"),
                Component.translatable("text.ae2craftingtime.chat.delayed",
                        Component.literal("output"),
                        Component.translatable("text.ae2craftingtime.chat.delayed.word")
                                .withStyle(ChatFormatting.RED),
                        Component.translatable("text.ae2craftingtime.value.whole_seconds", 4), "~8s"));
        var off = TtcComponents.decorate(raw, false);
        var on = TtcComponents.decorate(raw, true);
        assertEquals(raw, off);
        assertEquals("chat.type.text", TtcComponents.translation(on).getKey());
        var nested = (Component) ((TranslatableContents) on.getContents()).getArgs()[1];
        var args = ((TranslatableContents) nested.getContents()).getArgs();
        assertEquals("⚠ ", ((Component) args[1]).getSiblings().get(0).getString());
        var seconds = (Component) ((TranslatableContents) ((Component) args[2]).getContents()).getArgs()[0];
        assertEquals("⏱ ", seconds.getSiblings().get(0).getString());
        assertEquals("⏱ ", ((Component) args[3]).getSiblings().get(0).getString());
        assertEquals(raw.getString(), off.getString());
        assertEquals(on, TtcComponents.decorate(on, true));
    }

    @Test
    void headingsHaveIndependentColorAndKnownTranslation() {
        var status = TtcComponents.decorate(Component.translatable("text.ae2craftingtime.no_power"), true);
        assertEquals("⚡ ", status.getSiblings().get(0).getString());
        assertEquals("text.ae2craftingtime.no_power", TtcComponents.translation(status).getKey());
        assertEquals(0xFF5555, status.getSiblings().get(0).getStyle().getColor().getValue());
        var unknown = Component.translatable("text.ae2craftingtime.unknown_key");
        assertEquals("text.ae2craftingtime.unknown_key",
                TtcComponents.translation(TtcComponents.decorate(unknown, true)).getKey());
        assertTrue(TtcComponents.decorate(Component.literal("other chat"), true).getSiblings().isEmpty());
        for (var word : new String[] {"no_power", "no_space"}) {
            var blocked = TtcComponents.decorate(Component.translatable("text.ae2craftingtime.chat.blocked",
                    Component.literal("output"),
                    Component.translatable("text.ae2craftingtime.chat." + word + ".word"),
                    Component.literal("details")), true);
            var reason = (Component) ((TranslatableContents) blocked.getContents()).getArgs()[1];
            assertEquals(word.equals("no_power") ? "⚡ " : "⚠ ", reason.getSiblings().get(0).getString());
        }
    }

    @Test
    void actualProviderLinksDecorateShowTextWithoutChangingClickOrSource() {
        var raw = DelayedChatText.delayedMessage("output", UUID.randomUUID(), 40, 100);
        var copy = TtcComponents.decorate(raw, true);
        var rawName = (Component) ((TranslatableContents) raw.getContents()).getArgs()[0];
        var name = (Component) ((TranslatableContents) copy.getContents()).getArgs()[0];
        assertEquals(rawName.getStyle().getClickEvent(), name.getStyle().getClickEvent());
        assertEquals("→ ", TtcHover.showText(name.getStyle()).getSiblings().get(0).getString());
        assertEquals("text.ae2craftingtime.chat.delayed.hint",
                TtcComponents.translation(TtcHover.showText(name.getStyle())).getKey());
        assertEquals("text.ae2craftingtime.chat.delayed.hint",
                TtcComponents.translation(TtcHover.showText(rawName.getStyle())).getKey());
        assertNull(TtcHover.showText(TtcComponents.decorate(Component.literal("plain"), true).getStyle()));

        var highlight = DelayedChatText.highlightingMessage(Component.literal("provider"),
                List.of(new BlockPos(1, 2, 3)), "overworld");
        var decorated = TtcComponents.decorate(highlight, true);
        var rawCoords = (Component) ((TranslatableContents) highlight.getContents()).getArgs()[1];
        var coords = (Component) ((TranslatableContents) TtcComponents.translation(decorated)).getArgs()[1];
        var rawLink = rawCoords.getSiblings().get(0);
        var link = coords.getSiblings().get(0);
        assertEquals(rawLink.getStyle().getClickEvent(), link.getStyle().getClickEvent());
        assertEquals("→ ", TtcHover.showText(link.getStyle()).getSiblings().get(0).getString());
        assertEquals("text.ae2craftingtime.chat.teleport.hint",
                TtcComponents.translation(TtcHover.showText(link.getStyle())).getKey());
        assertNotNull(TtcHover.showText(rawLink.getStyle()));
    }

    @Test
    void fallbackStylesAccuracyAndMissingTimesSurviveCopy() {
        var styled = Component.translatableWithFallback("text.ae2craftingtime.unknown_key", "fallback")
                .withStyle(ChatFormatting.GREEN).append(Component.literal(" sibling"));
        var copied = TtcComponents.decorate(styled, true);
        assertEquals("fallback", TtcComponents.translation(copied).getFallback());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), copied.getStyle().getColor());
        assertEquals(styled.getSiblings(), copied.getSiblings());

        var summary = TtcComponents.decorate(Component.translatable("text.ae2craftingtime.chat.summary",
                Component.literal("item").withStyle(ChatFormatting.YELLOW), 3, "?"), true);
        var args = TtcComponents.translation(summary).getArgs();
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW), ((Component) args[0]).getStyle().getColor());
        assertEquals("ℹ ", ((Component) args[2]).getSiblings().get(0).getString());

        var accuracy = TtcComponents.decorate(Component.translatable("text.ae2craftingtime.value.accuracy",
                1, 2, "10", "1", "+1.25", "100"), true);
        var accuracyArgs = TtcComponents.translation(accuracy).getArgs();
        assertEquals("⏱ ", ((Component) accuracyArgs[4]).getSiblings().get(0).getString());
        assertEquals("10", accuracyArgs[2]);
        assertEquals("100", accuracyArgs[5]);
    }

    @Test
    void stalledCompoundLineKeepsAquaValuesWithBothSettings() {
        var options = ClientOptionsRuntime.current().features();
        var original = options.enabled(OptionFeature.SHOW_EMOJI);
        var stats = new ProfileStats(4, 100, 0.2, 4, 100, ProfileUnit.ITEM);
        try {
            for (var show : new boolean[] {false, true}) {
                options.setEnabled(OptionFeature.SHOW_EMOJI, show);
                var line = TtcText.stallLines(2, 1, stats, new StallDiagnostic(960, 240, 1, 1, 4)).get(0);
                assertEquals("text.ae2craftingtime.stats.ttc", TtcComponents.translation(line).getKey());
                var parts = line.getSiblings();
                var offset = show ? 2 : 0;
                for (var index : new int[] {1, 5, 9}) {
                    var value = parts.get(index + offset);
                    assertEquals(TextColor.fromLegacyFormat(ChatFormatting.AQUA), value.getStyle().getColor());
                }
                if (show) {
                    assertEquals("ℹ ", parts.get(0).getString());
                    assertEquals("⏱ ", parts.get(3).getSiblings().get(0).getString());
                    assertEquals("⏱ ", parts.get(11).getSiblings().get(0).getString());
                }
            }
        } finally {
            options.setEnabled(OptionFeature.SHOW_EMOJI, original);
        }
    }
}
