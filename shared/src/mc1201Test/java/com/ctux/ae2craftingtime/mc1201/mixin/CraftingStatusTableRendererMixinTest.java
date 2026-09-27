package com.ctux.ae2craftingtime.mc1201.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import appeng.core.localization.GuiText;
import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.ProfileUnit;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.StallDiagnostic;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import com.ctux.ae2craftingtime.mc1201.TtcText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

class CraftingStatusTableRendererMixinTest {
    @BeforeEach
    void supportingConnection() throws ReflectiveOperationException { setConnectionSupport(() -> true); }

    @AfterEach
    void restoreConnectionSupport() throws ReflectiveOperationException { setConnectionSupport(null); }

    private static void setConnectionSupport(BooleanSupplier support) throws ReflectiveOperationException {
        var method = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.class
                .getDeclaredMethod("setConnectionSupportForTests", BooleanSupplier.class);
        method.setAccessible(true);
        method.invoke(null, support);
    }

    @Test
    void nativeAmountsCompactWithoutMovingForeignDescriptionLines() throws ReflectiveOperationException {
        var before = Component.literal("before");
        var middle = Component.literal("middle");
        var after = Component.literal("after");
        var lines = new ArrayList<Component>(List.of(before, GuiText.FromStorage.text("4"), middle,
                GuiText.Crafting.text("10"), GuiText.Scheduled.text("200"), after));
        var summary = compact(lines, 4, "4", 10, "10", 200, "200");
        assertNotNull(summary);
        assertEquals(List.of(before, summary, middle, after), lines);
        assertEquals("text.ae2craftingtime.status.amounts", ((TranslatableContents) summary.getContents()).getKey());
        assertEquals(List.of("4/10/200"), List.of(((TranslatableContents) summary.getContents()).getArgs()));
    }

    @Test
    void malformedOrMissingNativeAmountsLeaveEntireDescriptionUntouched() throws ReflectiveOperationException {
        var valid = GuiText.FromStorage.text("4");
        for (var lines : List.of(
                new ArrayList<Component>(List.of(Component.literal("foreign"))),
                new ArrayList<Component>(List.of(valid, valid.copy())),
                new ArrayList<Component>(List.of(GuiText.FromStorage.text("5"))),
                new ArrayList<Component>(List.of(valid.copy().append(" extra"))),
                new ArrayList<Component>(List.of(valid.copy().withStyle(ChatFormatting.RED))))) {
            var original = List.copyOf(lines);
            assertNull(compact(lines, 4, "4", 0, null, 0, null));
            assertEquals(original, lines);
        }
        var absent = new ArrayList<Component>(List.of(GuiText.Crafting.text("10")));
        assertNull(compact(absent, 0, null, 0, null, 0, null));
        assertEquals(List.of(GuiText.Crafting.text("10")), absent);
    }

    @Test
    void singleAndEmptyNativeAmountsKeepTheirPositions() throws ReflectiveOperationException {
        var lines = new ArrayList<Component>(List.of(GuiText.Crafting.text("1.5 mB")));
        var summary = compact(lines, 0, null, 1, "1.5 mB", 0, null);
        assertEquals(List.of(summary), lines);
        assertEquals(List.of("C1.5 mB"), List.of(((TranslatableContents) summary.getContents()).getArgs()));
        lines.clear();
        assertNull(compact(lines, 0, null, 0, null, 0, null));
        assertEquals(List.of(), lines);
    }

