package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ProfileStats;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.core.StallDiagnostic;
import com.ctux.ae2craftingtime.core.TimeEstimate;
import com.ctux.ae2craftingtime.core.TtcAccuracyStats;
import com.ctux.ae2craftingtime.core.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TtcText {
    public static MutableComponent statusAmounts(String amounts) {
        return Component.translatable("text.ae2craftingtime.status.amounts", amounts);
    }

    public static MutableComponent statusAmountsLegend() {
        return Component.translatable("text.ae2craftingtime.status.amounts_legend");
    }

    public static MutableComponent planAmountsLegend() {
        return Component.translatable("text.ae2craftingtime.plan.amounts_legend");
    }

    public static MutableComponent recurrent(Object... arguments) {
        return TtcComponents.text("text.ae2craftingtime.plan.recurrent", arguments)
                .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(false));
    }

    public static MutableComponent recurrentHint() {
        return Component.translatable("text.ae2craftingtime.plan.recurrent_hint");
    }

    public static MutableComponent storedVariant() {
        return TtcComponents.text("text.ae2craftingtime.plan.stored_variant")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(false));
    }

    public static List<Component> storedVariantHints() {
        return List.of(Component.translatable("text.ae2craftingtime.plan.stored_variant.explanation"),
                Component.translatable("text.ae2craftingtime.plan.stored_variant.suggestion"));
    }

    public static MutableComponent ttc(String eta) {
        return TtcComponents.text("text.ae2craftingtime.ttc", eta);
    }

    public static MutableComponent tooltipTtc(Component value) {
        return TtcComponents.text("text.ae2craftingtime.stats.ttc")
                .append(": ").append(value).setStyle(value.getStyle());
    }

    public static MutableComponent ttcDelayed() {
        return TtcComponents.text("text.ae2craftingtime.ttc_delayed");
    }

    public static MutableComponent chanceOutput() {
        return TtcComponents.text("text.ae2craftingtime.chance_output")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5555)).withBold(false));
    }

    public static List<Component> chanceOutputTooltip(long activeAmount, long chanceBasisPoints,
            long idleTicks) {
        var percentage = java.math.BigDecimal.valueOf(chanceBasisPoints, 2)
                .stripTrailingZeros().toPlainString();
        var lines = new ArrayList<Component>();
        lines.add(chanceOutput());
        lines.add(Component.translatable("text.ae2craftingtime.chance_output.explanation", percentage));
        if (activeAmount > 0) {
            lines.add(Component.translatable("text.ae2craftingtime.chance_output.outstanding", activeAmount));
        }
        if (idleTicks > 0) {
            lines.add(Component.translatable("text.ae2craftingtime.chance_output.idle",
                    secondsRounded(idleTicks)));
        }
        lines.add(Component.translatable("text.ae2craftingtime.chance_output.suggestion"));
        return List.copyOf(lines);
    }

    public static MutableComponent waiting() {
        return TtcComponents.text("text.ae2craftingtime.waiting");
    }

    public static MutableComponent noSpace() {
        return TtcComponents.text("text.ae2craftingtime.no_space")
                .withStyle(style -> style.withColor(TextColor.fromRgb(
                        ClientOptionsRuntime.current().color(ClientConfig.Color.DELAYED))));
    }

    public static List<Component> noSpaceTooltip() {
        return List.of(noSpace(), Component.translatable("text.ae2craftingtime.no_space.explanation"),
                Component.translatable("text.ae2craftingtime.no_space.suggestion"));
    }

    public static MutableComponent blockReason(CraftingBlockReason reason) {
        return TtcComponents.text("text.ae2craftingtime." + reason.name().toLowerCase(Locale.ROOT))
                .withStyle(style -> style.withColor(TextColor.fromRgb(
                        ClientOptionsRuntime.current().color(ClientConfig.Color.DELAYED))).withBold(false));
    }

    public static List<Component> blockReasonTooltip(CraftingBlockReason reason) {
        return blockReasonTooltip(reason, false);
    }

    public static List<Component> blockReasonTooltip(CraftingBlockReason reason, boolean mixedActiveAndScheduled) {
        var key = "text.ae2craftingtime." + reason.name().toLowerCase(Locale.ROOT);
        var lines = new ArrayList<Component>();
        lines.add(blockReason(reason));
        lines.add(Component.translatable(key + ".explanation"));
        lines.add(Component.translatable(key + ".suggestion"));
        if (mixedActiveAndScheduled && isDispatchReason(reason)) {
            lines.add(Component.translatable("text.ae2craftingtime.dispatch_status.scheduled_only"));
        }
        return List.copyOf(lines);
    }

    private static boolean isDispatchReason(CraftingBlockReason reason) {
        return reason == CraftingBlockReason.NO_CHANNEL
                || reason == CraftingBlockReason.NO_TARGET
                || reason == CraftingBlockReason.INPUT_BLOCKED
                || reason == CraftingBlockReason.LOCKED;
    }

    public static MutableComponent ttcCollectingData() {
        return TtcComponents.text("text.ae2craftingtime.ttc",
                TtcComponents.text("text.ae2craftingtime.collecting_data"))
                .withStyle(style -> style.withColor(TextColor.fromRgb(
                        ClientOptionsRuntime.current().color(ClientConfig.Color.COLLECTING))));
    }

    public static MutableComponent totalTtc(String eta) {
        return TtcComponents.text("text.ae2craftingtime.total_ttc", eta)
                .withStyle(style -> style.withColor(TextColor.fromRgb(
                        ClientOptionsRuntime.current().color(ClientConfig.Color.TOTAL))));
    }

    public static MutableComponent noStats() {
        return TtcComponents.text("text.ae2craftingtime.no_stats");
    }

    public static MutableComponent detailsHint() {
        return TtcComponents.text("text.ae2craftingtime.details_hint");
    }

    public static MutableComponent resetHint() {
        return TtcComponents.text("text.ae2craftingtime.reset_hint");
    }

    public static MutableComponent locateHint() {
        return TtcComponents.text("text.ae2craftingtime.locate_hint");
    }

    public static MutableComponent sortTitle() {
        return Component.translatable("text.ae2craftingtime.sort.title");
    }

    public static MutableComponent sortMode(int mode) {
        return Component.translatable(switch (mode) {
            case 1 -> "text.ae2craftingtime.sort.shortest";
            case 2 -> "text.ae2craftingtime.sort.longest";
            default -> "text.ae2craftingtime.sort.ae2";
        });
    }

    public static List<Component> statsLines(ProfileStats stats) {
        var lines = new ArrayList<Component>();
        lines.add(statsLine("text.ae2craftingtime.stats.throughput",
                I18n.get("text.ae2craftingtime.value.throughput", rate(stats.amountPerTick()), unitName(stats),
                        rate(stats.amountPerSecond()), unitName(stats))));
        if (stats.usedSampleCount() != stats.sampleCount()) {
            lines.add(statsLine("text.ae2craftingtime.stats.used_samples",
                    I18n.get("text.ae2craftingtime.value.used_samples", stats.usedSampleCount(),
                            stats.sampleCount())));
        }
        if (!stats.sampleDurationTicks().isEmpty()) {
            var windows = windows(stats);
            if (!windows.getSiblings().isEmpty()) {
                lines.add(statsLine("text.ae2craftingtime.stats.samples",
                        windows.append(" (" + stats.sampleCount() + ")")));
                lines.add(Component.translatable("text.ae2craftingtime.stats.samples.explanation")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (!stats.reliableEstimate()) {
                lines.add(statsLine("text.ae2craftingtime.stats.confidence", confidence(stats)));
        }
        return List.copyOf(lines);
    }

    public static List<Component> stallLines(long amount, long scheduledAmount, ProfileStats stats, StallDiagnostic stall) {
        var lines = new ArrayList<Component>();
        var eta = TimeEstimate.format(amount, stats);
        lines.add(TtcComponents.text("text.ae2craftingtime.stats.ttc").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                .append(eta.<Component>map(value -> TtcComponents.time(value).withStyle(ChatFormatting.AQUA))
                        .orElseGet(() -> TtcComponents.text("text.ae2craftingtime.unknown")
                                .withStyle(ChatFormatting.AQUA)))
                .append(Component.literal(", ").withStyle(ChatFormatting.AQUA))
                .append(TtcComponents.text("text.ae2craftingtime.stall.delayed").withStyle(ChatFormatting.RED))
                .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                .append(TtcComponents.text("text.ae2craftingtime.value.whole_seconds",
                        secondsRounded(stall.idleTicks())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(", ").withStyle(ChatFormatting.AQUA))
                .append(Component.translatable("text.ae2craftingtime.stall.typical").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.AQUA))
                .append(TtcComponents.time(TimeEstimate.formatTicks(stall.typicalDurationTicks()))
                        .withStyle(ChatFormatting.AQUA)));
        lines.add(Component.empty());
        lines.add(TtcComponents.text("text.ae2craftingtime.stall.improvements")
                .withStyle(ChatFormatting.GOLD));
        for (var hint : stall.hints(scheduledAmount)) {
            appendHint(lines, hint, stall);
        }
        lines.add(Component.translatable("text.ae2craftingtime.chance_output.unknown_hint"));
        return List.copyOf(lines);
    }

    public static List<String> compactMessages(String name, long amount, ProfileStats stats) {
        return compactMessages(name, amount, stats, Optional.empty());
    }

    public static List<String> compactMessages(String name, long amount, ProfileStats stats,
            Optional<TtcAccuracyStats> accuracy) {
        var messages = new ArrayList<String>();
        messages.add(I18n.get("text.ae2craftingtime.chat.summary", name, amount,
                TimeEstimate.format(amount, stats).orElse(I18n.get("text.ae2craftingtime.unknown"))));

        var details = normalizedDetails(stats);
        if (stats.usedSampleCount() != stats.sampleCount()) {
            details += I18n.get("text.ae2craftingtime.chat.details.used", stats.usedSampleCount(),
                    stats.sampleCount());
        }
        if (!stats.reliableEstimate()) {
            details += I18n.get("text.ae2craftingtime.chat.details.low_confidence");
        }
        if (accuracy.isPresent() && ClientOptionsRuntime.enabled(com.ctux.ae2craftingtime.core.OptionFeature.ACCURACY_DETAILS)) {
            details += " | " + accuracy(accuracy.get()) + "; " + latestAccuracy(accuracy.get());
        }
        messages.add(details);
        return List.copyOf(messages);
    }

    public static String noCachedStats(String name) {
        return I18n.get("text.ae2craftingtime.chat.no_cached", name);
    }

    public static String resetStats(String name) {
        return I18n.get("text.ae2craftingtime.chat.reset", name);
    }

    private static Component statsLine(String labelKey, String value) {
        return statsLine(labelKey, Component.literal(value));
    }

    private static Component statsLine(String labelKey, Component value) {
        return TtcComponents.text(labelKey).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value.copy().withStyle(ChatFormatting.AQUA));
    }

    private static String unitName(ProfileStats stats) {
        return I18n.get(stats.unit().translationKey());
    }

    private static String singularUnitName(ProfileStats stats) {
        return I18n.get(stats.unit() == com.ctux.ae2craftingtime.core.ProfileUnit.ITEM
                ? "text.ae2craftingtime.unit.item.singular"
                : stats.unit().translationKey());
    }

    private static long secondsRounded(long ticks) {
        return (long) Math.ceil(ticks / 20.0);
    }

    private static void appendHint(List<Component> lines, StallDiagnostic.Hint hint, StallDiagnostic stall) {
        switch (hint) {
            case ADD_PARALLEL_PROVIDERS -> {
                lines.add(Component.translatable("text.ae2craftingtime.stall.hint.parallel")
                        .withStyle(ChatFormatting.YELLOW));
                var available = Math.max(0, stall.totalParallelSlots() - stall.usedParallelSlots());
                var reasonKey = stall.activeBatches() == 1
                        ? "text.ae2craftingtime.stall.reason.parallel_one"
                        : "text.ae2craftingtime.stall.reason.parallel_many";
                lines.add(Component.translatable(reasonKey, available, stall.activeBatches())
                        .withStyle(ChatFormatting.GRAY));
            }
            case SPEED_UP_MACHINE -> {
                lines.add(Component.translatable("text.ae2craftingtime.stall.hint.speed")
                        .withStyle(ChatFormatting.YELLOW));
                var slowdown = stall.typicalDurationTicks() <= 0 ? 1.0
                        : stall.idleTicks() / stall.typicalDurationTicks();
                lines.add(Component.translatable("text.ae2craftingtime.stall.reason.speed", rate(slowdown))
                        .withStyle(ChatFormatting.GRAY));
            }
            case ADD_CRAFTING_CO_PROCESSORS -> {
                lines.add(Component.translatable("text.ae2craftingtime.stall.hint.co_processors")
                        .withStyle(ChatFormatting.YELLOW));
                lines.add(Component.translatable("text.ae2craftingtime.stall.reason.co_processors",
                        stall.totalParallelSlots()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static String rate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static MutableComponent windows(ProfileStats stats) {
        var values = Component.empty();
        for (var i = 0; i < stats.sampleDurationTicks().size(); i++) {
            var ticks = stats.sampleTicksPerUnit(i);
            if (ticks.isPresent()) {
                TimeEstimate.formatSampleTicks(ticks.getAsDouble()).ifPresent(value -> {
                    if (!values.getSiblings().isEmpty()) values.append(", ");
                    values.append(TtcComponents.text("text.ae2craftingtime.value.window", value));
                });
            }
        }
        return values;
    }

    private static String normalizedDetails(ProfileStats stats) {
        var average = stats.averageTicksPerUnit();
        var latest = stats.latestTicksPerUnit();
        if (average.isEmpty() || latest.isEmpty()) {
            return I18n.get("text.ae2craftingtime.chat.details.rate", stats.sampleCount(),
                    rate(stats.amountPerSecond()), unitName(stats));
        }
        return I18n.get("text.ae2craftingtime.chat.details", stats.sampleCount(), singularUnitName(stats),
                TimeEstimate.formatSampleTicks(average.getAsDouble()).orElse("?"), singularUnitName(stats),
                TimeEstimate.formatSampleTicks(latest.getAsDouble()).orElse("?"),
                rate(stats.amountPerSecond()), unitName(stats));
    }

    private static String confidence(ProfileStats stats) {
        if (stats.sampleCount() < 3) {
            return I18n.get("text.ae2craftingtime.confidence.low_samples");
        }
        if (stats.usedSampleCount() != stats.sampleCount()) {
            return I18n.get("text.ae2craftingtime.confidence.low_outliers");
        }
        return I18n.get("text.ae2craftingtime.confidence.low");
    }

    private static String accuracy(TtcAccuracyStats stats) {
        if (stats.fullyCoveredSampleCount() == 0) {
            return I18n.get("text.ae2craftingtime.value.accuracy_pending", stats.sampleCount(),
                    percent(stats.averageCoverage()));
        }
        return I18n.get("text.ae2craftingtime.value.accuracy", stats.fullyCoveredSampleCount(), stats.sampleCount(),
                rate(stats.meanAbsolutePercentageError()), rate(stats.meanActualToPredictedRatio()),
                String.format(Locale.ROOT, "%+.2f", stats.meanSignedErrorSeconds()), percent(stats.averageCoverage()));
    }

    private static String latestAccuracy(TtcAccuracyStats stats) {
        return I18n.get("text.ae2craftingtime.value.latest_accuracy", stats.lastPredictedSeconds(),
                rate(stats.lastActualWallSeconds()), rate(stats.lastActualTickSeconds()), stats.lastKnownRows(),
                stats.lastTotalRows());
    }

    private static String percent(double ratio) {
        return rate(ratio * 100.0);
    }

    private TtcText() {
    }
}
