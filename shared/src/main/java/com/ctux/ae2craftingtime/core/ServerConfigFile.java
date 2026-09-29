package com.ctux.ae2craftingtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/** World-scoped server values. Invalid fields keep their own defaults. */
public final class ServerConfigFile {
    private static final System.Logger LOGGER = System.getLogger("ae2craftingtime");
    private static final Map<OptionFeature, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(OptionFeature.PROFILING, "Collect server craft timing and derived TTC/diagnostics. False suppresses new profiling and those derived results."),
            Map.entry(OptionFeature.SAVE_HISTORY, "Persist learned timing history across world loads. False stops new history writes; existing saved history is not deleted."),
            Map.entry(OptionFeature.ACCURACY_RECORDING, "Record prediction accuracy for server diagnostics. False stops accuracy recording."),
            Map.entry(OptionFeature.WAITING_TRACKING, "Track crafts waiting for progress. False removes this server diagnostic from client status displays."),
            Map.entry(OptionFeature.DELAYED_DETECTION, "Classify crafts taking longer than the delay thresholds. False disables delayed status and related warnings."),
            Map.entry(OptionFeature.CHANCE_OUTPUT_DETECTION, "Experimental. Detects verified Mekanism Precision Sawmill secondary outputs only on Minecraft 1.20.1 Forge. Other machines are not detected yet; support may expand later. The client must also enable chanceOutputStatus. False disables Chance output status."),
            Map.entry(OptionFeature.RECURRENT_DETECTION, "Detect proven ingredient or recipe loops. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NO_PROVIDER_DETECTION, "Detect crafts with no available provider. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NO_POWER_DETECTION, "Detect crafts blocked by insufficient power. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NO_SPACE_DETECTION, "Detect crafts blocked by insufficient storage space. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NO_CHANNEL_DETECTION, "Detect crafts blocked by missing channels. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NO_TARGET_DETECTION, "Detect crafts with no valid crafting target. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.INPUT_BLOCKED_DETECTION, "Detect blocked inputs and locked providers. False disables this server diagnostic and its client status."),
            Map.entry(OptionFeature.NOTIFY_ON_DELAYED, "Send private delayed and blocked-craft warnings when detected and the client accepts them. False stops server warning delivery."),
            Map.entry(OptionFeature.SHOW_CHAT_MESSAGES, "Send server diagnostic and reset notices in chat. False hides notices but does not prevent a history reset."),
            Map.entry(OptionFeature.ADVANCED_AE, "Include AdvancedAE crafting CPUs in server profiling when that addon is installed. False skips its CPUs."),
            Map.entry(OptionFeature.NEO_ECO, "Include NeoEco crafting CPUs in server profiling when that addon is installed. False skips its CPUs."),
            Map.entry(OptionFeature.AE2_LIGHTNING_TECH, "Include AE2 Lightning Tech crafting CPUs in server profiling when that addon is installed. False skips its CPUs."),
            Map.entry(OptionFeature.APPLIED_MEKANISTICS, "Include Applied Mekanistics chemical statistics when that addon is installed. False skips chemical statistics."));
    public static ServerConfig load(Path path, Path legacyPath) throws IOException {
        var config = new ServerConfig();
        if (Files.isRegularFile(path)) read(path, config, false);
        else if (Files.isRegularFile(legacyPath)) read(legacyPath, config, true);
        if (Files.notExists(path)) {
            try { save(path, config); }
            catch (IOException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not create server options at " + path, error);
            }
        }
        return config;
    }

    public static void save(Path path, ServerConfig config) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("# AE2 Crafting Time server options");
        lines.add("# World-owned: <world>/serverconfig/ae2craftingtime-server.toml (<world> is level-name on a dedicated server).");
        lines.add("# A missing file is created when the world starts. Stop the world/server before editing this file.");
        lines.add("# External edits load at world/server startup; valid Server Options saved with Done apply live.");
        lines.add("# If this file is absent, known values migrate from config/ae2craftingtime-common.toml without changing it.");
        lines.add("# This world file wins over the common file. Loading an existing file never rewrites it.");
        var defaults = new FeatureOptions(OptionFeature.Owner.SERVER);
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER) {
                lines.add("# " + Objects.requireNonNull(DESCRIPTIONS.get(feature)) + " Default: "
                        + defaults.enabled(feature) + ".");
                lines.add(feature.key() + " = " + config.features().enabled(feature));
            }
        }
        lines.add("# Maximum recent timing, throughput and accuracy samples per output, including chemicals (1-100). Smaller windows adapt faster. Default: 10.");
        lines.add("maxSamples = " + config.maxSamples());
        lines.add("# Filter unusually fast and slow duration-per-unit samples beyond this median multiple once at least five exist (finite 1.0-1000.0). Default: 4.0.");
        lines.add("outlierMultiplier = " + config.outlierMultiplier());
        lines.add("# Minimum time without crafting progress for delayed classification, in seconds (1-3600). Default: 10.");
        lines.add("minimumNoProgressSeconds = " + config.minimumNoProgressSeconds());
        lines.add("# Delay threshold also considers this multiple of learned typical duration; the greater threshold wins (finite 1.0-1000.0). Default: 2.0.");
        lines.add("typicalDurationMultiplier = " + config.typicalDurationMultiplier());
        ClientConfigFile.saveLines(path, lines);
    }

    private static void read(Path path, ServerConfig config, boolean legacyOnly) throws IOException {
        for (var line : Files.readAllLines(path)) {
            var parts = line.split("=", 2);
            if (parts.length != 2) continue;
            var key = parts[0].trim();
            if (legacyOnly && !legacyKey(key)) continue;
            try { set(config, key, parts[1].trim()); }
            catch (IllegalArgumentException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Invalid server option {0} in {1}; using default",
                        key, path);
            }
        }
    }

    private static boolean legacyKey(String key) {
        return key.equals("enabled") || key.equals("notifyOnDelayed") || key.equals("showChatMessages")
                || key.equals("maxSamples") || key.equals("outlierMultiplier");
    }

    private static void set(ServerConfig config, String key, String value) {
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.SERVER && feature.key().equals(key)) {
                if (value.equalsIgnoreCase("true")) config.features().setEnabled(feature, true);
                else if (value.equalsIgnoreCase("false")) config.features().setEnabled(feature, false);
                else throw new IllegalArgumentException("Expected boolean");
                return;
            }
        }
        switch (key) {
            case "maxSamples" -> config.setMaxSamples(Integer.parseInt(value));
            case "outlierMultiplier" -> config.setOutlierMultiplier(Double.parseDouble(value));
            case "minimumNoProgressSeconds" -> config.setMinimumNoProgressSeconds(Integer.parseInt(value));
            case "typicalDurationMultiplier" -> config.setTypicalDurationMultiplier(Double.parseDouble(value));
            default -> { }
        }
    }

    private ServerConfigFile() { }
}
