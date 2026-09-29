package com.ctux.ae2craftingtime.testdriver;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

final class ResourceFixtureServer {
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("OfflinePlayer:Ae2ctAlpha".getBytes(StandardCharsets.UTF_8));
    private final String scenario;
    private final String target;
    private final boolean connected;
    private final Path control;
    private final UUID epoch;
    private final UUID fixture;
    private final StandardCraftFixture grid;
    private final FixtureMarker marker;
    private ResourceFixtureControl.State state;
    private ResourceFixtureControl.Command lastAccepted;
    private ResourceFixtureControl.Command operationInFlight;
    private ResourceFixtureControl.Decision operationDecision;
    private long operationAcceptedTick;
    private int operationPolls;
    private Long releaseWaitingAtAccept;
    private ResourceProcessingFixture processing;
    private boolean disconnected;
    private ServerPlayer rejoinPlayer;
    private ServerPlayer fixturePlayer;
    private ServerLevel forcedLevel;
    private final Set<ChunkCoord> ownedForcedChunks = new LinkedHashSet<>();
    private int warmups;
    private long warmupHeldAt;
    private boolean warmupReturning;
    private boolean teardownComplete;
    private boolean providerRemoved;
    private int unloadPhase;
    private BlockPos unloadProvider;
    private boolean unloadObserved;
    private UUID foreignRecordId;
    private Map<String, Object> cleanupOutcome = Map.of();
    private Map<String, Object> failureSnapshot = Map.of();
    private String terminalFailure = "";
    private final List<Map<String, Object>> receipts = new ArrayList<>();
    private final Map<String, Map<String, Object>> caseFacts = new LinkedHashMap<>();

    ResourceFixtureServer(String serverScenario, String target, StandardCraftFixture grid, FixtureMarker marker) {
        if (!DriverOptions.isResourceScenario(serverScenario.replaceFirst("-connected$", ""))) {
            throw new IllegalArgumentException("resource server requires a resource scenario");
        }
        scenario = serverScenario.replaceFirst("-connected$", "");
        connected = serverScenario.endsWith("-connected");
        this.target = target;
        this.grid = grid;
        this.marker = marker;
        control = Path.of(System.getProperty("ae2ct.testDriver.serverControl")).toAbsolutePath().normalize();
        epoch = ResourceFixtureControl.parseUuid(System.getProperty("ae2ct.testDriver.serverCampaign"));
        fixture = ResourceFixtureControl.parseUuid(System.getProperty("ae2craftingtime.test.resourceFixture"));
        state = new ResourceFixtureControl.State(epoch, scenario, PLAYER, fixture, 1, 0, 0,
                ResourceFixtureControl.Action.CREATE, ResourceFixtureControl.Case.ITEM, 0,
                ResourceFixtureControl.Phase.READY, "", "0,0,0", "[]", "[]");
        ResourceFixtureControl.writeState(control, state);
    }

