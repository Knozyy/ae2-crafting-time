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

    @Test void savesAndReplacesBothContinuationFormatsWithTheOriginalIdentity() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var output = Files.createDirectories(directory.resolve(leaf));
            var scenario = new StandardAe2Scenario(leaf, "world", output, false);
            for (var screenshots : List.of(List.of("before.png"), List.of("replacement.png"))) {
                write(scenario, leaf, screenshots);
                var file = output.resolve(leaf.equals("badge-background")
                        ? "badge-background-continuation.json" : "status-amounts-continuation.json");
                withContinuation(file, () -> {
                    var restored = new ArrayList<String>();
                    new StandardAe2Scenario(leaf, "world", output, false, restored);
                    assertEquals(screenshots, restored);
                });
                assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
            }
        }
    }

    @Test void failedWritesExposeTheCauseAndPreserveExistingTargets() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var missing = directory.resolve(leaf + "-missing");
            var scenario = new StandardAe2Scenario(leaf, "world", missing, false);
            var failure = assertThrows(IllegalStateException.class, () -> write(scenario, leaf, List.of()));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(failure.getMessage().contains("Cannot save"));
            var target = Files.createDirectories(missing.resolve(leaf.equals("badge-background")
                    ? "badge-background-continuation.json" : "status-amounts-continuation.json"));
            Files.writeString(target.resolve("preserved"), "keep");
            failure = assertThrows(IllegalStateException.class, () -> write(scenario, leaf, List.of()));
            assertInstanceOf(IOException.class, failure.getCause());
            assertEquals("keep", Files.readString(target.resolve("preserved")));
        }
    }

    @Test void relaunchRejectsIncompleteChecksBeforeTouchingMinecraft() throws Exception {
        for (var leaf : List.of("standard-status-controls", "badge-background")) {
            var path = directory.resolve(leaf + "-incomplete.json");
            Files.writeString(path, continuation().toString());
            withContinuation(path, () -> {
                var scenario = new StandardAe2Scenario(leaf, "world", directory, false);
                var checks = new java.util.LinkedHashMap<String, Boolean>();
                var failure = assertThrows(IllegalStateException.class, () -> scenario.tick(null, null, checks,
                        name -> fail("Invalid continuations must not capture: " + name),
                        (x, y) -> fail("Invalid continuations must not move the mouse")));
                assertEquals(leaf.equals("badge-background")
                        ? "Badge relaunch predecessor or saved config differs"
                        : "Status relaunch predecessor omitted required checks", failure.getMessage());
                assertTrue(checks.isEmpty());
            });
        }
    }

    private void write(StandardAe2Scenario scenario, String leaf, List<String> screenshots) {
        if (leaf.equals("badge-background")) {
            scenario.writeBadgeContinuation(new StandardAe2Scenario.BadgeContinuation(
                    1, "world", "campaign", "a".repeat(64), List.of(), screenshots));
        } else {
            scenario.writeAmountContinuation(new StandardAe2Scenario.AmountContinuation(
                    1, "world", "campaign", "a".repeat(64), List.of(), screenshots));
        }
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
