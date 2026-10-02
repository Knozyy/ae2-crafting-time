package com.ctux.ae2craftingtime.testdriver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class ResourceFixtureControl {
    static final int SCHEMA = 1;
    static final int MAX_BYTES = 64 * 1024;
    private static final Set<String> COMMAND_KEYS = Set.of(
            "schema", "epoch", "scenario", "player", "fixture", "revision", "sequence", "action", "case", "slot");
    private static final Set<String> STATE_KEYS = Set.of(
            "schema", "epoch", "scenario", "player", "fixture", "revision", "ack", "ackRevision", "action", "case",
            "slot", "phase", "failure", "terminal", "providers", "jobs");
    private static final Set<String> CLIENT_EVIDENCE_KEYS = Set.of(
            "schema", "epoch", "scenario", "player", "fixture", "revision", "captures", "digest");
    private static final Set<String> ABORT_KEYS = Set.of(
            "schema", "epoch", "scenario", "player", "fixture", "revision", "failure");

    enum Action { CREATE, RELEASE, CANCEL, REJOIN_PREPARE, RECONNECT, UNLOAD_RELOAD, REMOVE_PROVIDER, RESET, COMPLETE, ABORT }
    enum Case { ITEM, WATER, LAVA, BUCKETLESS, FLUID_OVERLAP, OXYGEN, HYDROGEN, CHEMICAL_OVERLAP }
    enum Phase { READY, HELD, REJOINING, SETTLED, CLEAN, COMPLETE, FAILED }

    record Command(UUID epoch, String scenario, UUID player, UUID fixture, long revision, long sequence,
            Action action, Case resourceCase, int slot) { }
    record State(UUID epoch, String scenario, UUID player, UUID fixture, long revision, long ack, long ackRevision,
            Action action, Case resourceCase, int slot, Phase phase, String failure, String terminal,
            String providers, String jobs) { }
    record ClientEvidence(UUID epoch, String scenario, UUID player, UUID fixture, long revision, int captures,
            String digest) { }
    record Abort(UUID epoch, String scenario, UUID player, UUID fixture, long revision, String failure) { }

    record Decision(boolean replay, Phase nextPhase, long nextRevision) {
        static Decision accept(Phase phase, long revision) { return new Decision(false, phase, revision); }
        static Decision replay(Phase phase, long revision) { return new Decision(true, phase, revision); }
    }

    static boolean reconnectReady(boolean disconnected, boolean newPlayer,
            java.util.function.BooleanSupplier delayed) {
        return disconnected && newPlayer && delayed.getAsBoolean();
    }

    static Command readCommand(Path controlRoot) {
        return decodeCommand(read(controlFile(controlRoot, "command.properties")));
    }

    static State readState(Path controlRoot) {
        return decodeState(read(controlFile(controlRoot, "state.properties")));
    }

    static ClientEvidence readClientEvidence(Path controlRoot) {
        var values = read(controlFile(controlRoot, "client-evidence.properties"));
        exact(values, CLIENT_EVIDENCE_KEYS);
        schema(values);
        int captures = Integer.parseInt(values.get("captures"));
        if (captures <= 0 || captures > 128) throw new IllegalArgumentException("invalid resource capture count");
        var digest = values.get("digest");
        if (!digest.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("invalid resource evidence digest");
        return new ClientEvidence(uuid(values, "epoch"), token(values, "scenario"), uuid(values, "player"),
                uuid(values, "fixture"), positive(values, "revision"), captures, digest);
    }

    static Abort readAbort(Path controlRoot) {
        var values = read(controlFile(controlRoot, "abort.properties"));
        exact(values, ABORT_KEYS);
        schema(values);
        return new Abort(uuid(values, "epoch"), token(values, "scenario"), uuid(values, "player"),
                uuid(values, "fixture"), positive(values, "revision"), bounded(values, "failure", 4096));
    }

    static void writeCommand(Path controlRoot, Command command) {
        var values = new LinkedHashMap<String, String>();
        values.put("schema", Integer.toString(SCHEMA));
        values.put("epoch", command.epoch().toString());
        values.put("scenario", command.scenario());
        values.put("player", command.player().toString());
        values.put("fixture", command.fixture().toString());
        values.put("revision", Long.toString(command.revision()));
        values.put("sequence", Long.toString(command.sequence()));
        values.put("action", wire(command.action()));
        values.put("case", wire(command.resourceCase()));
        values.put("slot", Integer.toString(command.slot()));
        write(controlFile(controlRoot, "command.properties"), values);
    }

    static void writeState(Path controlRoot, State state) {
        var values = new LinkedHashMap<String, String>();
        values.put("schema", Integer.toString(SCHEMA));
        values.put("epoch", state.epoch().toString());
        values.put("scenario", state.scenario());
        values.put("player", state.player().toString());
        values.put("fixture", state.fixture().toString());
        values.put("revision", Long.toString(state.revision()));
        values.put("ack", Long.toString(state.ack()));
        values.put("ackRevision", Long.toString(state.ackRevision()));
        values.put("action", wire(state.action()));
        values.put("case", wire(state.resourceCase()));
        values.put("slot", Integer.toString(state.slot()));
        values.put("phase", state.phase().name());
        values.put("failure", state.failure());
        values.put("terminal", state.terminal());
        values.put("providers", state.providers());
        values.put("jobs", state.jobs());
        write(controlFile(controlRoot, "state.properties"), values);
    }

    static void writeClientEvidence(Path controlRoot, ClientEvidence evidence) {
        var values = new LinkedHashMap<String, String>();
        values.put("schema", Integer.toString(SCHEMA));
        values.put("epoch", evidence.epoch().toString());
        values.put("scenario", evidence.scenario());
        values.put("player", evidence.player().toString());
        values.put("fixture", evidence.fixture().toString());
        values.put("revision", Long.toString(evidence.revision()));
        values.put("captures", Integer.toString(evidence.captures()));
        values.put("digest", evidence.digest());
        write(controlFile(controlRoot, "client-evidence.properties"), values);
    }

    static void writeAbort(Path controlRoot, Abort abort) {
        var values = new LinkedHashMap<String, String>();
        values.put("schema", Integer.toString(SCHEMA));
        values.put("epoch", abort.epoch().toString());
        values.put("scenario", abort.scenario());
        values.put("player", abort.player().toString());
        values.put("fixture", abort.fixture().toString());
        values.put("revision", Long.toString(abort.revision()));
        values.put("failure", abort.failure());
        write(controlFile(controlRoot, "abort.properties"), values);
    }

    static Decision decide(State state, Command command, Command lastAccepted, Command operationInFlight) {
        if (lastAccepted != null && command.sequence() == lastAccepted.sequence()) {
            if (!command.equals(lastAccepted)) throw new IllegalArgumentException("conflicting resource command replay");
            return Decision.replay(state.phase(), state.revision());
        }
        if (operationInFlight != null) {
            if (!command.equals(operationInFlight)) {
                throw new IllegalStateException("another resource operation is in flight");
            }
            return Decision.replay(state.phase(), state.revision());
        }
        if (!command.epoch().equals(state.epoch()) || !command.scenario().equals(state.scenario())
                || !command.player().equals(state.player()) || !command.fixture().equals(state.fixture())) {
            throw new IllegalArgumentException("resource command identity mismatch");
        }
        if (command.revision() != state.revision()) throw new IllegalArgumentException("resource command revision mismatch");
        if (state.ack() == Long.MAX_VALUE || command.sequence() != state.ack() + 1 || command.sequence() <= 0) {
            throw new IllegalArgumentException("resource command sequence gap or overflow");
        }
        if (command.action() != Action.CREATE && command.resourceCase() != state.resourceCase()) {
            throw new IllegalArgumentException("resource case changed during fixture lifecycle");
        }
        if (command.slot() != 0 && command.action() != Action.RELEASE && command.action() != Action.CANCEL) {
            throw new IllegalArgumentException("resource action requires slot zero");
        }
        if (command.slot() == 1 && command.resourceCase() != Case.FLUID_OVERLAP
                && command.resourceCase() != Case.CHEMICAL_OVERLAP) {
            throw new IllegalArgumentException("resource slot one requires an overlap case");
        }
        return switch (command.action()) {
            case CREATE -> require(state.phase(), EnumSet.of(Phase.READY, Phase.CLEAN), Phase.HELD, state.revision());
            case RELEASE, CANCEL -> require(state.phase(), EnumSet.of(Phase.HELD), Phase.SETTLED, state.revision());
            case REJOIN_PREPARE -> require(state.phase(), EnumSet.of(Phase.HELD), Phase.REJOINING, state.revision());
            case RECONNECT -> require(state.phase(), EnumSet.of(Phase.REJOINING), Phase.HELD, state.revision());
            case UNLOAD_RELOAD -> require(state.phase(), EnumSet.of(Phase.HELD), Phase.HELD, state.revision());
            case REMOVE_PROVIDER -> require(state.phase(), EnumSet.of(Phase.HELD), Phase.HELD, state.revision());
            case RESET -> require(state.phase(), EnumSet.of(Phase.SETTLED), Phase.CLEAN, increment(state.revision()));
            case COMPLETE -> require(state.phase(), EnumSet.of(Phase.CLEAN), Phase.COMPLETE, state.revision());
            case ABORT -> require(state.phase(), EnumSet.complementOf(EnumSet.of(Phase.COMPLETE, Phase.FAILED)),
                    Phase.FAILED, increment(state.revision()));
        };
    }

    static Decision decide(State state, Command command, Command lastAccepted, Command operationInFlight,
            Decision acceptedDecision) {
        var decision = decide(state, command, lastAccepted, operationInFlight);
        if (operationInFlight == null) return decision;
        if (acceptedDecision == null) {
            throw new IllegalStateException("resource in-flight operation lost its accepted transition");
        }
        return acceptedDecision;
    }

    static Decision decide(State state, Command command, Command lastAccepted, boolean operationInFlight) {
        return decide(state, command, lastAccepted, operationInFlight ? commandWithDifferentSequence(command) : null);
    }

    static void requireAcknowledgement(State state, Command command) {
        long expectedRevision = command.action() == Action.RESET || command.action() == Action.ABORT
                ? increment(command.revision()) : command.revision();
        if (!state.epoch().equals(command.epoch()) || !state.scenario().equals(command.scenario())
                || !state.player().equals(command.player()) || !state.fixture().equals(command.fixture())
                || state.revision() != expectedRevision || state.ack() != command.sequence()
                || state.ackRevision() != command.revision()
                || state.action() != command.action() || state.resourceCase() != command.resourceCase()
                || state.slot() != command.slot() || !acknowledgedPhase(command.action(), state.phase())) {
            throw new IllegalArgumentException("resource acknowledgement does not bind the complete command");
        }
    }

    static void requireInitialIdentity(State state, UUID epoch, UUID fixture, UUID player, String scenario) {
        if (!state.epoch().equals(epoch) || !state.fixture().equals(fixture) || !state.player().equals(player)
                || !state.scenario().equals(scenario) || state.revision() != 1 || state.ack() != 0) {
            throw new IllegalArgumentException("resource initial state does not match its launch identity");
        }
    }

    static void requireClientEvidence(State state, ClientEvidence evidence, int expectedCaptures) {
        if (state.phase() != Phase.CLEAN || !state.epoch().equals(evidence.epoch())
                || !state.scenario().equals(evidence.scenario()) || !state.player().equals(evidence.player())
                || !state.fixture().equals(evidence.fixture()) || state.revision() != evidence.revision()
                || evidence.captures() != expectedCaptures) {
            throw new IllegalArgumentException("resource client evidence does not bind the final clean state");
        }
    }

    static List<String> expectedCheckpoints(Case resourceCase, boolean connected) {
        return expectedCheckpoints(resourceCase, connected, false);
    }

    static List<String> expectedCheckpoints(Case resourceCase, boolean connected, boolean production) {
        var values = new java.util.ArrayList<String>();
        values.add("held");
        if (production && resourceCase == Case.WATER) values.add("resource-reloaded");
        if (production && resourceCase == Case.WATER) values.add("chunk-reloaded");
        if (connected) values.add("rejoined");
        if (resourceCase == Case.FLUID_OVERLAP || resourceCase == Case.CHEMICAL_OVERLAP) {
            values.add("winner-promoted");
        }
        if (resourceCase == Case.ITEM && !connected) values.add("recovery-pair");
        values.add("completed");
        values.add("cancel-held");
        if (production && (resourceCase == Case.FLUID_OVERLAP || resourceCase == Case.CHEMICAL_OVERLAP)) {
            values.add("provider-removed");
        }
        values.add("cancelled");
        return List.copyOf(values);
    }

    static List<String> expectedScreenshots(Case resourceCase, boolean connected) {
        return expectedScreenshots(resourceCase, connected, false);
    }

    static int postReconnectStage(Case resourceCase, boolean production) {
        return production && resourceCase == Case.WATER ? 4 : 3;
    }

    static List<String> expectedScreenshots(Case resourceCase, boolean connected, boolean production) {
        var prefix = wireCase(resourceCase) + "-";
        return expectedCheckpoints(resourceCase, connected, production).stream()
                .map(value -> prefix + value + ".png").toList();
    }

    static String wireCase(Case value) { return wire(value); }

    private static boolean acknowledgedPhase(Action action, Phase phase) {
        return switch (action) {
            case CREATE, RECONNECT, UNLOAD_RELOAD, REMOVE_PROVIDER -> phase == Phase.HELD;
            case RELEASE, CANCEL -> phase == Phase.HELD || phase == Phase.SETTLED;
            case REJOIN_PREPARE -> phase == Phase.REJOINING;
            case RESET -> phase == Phase.CLEAN;
            case COMPLETE -> phase == Phase.COMPLETE;
            case ABORT -> phase == Phase.FAILED;
        };
    }

    static void requireAbort(Command command, Abort abort) {
        if (command.action() != Action.ABORT || !command.epoch().equals(abort.epoch())
                || !command.scenario().equals(abort.scenario()) || !command.player().equals(abort.player())
                || !command.fixture().equals(abort.fixture()) || command.revision() != abort.revision()
                || abort.failure().isBlank()) {
            throw new IllegalArgumentException("resource abort does not bind its command and original failure");
        }
    }

    static void validateCase(String target, String scenario, Case value) {
        boolean chemicalScenario = scenario.equals("appmek-resource-icons");
        boolean chemicalCase = EnumSet.of(Case.OXYGEN, Case.HYDROGEN, Case.CHEMICAL_OVERLAP).contains(value);
        if (chemicalCase != chemicalScenario) throw new IllegalArgumentException("resource case does not match scenario");
        if (chemicalScenario && !(target.equals("1.20.1-forge") || target.equals("1.21.1-neoforge"))) {
            throw new IllegalArgumentException("chemical fixture is unsupported on target");
        }
        if (value == Case.BUCKETLESS && !target.equals("1.20.1-forge")) {
            throw new IllegalArgumentException("bucketless fixture is Forge 1.20.1 only");
        }
    }

    private static Decision require(Phase actual, Set<Phase> allowed, Phase next, long revision) {
        if (!allowed.contains(actual)) throw new IllegalStateException("resource action is invalid in phase " + actual);
        return Decision.accept(next, revision);
    }

    private static long increment(long value) {
        if (value <= 0 || value == Long.MAX_VALUE) throw new IllegalArgumentException("resource revision overflow");
        return value + 1;
    }

    private static Command decodeCommand(Map<String, String> values) {
        exact(values, COMMAND_KEYS);
        schema(values);
        return new Command(uuid(values, "epoch"), token(values, "scenario"), uuid(values, "player"), uuid(values, "fixture"),
                positive(values, "revision"), positive(values, "sequence"), action(values, "action"), resourceCase(values, "case"),
                slot(values));
    }

    private static State decodeState(Map<String, String> values) {
        exact(values, STATE_KEYS);
        schema(values);
        long ack = nonNegative(values, "ack");
        long ackRevision = nonNegative(values, "ackRevision");
        var failure = values.get("failure");
        if (failure.length() > 4096) throw new IllegalArgumentException("resource failure is too long");
        return new State(uuid(values, "epoch"), token(values, "scenario"), uuid(values, "player"), uuid(values, "fixture"),
                positive(values, "revision"), ack, ackRevision, action(values, "action"), resourceCase(values, "case"), slot(values),
                enumValue(Phase.class, values.get("phase")), failure, bounded(values, "terminal", 128),
                bounded(values, "providers", 4096), bounded(values, "jobs", 32768));
    }

    private static Map<String, String> read(Path path) {
        checkedRegular(path);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("resource control file exceeds 64 KiB");
            return parseUnique(bytes);
        } catch (IOException error) {
            throw new IllegalStateException("cannot read resource control file", error);
        }
    }

    private static Map<String, String> parseUnique(byte[] bytes) throws IOException {
        var result = new LinkedHashMap<String, String>();
        var text = new String(bytes, StandardCharsets.ISO_8859_1);
        for (var line : text.split("\\r?\\n")) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;
            int separator = firstSeparator(trimmed);
            if (separator <= 0) throw new IllegalArgumentException("malformed resource property");
            var key = trimmed.substring(0, separator).trim();
            if (result.putIfAbsent(key, "") != null) throw new IllegalArgumentException("duplicate resource property");
        }
        var parsed = new Properties();
        parsed.load(new ByteArrayInputStream(bytes));
        for (var key : result.keySet()) result.put(key, parsed.getProperty(key));
        return result;
    }

    private static int firstSeparator(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) return colon;
        return colon < 0 ? equals : Math.min(equals, colon);
    }

    private static void write(Path path, Map<String, String> values) {
        try {
            checkedParent(path);
            var properties = new Properties();
            properties.putAll(values);
            var bytes = new ByteArrayOutputStream();
            properties.store(bytes, null);
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("resource control file exceeds 64 KiB");
            var temporary = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
            try {
                try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    output.write(bytes.toByteArray());
                }
                DriverProgress.moveWithAccessDeniedRetry(temporary, path, (source, target) -> Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE));
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot write resource control file", error);
        }
    }

    private static Path controlFile(Path root, String name) {
        return root.toAbsolutePath().normalize().resolve("resource").resolve(name);
    }

    private static void checkedRegular(Path path) {
        checkedParent(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("resource control file is missing, linked, or non-regular");
        }
    }

    private static void checkedParent(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.isSymbolicLink(path.getParent()) || !path.getParent().toRealPath().equals(path.getParent().toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("resource control directory is linked");
            }
        } catch (IOException error) {
            throw new IllegalStateException("cannot validate resource control directory", error);
        }
    }

    private static void exact(Map<String, String> values, Set<String> expected) {
        if (!values.keySet().equals(expected)) throw new IllegalArgumentException("resource properties are missing or unknown");
    }
    private static void schema(Map<String, String> values) {
        if (!values.get("schema").equals(Integer.toString(SCHEMA))) throw new IllegalArgumentException("unsupported resource schema");
    }
    static UUID parseUuid(String value) {
        if (value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        return UUID.fromString(value);
    }

    private static UUID uuid(Map<String, String> values, String key) { return parseUuid(values.get(key)); }
    private static long positive(Map<String, String> values, String key) {
        long value = Long.parseLong(values.get(key));
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }
    private static long nonNegative(Map<String, String> values, String key) {
        long value = Long.parseLong(values.get(key));
        if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
        return value;
    }
    private static int slot(Map<String, String> values) {
        int value = Integer.parseInt(values.get("slot"));
        if (value < 0 || value > 1) throw new IllegalArgumentException("resource slot must be zero or one");
        return value;
    }
    private static String token(Map<String, String> values, String key) {
        var value = values.get(key);
        if (value.isEmpty() || value.length() > 64 || !value.matches("[\\x20-\\x7e]+")) {
            throw new IllegalArgumentException("invalid resource " + key);
        }
        return value;
    }
    private static String bounded(Map<String, String> values, String key, int maximum) {
        var value = values.get(key);
        if (value.length() > maximum) throw new IllegalArgumentException("resource " + key + " is too long");
        return value;
    }
    private static Action action(Map<String, String> values, String key) {
        return enumValue(Action.class, values.get(key).replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
    private static Case resourceCase(Map<String, String> values, String key) {
        return enumValue(Case.class, values.get(key).replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("invalid resource enum");
        try { return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("unknown resource enum", error); }
    }
    private static String wire(Enum<?> value) { return value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
    private static Command commandWithDifferentSequence(Command command) {
        long sequence = command.sequence() == Long.MAX_VALUE ? command.sequence() - 1 : command.sequence() + 1;
        return new Command(command.epoch(), command.scenario(), command.player(), command.fixture(), command.revision(),
                sequence, command.action(), command.resourceCase(), command.slot());
    }
    private ResourceFixtureControl() { }
}
