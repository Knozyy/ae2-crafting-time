package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardContinuationTest {
    @TempDir Path directory;

    @Test void restoresTheMatchingStageAndPreviouslyCapturedScreenshots() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var path = directory.resolve(leaf + ".json");
            Files.writeString(path, continuation().toString());
            withContinuation(path, () -> {
                var captures = new ArrayList<String>();
                var scenario = new StandardAe2Scenario(leaf, "world", directory, false, captures);
                assertTrue(scenario.checkpoint().startsWith(leaf.equals("badge-background")
                        ? "phase=BADGE_RELAUNCH " : "phase=STATUS_RELAUNCH "));
                assertEquals(List.of("before.png"), captures);
                captures.add("after.png");
                assertEquals(List.of("before.png", "after.png"), captures);
            });
        }
    }

    @Test void rejectsEveryMissingOrMismatchedContinuationIdentityField() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var path = directory.resolve(leaf + ".json");
            for (var field : List.of("schema", "world", "campaign", "checks", "screenshots", "configSha256")) {
                var value = continuation();
                switch (field) {
                    case "schema" -> value.addProperty(field, 2);
                    case "world", "campaign" -> value.addProperty(field, "other");
                    default -> value.add(field, JsonNull.INSTANCE);
                }
                Files.writeString(path, value.toString());
                withContinuation(path, () -> {
                    var failure = assertThrows(IllegalStateException.class,
                            () -> new StandardAe2Scenario(leaf, "world", directory, false), field);
                    assertTrue(failure.getMessage().contains("continuation identity differs"));
                });
            }
            Files.writeString(path, "null");
            withContinuation(path, () -> assertThrows(IllegalStateException.class,
                    () -> new StandardAe2Scenario(leaf, "world", directory, false)));
        }
    }

    @Test void exposesMissingAndMalformedContinuationFilesInsteadOfStartingFresh() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var missing = directory.resolve(leaf + "-missing.json");
            withContinuation(missing, () -> {
                var failure = assertThrows(IllegalStateException.class,
                        () -> new StandardAe2Scenario(leaf, "world", directory, false));
                assertInstanceOf(IOException.class, failure.getCause());
                assertTrue(failure.getMessage().contains("Cannot read"));
            });
            var malformed = directory.resolve(leaf + "-malformed.json");
            Files.writeString(malformed, "{not json");
            withContinuation(malformed, () -> assertThrows(JsonSyntaxException.class,
                    () -> new StandardAe2Scenario(leaf, "world", directory, false)));
        }
    }

    @Test void unrelatedScenariosAndBlankPathsKeepTheFreshPreparationStage() {
        withContinuation(directory.resolve("missing.json"), () -> assertTrue(
                new StandardAe2Scenario("waiting-status", "world", directory, false)
                        .checkpoint().startsWith("phase=PREPARE ")));
        withContinuation(Path.of(""), () -> {
            for (var leaf : List.of("standard-status-controls", "badge-background")) {
                assertTrue(new StandardAe2Scenario(leaf, "world", directory, false)
                        .checkpoint().startsWith("phase=PREPARE "));
            }
        });
    }

    private JsonObject continuation() {
        var value = new JsonObject();
        value.addProperty("schema", 1);
        value.addProperty("world", "world");
        value.addProperty("campaign", "campaign");
        value.addProperty("configSha256", "a".repeat(64));
        value.add("checks", new JsonArray());
        var screenshots = new JsonArray();
        screenshots.add("before.png");
        value.add("screenshots", screenshots);
        return value;
    }

    private void withContinuation(Path path, Runnable check) {
        var previous = new HashMap<String, String>();
        for (var name : List.of("statusRelaunch", "badgeRelaunch", "continuation", "campaign")) {
            previous.put(name, System.getProperty("ae2craftingtime.test." + name));
        }
        System.setProperty("ae2craftingtime.test.statusRelaunch", "true");
        System.setProperty("ae2craftingtime.test.badgeRelaunch", "true");
        System.setProperty("ae2craftingtime.test.continuation", path.toString());
        System.setProperty("ae2craftingtime.test.campaign", "campaign");
        try {
            check.run();
        } finally {
            previous.forEach((name, value) -> {
                if (value == null) System.clearProperty("ae2craftingtime.test." + name);
                else System.setProperty("ae2craftingtime.test." + name, value);
            });
        }
    }
}
