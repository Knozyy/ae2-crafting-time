package com.ctux.ae2craftingtime.testdriver;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerPlayer;

/** Development-only counts at the guarded wrapper and loader send invocation. */
public final class ConnectionObservation {
    private static final Map<String, Integer> ATTEMPTED = new TreeMap<>();
    private static final Map<String, Integer> SENT = new TreeMap<>();
    private static String readyAt;
    private static int connectionOrdinal = 1;

    public static synchronized void ready() {
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection")) return;
        if (readyAt == null) readyAt = Instant.now().toString();
        flush();
    }

    public static synchronized void beginConnection() {
        // Login listeners can run after production sends. Never erase those counts.
        ready();
    }

    public static synchronized void endConnection() {
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection")) return;
        flush();
        connectionOrdinal++;
        ATTEMPTED.clear();
        SENT.clear();
        readyAt = Instant.now().toString();
        flush();
    }

    public static void attempted(String direction, String type, ServerPlayer recipient) {
        record(ATTEMPTED, direction, type, recipient);
    }

    public static void sent(String direction, String type, ServerPlayer recipient) {
        record(SENT, direction, type, recipient);
    }

    private static synchronized void record(Map<String, Integer> counts, String direction, String type,
            ServerPlayer recipient) {
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection")) return;
        if (readyAt == null) readyAt = Instant.now().toString();
        String peer = recipient == null ? "server" : recipient.getUUID().toString();
        counts.merge(direction + ":" + type + ":" + peer, 1, Integer::sum);
        flush();
    }

    private static void flush() {
        String output = System.getProperty("ae2craftingtime.test.observationFile", "");
        if (output.isBlank()) throw new IllegalStateException("Connection observation requires an output file");
        var root = new JsonObject();
        root.addProperty("target", System.getProperty("ae2craftingtime.test.target", ""));
        root.addProperty("role", System.getProperty("ae2craftingtime.test.role", ""));
        root.addProperty("connectionEpoch", System.getProperty("ae2craftingtime.test.connectionEpoch", "")
                + ":" + connectionOrdinal);
        root.addProperty("productionSha256", System.getProperty("ae2craftingtime.test.productionSha256", ""));
        root.addProperty("driverSha256", System.getProperty("ae2craftingtime.test.driverSha256", ""));
        root.addProperty("observerReadyAt", readyAt);
        root.addProperty("flushedAt", Instant.now().toString());
        var attempted = new JsonObject();
        ATTEMPTED.forEach(attempted::addProperty);
        root.add("attempted", attempted);
        var sent = new JsonObject();
        SENT.forEach(sent::addProperty);
        root.add("sent", sent);
        try {
            var path = Path.of(output.replace("{epoch}", Integer.toString(connectionOrdinal))).toAbsolutePath();
            Files.createDirectories(path.getParent());
            var temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot write connection observation", error);
        }
    }

    private ConnectionObservation() { }
}
