package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigFileTest {
    @TempDir Path directory;

    @Test
    void missingWorldFileWritesAllServerDefaults() throws IOException {
        var path = directory.resolve("world/serverconfig/server.toml");
        var legacy = directory.resolve("common.toml");
        assertTrue(ServerConfigFile.load(path, legacy).features().enabled(OptionFeature.PROFILING));
        var lines = Files.readAllLines(path);
        assertDocumentedSettings(lines);
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER) {
                assertTrue(lines.contains(feature.key() + " = "
                        + (feature != OptionFeature.RECURRENT_DETECTION
                        && feature != OptionFeature.CHANCE_OUTPUT_DETECTION)), feature.key());
            }
        }
        assertTrue(lines.contains("maxSamples = 10"));
        assertTrue(lines.contains("outlierMultiplier = 4.0"));
        assertTrue(lines.contains("minimumNoProgressSeconds = 10"));
        assertTrue(lines.contains("typicalDurationMultiplier = 2.0"));
        var generatedBytes = Files.readAllBytes(path);
        ServerConfigFile.load(path, legacy);
        assertArrayEquals(generatedBytes, Files.readAllBytes(path));
    }

    @Test
    void missingWorldFileMigratesLegacyWithoutChangingIt() throws IOException {
        var path = directory.resolve("world/serverconfig/server.toml");
        var legacy = directory.resolve("common.toml");
        Files.write(legacy, List.of("enabled = false", "notifyOnDelayed = false", "showChatMessages = false", "maxSamples = 20",
                "outlierMultiplier = 5.0", "showInTree = false", "unrelated = false"));
        var legacyBytes = Files.readAllBytes(legacy);
        var migrated = ServerConfigFile.load(path, legacy);
        assertFalse(migrated.features().enabled(OptionFeature.PROFILING));
        assertFalse(migrated.features().enabled(OptionFeature.NOTIFY_ON_DELAYED));
        assertFalse(migrated.features().enabled(OptionFeature.SHOW_CHAT_MESSAGES));
        assertEquals(20, migrated.maxSamples());
        assertEquals(5.0, migrated.outlierMultiplier());
        assertTrue(migrated.features().enabled(OptionFeature.SAVE_HISTORY));
        assertArrayEquals(legacyBytes, Files.readAllBytes(legacy));
        var lines = Files.readAllLines(path);
        assertTrue(lines.contains("enabled = false"));
        assertTrue(lines.contains("maxSamples = 20"));
        assertTrue(lines.contains("outlierMultiplier = 5.0"));
        assertTrue(lines.contains("saveHistory = true"));
        assertTrue(lines.contains("recurrentDetection = false"));
        assertFalse(lines.contains("showInTree = false"));
        assertDocumentedSettings(lines);
    }

    @Test
    void existingWorldFileKeepsExactBytesAcrossLoadsAndEdits() throws IOException {
        var path = directory.resolve("world/serverconfig/server.toml");
        var legacy = directory.resolve("common.toml");
        Files.createDirectories(path.getParent());
        Files.write(legacy, List.of("enabled = false"));
        var changed = new ServerConfig();
        changed.features().setEnabled(OptionFeature.SAVE_HISTORY, false);
        changed.setMaxSamples(100);
        changed.setOutlierMultiplier(1000.0);
        changed.setMinimumNoProgressSeconds(3600);
        changed.setTypicalDurationMultiplier(1.0);
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER)
                changed.features().setEnabled(feature, feature == OptionFeature.RECURRENT_DETECTION);
        }
        changed.features().setEnabled(OptionFeature.PROFILING, true);
        ServerConfigFile.save(path, changed);
        var loaded = ServerConfigFile.load(path, legacy);
        assertDocumentedSettings(Files.readAllLines(path));
        assertTrue(loaded.features().enabled(OptionFeature.PROFILING));
        assertFalse(loaded.features().enabled(OptionFeature.SAVE_HISTORY));
        assertEquals(100, loaded.maxSamples());
        assertEquals(1000.0, loaded.outlierMultiplier());
        assertEquals(3600, loaded.minimumNoProgressSeconds());
        assertEquals(1.0, loaded.typicalDurationMultiplier());
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER)
                assertEquals(changed.features().enabled(feature), loaded.features().enabled(feature), feature.key());
        }
        ServerConfigFile.save(path, loaded);
        assertDocumentedSettings(Files.readAllLines(path));
        var customBytes = ("# owner comment\nunknown = 42\nmaxSamples = bad\nenabled = false\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, customBytes);
        assertFalse(ServerConfigFile.load(path, legacy).features().enabled(OptionFeature.PROFILING));
        assertArrayEquals(customBytes, Files.readAllBytes(path));
        assertFalse(ServerConfigFile.load(path, legacy).features().enabled(OptionFeature.PROFILING));
        assertArrayEquals(customBytes, Files.readAllBytes(path));
    }

    @Test
    void existingWorldCanExplicitlyEnableRecurrentDetection() throws IOException {
        var path = directory.resolve("server.toml");
        Files.write(path, List.of("recurrentDetection = true"));
        var loaded = ServerConfigFile.load(path, directory.resolve("missing.toml"));
        assertTrue(loaded.features().enabled(OptionFeature.RECURRENT_DETECTION));
        loaded.reset();
        assertFalse(loaded.features().enabled(OptionFeature.RECURRENT_DETECTION));
    }

    @Test
    void failedGenerationKeepsMigratedValuesAndExistingDestination() throws IOException {
        var legacy = directory.resolve("common.toml");
        Files.write(legacy, List.of("enabled = false", "maxSamples = 20"));
        var parent = Files.createDirectory(directory.resolve("serverconfig"));
        var path = parent.resolve("s".repeat(245) + ".toml");
        assertTrue(Files.notExists(path));
        var loaded = ServerConfigFile.load(path, legacy);
        assertFalse(loaded.features().enabled(OptionFeature.PROFILING));
        assertEquals(20, loaded.maxSamples());
        assertTrue(Files.notExists(path));

        var occupied = directory.resolve("occupied");
        Files.createDirectory(occupied);
        assertFalse(ServerConfigFile.load(occupied, legacy).features().enabled(OptionFeature.PROFILING));
        assertTrue(Files.isDirectory(occupied));
    }

    @Test
    void readFailureDoesNotReplaceExistingWorldFile() throws IOException {
        var path = directory.resolve("server.toml");
        var invalidUtf8 = new byte[] {(byte) 0xC3, (byte) 0x28};
        Files.write(path, invalidUtf8);
        assertThrows(IOException.class, () -> ServerConfigFile.load(path, directory.resolve("missing.toml")));
        assertArrayEquals(invalidUtf8, Files.readAllBytes(path));
    }

    @Test
    void invalidFieldsFallBackIndividually() throws IOException {
        var path = directory.resolve("server.toml");
        Files.write(path, List.of("# comment", "not a setting", "unknown = 1", "enabled = FALSE",
                "showChatMessages = maybe", "saveHistory = true", "maxSamples = bad",
                "outlierMultiplier = NaN", "minimumNoProgressSeconds = 0",
                "typicalDurationMultiplier = Infinity", "noPowerDetection = false"));
        var loaded = ServerConfigFile.load(path, directory.resolve("missing.toml"));
        assertFalse(loaded.features().enabled(OptionFeature.PROFILING));
        assertTrue(loaded.features().enabled(OptionFeature.SHOW_CHAT_MESSAGES));
        assertTrue(loaded.features().enabled(OptionFeature.SAVE_HISTORY));
        assertFalse(loaded.features().enabled(OptionFeature.NO_POWER_DETECTION));
        assertEquals(10, loaded.maxSamples());
        assertEquals(4.0, loaded.outlierMultiplier());
        assertEquals(10, loaded.minimumNoProgressSeconds());
        assertEquals(2.0, loaded.typicalDurationMultiplier());
    }

    private static void assertDocumentedSettings(List<String> lines) {
        var expected = java.util.Arrays.stream(OptionFeature.values())
                .filter(feature -> feature.owner() == OptionFeature.Owner.SERVER)
                .map(OptionFeature::key).collect(Collectors.toSet());
        expected.addAll(List.of("maxSamples", "outlierMultiplier", "minimumNoProgressSeconds",
                "typicalDurationMultiplier"));
        var actual = lines.stream().filter(line -> !line.startsWith("#") && line.contains(" = "))
                .map(line -> line.substring(0, line.indexOf(" = "))).collect(Collectors.toList());
        assertEquals(expected, new java.util.HashSet<>(actual));
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("#") && lines.get(i).contains(" = ")) {
                assertTrue(i > 0 && lines.get(i - 1).startsWith("# ") && lines.get(i - 1).length() > 3,
                        lines.get(i));
            }
        }
    }
}
