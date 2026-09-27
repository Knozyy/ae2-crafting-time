package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.ClientConfigFile;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.TtcColor;
import com.ctux.ae2craftingtime.mc1201.net.WarningPreferenceC2S;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import org.slf4j.LoggerFactory;

/** Client-only loaded state; the screen edits a copy and applies it after a successful save. */
public final class ClientOptionsRuntime {
    private static ClientConfig current = new ClientConfig();
    private static Path path;
    private static BooleanSupplier connectionSupport = () -> StatsNetwork.canSend();

    static void setConnectionSupportForTests(BooleanSupplier support) {
        connectionSupport = support == null ? () -> StatsNetwork.canSend() : support;
    }

    public static void initialize(Path configDirectory) {
        path = configDirectory.resolve("ae2craftingtime-client.toml");
        try {
            current = ClientConfigFile.load(path, configDirectory.resolve("ae2craftingtime-common.toml"));
        } catch (IOException error) {
            LoggerFactory.getLogger("ae2craftingtime").warn("Could not read client options; using defaults", error);
            current = new ClientConfig();
        }
        TtcBadge.BACKGROUND = badgeBackground();
    }

    public static ClientConfig current() { return current; }

    public static boolean textShadow() { return current.features().enabled(OptionFeature.TEXT_SHADOW); }

    public static boolean profilingEnabled() {
        if (!connectionSupport.getAsBoolean()) return false;
        var snapshot = ClientServerOptions.snapshot();
        return snapshot == null || snapshot.config().features().enabled(OptionFeature.PROFILING);
    }

    public static boolean enabled(OptionFeature feature) {
        if (!connectionSupport.getAsBoolean() || !current.features().enabled(feature)) return false;
        var snapshot = ClientServerOptions.snapshot();
        if (snapshot != null && feature != OptionFeature.RECEIVE_CRAFT_WARNINGS
                && !profilingEnabled()) return false;
        var serverFeature = switch (feature) {
            case ACCURACY_DETAILS -> OptionFeature.ACCURACY_RECORDING;
            case WAITING_STATUS -> OptionFeature.WAITING_TRACKING;
            case DELAYED_STATUS -> OptionFeature.DELAYED_DETECTION;
            case RECURRENT_STATUS -> OptionFeature.RECURRENT_DETECTION;
            case NO_PROVIDER_STATUS -> OptionFeature.NO_PROVIDER_DETECTION;
            case NO_POWER_STATUS -> OptionFeature.NO_POWER_DETECTION;
            case NO_SPACE_STATUS -> OptionFeature.NO_SPACE_DETECTION;
            case NO_CHANNEL_STATUS -> OptionFeature.NO_CHANNEL_DETECTION;
            case NO_TARGET_STATUS -> OptionFeature.NO_TARGET_DETECTION;
            case INPUT_BLOCKED_STATUS -> OptionFeature.INPUT_BLOCKED_DETECTION;
            default -> null;
        };
        return serverFeature == null || snapshot == null || snapshot.config().features().enabled(serverFeature);
    }

    public static int ttcColor(long seconds, long min, long max) {
        if (!enabled(OptionFeature.TTC_COLORS)) return current.color(ClientConfig.Color.TOTAL);
        return TtcColor.forSeconds(seconds, min, max,
                current.color(ClientConfig.Color.FAST), current.color(ClientConfig.Color.MIDDLE),
                current.color(ClientConfig.Color.SLOW));
    }

    public static int badgeBackground() {
        return current.badgeOpacity() << 24 | current.color(ClientConfig.Color.BADGE);
    }

    public static void apply(ClientConfig changed) throws IOException {
        var emojiChanged = current.features().enabled(OptionFeature.SHOW_EMOJI)
                != changed.features().enabled(OptionFeature.SHOW_EMOJI);
        ClientConfigFile.save(path, changed);
        current = changed.copy();
        TtcBadge.BACKGROUND = badgeBackground();
        if (emojiChanged) Minecraft.getInstance().gui.getChat().rescaleChat();
        syncWarningPreference();
    }

    public static void syncWarningPreference() {
        if (Minecraft.getInstance().getConnection() != null) {
            StatsNetwork.sendToServer(new WarningPreferenceC2S(
                    current.features().enabled(OptionFeature.RECEIVE_CRAFT_WARNINGS)));
        }
    }

    private ClientOptionsRuntime() {
    }
}
