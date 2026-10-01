package com.ctux.ae2craftingtime.testdriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small file rendezvous shared by a connected smoke client and its disposable server. */
public final class CpuListTtcControl {
    private static long clientSequence;
    private static long pendingSequence;
    private static String pending;
    private static final java.util.Map<Path, String> lastPublished = new java.util.HashMap<>();

    public static boolean enabled() { return !System.getProperty("ae2craftingtime.test.control", "").isBlank(); }

    static void validateDisposableServer(Path root, String target) {
        try {
            var marker = new com.google.gson.Gson().fromJson(Files.readString(
                    root.resolve(".ae2-crafting-time-dedicated-fixture.json")), ServerMarker.class);
            if (marker == null || marker.schema() != 2 || !"ae2-crafting-time".equals(marker.sourceFixtureId())
                    || !"disposable".equals(marker.role()) || !target.equals(marker.target())) {
                throw new IllegalStateException("Connected CPU-list driver requires a matching disposable server marker");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Connected CPU-list driver requires a disposable server marker", error);
        }
    }

    public static Path directory() {
        var value = System.getProperty("ae2craftingtime.test.control",
                System.getProperty("ae2ct.testDriver.serverControl", ""));
        if (value.isBlank()) throw new IllegalStateException("CPU-list connected control directory is missing");
        return Path.of(value).toAbsolutePath().normalize();
    }

    public static State state() {
        return state(directory());
    }

    public static State state(Path root) {
        var values = read(root.resolve("state.properties"));
        return new State(Boolean.parseBoolean(values.getProperty("ready", "false")),
                values.getProperty("epoch", ""), Long.parseLong(values.getProperty("ack", "0")),
                values.getProperty("action", ""), values.getProperty("phase", ""),
                Integer.parseInt(values.getProperty("x", "0")), Integer.parseInt(values.getProperty("y", "0")),
                Integer.parseInt(values.getProperty("z", "0")), values.getProperty("serverEstimates", ""),
                values.getProperty("serverState", ""));
    }

    public static boolean request(String action) {
        if (!action.equals(pending)) {
            pending = action;
            clientSequence = nextSequence(clientSequence, state().ack());
            pendingSequence = clientSequence;
            var command = new Properties();
            command.setProperty("epoch", epoch());
            command.setProperty("sequence", Long.toString(pendingSequence));
            command.setProperty("action", action);
            write(directory().resolve("command.properties"), command);
        }
        if (!acknowledges(state(), epoch(), pendingSequence, action)) return false;
        pending = null;
        return true;
    }

    static long nextSequence(long current, long acknowledged) {
        return Math.max(current, acknowledged) + 1;
    }

    static boolean acknowledges(State state, String epoch, long sequence, String action) {
        return state.ready() && state.epoch().equals(epoch) && state.ack() == sequence
                && state.action().equals(action);
    }

    static long clientSequence() { return clientSequence; }
    static String epoch() {
        var value = System.getProperty("ae2craftingtime.test.campaign",
                System.getProperty("ae2ct.testDriver.serverCampaign", ""));
        if (value.isBlank()) throw new IllegalStateException("CPU-list connected campaign identity is missing");
        return value;
    }

    public static Command command() {
        return command(directory());
    }

    public static Command command(Path root) {
        var values = read(root.resolve("command.properties"));
        return new Command(values.getProperty("epoch", ""),
                Long.parseLong(values.getProperty("sequence", "0")), values.getProperty("action", ""));
    }

    public static void publish(long ack, String action, String phase, net.minecraft.core.BlockPos terminal, String estimates,
            String serverState) {
        publish(directory(), ack, action, phase, terminal, estimates, serverState);
    }

    public static void publish(Path root, long ack, String action, String phase, net.minecraft.core.BlockPos terminal,
            String estimates, String serverState) {
        var state = new Properties();
        state.setProperty("ready", "true");
        state.setProperty("epoch", epoch());
        state.setProperty("ack", Long.toString(ack));
        state.setProperty("action", action);
        state.setProperty("phase", phase);
        state.setProperty("x", Integer.toString(terminal.getX()));
        state.setProperty("y", Integer.toString(terminal.getY()));
        state.setProperty("z", Integer.toString(terminal.getZ()));
        state.setProperty("serverEstimates", estimates);
        state.setProperty("serverState", serverState);
        write(root.resolve("state.properties"), state);
        var fingerprint = epoch() + "|" + ack + "|" + action + "|" + phase + "|" + serverState;
        if (!fingerprint.equals(lastPublished.get(root))) {
            lastPublished.put(root, fingerprint);
            try {
                var encoded = java.util.Base64.getEncoder().encodeToString(serverState.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Files.writeString(root.resolve("server-checkpoints.log"), epoch() + "\t" + ack + "\t"
                        + action + "\t" + phase + "\t" + encoded + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException error) { throw new IllegalStateException("Cannot retain server checkpoint", error); }
        }
    }

    private static Properties read(Path path) {
        var result = new Properties();
        if (!Files.isRegularFile(path)) return result;
        try (var input = Files.newInputStream(path)) { result.load(input); }
        catch (IOException error) { throw new IllegalStateException("Cannot read connected smoke control " + path, error); }
        return result;
    }

    private static void write(Path path, Properties values) {
        write(path, values, (source, target) -> Files.move(source, target,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE));
    }

    static void write(Path path, Properties values, DriverProgress.ProgressMover mover) {
        try {
            Files.createDirectories(path.getParent());
            var temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (var output = Files.newOutputStream(temporary)) { values.store(output, null); }
            DriverProgress.moveWithAccessDeniedRetry(temporary, path, mover);
        } catch (IOException error) { throw new IllegalStateException("Cannot write connected smoke control " + path, error); }
    }

    public record State(boolean ready, String epoch, long ack, String action, String phase,
            int x, int y, int z, String serverEstimates,
            String serverState) { }
    public record Command(String epoch, long sequence, String action) { }
    public record CpuState(String position, int serial, String jobId, long amount, boolean live, boolean busy,
            Long seconds, long elapsedNanos, long progress) { }
    public record ServerState(String network, int container, int selectedSerial, boolean stoneProfile, boolean smoothProfile,
            java.util.List<CpuState> cpus) { }
    private record ServerMarker(int schema, String sourceFixtureId, String role, String target) { }
    private CpuListTtcControl() { }
}
