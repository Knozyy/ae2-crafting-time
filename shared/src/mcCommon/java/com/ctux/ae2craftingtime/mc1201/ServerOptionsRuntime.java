package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.core.ServerConfig;
import com.ctux.ae2craftingtime.core.ServerConfigFile;
import com.ctux.ae2craftingtime.core.ServerOptionsWire;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsSnapshotS2C;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.LoggerFactory;

/** The logical server owns the effective values for one world. */
public final class ServerOptionsRuntime {
    private static ServerConfig current = new ServerConfig();
    private static Path file;
    private static MinecraftServer activeServer;
    private static int revision;

    public static void initialize(MinecraftServer server, Path legacyFile) {
        activeServer = server;
        file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/ae2craftingtime-server.toml");
        revision = 0;
        try {
            current = ServerConfigFile.load(file, legacyFile);
        } catch (IOException error) {
            LoggerFactory.getLogger("ae2craftingtime").warn("Could not read server options; using defaults", error);
            current = new ServerConfig();
        }
    }

    public static ServerConfig current() { return current; }

    public static boolean enabled(OptionFeature feature) { return current.features().enabled(feature); }

    public static boolean scopeEnabled(Object scope) {
        if (scope == null) return true;
        String type = scope.getClass().getName();
        if (type.startsWith("net.pedroksl.advanced_ae.")) return enabled(OptionFeature.ADVANCED_AE);
        if (type.startsWith("cn.dancingsnow.neoecoae.")) return enabled(OptionFeature.NEO_ECO);
        if (type.startsWith("com.moakiee.ae2lt.")) return enabled(OptionFeature.AE2_LIGHTNING_TECH);
        return true;
    }

    public static boolean keyEnabled(appeng.api.stacks.AEKey key) {
        return key == null || !key.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("mekanism")
                || enabled(OptionFeature.APPLIED_MEKANISTICS);
    }

    public static void sendTo(ServerPlayer player) {
        StatsNetwork.sendTo(player, new ServerOptionsSnapshotS2C(ServerOptionsWire.encode(
                new ServerOptionsWire.Snapshot(revision, ServerOptionsPermission.canEdit(activeServer, player), current))));
    }

    public static void accept(ServerPlayer sender, byte[] bytes) {
        if (file == null || activeServer == null) return;
        if (!ServerOptionsPermission.canEdit(activeServer, sender)) {
            sendTo(sender);
            return;
        }
        try {
            var update = ServerOptionsWire.decode(bytes);
            if (update.editable() || update.revision() != revision) {
                sendTo(sender);
                return;
            }
            ServerConfigFile.save(file, update.config());
            current = update.config().copy();
            ProfilerBridge.clearChanceEvidence();
            ProfilerBridge.configure(current);
            revision++;
            for (var player : activeServer.getPlayerList().getPlayers()) sendTo(player);
        } catch (IllegalArgumentException | IOException error) {
            LoggerFactory.getLogger("ae2craftingtime").warn("Rejected server options update", error);
            sendTo(sender);
        }
    }

    public static void clear() {
        current = new ServerConfig();
        file = null;
        activeServer = null;
        revision = 0;
    }

    private ServerOptionsRuntime() { }
}