    @Test
    void amountBadgeCopiesOnlyStatusColorAndHasNormalFallback() {
        var config = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current();
        for (var color : List.of(ChatFormatting.RED, ChatFormatting.YELLOW, ChatFormatting.GREEN)) {
            var badge = TtcText.statusAmounts("A10");
            com.ctux.ae2craftingtime.mc1201.RowTextColor.amounts(badge,
                    Component.literal("warning").withStyle(color, ChatFormatting.BOLD), config);
            assertEquals(TextColor.fromLegacyFormat(color), badge.getStyle().getColor());
            assertEquals(false, badge.getStyle().isBold());
        }
        var inherited = TtcText.statusAmounts("A10");
        com.ctux.ae2craftingtime.mc1201.RowTextColor.amounts(inherited, Component.literal("status"), config);
        var neutralColor = config.badgeBackground()
                ? TextColor.fromRgb(config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.TOTAL)) : null;
        assertEquals(neutralColor, inherited.getStyle().getColor());
        var fallback = TtcText.statusAmounts("A10");
        com.ctux.ae2craftingtime.mc1201.RowTextColor.amounts(fallback, null, config);
        assertEquals(neutralColor, fallback.getStyle().getColor());
    }

    private static Component compact(List<Component> lines, long stored, String storedText, long active,
            String activeText, long pending, String pendingText) throws ReflectiveOperationException {
        var method = CraftingStatusTableRendererMixin.class.getDeclaredMethod("ae2craftingtime$compactAmounts",
                List.class, long.class, String.class, long.class, String.class, long.class, String.class);
        method.setAccessible(true);
        return (Component) method.invoke(null, lines, stored, storedText, active, activeText, pending, pendingText);
    }

    private static final List<String> CONTROLS = List.of(
            "text.ae2craftingtime.locate_hint",
            "text.ae2craftingtime.details_hint",
            "text.ae2craftingtime.reset_hint");
    private static final BooleanSupplier NO_STATS_LOOKUP = () -> fail("Warning or empty row requested stats");
    private static final ProfileStats STATS = new ProfileStats(4, 100, 0.2, 4, 100, ProfileUnit.ITEM);

    @Test
    void unsupportedConnectionLeavesNativeTooltip() throws ReflectiveOperationException {
        setConnectionSupport(() -> false);
        var lines = nativeTooltip();
        appendTooltip(lines, 1, 1, true, null, NO_STATS_LOOKUP);
        assertEquals(nativeTooltip(), lines);
    }

    @Test
    void noSampleWarningsComposeTheirEntireBodyAndOrderedGrayControls() throws ReflectiveOperationException {
        var lines = nativeTooltip();
        appendTooltip(lines, 0, 0, true, null, NO_STATS_LOOKUP);
        assertTooltip(lines, TtcText.noSpaceTooltip(), CONTROLS);
        for (var reason : CraftingBlockReason.values()) {
            for (long active : new long[] {0, 1}) {
                lines = nativeTooltip();
                appendTooltip(lines, active, 1, false, reason, NO_STATS_LOOKUP);
                assertTooltip(lines, TtcText.blockReasonTooltip(reason, active > 0), CONTROLS);
            }
        }
    }

    @Test
    void delayedBodyHasExactlyOneLocateHintAfterAllAdvice() throws ReflectiveOperationException {
        var lines = nativeTooltip();
        var body = TtcText.stallLines(2, 1, STATS, new StallDiagnostic(960, 240, 1, 1, 4));
        appendTooltip(lines, 1, 1, false, null, () -> {
            lines.addAll(body);
            return true;
        });
        assertTooltip(lines, body, CONTROLS);
    }

    @Test
    void ordinaryAndCollectingRowsKeepTheirOriginalTwoControls() throws ReflectiveOperationException {
        for (var body : List.of(TtcText.statsLines(STATS), List.<Component>of())) {
            var lines = nativeTooltip();
            appendTooltip(lines, 1, 0, false, null, () -> {
                lines.addAll(body);
                return false;
            });
            assertTooltip(lines, body, CONTROLS.subList(1, 3));
        }
    }

    @Test
    void emptyRowsKeepTheNativeTooltipWithoutControlsOrStatsRequests() throws ReflectiveOperationException {
        var lines = nativeTooltip();
        appendTooltip(lines, 0, 0, false, null, NO_STATS_LOOKUP);
        assertEquals(nativeTooltip(), lines);
    }

    @Test
    void controlsKeepTheirKeysAndGrayLabelsWithSymbolsOnAndOff() throws ReflectiveOperationException {
        var options = ClientOptionsRuntime.current().features();
        var original = options.enabled(OptionFeature.SHOW_EMOJI);
        try {
            for (var show : new boolean[] {false, true}) {
                options.setEnabled(OptionFeature.SHOW_EMOJI, show);
                var lines = nativeTooltip();
                appendTooltip(lines, 1, 1, true, null, NO_STATS_LOOKUP);
                assertTooltip(lines, TtcText.noSpaceTooltip(), CONTROLS);
                var locate = lines.get(lines.size() - 3);
                if (show) assertEquals("→ ", locate.getSiblings().get(0).getString());
                else assertEquals(List.of(), locate.getSiblings());
            }
        } finally {
            options.setEnabled(OptionFeature.SHOW_EMOJI, original);
        }
    }

    private static ArrayList<Component> nativeTooltip() {
        return new ArrayList<>(List.of(Component.literal("Native item").withStyle(ChatFormatting.AQUA)));
    }

    private static void appendTooltip(List<Component> lines, long active, long pending, boolean noSpace,
            CraftingBlockReason reason, BooleanSupplier appendStats) throws ReflectiveOperationException {
        var method = CraftingStatusTableRendererMixin.class.getDeclaredMethod("ae2craftingtime$appendTooltip",
                List.class, long.class, long.class, boolean.class, CraftingBlockReason.class, BooleanSupplier.class);
        method.setAccessible(true);
        method.invoke(null, lines, active, pending, noSpace, reason, appendStats);
    }

    private static void assertTooltip(List<Component> lines, List<Component> body, List<String> controls) {
        var expectedBody = nativeTooltip();
        expectedBody.addAll(body);
        assertEquals(expectedBody, lines.subList(0, expectedBody.size()));
        var suffix = lines.subList(expectedBody.size(), lines.size());
        assertEquals(controls, suffix.stream().map(line -> TtcComponents.translation(line).getKey()).toList());
        for (var key : CONTROLS) {
            assertEquals(controls.contains(key) ? 1 : 0, lines.stream().filter(line -> {
                var contents = TtcComponents.translation(line);
                return contents != null && contents.getKey().equals(key);
            }).count());
        }
        for (var line : suffix) {
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), line.getStyle().getColor());
        }
    }
}
