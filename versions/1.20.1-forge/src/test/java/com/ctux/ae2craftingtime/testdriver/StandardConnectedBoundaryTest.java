package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardConnectedBoundaryTest {
    @TempDir Path directory;
    private final HashMap<String, String> previous = new HashMap<>();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();

    @BeforeEach void configureControl() {
        for (var name : java.util.List.of("role", "control", "campaign", "suspensionReload", "continuation")) {
            var key = "ae2craftingtime.test." + name;
            previous.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        System.setProperty("ae2craftingtime.test.control", directory.toString());
        System.setProperty("ae2craftingtime.test.campaign", "epoch");
        checks.put("same-live-job", false);
    }

    @AfterEach void restoreControl() {
        StoredVariantObservation.enable(false);
        previous.forEach((key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
        });
    }

    @Test void savedClientOptionsHashTracksExactBytesAndPreservesReadFailures() throws Exception {
        var path = directory.resolve("client.toml");
        Files.writeString(path, "compactAmounts = false\n");
        var first = StandardAe2Scenario.configHash(path);
        assertEquals(CaptureEvidence.sha256(Files.readAllBytes(path)), first);
        Files.writeString(path, "compactAmounts = true\n");
        assertNotEquals(first, StandardAe2Scenario.configHash(path));
        for (var unreadable : java.util.List.of(directory, directory.resolve("missing.toml"))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.configHash(unreadable));
            assertEquals("Cannot hash saved client options", failure.getMessage());
            assertInstanceOf(java.io.IOException.class, failure.getCause());
        }
    }

    @Test void addonEvidenceRetainsTheObservedPayloadAndPreservesWriteFailures() throws Exception {
        var result = new com.google.gson.JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("appbotLoaded", false);
        result.addProperty("appmekLoaded", true);
        var captured = new com.google.gson.JsonArray();
        captured.add("appmek");
        result.add("captured", captured);
        var path = directory.resolve("status-addon-keys.json");
        StandardAe2Scenario.writeAddonKeyEvidence(path, result);
        assertEquals(result, com.google.gson.JsonParser.parseString(Files.readString(path)));
        for (var unwritable : java.util.List.of(directory, directory.resolve("missing/keys.json"))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> StandardAe2Scenario.writeAddonKeyEvidence(unwritable, result));
            assertEquals("Cannot retain addon status key evidence", failure.getMessage());
            assertInstanceOf(java.io.IOException.class, failure.getCause());
        }
    }

    @Test void rejectsAnUnknownParticipantBeforeInteractingWithMinecraft() {
        for (var role : java.util.List.of("", "gamma")) {
            System.setProperty("ae2craftingtime.test.role", role);
            var failure = assertThrows(IllegalStateException.class, () -> tick(scenario()));
            assertEquals("Connected suspension needs Alpha/Beta role", failure.getMessage());
        }
    }

    @Test void waitsForAReadyMatchingCampaignAndAnActualJob() throws Exception {
        for (var role : java.util.List.of("alpha", "beta")) {
            System.setProperty("ae2craftingtime.test.role", role);
            state(false, "epoch", "{}");
            assertFalse(tick(scenario()));
            state(true, "other", "{}");
            assertFalse(tick(scenario()));
            state(true, "epoch", "");
            assertFalse(tick(scenario()));
            state(true, "epoch", "null");
            assertFalse(tick(scenario()));
            state(true, "epoch", "{\"jobId\":\"\"}");
            assertFalse(tick(scenario()));
        }
        assertEquals(java.util.Map.of("same-live-job", false), checks);
    }

    @Test void aChangedServerJobCannotBeAcceptedAsTheRememberedJob() throws Exception {
        System.setProperty("ae2craftingtime.test.role", "alpha");
        var scenario = scenario();
        var remembered = StandardAe2Scenario.class.getDeclaredField("suspensionLargeId");
        remembered.setAccessible(true);
        remembered.set(scenario, "original-job");
        state(true, "epoch", "{\"jobId\":\"different-job\"}");
        var failure = assertThrows(IllegalStateException.class, () -> tick(scenario));
        assertEquals("Connected suspension changed job identity", failure.getMessage());
        assertEquals(java.util.Map.of("same-live-job", false), checks);
    }

    @Test void storedVariantWaitsForAReadyMatchingCampaignBeforeReadingThePlayer() throws Exception {
        var stateDirectory = Files.createDirectories(directory.resolve("variant"));
        for (var ready : java.util.List.of(false, true)) {
            var state = new Properties();
            state.setProperty("ready", Boolean.toString(ready));
            state.setProperty("epoch", "other");
            try (var output = Files.newOutputStream(stateDirectory.resolve("state.properties"))) {
                state.store(output, null);
            }
            assertFalse(tick(new StandardAe2Scenario("stored-variant-plan", "world", directory, true)));
        }
        assertEquals(java.util.Map.of("same-live-job", false), checks);
    }

    @Test void cpuBadgeSelectionPreparesLoadedOptionsAndWaitsForServerReadiness() throws Exception {
        var features = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features();
        var feature = com.ctux.ae2craftingtime.core.OptionFeature.BADGE_BACKGROUND;
        boolean original = features.enabled(feature);
        try {
            for (var initial : java.util.List.of(false, true)) {
                features.setEnabled(feature, initial);
                var scenario = new StandardAe2Scenario("cpu-list-total-ttc", "world", directory, true);
                // Startup can load options after constructing the scenario.
                features.setEnabled(feature, initial);
                CpuListTtcScenario.prepareClientOptions(features);
                assertTrue(features.enabled(feature));
                assertFalse(tick(scenario));
            }
        } finally {
            features.setEnabled(feature, original);
        }
        assertEquals(java.util.Map.of("same-live-job", false), checks);
    }

    private StandardAe2Scenario scenario() {
        return new StandardAe2Scenario("crafting-suspension", "world", directory, true);
    }

    private boolean tick(StandardAe2Scenario scenario) throws Exception {
        return scenario.tick(null, null, checks,
                name -> fail("Unready or mismatched jobs must not produce captures: " + name),
                (x, y) -> fail("Unready or mismatched jobs must not move the mouse"));
    }

    private void state(boolean ready, String epoch, String job) throws Exception {
        var state = new Properties();
        state.setProperty("ready", Boolean.toString(ready));
        state.setProperty("epoch", epoch);
        state.setProperty("serverState", job);
        try (var output = Files.newOutputStream(directory.resolve("state.properties"))) {
            state.store(output, null);
        }
    }
}