    boolean tick(MinecraftServer server) {
        var player = server.getPlayerList().getPlayer(PLAYER);
        if (player == null) {
            if (state.phase() == ResourceFixtureControl.Phase.REJOINING) disconnected = true;
            return false;
        }
        fixturePlayer = player;
        var commandPath = control.resolve("resource/command.properties");
        if (!Files.exists(commandPath)) return false;
        var command = ResourceFixtureControl.readCommand(control);
        if (acknowledgedReplay(state, command, lastAccepted)) return false;
        ResourceFixtureControl.validateCase(target, scenario, command.resourceCase());
        var decision = ResourceFixtureControl.decide(state, command, lastAccepted, operationInFlight, operationDecision);
        if (operationInFlight == null) {
            operationInFlight = command;
            operationDecision = decision;
            operationAcceptedTick = player.level().getGameTime();
            operationPolls = 0;
        }
        operationPolls++;
        if (operationPolls == 1 && command.action() == ResourceFixtureControl.Action.RELEASE && processing != null) {
            var slot = processing.slots().get(command.slot());
            releaseWaitingAtAccept = grid.resourceCpus(player).get(command.slot()).getCluster()
                    .craftingLogic.getWaitingFor(slot.key());
        }
        boolean complete = apply(player, command, decision);
        if (!complete) return false;
        long revision = decision.nextRevision();
        var nextPhase = decision.nextPhase();
        if ((command.action() == ResourceFixtureControl.Action.RELEASE || command.action() == ResourceFixtureControl.Action.CANCEL)
                && processing.slots().stream().anyMatch(slot -> processing.held(slot.index()))) {
            nextPhase = ResourceFixtureControl.Phase.HELD;
        }
        state = new ResourceFixtureControl.State(epoch, scenario, PLAYER, fixture, revision, command.sequence(),
                command.revision(), command.action(), command.resourceCase(), command.slot(), nextPhase, "",
                 position(), providers(), jobs(player));
        lastAccepted = command;
        operationInFlight = null;
        operationDecision = null;
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("sequence", command.sequence());
        receipt.put("revision", command.revision());
        receipt.put("stateRevision", revision);
        receipt.put("action", command.action().name());
        receipt.put("case", command.resourceCase().name());
        receipt.put("slot", command.slot());
        receipt.put("phase", nextPhase.name());
        receipt.put("serverTick", player.level().getGameTime());
        receipt.put("acceptedTick", operationAcceptedTick);
        receipt.put("pollCount", operationPolls);
        if (command.action() == ResourceFixtureControl.Action.RELEASE && releaseWaitingAtAccept != null) {
            var slot = processing.slots().get(command.slot());
            var cpu = grid.resourceCpus(player).get(command.slot()).getCluster();
            var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(
                    grid.cpu(player).getMainNode().getGrid());
            var key = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(network, slot.key());
            receipt.put("waitingBeforeRelease", releaseWaitingAtAccept);
            receipt.put("waitingAfterRelease", cpu.craftingLogic.getWaitingFor(slot.key()));
            receipt.put("providerStartAfterRelease",
                    com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.startFor(key).isPresent());
            receipt.put("profilePendingAfterRelease",
                    com.ctux.ae2craftingtime.mc1201.ProfilerBridge.hasPending(key));
            releaseWaitingAtAccept = null;
        }
        if (command.action() == ResourceFixtureControl.Action.UNLOAD_RELOAD) {
            receipt.put("unloadedObserved", unloadObserved);
        }
        receipt.put("providers", new Gson().fromJson(state.providers(), List.class));
        receipt.put("jobs", new Gson().fromJson(state.jobs(), List.class));
        receipts.add(Map.copyOf(receipt));
        ResourceFixtureControl.writeState(control, state);
        if (command.action() == ResourceFixtureControl.Action.ABORT) {
            state = new ResourceFixtureControl.State(state.epoch(), state.scenario(), state.player(), state.fixture(),
                    state.revision(), state.ack(), state.ackRevision(), state.action(), state.resourceCase(), state.slot(),
                    state.phase(), terminalFailure, state.terminal(), state.providers(), state.jobs());
            ResourceFixtureControl.writeState(control, state);
        }
        return state.phase() == ResourceFixtureControl.Phase.COMPLETE
                || state.phase() == ResourceFixtureControl.Phase.FAILED;
    }

    static boolean acknowledgedReplay(ResourceFixtureControl.State state,
            ResourceFixtureControl.Command command, ResourceFixtureControl.Command lastAccepted) {
        if (command.sequence() != state.ack()) return false;
        ResourceFixtureControl.decide(state, command, lastAccepted, false);
        return true;
    }

