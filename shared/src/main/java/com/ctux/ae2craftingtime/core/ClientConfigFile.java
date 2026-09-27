package com.ctux.ae2craftingtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Small flat TOML reader/writer for client-owned options. */
public final class ClientConfigFile {
    private static final System.Logger LOGGER = System.getLogger("ae2craftingtime");
    private static final Map<OptionFeature, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(OptionFeature.PLAN_ROWS, "Show TTC estimates beside AE2 Crafting Plan rows. False hides only the added estimates."),
            Map.entry(OptionFeature.PLAN_TOTAL, "Show the TTC total in Crafting Plan. False hides only that added total."),
            Map.entry(OptionFeature.STATUS_ROWS, "Show TTC estimates beside AE2 Crafting Status rows. False hides only the added estimates."),
            Map.entry(OptionFeature.COMPACT_STATUS_AMOUNTS, "Shorten item and fluid amounts in Crafting Plan and Crafting Status TTC rows. False keeps full amounts."),
            Map.entry(OptionFeature.STATUS_TOTAL, "Show the TTC total in Crafting Status. False hides only that added total."),
            Map.entry(OptionFeature.CPU_CARD_TOTAL, "Show time totals on crafting CPU cards. False hides those totals locally."),
            Map.entry(OptionFeature.CRAFTING_TREE, "Show timing information in the optional Crafting Tree addon. False hides only this overlay; the addon still works."),
            Map.entry(OptionFeature.ME_REQUESTER, "Show timing information in the optional ME Requester addon. False hides only this overlay; the addon still works."),
            Map.entry(OptionFeature.TTC_COLORS, "Color time-to-craft text from fast to slow. False keeps native or neutral row colors."),
            Map.entry(OptionFeature.ACCURACY_DETAILS, "Show prediction accuracy details when the server records them. False hides details locally."),
            Map.entry(OptionFeature.DETAILED_TOOLTIPS, "Show expanded timing tooltips. False keeps shorter tooltips."),
            Map.entry(OptionFeature.CONTROL_HINTS, "Show hints for mod-added controls. False hides those hints locally."),
            Map.entry(OptionFeature.WAITING_STATUS, "Show Waiting status when server tracking supplies it. False hides the label locally."),
            Map.entry(OptionFeature.COLLECTING_STATUS, "Show Collecting status when available. False hides the label locally."),
            Map.entry(OptionFeature.DELAYED_STATUS, "Show Delayed status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.RECURRENT_STATUS, "Show Recurrent status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.NO_PROVIDER_STATUS, "Show No Provider status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.NO_POWER_STATUS, "Show No Power status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.NO_SPACE_STATUS, "Show No Space status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.NO_CHANNEL_STATUS, "Show No Channel status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.NO_TARGET_STATUS, "Show No Target status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.INPUT_BLOCKED_STATUS, "Show Input Blocked or Locked status when server detection supplies it. False hides the label locally."),
            Map.entry(OptionFeature.RECEIVE_CRAFT_WARNINGS, "Receive server craft warnings on this client. False mutes them locally; server delivery is separate."),
            Map.entry(OptionFeature.TEXT_SHADOW, "Add a shadow to mod-added text. False leaves that text without a shadow and preserves native shadows."),
            Map.entry(OptionFeature.SHOW_EMOJI, "Show emoji in mod-added status text. False uses text without emoji."),
            Map.entry(OptionFeature.BADGE_BACKGROUND, "Draw a background behind AE2 Crafting Time status badges. False leaves badges unboxed."),
            Map.entry(OptionFeature.PLAN_SORT_CONTROL, "Enable TTC sorting in Crafting Plan. False restores native AE2 order while retaining the saved sort mode."),
            Map.entry(OptionFeature.STATUS_SORT_CONTROL, "Enable TTC sorting in Crafting Status. False restores native AE2 order while retaining the saved sort mode."),
            Map.entry(OptionFeature.CPU_SORT_CONTROL, "Enable TTC sorting on crafting CPU cards. False restores native order while retaining the saved sort mode."),
            Map.entry(OptionFeature.TTC_DETAILS_CLICK, "Allow clicking timing details from supported rows. False disables this shortcut."),
            Map.entry(OptionFeature.RESET_HISTORY_CLICK, "Allow the reset-history shortcut where supported. False disables this shortcut, not server history."),
            Map.entry(OptionFeature.PROVIDER_LOCATE_CLICK, "Allow clicking to locate a provider from supported rows. False disables this shortcut."));
    public static ClientConfig load(Path path, Path legacyPath) throws IOException {
        var config = new ClientConfig();
        if (Files.isRegularFile(path)) {
            read(path, config, false);
        } else if (Files.isRegularFile(legacyPath)) {
            read(legacyPath, config, true);
        }
        return config;
    }

