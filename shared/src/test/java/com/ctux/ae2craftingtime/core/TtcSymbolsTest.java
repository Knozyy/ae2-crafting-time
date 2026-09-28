package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TtcSymbolsTest {
    @Test
    void everyStatusAndHeadingHasItsSpecifiedSymbol() {
        var expected = Map.ofEntries(
                Map.entry(TtcSymbols.Symbol.WAITING, "waiting"),
                Map.entry(TtcSymbols.Symbol.POWER, "no_power"),
                Map.entry(TtcSymbols.Symbol.INFO, "collecting_data"),
                Map.entry(TtcSymbols.Symbol.RECURRENT, "plan.recurrent"),
                Map.entry(TtcSymbols.Symbol.SUCCESS, "chat.reset"),
                Map.entry(TtcSymbols.Symbol.LOCATE, "locate_hint"),
                Map.entry(TtcSymbols.Symbol.SUGGESTIONS, "stall.improvements"),
                Map.entry(TtcSymbols.Symbol.TIME, "ttc"));
        for (var entry : expected.entrySet()) {
            if (entry.getKey() != TtcSymbols.Symbol.TIME)
                assertEquals(entry.getKey(), TtcSymbols.heading("text.ae2craftingtime." + entry.getValue()));
        }
        for (var key : new String[] {"ttc_delayed", "stall.delayed", "plan.stored_variant",
                "stats.confidence", "chat.delayed.word", "chat.delayed.expired",
                "chat.details.low_confidence"})
            assertEquals(TtcSymbols.Symbol.WARNING, TtcSymbols.heading("text.ae2craftingtime." + key));
        for (var key : new String[] {"no_provider", "no_channel", "no_target", "input_blocked",
                "locked", "no_space", "chance_output", "chat.chance_output.word", "chat.no_space.word"})
            assertEquals(TtcSymbols.Symbol.ERROR, TtcSymbols.heading("text.ae2craftingtime." + key));
        for (var key : new String[] {"no_stats", "unknown", "chat.no_cached", "details_hint", "reset_hint",
                "stats.ttc", "stats.used_samples", "stats.samples", "stats.accuracy",
                "stats.latest_accuracy"})
            assertEquals(TtcSymbols.Symbol.INFO, TtcSymbols.heading("text.ae2craftingtime." + key));
        assertEquals(TtcSymbols.Symbol.POWER, TtcSymbols.heading("text.ae2craftingtime.chat.no_power.word"));
        assertEquals(TtcSymbols.Symbol.LOCATE, TtcSymbols.heading("text.ae2craftingtime.chat.highlighting"));
        assertEquals(TtcSymbols.Symbol.LOCATE, TtcSymbols.heading("text.ae2craftingtime.chat.delayed.hint"));
        assertEquals(TtcSymbols.Symbol.LOCATE, TtcSymbols.heading("text.ae2craftingtime.chat.teleport.hint"));
        assertEquals(TtcSymbols.Symbol.LOCATE, TtcSymbols.heading("text.ae2craftingtime.stats.throughput"));
        assertNull(TtcSymbols.heading("text.ae2craftingtime.other"));
        var colors = Map.of(
                TtcSymbols.Symbol.TIME, 0x55FFFF,
                TtcSymbols.Symbol.WAITING, 0xFFFF55,
                TtcSymbols.Symbol.WARNING, 0xFFAA00,
                TtcSymbols.Symbol.ERROR, 0xFF5555,
                TtcSymbols.Symbol.POWER, 0xFF5555,
                TtcSymbols.Symbol.INFO, 0x55FFFF,
                TtcSymbols.Symbol.SUCCESS, 0x55FF55,
                TtcSymbols.Symbol.LOCATE, 0x55FFFF,
                TtcSymbols.Symbol.RECURRENT, 0xFF5555,
                TtcSymbols.Symbol.SUGGESTIONS, 0xFFAA00);
        for (var symbol : TtcSymbols.Symbol.values()) {
            assertEquals(1, symbol.glyph().codePointCount(0, symbol.glyph().length()));
            assertEquals(colors.get(symbol).intValue(), symbol.color());
        }
    }

    @Test
    void onlyDurationArgumentsReceiveTimeSymbol() {
        var positions = Map.ofEntries(
                Map.entry("ttc", 0), Map.entry("total_ttc", 0), Map.entry("chat.summary", 2),
                Map.entry("chat.details", 2), Map.entry("chat.delayed", 2),
                Map.entry("chat.chance_output", 3),
                Map.entry("value.window", 0), Map.entry("value.whole_seconds", 0),
                Map.entry("value.seconds", 0), Map.entry("value.accuracy", 4),
                Map.entry("value.latest_accuracy", 0));
        for (var entry : positions.entrySet()) {
            var key = "text.ae2craftingtime." + entry.getKey();
            assertEquals(true, TtcSymbols.timeArgument(key, entry.getValue()));
            assertEquals(false, TtcSymbols.timeArgument(key, 99));
        }
        assertEquals(true, TtcSymbols.timeArgument("text.ae2craftingtime.chat.details", 4));
        assertEquals(true, TtcSymbols.timeArgument("text.ae2craftingtime.chat.delayed", 3));
        assertEquals(true, TtcSymbols.timeArgument("text.ae2craftingtime.value.latest_accuracy", 1));
        assertEquals(true, TtcSymbols.timeArgument("text.ae2craftingtime.value.latest_accuracy", 2));
        assertEquals(false, TtcSymbols.timeArgument("text.ae2craftingtime.value.latest_accuracy", 3));
        assertEquals(false, TtcSymbols.timeArgument("text.ae2craftingtime.value.throughput", 0));
    }
}
