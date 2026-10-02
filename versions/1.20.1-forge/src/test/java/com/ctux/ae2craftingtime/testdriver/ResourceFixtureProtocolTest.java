package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceFixtureProtocolTest {
    @TempDir Path directory;
    private final UUID epoch = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final UUID fixture = UUID.randomUUID();

    @Test void commandRoundTripsEveryActionCaseAndSlot() {
        for (var action : ResourceFixtureControl.Action.values()) {
            for (var resource : ResourceFixtureControl.Case.values()) {
                for (int slot = 0; slot <= 1; slot++) {
                    var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player,
                            fixture, 3, 4, action, resource, slot);
                    ResourceFixtureControl.writeCommand(directory, command);
                    assertEquals(command, ResourceFixtureControl.readCommand(directory));
                }
            }
        }
    }

    @Test void rejectsMalformedCommandValuesAtTheWireBoundary() throws Exception {
        var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player, fixture,
                3, 4, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0);
        var invalid = Map.of(
                "schema", List.of("0", "2"),
                "revision", List.of("0", "-1", "not-a-number"),
                "sequence", List.of("0", "-1"),
                "slot", List.of("-1", "2"),
                "scenario", List.of("", "x".repeat(65), "\t"),
                "action", List.of("unknown", "!", "x".repeat(65)),
                "case", List.of("unknown", "!", "x".repeat(65)),
                "epoch", List.of("invalid"));
        for (var entry : invalid.entrySet()) {
            for (var value : entry.getValue()) {
                ResourceFixtureControl.writeCommand(directory, command);
                replace("command.properties", entry.getKey(), value);
                assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.readCommand(directory),
                        entry.getKey() + "=" + value);
            }
        }
    }

    @Test void rejectsMalformedStateAndEvidenceValues() throws Exception {
        for (var entry : Map.of(
                "ack", List.of("-1"), "ackRevision", List.of("-1"),
                "failure", List.of("x".repeat(4097)), "terminal", List.of("x".repeat(129)),
                "providers", List.of("x".repeat(4097)), "jobs", List.of("x".repeat(32769)),
                "phase", List.of("unknown", "!", "x".repeat(65))).entrySet()) {
            for (var value : entry.getValue()) {
                ResourceFixtureControl.writeState(directory, state(ResourceFixtureControl.Action.CREATE,
                        ResourceFixtureControl.Phase.HELD));
                replace("state.properties", entry.getKey(), value);
                assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.readState(directory),
                        entry.getKey());
            }
        }
        for (var entry : Map.of("captures", List.of("0", "-1", "129"),
                "digest", List.of("", "x".repeat(64), "a".repeat(63))).entrySet()) {
            for (var value : entry.getValue()) {
                ResourceFixtureControl.writeClientEvidence(directory,
                        new ResourceFixtureControl.ClientEvidence(epoch, "delayed-resource-icons", player,
                                fixture, 3, 128, "a".repeat(64)));
                replace("client-evidence.properties", entry.getKey(), value);
                assertThrows(IllegalArgumentException.class,
                        () -> ResourceFixtureControl.readClientEvidence(directory), entry.getKey());
            }
        }
    }

    @Test void acknowledgementsBindEveryActionAndItsAllowedPhases() {
        for (var action : ResourceFixtureControl.Action.values()) {
            var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player,
                    fixture, 3, 4, action, ResourceFixtureControl.Case.ITEM, 0);
            for (var phase : ResourceFixtureControl.Phase.values()) {
                boolean allowed = switch (action) {
                    case CREATE, RECONNECT, UNLOAD_RELOAD, REMOVE_PROVIDER -> phase == ResourceFixtureControl.Phase.HELD;
                    case RELEASE, CANCEL -> phase == ResourceFixtureControl.Phase.HELD
                            || phase == ResourceFixtureControl.Phase.SETTLED;
                    case REJOIN_PREPARE -> phase == ResourceFixtureControl.Phase.REJOINING;
                    case RESET -> phase == ResourceFixtureControl.Phase.CLEAN;
                    case COMPLETE -> phase == ResourceFixtureControl.Phase.COMPLETE;
                    case ABORT -> phase == ResourceFixtureControl.Phase.FAILED;
                };
                if (allowed) {
                    assertDoesNotThrow(() -> ResourceFixtureControl.requireAcknowledgement(state(action, phase), command));
                } else {
                    assertThrows(IllegalArgumentException.class,
                            () -> ResourceFixtureControl.requireAcknowledgement(state(action, phase), command),
                            action + ":" + phase);
                }
            }
        }
    }

    @Test void rejectsAcknowledgementWithAnyChangedCommandField() throws Exception {
        var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player,
                fixture, 3, 4, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0);
        var mutations = Map.ofEntries(
                Map.entry("epoch", UUID.randomUUID().toString()), Map.entry("player", UUID.randomUUID().toString()),
                Map.entry("fixture", UUID.randomUUID().toString()), Map.entry("scenario", "other"),
                Map.entry("revision", "4"), Map.entry("ack", "5"), Map.entry("ackRevision", "4"),
                Map.entry("action", "release"), Map.entry("case", "water"), Map.entry("slot", "1"));
        for (var entry : mutations.entrySet()) {
            ResourceFixtureControl.writeState(directory, state(command.action(), ResourceFixtureControl.Phase.HELD));
            replace("state.properties", entry.getKey(), entry.getValue());
            var altered = ResourceFixtureControl.readState(directory);
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceFixtureControl.requireAcknowledgement(altered, command), entry.getKey());
        }
    }

    @Test void abortRequiresItsOriginalFailureAndFullIdentity() throws Exception {
        var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player,
                fixture, 3, 4, ResourceFixtureControl.Action.ABORT, ResourceFixtureControl.Case.ITEM, 0);
        var abort = new ResourceFixtureControl.Abort(epoch, "delayed-resource-icons", player, fixture, 3,
                "original failure");
        assertDoesNotThrow(() -> ResourceFixtureControl.requireAbort(command, abort));
        for (var entry : Map.of("epoch", UUID.randomUUID().toString(), "player", UUID.randomUUID().toString(),
                "fixture", UUID.randomUUID().toString(), "scenario", "other", "revision", "4",
                "failure", " ").entrySet()) {
            ResourceFixtureControl.writeAbort(directory, abort);
            replace("abort.properties", entry.getKey(), entry.getValue());
            var altered = ResourceFixtureControl.readAbort(directory);
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceFixtureControl.requireAbort(command, altered), entry.getKey());
        }
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.requireAbort(
                new ResourceFixtureControl.Command(epoch, command.scenario(), player, fixture, 3, 4,
                        ResourceFixtureControl.Action.CREATE, command.resourceCase(), 0), abort));
    }

    @Test void retainedInFlightTransitionIsRequiredAndReturnedUnchanged() {
        var state = state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD);
        var command = new ResourceFixtureControl.Command(epoch, state.scenario(), player, fixture,
                3, 5, ResourceFixtureControl.Action.RELEASE, ResourceFixtureControl.Case.ITEM, 0);
        var accepted = ResourceFixtureControl.Decision.accept(ResourceFixtureControl.Phase.SETTLED, 3);
        assertSame(accepted, ResourceFixtureControl.decide(state, command, null, command, accepted));
        assertThrows(IllegalStateException.class,
                () -> ResourceFixtureControl.decide(state, command, null, command, null));
        assertEquals(accepted, ResourceFixtureControl.decide(state, command, null, null, null));
        for (var action : List.of(ResourceFixtureControl.Action.UNLOAD_RELOAD,
                ResourceFixtureControl.Action.REMOVE_PROVIDER)) {
            var transition = ResourceFixtureControl.decide(state,
                    new ResourceFixtureControl.Command(epoch, state.scenario(), player, fixture, 3, 5,
                            action, ResourceFixtureControl.Case.ITEM, 0), null, false);
            assertEquals(ResourceFixtureControl.Phase.HELD, transition.nextPhase());
            assertEquals(3, transition.nextRevision());
        }
    }

    @Test void commandParserAcceptsCommentsAndBothPropertySeparators() throws Exception {
        var command = new ResourceFixtureControl.Command(epoch, "delayed-resource-icons", player, fixture,
                3, 4, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0);
        ResourceFixtureControl.writeCommand(directory, command);
        var file = directory.resolve("resource/command.properties");
        var contents = Files.readString(file);
        Files.writeString(file, "\n! fixture comment\n" + contents.replace("schema=", "schema:")
                .replace("revision=3", "revision:3"));
        assertEquals(command, ResourceFixtureControl.readCommand(directory));
    }

    @Test void lifecycleRejectsScenarioSlotAndOverflowViolations() {
        var held = state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD);
        var command = new ResourceFixtureControl.Command(epoch, held.scenario(), player, fixture,
                3, 5, ResourceFixtureControl.Action.RELEASE, ResourceFixtureControl.Case.ITEM, 0);
        var previous = new ResourceFixtureControl.Command(epoch, held.scenario(), player, fixture,
                3, 4, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0);
        assertEquals(ResourceFixtureControl.Phase.SETTLED,
                ResourceFixtureControl.decide(held, command, previous, false).nextPhase());
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.decide(held,
                new ResourceFixtureControl.Command(epoch, "other", player, fixture, 3, 5,
                        command.action(), command.resourceCase(), 0), null, false));
        for (var resource : List.of(ResourceFixtureControl.Case.ITEM, ResourceFixtureControl.Case.FLUID_OVERLAP,
                ResourceFixtureControl.Case.CHEMICAL_OVERLAP)) {
            var overlapping = new ResourceFixtureControl.State(epoch, held.scenario(), player, fixture,
                    3, 4, 3, held.action(), resource, 0, held.phase(), "", "", "[]", "[]");
            for (var action : List.of(ResourceFixtureControl.Action.RELEASE, ResourceFixtureControl.Action.CANCEL)) {
                var slotOne = new ResourceFixtureControl.Command(epoch, held.scenario(), player, fixture,
                        3, 5, action, resource, 1);
                if (resource == ResourceFixtureControl.Case.ITEM) {
                    assertThrows(IllegalArgumentException.class,
                            () -> ResourceFixtureControl.decide(overlapping, slotOne, null, false));
                } else {
                    assertEquals(ResourceFixtureControl.Phase.SETTLED,
                            ResourceFixtureControl.decide(overlapping, slotOne, null, false).nextPhase());
                }
            }
        }
        var corrupt = new ResourceFixtureControl.State(epoch, held.scenario(), player, fixture,
                0, 4, 0, held.action(), held.resourceCase(), 0, held.phase(), "", "", "[]", "[]");
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.decide(corrupt,
                new ResourceFixtureControl.Command(epoch, held.scenario(), player, fixture,
                        0, 5, ResourceFixtureControl.Action.ABORT, held.resourceCase(), 0), null, false));
        var overflow = new ResourceFixtureControl.Command(epoch, held.scenario(), player, fixture,
                3, Long.MAX_VALUE, command.action(), command.resourceCase(), 0);
        assertThrows(IllegalStateException.class,
                () -> ResourceFixtureControl.decide(held, overflow, null, true));
    }

    @Test void checkpointNamesAndScreenshotsMatchEachSupportedResourceRoute() {
        for (var resource : ResourceFixtureControl.Case.values()) {
            for (boolean connected : new boolean[]{false, true}) {
                for (boolean production : new boolean[]{false, true}) {
                    var checkpoints = ResourceFixtureControl.expectedCheckpoints(resource, connected, production);
                    assertEquals("held", checkpoints.get(0));
                    assertEquals("cancelled", checkpoints.get(checkpoints.size() - 1));
                    assertEquals(connected, checkpoints.contains("rejoined"));
                    assertEquals(production && resource == ResourceFixtureControl.Case.WATER,
                            checkpoints.contains("resource-reloaded"));
                    boolean overlap = resource == ResourceFixtureControl.Case.FLUID_OVERLAP
                            || resource == ResourceFixtureControl.Case.CHEMICAL_OVERLAP;
                    assertEquals(overlap, checkpoints.contains("winner-promoted"));
                    assertEquals(production && overlap, checkpoints.contains("provider-removed"));
                    assertEquals(resource == ResourceFixtureControl.Case.ITEM && !connected,
                            checkpoints.contains("recovery-pair"));
                    assertEquals(checkpoints.stream().map(checkpoint -> ResourceFixtureControl.wireCase(resource)
                            + "-" + checkpoint + ".png").toList(),
                            ResourceFixtureControl.expectedScreenshots(resource, connected, production));
                }
            }
        }
        assertDoesNotThrow(() -> ResourceFixtureControl.validateCase("1.21.1-neoforge",
                "appmek-resource-icons", ResourceFixtureControl.Case.CHEMICAL_OVERLAP));
    }

    @Test void writeRejectsOversizedStateAndInvalidControlParent() throws Exception {
        var state = state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD);
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.writeState(directory,
                new ResourceFixtureControl.State(epoch, state.scenario(), player, fixture, 3, 4, 3,
                        state.action(), state.resourceCase(), 0, state.phase(), "", "", "",
                        "x".repeat(ResourceFixtureControl.MAX_BYTES))));
        var blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var failure = assertThrows(IllegalStateException.class,
                () -> ResourceFixtureControl.writeState(blocked, state));
        assertNotNull(failure.getCause());
    }

    @Test void initialIdentityAndFinalEvidenceRejectEveryChangedField() throws Exception {
        var initial = new ResourceFixtureControl.State(epoch, "delayed-resource-icons", player, fixture,
                1, 0, 0, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0,
                ResourceFixtureControl.Phase.READY, "", "0,64,0", "[]", "[]");
        assertDoesNotThrow(() -> ResourceFixtureControl.requireInitialIdentity(initial, epoch, fixture,
                player, initial.scenario()));
        for (var entry : Map.of("epoch", UUID.randomUUID().toString(), "player", UUID.randomUUID().toString(),
                "fixture", UUID.randomUUID().toString(), "scenario", "other", "revision", "2", "ack", "1")
                .entrySet()) {
            ResourceFixtureControl.writeState(directory, initial);
            replace("state.properties", entry.getKey(), entry.getValue());
            var changed = ResourceFixtureControl.readState(directory);
            assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.requireInitialIdentity(
                    changed, epoch, fixture, player, initial.scenario()), entry.getKey());
        }
        var clean = state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.CLEAN);
        var evidence = new ResourceFixtureControl.ClientEvidence(epoch, clean.scenario(), player, fixture,
                3, 5, "a".repeat(64));
        assertDoesNotThrow(() -> ResourceFixtureControl.requireClientEvidence(clean, evidence, 5));
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.requireClientEvidence(
                state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD), evidence, 5));
        for (var entry : Map.of("epoch", UUID.randomUUID().toString(), "player", UUID.randomUUID().toString(),
                "fixture", UUID.randomUUID().toString(), "scenario", "other", "revision", "4", "captures", "6")
                .entrySet()) {
            ResourceFixtureControl.writeClientEvidence(directory, evidence);
            replace("client-evidence.properties", entry.getKey(), entry.getValue());
            var changed = ResourceFixtureControl.readClientEvidence(directory);
            assertThrows(IllegalArgumentException.class,
                    () -> ResourceFixtureControl.requireClientEvidence(clean, changed, 5), entry.getKey());
        }
    }

    @Test void zeroSequenceCannotPassAgainstACorruptNegativeAcknowledgement() {
        var corrupt = new ResourceFixtureControl.State(epoch, "delayed-resource-icons", player, fixture,
                3, -1, 0, ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0,
                ResourceFixtureControl.Phase.HELD, "", "", "[]", "[]");
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.decide(corrupt,
                new ResourceFixtureControl.Command(epoch, corrupt.scenario(), player, fixture, 3, 0,
                        ResourceFixtureControl.Action.RELEASE, ResourceFixtureControl.Case.ITEM, 0), null, false));
    }

    @Test void parserHandlesValueSeparatorsAndRejectsAnAmbiguousEnumKey() throws Exception {
        for (var scenario : List.of("resource:icons", "resource=icons")) {
            var command = new ResourceFixtureControl.Command(epoch, scenario, player, fixture, 3, 4,
                    ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0);
            ResourceFixtureControl.writeCommand(directory, command);
            assertEquals(command, ResourceFixtureControl.readCommand(directory));
        }
        ResourceFixtureControl.writeState(directory,
                state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD));
        var path = directory.resolve("resource/state.properties");
        Files.writeString(path, Files.readString(path).replace("phase=", "phase\u000b="));
        assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.readState(directory));
    }

    @Test void failedAtomicWritePreservesTheTargetAndRemovesItsTemporaryFile() throws Exception {
        var target = Files.createDirectories(directory.resolve("resource/state.properties"));
        Files.writeString(target.resolve("preserved"), "keep");
        var failure = assertThrows(IllegalStateException.class, () -> ResourceFixtureControl.writeState(directory,
                state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD)));
        assertInstanceOf(java.io.IOException.class, failure.getCause());
        assertEquals("keep", Files.readString(target.resolve("preserved")));
        try (var files = Files.list(target.getParent())) {
            assertEquals(List.of("state.properties"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test void filesystemErrorsAndLinkedParentsCannotBypassTheControlBoundary() throws Exception {
        var root = directory.resolve("read");
        var value = state(ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Phase.HELD);
        ResourceFixtureControl.writeState(root, value);
        assertEquals(value, ResourceFixtureControl.readState(root));
        var path = root.resolve("resource/state.properties");
        var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) {
            var original = posix.readAttributes().permissions();
            try {
                posix.setPermissions(java.util.Set.of());
                if (!Files.isReadable(path)) {
                    var failure = assertThrows(IllegalStateException.class,
                            () -> ResourceFixtureControl.readState(root));
                    assertInstanceOf(java.io.IOException.class, failure.getCause());
                }
            } finally {
                posix.setPermissions(original);
            }
            assertEquals(value, ResourceFixtureControl.readState(root));
            var real = Files.createDirectories(directory.resolve("real"));
            var linkedParent = Files.createDirectories(directory.resolve("linked-parent"));
            Files.createSymbolicLink(linkedParent.resolve("resource"), real);
            assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.readState(linkedParent));
            var linkedAncestor = directory.resolve("linked-ancestor");
            Files.createSymbolicLink(linkedAncestor, root);
            assertThrows(IllegalArgumentException.class, () -> ResourceFixtureControl.readState(linkedAncestor));
        }
    }

    private ResourceFixtureControl.State state(ResourceFixtureControl.Action action, ResourceFixtureControl.Phase phase) {
        long revision = action == ResourceFixtureControl.Action.RESET || action == ResourceFixtureControl.Action.ABORT ? 4 : 3;
        return new ResourceFixtureControl.State(epoch, "delayed-resource-icons", player, fixture, revision,
                4, 3, action, ResourceFixtureControl.Case.ITEM, 0, phase, "", "0,64,0", "[]", "[]");
    }

    private void replace(String file, String key, String value) throws Exception {
        var path = directory.resolve("resource").resolve(file);
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) { properties.load(input); }
        properties.setProperty(key, value);
        try (var output = Files.newOutputStream(path)) { properties.store(output, null); }
    }
}