    public static void save(Path path, ClientConfig config) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("# AE2 Crafting Time client options");
        lines.add("# Local to this client: config/ae2craftingtime-client.toml.");
        lines.add("# Saving Client Options with Done creates this file; client startup alone does not.");
        lines.add("# Stop the client before external edits; they load on client restart. Done applies valid changes live.");
        lines.add("# If this file is absent, only showInTree migrates from config/ae2craftingtime-common.toml.");
        lines.add("# This client file wins over the common file; migration leaves the common file intact.");
        lines.add("# Loading an existing file never rewrites it to add comments.");
        var defaults = new FeatureOptions(OptionFeature.Owner.CLIENT);
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT) {
                lines.add("# " + Objects.requireNonNull(DESCRIPTIONS.get(feature)) + " Default: "
                        + defaults.enabled(feature) + ".");
                lines.add(feature.key() + " = " + config.features().enabled(feature));
            }
        }
        lines.add("# Initial Crafting Plan order: 0 AE2 order, 1 shortest first, 2 longest first. Default: 2.");
        lines.add("planSort = " + config.planSort());
        lines.add("# Initial Crafting Status order: 0 AE2 order, 1 shortest first, 2 longest first. Default: 2.");
        lines.add("statusSort = " + config.statusSort());
        lines.add("# Background alpha when badgeBackground is true: 0 transparent through 255 opaque. Default: 176.");
        lines.add("badgeOpacity = " + config.badgeOpacity());
        for (var color : ClientConfig.Color.values()) {
            lines.add("# " + colorDescription(color) + " Six-digit quoted RGB; default: \"#"
                    + String.format(Locale.ROOT, "%06X", color.defaultRgb()) + "\".");
            lines.add(colorKey(color) + " = \"#" + String.format(Locale.ROOT, "%06X", config.color(color)) + "\"");
        }
        saveLines(path, lines);
    }

    private static String colorDescription(ClientConfig.Color color) {
        return switch (color) {
            case FAST -> "Fast-end timing text color when ttcColors is true.";
            case MIDDLE -> "Middle timing text color when ttcColors is true.";
            case SLOW -> "Slow-end timing text color when ttcColors is true.";
            case WAITING -> "Retained Waiting color with no current rendering effect.";
            case DELAYED -> "Delayed and blocked-warning text color where custom status colors are used.";
            case COLLECTING -> "Collecting status text color where custom status colors are used.";
            case TOTAL -> "Total and neutral TTC text color where custom colors are used.";
            case BADGE -> "Badge background color when badgeBackground is true; badgeOpacity sets its alpha.";
        };
    }

    static void saveLines(Path path, ArrayList<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        var temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, lines);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void read(Path path, ClientConfig config, boolean legacyOnly) throws IOException {
        for (var line : Files.readAllLines(path)) {
            var parts = line.split("=", 2);
            if (parts.length != 2) continue;
            var key = parts[0].trim();
            var value = parts[1].trim();
            if (legacyOnly && !key.equals("showInTree")) continue;
            try {
                set(config, key, value);
            } catch (IllegalArgumentException error) {
                LOGGER.log(System.Logger.Level.WARNING, "Invalid client option {0} in {1}; using default",
                        key, path);
            }
        }
    }

    private static void set(ClientConfig config, String key, String value) {
        for (var feature : OptionFeature.values()) {
            if (feature.owner() == OptionFeature.Owner.CLIENT && feature.key().equals(key)) {
                if (value.equalsIgnoreCase("true")) config.features().setEnabled(feature, true);
                else if (value.equalsIgnoreCase("false")) config.features().setEnabled(feature, false);
                else throw new IllegalArgumentException("Expected boolean");
                return;
            }
        }
        switch (key) {
            case "planSort" -> config.setPlanSort(Integer.parseInt(value));
            case "statusSort" -> config.setStatusSort(Integer.parseInt(value));
            case "badgeOpacity" -> config.setBadgeOpacity(Integer.parseInt(value));
            default -> {
                for (var color : ClientConfig.Color.values()) {
                    if (colorKey(color).equals(key)) {
                        var rgb = value.replace("\"", "");
                        if (!rgb.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB");
                        config.setColor(color, Integer.parseInt(rgb.substring(1), 16));
                        return;
                    }
                }
            }
        }
    }

    private static String colorKey(ClientConfig.Color color) {
        return color.name().toLowerCase(Locale.ROOT) + "Color";
    }

    private ClientConfigFile() {
    }
}
