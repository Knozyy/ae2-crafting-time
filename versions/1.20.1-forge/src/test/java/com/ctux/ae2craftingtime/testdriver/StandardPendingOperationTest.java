package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.core.*;
import com.ctux.ae2craftingtime.mc1201.ClientStats;
import com.ctux.ae2craftingtime.mc1201.TtcComponents;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardPendingOperationTest {
    @TempDir Path directory;

    @Test void pendingServerWorkIsRetainedUntilItsResultIsConsumed() throws Exception {
        var scenario = new StandardAe2Scenario("standard-plan-controls", "world", directory, false);
        var operation = StandardAe2Scenario.class.getDeclaredField("operation");
        operation.setAccessible(true);
        var poll = StandardAe2Scenario.class.getDeclaredMethod("server", Minecraft.class, Function.class);
        poll.setAccessible(true);
        Function<ServerPlayer, Boolean> unexpected = player -> { throw new AssertionError("work was resubmitted"); };
        for (boolean result : new boolean[]{false, true}) {
            var pending = new CompletableFuture<Boolean>();
            operation.set(scenario, pending);
            assertEquals(false, poll.invoke(scenario, null, unexpected));
            assertSame(pending, operation.get(scenario));
            pending.complete(result);
            assertEquals(result, poll.invoke(scenario, null, unexpected));
            assertNull(operation.get(scenario));
        }
    }

    @Test void resetUsesThePlanOutputUntilTheStatusScreenOpens() throws Exception {
        var scenario = new StandardAe2Scenario("standard-plan-controls", "world", directory, false);
        var phase = StandardAe2Scenario.class.getDeclaredField("phase");
        phase.setAccessible(true);
        var output = StandardAe2Scenario.class.getDeclaredMethod("statsOutput");
        output.setAccessible(true);
        boolean status = false;
        for (var stage : phase.getType().getEnumConstants()) {
            if (stage.toString().equals("OPEN_STATUS")) status = true;
            phase.set(scenario, stage);
            assertEquals(status ? "minecraft:smooth_stone" : "minecraft:stone", output.invoke(scenario));
        }
    }

    @Test void delayedTooltipRequiresSynchronizedNumbersAndEveryControl() throws Exception {
        var method = StandardAe2Scenario.class.getDeclaredMethod("delayedTooltip", UiSnapshot.class);
        method.setAccessible(true);
        var key = new ProfileKey("minecraft:stone");
        var previous = ClientStats.CACHE.get(key).map(stats -> new StatsEntry(key, stats,
                ClientStats.CACHE.accuracy(key), ClientStats.CACHE.stall(key)));
        var language = Language.getInstance();
        var languageFields = Arrays.stream(I18n.class.getDeclaredFields())
                .filter(field -> Language.class.isAssignableFrom(field.getType())).toList();
        assertEquals(1, languageFields.size(), "I18n must retain one client language");
        var i18n = languageFields.get(0);
        i18n.setAccessible(true);
        var previousI18n = i18n.get(null);
        try {
            var translations = new HashMap<String, String>();
            try (var source = getClass().getResourceAsStream("/assets/ae2craftingtime/lang/en_us.json")) {
                assertNotNull(source);
                Language.loadFromJson(source, translations::put);
            }
            var constructor = ClientLanguage.class.getDeclaredConstructor(Map.class, boolean.class);
            constructor.setAccessible(true);
            var english = constructor.newInstance(translations, false);
            Language.inject(english);
            i18n.set(null, english);
            ClientStats.CACHE.replace(List.of(key), List.of());
            assertEquals(false, method.invoke(null, snapshot(List.of())));
            ClientStats.CACHE.replace(List.of(new StatsEntry(key,
                    new ProfileStats(1, 100, 1, 20, 100, ProfileUnit.ITEM), Optional.empty(),
                    Optional.of(new StallDiagnostic(61, 100, 1, 1, 2)))));
            var heading = TtcComponents.text("text.ae2craftingtime.stats.ttc").getString();
            var time = TtcComponents.time("~5s").getString();
            var delayed = TtcComponents.text("text.ae2craftingtime.stall.delayed").getString();
            var seconds = TtcComponents.text("text.ae2craftingtime.value.whole_seconds", 4L).getString();
            var valid = new ArrayList<>(List.of(text("unrelated", "ignored"),
                    text("text.ae2craftingtime.stats.ttc", heading + ": 7s, " + delayed + ": " + seconds + ", Typical: " + time),
                    text("text.ae2craftingtime.stall.improvements", "Improve"),
                    text("text.ae2craftingtime.locate_hint", "Locate"),
                    text("text.ae2craftingtime.details_hint", "Details"),
                    text("text.ae2craftingtime.reset_hint", "Reset")));
            assertEquals(true, method.invoke(null, snapshot(valid)));
            for (int index = 1; index < valid.size(); index++) {
                var missing = new ArrayList<>(valid);
                missing.remove(index);
                assertEquals(false, method.invoke(null, snapshot(missing)), "missing " + index);
            }
            for (var wrong : List.of("wrong", heading + ": 7s, " + delayed + ": 3s, Typical: " + time,
                    "wrong: 7s, " + delayed + ": " + seconds + ", Typical: " + time)) {
                var mismatch = new ArrayList<>(valid);
                mismatch.set(1, text("text.ae2craftingtime.stats.ttc", wrong));
                assertEquals(false, method.invoke(null, snapshot(mismatch)), wrong);
            }
        } finally {
            ClientStats.CACHE.replace(List.of(key), previous.map(List::of).orElseGet(List::of));
            Language.inject(language);
            i18n.set(null, previousI18n);
        }
    }

    private static UiSnapshot.ObservedText text(String key, String rendered) {
        return new UiSnapshot.ObservedText(key, rendered, List.of(), null);
    }

    private static UiSnapshot snapshot(List<UiSnapshot.ObservedText> tooltip) {
        return new UiSnapshot("screen", "menu", new Rect(0, 0, 100, 100), 100, 100, 1, 1, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), tooltip);
    }
}