    private boolean apply(ServerPlayer player, ResourceFixtureControl.Command command,
            ResourceFixtureControl.Decision decision) {
        return switch (command.action()) {
            case CREATE -> create(player, command.resourceCase());
            case RELEASE -> processing.release(player, command.slot());
            case CANCEL -> processing.cancel(player, command.slot());
            case REJOIN_PREPARE -> {
                retainResourceChunks((ServerLevel) player.level());
                rejoinPlayer = player;
                yield true;
            }
            case RECONNECT -> ResourceFixtureControl.reconnectReady(disconnected, player != rejoinPlayer,
                    () -> processing.delayed(player));
            case UNLOAD_RELOAD -> unloadReload(player);
            case REMOVE_PROVIDER -> {
                if (Boolean.getBoolean("ae2craftingtime.test.resourceFixtureOnly") || processing == null) {
                    throw new IllegalStateException("provider removal requires a live production fixture");
                }
                providerRemoved = grid.removeResourceProvider(player);
                yield providerRemoved;
            }
            case RESET -> {
                if (processing == null) throw new IllegalStateException("resource reset has no processing state");
                com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.removeRecord(foreignRecordId);
                foreignRecordId = null;
                processing.close(player);
                processing = null;
                disconnected = false;
                rejoinPlayer = null;
                warmups = 0;
                warmupHeldAt = 0;
                warmupReturning = false;
                providerRemoved = false;
                unloadPhase = 0;
                unloadProvider = null;
                unloadObserved = false;
                yield true;
            }
            case COMPLETE -> {
                if (!completeReceipts()) yield false;
                ResourceFixtureControl.requireClientEvidence(state, ResourceFixtureControl.readClientEvidence(control),
                        expectedCaptureCount());
                grid.teardownResourceFixture(player);
                teardownComplete = true;
                releaseResourceChunks();
                yield true;
            }
            case ABORT -> {
                var abort = ResourceFixtureControl.readAbort(control);
                ResourceFixtureControl.requireAbort(command, abort);
                cleanupOutcome = cleanup(abort.failure());
                terminalFailure = abort.failure() + "; cleanup=" + cleanupOutcome.get("liveCleanup")
                        + (cleanupOutcome.get("cleanupFailure").toString().isEmpty() ? ""
                                : ": " + cleanupOutcome.get("cleanupFailure"));
                if (terminalFailure.length() > 4096) terminalFailure = terminalFailure.substring(0, 4096);
                yield true;
            }
        };
    }

    record ChunkCoord(int x, int z) { }

    static Set<ChunkCoord> resourceChunks(BlockPos terminal) {
        var chunks = new LinkedHashSet<ChunkCoord>();
        for (int x = (terminal.getX() - 4) >> 4; x <= (terminal.getX() + 13) >> 4; x++) {
            for (int z = (terminal.getZ() - 3) >> 4; z <= (terminal.getZ() + 3) >> 4; z++) {
                chunks.add(new ChunkCoord(x, z));
            }
        }
        return Set.copyOf(chunks);
    }

    private void retainResourceChunks(ServerLevel level) {
        if (!connected || forcedLevel != null) return;
        if (grid.terminal == null) throw new IllegalStateException("resource grid has no terminal to retain");
        forcedLevel = level;
        for (var chunk : resourceChunks(grid.terminal)) {
            forceOwnedChunk(ownedForcedChunks, chunk,
                    ServerDriverPlatform.isResourceChunkForced(level, chunk.x(), chunk.z()),
                    () -> level.setChunkForced(chunk.x(), chunk.z(), true));
        }
    }

    static void forceOwnedChunk(Set<ChunkCoord> owned, ChunkCoord chunk, boolean preexisting, Runnable force) {
        if (preexisting) return;
        owned.add(chunk); // setChunkForced may mutate saved data and then throw while loading the chunk.
        force.run();
    }

