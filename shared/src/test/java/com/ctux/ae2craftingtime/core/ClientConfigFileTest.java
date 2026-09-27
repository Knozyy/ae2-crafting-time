package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientConfigFileTest {
    @TempDir Path directory;

    @Test
    void missingAndLegacyFilesUseSafeDefaults() throws IOException {
        var path = directory.resolve("client.toml");
        var legacy = directory.resolve("common.toml");
        assertTrue(ClientConfigFile.load(path, legacy).features().enabled(OptionFeature.CRAFTING_TREE));
        assertFalse(ClientConfigFile.load(path, legacy).features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
        assertFalse(ClientConfigFile.load(path, legacy).features().enabled(OptionFeature.TEXT_SHADOW));
        assertFalse(ClientConfigFile.load(path, legacy).features().enabled(OptionFeature.TTC_COLORS));
        assertTrue(ClientConfigFile.load(path, legacy).features().enabled(OptionFeature.SHOW_EMOJI));
        assertFalse(ClientConfigFile.load(path, legacy).badgeBackground());
        Files.write(legacy, List.of("enabled = false", "showInTree = false"));
        var migrated = ClientConfigFile.load(path, legacy);
        assertFalse(migrated.features().enabled(OptionFeature.CRAFTING_TREE));
        assertTrue(migrated.features().enabled(OptionFeature.PLAN_ROWS));
        assertFalse(migrated.features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
        assertFalse(migrated.badgeBackground());
    }

    @Test
    void validValuesRoundTripAndClientFileWinsOverLegacy() throws IOException {
        var path = directory.resolve("nested/client.toml");
        var legacy = directory.resolve("common.toml");
        Files.write(legacy, List.of("showInTree = true"));
        var config = new ClientConfig();
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT)
                config.features().setEnabled(feature, !config.features().enabled(feature));
        }
        config.features().setEnabled(OptionFeature.RECEIVE_CRAFT_WARNINGS, false);
        config.features().setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
        config.features().setEnabled(OptionFeature.TEXT_SHADOW, true);
        config.features().setEnabled(OptionFeature.TTC_COLORS, true);
        config.features().setEnabled(OptionFeature.SHOW_EMOJI, false);
        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
        config.setPlanSort(0);
        config.setStatusSort(1);
        config.setBadgeOpacity(0);
        for (var color : ClientConfig.Color.values()) config.setColor(color, 0x123456 + color.ordinal());
        config.setColor(ClientConfig.Color.BADGE, 0x654321);
        ClientConfigFile.save(path, config);
        assertDocumentedSettings(Files.readAllLines(path));
        var originalBytes = Files.readAllBytes(path);
        var loaded = ClientConfigFile.load(path, legacy);
        assertArrayEquals(originalBytes, Files.readAllBytes(path));
        assertFalse(loaded.features().enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS));
        assertTrue(loaded.features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
        assertTrue(loaded.features().enabled(OptionFeature.TEXT_SHADOW));
        assertTrue(loaded.features().enabled(OptionFeature.TTC_COLORS));
        assertFalse(loaded.features().enabled(OptionFeature.SHOW_EMOJI));
        assertTrue(loaded.badgeBackground());
        assertFalse(loaded.features().enabled(OptionFeature.CRAFTING_TREE));
        assertEquals(0, loaded.planSort());
        assertEquals(1, loaded.statusSort());
        assertEquals(0, loaded.badgeOpacity());
        assertEquals(0x123456, loaded.color(ClientConfig.Color.FAST));
        assertEquals(0x654321, loaded.color(ClientConfig.Color.BADGE));
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT)
                assertEquals(config.features().enabled(feature), loaded.features().enabled(feature), feature.key());
        }
        for (var color : ClientConfig.Color.values()) assertEquals(config.color(color), loaded.color(color), color.name());
        ClientConfigFile.save(path, loaded);
        assertDocumentedSettings(Files.readAllLines(path));
    }

    @Test
    void malformedFieldsFallBackIndividually() throws IOException {
        var path = directory.resolve("client.toml");
        Files.write(path, List.of(
                "# comment", "unknown = ignored", "not a setting", "planRows = false",
                "receiveCraftWarnings = maybe", "showInTree = TRUE", "textShadow = maybe", "showEmoji = maybe",
                "badgeBackground = maybe", "statusSort = 9",
                "planSort = nope", "badgeOpacity = 999", "fastColor = \"broken\"",
                "middleColor = \"#ABCDEF\"", "slowColor = \"#000000\""));
        var config = ClientConfigFile.load(path, directory.resolve("missing.toml"));
        assertFalse(config.features().enabled(OptionFeature.PLAN_ROWS));
        assertTrue(config.features().enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS));
        assertTrue(config.features().enabled(OptionFeature.CRAFTING_TREE));
        assertFalse(config.features().enabled(OptionFeature.TEXT_SHADOW));
        assertTrue(config.features().enabled(OptionFeature.SHOW_EMOJI));
        assertFalse(config.badgeBackground());
        assertEquals(2, config.planSort());
        assertEquals(2, config.statusSort());
        assertEquals(176, config.badgeOpacity());
        assertEquals(ClientConfig.Color.FAST.defaultRgb(), config.color(ClientConfig.Color.FAST));
        assertEquals(0xABCDEF, config.color(ClientConfig.Color.MIDDLE));
        assertEquals(0, config.color(ClientConfig.Color.SLOW));
    }

    @Test
    void generatedDefaultsHaveCommentsAndExpectedValues() throws IOException {
        var path = directory.resolve("client.toml");
        ClientConfigFile.save(path, new ClientConfig());
        var lines = Files.readAllLines(path);
        assertDocumentedSettings(lines);
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT) {
                var off = java.util.Set.of(OptionFeature.COMPACT_STATUS_AMOUNTS, OptionFeature.TTC_COLORS,
                        OptionFeature.TEXT_SHADOW, OptionFeature.BADGE_BACKGROUND).contains(feature);
                assertTrue(lines.contains(feature.key() + " = " + !off), feature.key());
            }
        }
        assertTrue(lines.contains("planSort = 2"));
        assertTrue(lines.contains("statusSort = 2"));
        assertTrue(lines.contains("badgeOpacity = 176"));
        for (var color : ClientConfig.Color.values()) {
            assertTrue(lines.contains(color.name().toLowerCase(java.util.Locale.ROOT) + "Color = \"#"
                    + String.format(java.util.Locale.ROOT, "%06X", color.defaultRgb()) + "\""));
        }
    }

    private static void assertDocumentedSettings(List<String> lines) {
        var expected = java.util.Arrays.stream(OptionFeature.values())
                .filter(feature -> feature.owner() == OptionFeature.Owner.CLIENT)
                .map(OptionFeature::key).collect(Collectors.toSet());
        expected.addAll(List.of("planSort", "statusSort", "badgeOpacity"));
        for (var color : ClientConfig.Color.values())
            expected.add(color.name().toLowerCase(java.util.Locale.ROOT) + "Color");
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
