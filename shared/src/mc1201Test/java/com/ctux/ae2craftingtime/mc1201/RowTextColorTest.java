package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.OptionFeature;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class RowTextColorTest {
    @Test
    void planAndStatusEstimatesAndAmountsFollowAllFourSwitchStates() {
        var config = new ClientConfig();
        config.setColor(ClientConfig.Color.TOTAL, 0x123456);
        var tint = TextColor.fromRgb(0x654321);
        for (boolean badge : new boolean[] {false, true}) {
            for (boolean colors : new boolean[] {false, true}) {
                config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, badge);
                config.features().setEnabled(OptionFeature.TTC_COLORS, colors);
                var nativeForeground = !badge && !colors;
                var estimate = RowTextColor.estimate("~1s", OptionalInt.of(tint.getValue()), config);
                assertEquals(nativeForeground ? null : tint, estimate.getStyle().getColor());
                assertEquals("text.ae2craftingtime.ttc", ((TranslatableContents) estimate.getContents()).getKey());
                for (var status : new Component[] {estimate, null}) {
                    var amounts = TtcText.statusAmounts("4/10").withStyle(ChatFormatting.ITALIC);
                    RowTextColor.amounts(amounts, status, config);
                    assertEquals(status == null ? badge ? TextColor.fromRgb(0x123456) : null
                            : nativeForeground ? null : tint, amounts.getStyle().getColor());
                    assertEquals(true, amounts.getStyle().isItalic());
                    assertEquals(List.of("4/10"), List.of(((TranslatableContents) amounts.getContents()).getArgs()));
                }
            }
        }
    }

    @Test
    void neutralRowLabelsAndAmountsUseOneForegroundWithoutChangingTooltipColors() {
        var config = new ClientConfig();
        config.setColor(ClientConfig.Color.TOTAL, 0xE0E0E0);
        for (boolean badge : new boolean[] {false, true}) {
            config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, badge);
            assertEquals(badge ? 0xE0E0E0 : 0x404040, RowTextColor.planTotalColor(config));
            for (boolean colors : new boolean[] {false, true}) {
                config.features().setEnabled(OptionFeature.TTC_COLORS, colors);
                for (var original : List.of(TtcText.waiting(), TtcText.ttcCollectingData(),
                        TtcText.storedVariant())) {
                    var originalColor = original.getStyle().getColor();
                    var row = RowTextColor.neutral(original, config);
                    var expected = badge ? TextColor.fromRgb(0xE0E0E0) : null;
                    assertEquals(expected, row.getStyle().getColor());
                    var amounts = TtcText.statusAmounts("A10");
                    RowTextColor.amounts(amounts, row, config);
                    assertEquals(expected, amounts.getStyle().getColor());
                    assertEquals(originalColor, original.getStyle().getColor());
                }
            }
        }
    }

    @Test
    void collectingAndWarningStatusesKeepTheirColorsEvenWhenEqualToTotal() {
        var config = new ClientConfig();
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, false);
        config.features().setEnabled(OptionFeature.TTC_COLORS, false);
        config.setColor(ClientConfig.Color.TOTAL, 0x123456);
        var color = TextColor.fromRgb(0x123456);
        var collecting = Component.translatable("text.ae2craftingtime.ttc",
                Component.translatable("text.ae2craftingtime.collecting_data")).withStyle(style -> style.withColor(color));
        var warnings = List.of(collecting, TtcText.waiting().withStyle(style -> style.withColor(color)),
                TtcText.ttcDelayed().withStyle(style -> style.withColor(color).withBold(false)),
                TtcText.noSpace().withStyle(style -> style.withColor(color)),
                TtcText.blockReason(com.ctux.ae2craftingtime.core.CraftingBlockReason.NO_POWER)
                        .withStyle(style -> style.withColor(color).withBold(false)),
                TtcText.recurrent().withStyle(style -> style.withColor(color).withBold(false)),
                TtcText.storedVariant().withStyle(style -> style.withColor(color)));
        for (var status : warnings) {
            var original = status.copy();
            var amounts = TtcText.statusAmounts("A10");
            RowTextColor.amounts(amounts, status, config);
            assertEquals(color, amounts.getStyle().getColor());
            assertEquals(false, amounts.getStyle().isBold());
            assertEquals(original, status);
        }
    }

    @Test
    void missingColorAndForeignStatusAreLeftAlone() {
        var config = new ClientConfig();
        var estimate = RowTextColor.estimate("~1s", OptionalInt.empty(), config);
        assertNull(estimate.getStyle().getColor());
        var unknown = Component.literal("foreign").withStyle(ChatFormatting.RED);
        var original = unknown.copy();
        var amounts = TtcText.statusAmounts("A10");
        RowTextColor.amounts(amounts, unknown, config);
        assertEquals(unknown.getStyle().getColor(), amounts.getStyle().getColor());
        assertEquals(original, unknown);
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, false);
        config.features().setEnabled(OptionFeature.TTC_COLORS, false);
        for (var status : List.of(Component.literal("foreign"), Component.translatable("unknown"),
                Component.translatable("text.ae2craftingtime.ttc"),
                Component.translatable("text.ae2craftingtime.ttc", "~1s", "extra"))) {
            var withColor = status.copy().withStyle(ChatFormatting.RED);
            var badge = TtcText.statusAmounts("A10");
            RowTextColor.amounts(badge, withColor, config);
            assertEquals(withColor.getStyle().getColor(), badge.getStyle().getColor());
        }
    }
}
