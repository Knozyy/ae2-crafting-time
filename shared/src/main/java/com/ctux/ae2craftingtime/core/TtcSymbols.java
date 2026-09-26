package com.ctux.ae2craftingtime.core;

/** Exact TTC translation roles; never infer a duration or warning from rendered prose. */
public final class TtcSymbols {
    public enum Symbol {
        TIME("⏱", 0x55FFFF), WAITING("⌛", 0xFFFF55), WARNING("⚠", 0xFFAA00),
        ERROR("⚠", 0xFF5555), POWER("⚡", 0xFF5555), INFO("ℹ", 0x55FFFF),
        SUCCESS("✓", 0x55FF55), LOCATE("→", 0x55FFFF), RECURRENT("↻", 0xFF5555),
        SUGGESTIONS("⚙", 0xFFAA00);

        private final String glyph;
        private final int color;

        Symbol(String glyph, int color) {
            this.glyph = glyph;
            this.color = color;
        }

        public String glyph() { return glyph; }
        public int color() { return color; }
    }

    public static Symbol heading(String key) {
        return switch (key) {
            case "text.ae2craftingtime.waiting" -> Symbol.WAITING;
            case "text.ae2craftingtime.ttc_delayed", "text.ae2craftingtime.stall.delayed",
                    "text.ae2craftingtime.plan.stored_variant", "text.ae2craftingtime.stats.confidence",
                    "text.ae2craftingtime.chat.delayed.word", "text.ae2craftingtime.chat.delayed.expired",
                    "text.ae2craftingtime.chat.details.low_confidence" -> Symbol.WARNING;
            case "text.ae2craftingtime.no_provider", "text.ae2craftingtime.no_channel",
                    "text.ae2craftingtime.no_target", "text.ae2craftingtime.input_blocked",
                    "text.ae2craftingtime.locked", "text.ae2craftingtime.no_space",
                    "text.ae2craftingtime.chat.no_space.word" -> Symbol.ERROR;
            case "text.ae2craftingtime.no_power", "text.ae2craftingtime.chat.no_power.word" -> Symbol.POWER;
            case "text.ae2craftingtime.collecting_data", "text.ae2craftingtime.no_stats",
                    "text.ae2craftingtime.unknown",
                    "text.ae2craftingtime.chat.no_cached", "text.ae2craftingtime.details_hint",
                    "text.ae2craftingtime.reset_hint", "text.ae2craftingtime.stats.ttc",
                    "text.ae2craftingtime.stats.used_samples",
                    "text.ae2craftingtime.stats.samples", "text.ae2craftingtime.stats.accuracy",
                    "text.ae2craftingtime.stats.latest_accuracy" -> Symbol.INFO;
            case "text.ae2craftingtime.chat.reset" -> Symbol.SUCCESS;
            case "text.ae2craftingtime.locate_hint", "text.ae2craftingtime.chat.highlighting",
                    "text.ae2craftingtime.chat.delayed.hint", "text.ae2craftingtime.chat.teleport.hint",
                    "text.ae2craftingtime.stats.throughput" -> Symbol.LOCATE;
            case "text.ae2craftingtime.plan.recurrent" -> Symbol.RECURRENT;
            case "text.ae2craftingtime.stall.improvements" -> Symbol.SUGGESTIONS;
            default -> null;
        };
    }

    public static boolean timeArgument(String key, int index) {
        return switch (key) {
            case "text.ae2craftingtime.ttc" -> index == 0;
            case "text.ae2craftingtime.total_ttc" -> index == 0;
            case "text.ae2craftingtime.chat.summary" -> index == 2;
            case "text.ae2craftingtime.chat.details" -> index == 2 || index == 4;
            case "text.ae2craftingtime.chat.delayed" -> index == 2 || index == 3;
            case "text.ae2craftingtime.value.window" -> index == 2;
            case "text.ae2craftingtime.value.whole_seconds", "text.ae2craftingtime.value.seconds" -> index == 0;
            case "text.ae2craftingtime.value.accuracy" -> index == 4;
            case "text.ae2craftingtime.value.latest_accuracy" -> index <= 2;
            default -> false;
        };
    }

    private TtcSymbols() { }
}