    private void releaseResourceChunks() {
        if (forcedLevel == null) return;
        RuntimeException failure = null;
        for (var chunk : List.copyOf(ownedForcedChunks)) {
            try {
                forcedLevel.setChunkForced(chunk.x(), chunk.z(), false);
                ownedForcedChunks.remove(chunk);
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (ownedForcedChunks.isEmpty()) forcedLevel = null;
        if (failure != null) throw failure;
    }

    private boolean create(ServerPlayer player, ResourceFixtureControl.Case resourceCase) {
        var keys = keys(resourceCase);
        if (processing == null) processing = new ResourceProcessingFixture(grid, keys, warmups == 0);
        if (!processing.prepare(player, marker, scenario.equals("appmek-resource-icons"))) return false;
        if (warmupReturning) {
            if (!processing.releaseAll(player)) return false;
            processing.close(player, false);
            processing = null;
            warmupHeldAt = 0;
            warmupReturning = false;
            warmups++;
            return false;
        }
        if (!processing.submit(player) || !processing.slots().stream().allMatch(slot -> processing.held(slot.index()))) return false;
        if (warmups < 2) {
            if (warmupHeldAt == 0) warmupHeldAt = player.level().getGameTime();
            if (player.level().getGameTime() < warmupHeldAt + 20) return false;
            warmupReturning = true;
            return false;
        }
        if (!processing.delayed(player)) return false;
        if (resourceCase == ResourceFixtureControl.Case.ITEM && foreignRecordId == null) {
            var slot = processing.slots().get(0);
            var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(
                    grid.cpu(player).getMainNode().getGrid());
            foreignRecordId = com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.create(UUID.randomUUID(),
                    com.ctux.ae2craftingtime.mc1201.ProfilerBridge.dimensionId(
                            grid.cpu(player).getMainNode().getGrid()), processing.providers(), "Foreign test link",
                    slot.key().getId().toString(), player.level().getGameTime(), network).id();
        }
        var facts = new LinkedHashMap<String, Object>(ServerDriverPlatform.resourceFacts(resourceCase));
        facts.put("storageValidated", processing.storageValidated());
        caseFacts.putIfAbsent(resourceCase.name(), Map.copyOf(facts));
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<AEKey> keys(ResourceFixtureControl.Case resourceCase) {
        return switch (resourceCase) {
            case ITEM -> List.of(AEItemKey.of(Items.STONE));
            case WATER -> List.of(AEFluidKey.of(Fluids.WATER));
            case LAVA -> List.of(AEFluidKey.of(Fluids.LAVA));
            case FLUID_OVERLAP -> List.of(AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.LAVA));
            case BUCKETLESS -> List.of(ServerDriverPlatform.bucketlessResourceKey());
            case OXYGEN, HYDROGEN, CHEMICAL_OVERLAP -> {
                try {
                    yield (List<AEKey>) Class.forName("com.ctux.ae2craftingtime.testdriver.AppliedMekanisticsFixture")
                            .getDeclaredMethod("resourceKeys", String.class).invoke(null, resourceCase.name());
                } catch (ReflectiveOperationException | LinkageError error) {
                    throw new IllegalStateException("AppMek resource fixture is unavailable", error);
                }
            }
        };
    }

    private String position() { return grid.terminal == null ? "0,0,0" : grid.terminal.getX() + "," + grid.terminal.getY() + "," + grid.terminal.getZ(); }
    private String providers() {
        if (processing == null || providerRemoved) return "[]";
        return new Gson().toJson(processing.providers().stream()
                .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()).toList());
    }
    private String jobs(ServerPlayer player) {
        if (processing == null) return "[]";
        var cpus = grid.resourceCpus(player);
        var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(
                grid.cpu(player).getMainNode().getGrid());
        var records = com.ctux.ae2craftingtime.mc1201.ProviderLocateRecords.snapshotRecords();
        return new Gson().toJson(processing.slots().stream().map(slot -> {
            var status = cpus.get(slot.index()).getCluster().getJobStatus();
            var job = status == null ? null : status.crafting();
            var value = new LinkedHashMap<String, Object>();
            value.put("slot", slot.index());
            value.put("resource", slot.key().getId().toString());
            value.put("keyFingerprint", fingerprint(slot.key(), player));
            value.put("keyEncoding", encoding(slot.key(), player));
            value.put("rawAmount", slot.amount());
            value.put("cpu", slot.cpu());
            value.put("provider", slot.provider());
            value.put("job", job == null ? "" : job.what().getId() + "@" + job.amount());
            value.put("heldAmount", slot.heldAmount());
            value.put("releasedAmount", slot.releasedAmount());
            value.put("dispatchCount", slot.dispatched() ? 1 : 0);
            value.put("busy", status != null);
            value.put("delayed", status != null && slot.dispatched() && slot.heldAmount() > 0
                    && grid.resourceDelayed(player, slot.key()));
            value.put("cancelled", slot.cancelled());
            value.put("tick", slot.tick());
            value.put("chatRecord", records.stream().filter(record -> player.getUUID().equals(record.owner())
                    && network.equals(record.networkId())
                    && slot.key().getId().toString().equals(record.outputId()))
                    .map(record -> record.id().toString()).findFirst().orElse(""));
            value.put("foreignRecord", foreignRecordId == null ? "" : foreignRecordId.toString());
            return value;
        }).toList());
    }
    private static String fingerprint(AEKey key, ServerPlayer player) {
        return HexFormat.of().formatHex(digest(java.util.Base64.getDecoder().decode(encoding(key, player))));
    }
    private static String encoding(AEKey key, ServerPlayer player) {
        return java.util.Base64.getEncoder().encodeToString(ServerDriverPlatform.encodeResourceKey(key, player));
    }
    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private boolean completeReceipts() {
        if (processing != null) throw new IllegalStateException("resource fixture completed with live processing state");
        requireReceiptHistory(receipts, expectedCases(), connected,
                !Boolean.getBoolean("ae2craftingtime.test.resourceFixtureOnly"));
        return true;
    }
    static void requireReceiptHistory(List<Map<String, Object>> receipts,
            List<ResourceFixtureControl.Case> expectedCases, boolean connected) {
        requireReceiptHistory(receipts, expectedCases, connected, false);
    }

    private boolean unloadReload(ServerPlayer player) {
        if (Boolean.getBoolean("ae2craftingtime.test.resourceFixtureOnly") || processing == null) {
            throw new IllegalStateException("chunk unload requires a live production fixture");
        }
        var level = (ServerLevel) player.level();
        if (unloadPhase == 0) {
            unloadProvider = grid.resourceProviders().get(0);
            releaseResourceChunks();
            player.teleportTo(unloadProvider.getX() + 512.5, unloadProvider.getY() + 1,
                    unloadProvider.getZ() + 512.5);
            unloadPhase = 1;
            return false;
        }
        if (unloadPhase == 1) {
            if (level.hasChunkAt(unloadProvider)) return false;
            unloadObserved = true;
            grid.refreshCpuIdentities();
            player.teleportTo(grid.terminal.getX() + 0.5, grid.terminal.getY() - 1,
                    grid.terminal.getZ() - 2.5);
            unloadPhase = 2;
            return false;
        }
        if (!level.hasChunkAt(unloadProvider)) return false;
        // The disposable grid uses explicit connections; rebind native nodes after chunk reload.
        if (!grid.prepare(player, marker) || !processing.delayed(player)) return false;
        if (connected) retainResourceChunks(level);
        unloadPhase = 0;
        return true;
    }

    static void requireReceiptHistory(List<Map<String, Object>> receipts,
            List<ResourceFixtureControl.Case> expectedCases, boolean connected, boolean production) {
        for (var resourceCase : expectedCases) {
            var values = receipts.stream().filter(receipt -> receipt.get("case").equals(resourceCase.name())).toList();
            var expected = new ArrayList<String>();
            expected.add("CREATE");
            if (connected) {
                expected.add("REJOIN_PREPARE");
                expected.add("RECONNECT");
            }
            if (production && resourceCase == ResourceFixtureControl.Case.WATER) {
                expected.add(1, "UNLOAD_RELOAD");
            }
            expected.add("RELEASE");
            if (resourceCase.name().endsWith("OVERLAP")) expected.add("RELEASE");
            expected.add("RESET");
            expected.add("CREATE");
            if (production && resourceCase == expectedCases.get(expectedCases.size() - 1)) {
                expected.add("REMOVE_PROVIDER");
            }
            expected.add("CANCEL");
            if (resourceCase.name().endsWith("OVERLAP")) expected.add("CANCEL");
            expected.add("RESET");
            var actual = values.stream().map(value -> value.get("action").toString()).toList();
            if (!actual.equals(expected)) throw new IllegalStateException(
                    "incomplete receipt history for " + resourceCase + ": expected " + expected + ", got " + actual);
        }
        var actualCases = receipts.stream().map(value -> value.get("case").toString())
                .distinct().toList();
        var cases = expectedCases.stream().map(Enum::name).toList();
        if (!actualCases.equals(cases)) throw new IllegalStateException(
                "resource receipt case order mismatch: expected " + cases + ", got " + actualCases);
    }
    private List<ResourceFixtureControl.Case> expectedCases() {
        if (scenario.equals("appmek-resource-icons")) return List.of(ResourceFixtureControl.Case.OXYGEN,
                ResourceFixtureControl.Case.HYDROGEN, ResourceFixtureControl.Case.CHEMICAL_OVERLAP);
        var values = new ArrayList<>(List.of(ResourceFixtureControl.Case.ITEM, ResourceFixtureControl.Case.WATER,
                ResourceFixtureControl.Case.LAVA));
        if (target.equals("1.20.1-forge")) values.add(ResourceFixtureControl.Case.BUCKETLESS);
        values.add(ResourceFixtureControl.Case.FLUID_OVERLAP);
        return List.copyOf(values);
    }
    private int expectedCaptureCount() {
        return expectedCases().stream().mapToInt(value ->
                ResourceFixtureControl.expectedScreenshots(value, connected,
                        !Boolean.getBoolean("ae2craftingtime.test.resourceFixtureOnly")).size()).sum() + 1;
    }
    Map<String, Object> evidence() {
        return Map.ofEntries(Map.entry("schema", 1), Map.entry("fixture", fixture.toString()),
                Map.entry("epoch", epoch.toString()), Map.entry("player", PLAYER.toString()),
                Map.entry("scenario", scenario), Map.entry("target", target), Map.entry("terminal", position()),
                Map.entry("receipts", List.copyOf(receipts)), Map.entry("state", state),
                Map.entry("caseFacts", Map.copyOf(caseFacts)),
                Map.entry("failureSnapshot", failureSnapshot),
                Map.entry("clientEvidence", state.phase() == ResourceFixtureControl.Phase.COMPLETE
                        ? ResourceFixtureControl.readClientEvidence(control) : Map.of()),
                Map.entry("teardownComplete", teardownComplete),
                Map.entry("fixtureResult", state.phase() == ResourceFixtureControl.Phase.COMPLETE ? "PASS" : "FAIL"),
                Map.entry("productionIconAcceptance", "NOT_RUN"));
    }

    private Map<String, Object> failureSnapshot() {
        var readiness = new LinkedHashMap<String, Object>();
        if (operationInFlight != null && operationInFlight.action() == ResourceFixtureControl.Action.UNLOAD_RELOAD
                && fixturePlayer != null) {
            try {
                var level = (ServerLevel) fixturePlayer.level();
                readiness.put("playerPosition", fixturePlayer.blockPosition().toShortString());
                readiness.put("providerPosition", unloadProvider == null ? "" : unloadProvider.toShortString());
                boolean providerLoaded = unloadProvider != null && level.hasChunkAt(unloadProvider);
                boolean terminalLoaded = grid.terminal != null && level.hasChunkAt(grid.terminal);
                boolean cpuLoaded = grid.terminal != null && level.hasChunkAt(grid.terminal.west(2))
                        && level.hasChunkAt(grid.terminal.west(4));
                readiness.put("providerLoaded", providerLoaded);
                readiness.put("terminalLoaded", terminalLoaded);
                readiness.put("cpuLoaded", cpuLoaded);
                boolean gridReady = false;
                boolean jobReady = false;
                boolean delayedReady = false;
                if (terminalLoaded && cpuLoaded) {
                    var cpu = grid.cpu(fixturePlayer);
                    gridReady = cpu != null && cpu.getMainNode().getGrid() != null;
                    if (gridReady && providerLoaded && processing != null) {
                        var cpus = grid.resourceCpus(fixturePlayer);
                        jobReady = processing.slots().stream().map(slot -> cpus.get(slot.index())).allMatch(candidate ->
                                candidate != null && candidate.getCluster() != null
                                        && candidate.getCluster().getJobStatus() != null
                                        && candidate.getCluster().getJobStatus().crafting() != null);
                        delayedReady = processing.delayed(fixturePlayer);
                    }
                }
                readiness.put("gridReady", gridReady);
                readiness.put("jobReady", jobReady);
                readiness.put("delayedReady", delayedReady);
            } catch (RuntimeException error) {
                readiness.put("observationError", error.getClass().getSimpleName());
            }
        }
        return operationSnapshot(operationInFlight, lastAccepted, operationAcceptedTick,
                operationPolls, unloadPhase, unloadObserved, readiness);
    }

    static Map<String, Object> operationSnapshot(ResourceFixtureControl.Command pending,
            ResourceFixtureControl.Command completed, long acceptedTick, int polls, int phase,
            boolean observedUnload, Map<String, Object> readiness) {
        if (pending == null) return Map.of("status", completed == null ? "absent" : "completed");
        return Map.ofEntries(Map.entry("status", "pending"), Map.entry("sequence", pending.sequence()),
                Map.entry("action", pending.action().name()), Map.entry("case", pending.resourceCase().name()),
                Map.entry("acceptedTick", acceptedTick), Map.entry("pollCount", polls),
                Map.entry("unloadPhase", phase), Map.entry("unloadObserved", observedUnload),
                Map.entry("readiness", Map.copyOf(readiness)));
    }

    Map<String, Object> cleanup(String originalFailure) {
        if (!cleanupOutcome.isEmpty()) return cleanupOutcome;
        if (!originalFailure.isEmpty()) failureSnapshot = failureSnapshot();
        var cleanupFailures = new ArrayList<String>();
        try {
            if (processing != null) {
                if (fixturePlayer == null) throw new IllegalStateException("resource cleanup has no fixture player");
                processing.close(fixturePlayer);
            }
        } catch (Exception error) {
            cleanupFailures.add("processing: " + error);
        } finally {
            processing = null;
        }
        try {
            if (!teardownComplete && fixturePlayer != null) {
                grid.teardownResourceFixture(fixturePlayer);
                teardownComplete = true;
            }
        } catch (Exception error) {
            cleanupFailures.add("teardown: " + error);
        }
        try { releaseResourceChunks(); }
        catch (Exception error) { cleanupFailures.add("chunks: " + error); }
        cleanupOutcome = cleanupEvidence(originalFailure, String.join("; ", cleanupFailures));
        return cleanupOutcome;
    }
    boolean failed() { return state.phase() == ResourceFixtureControl.Phase.FAILED; }
    String failure() { return terminalFailure; }
    static Map<String, Object> cleanupEvidence(String originalFailure, String cleanupFailure) {
        return Map.of("originalFailure", originalFailure,
                "liveCleanup", cleanupFailure.isEmpty() ? "PASS" : "FAIL", "cleanupFailure", cleanupFailure);
    }
}
