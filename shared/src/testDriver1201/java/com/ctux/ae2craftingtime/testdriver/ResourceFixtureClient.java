package com.ctux.ae2craftingtime.testdriver;

import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.crafting.CraftingStatusScreen;
import com.ctux.ae2craftingtime.testdriver.mixin.MEStorageScreenAccessor;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class ResourceFixtureClient {
    private static final long SETUP_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(300);
    private static final long OBSERVATION_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
    private static final long CAPTURE_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("OfflinePlayer:Ae2ctAlpha".getBytes(StandardCharsets.UTF_8));
    private final Minecraft minecraft;
    private final DriverOptions options;
    private final Path control;
    private final String driverFile;
    private final List<ResourceFixtureControl.Case> cases;
    private final List<Map<String, Object>> receipts = new ArrayList<>();
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private final List<Map<String, String>> screenshots = new ArrayList<>();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private ResourceFixtureControl.State state;
    private UUID boundEpoch;
    private UUID boundFixture;
    private final UUID expectedEpoch;
    private final UUID expectedFixture;
    private ResourceFixtureControl.Command pending;
    private ScenarioState result = ScenarioState.STARTING;
    private int caseIndex;
    private int stage;
    private long sequence;
    private boolean reconnectRequested;
    private boolean locateSent;
    private long foreignLocateFrame = -1;
    private boolean chatLocateSent;
    private long chatBeamStartedAt;
    private long chatExpiryMillis;
    private boolean beamSeenAfterRecovery;
    private boolean terminalInteractionSent;
    private boolean statusButtonSent;
    private boolean cpuSelectionSent;
    private long stageStarted = System.nanoTime();
    private String stableCheckpoint = "";
    private long stableFrame = -1;
    private boolean finalEvidencePublished;
    private ResourceFixtureServer integratedServer;
    private CompletableFuture<Boolean> serverTick;
    private CompletableFuture<Void> captureWrite = CompletableFuture.completedFuture(null);
    private CompletableFuture<?> resourceReload;
    private Capture pendingCapture;
    private boolean aborting;
    private boolean abortSent;
    private long abortStarted;
    private String originalFailure = "";
    private boolean measuredStarted;

    ResourceFixtureClient(Minecraft minecraft, DriverOptions options, String driverFile) {
        this.minecraft = minecraft;
        this.options = options;
        this.driverFile = driverFile;
        expectedEpoch = ResourceFixtureControl.parseUuid(options.campaign());
        expectedFixture = ResourceFixtureControl.parseUuid(options.resourceFixture());
        control = options.connectedDedicated() ? CpuListTtcControl.directory() : options.output().resolve("control");
        if (!options.connectedDedicated()) {
            System.setProperty("ae2ct.testDriver.serverControl", control.toString());
            System.setProperty("ae2ct.testDriver.serverCampaign", expectedEpoch.toString());
        }
        cases = options.scenario().equals("appmek-resource-icons")
                ? List.of(ResourceFixtureControl.Case.OXYGEN, ResourceFixtureControl.Case.HYDROGEN,
                        ResourceFixtureControl.Case.CHEMICAL_OVERLAP)
                : nativeCases();
        for (var check : List.of("server-identity", "real-dispatch", "delayed-plates", "native-locate",
                "lifecycle", "capture-integrity", "cleanup")) checks.put(check, false);
        checks.put(options.resourceFixtureOnly() ? "fixture-only" : "typed-keys", false);
        if (cases.contains(ResourceFixtureControl.Case.ITEM)) {
            for (var check : List.of("roof", "foreign-owner", "chat-beam"))
                checks.put(check, false);
            if (options.connectedDedicated()) checks.put("reconnect-clears-beam", false);
            else {
                checks.put("beam-recovery", false);
                checks.put("beam-expiry", false);
            }
        }
    }

    void tick() {
        try {
            if (!measuredStarted) { stageStarted = System.nanoTime(); measuredStarted = true; }
            if (aborting) {
                quarantineCapture();
                tickAbort();
                return;
            }
            if (result == ScenarioState.FAILED) {
                quarantineCapture();
                minecraft.stop();
                return;
            }
            if (pendingCapture != null && !captureWrite.isDone()
                    && System.nanoTime() - pendingCapture.started() > CAPTURE_TIMEOUT_NANOS) {
                throw new IllegalStateException("resource fixture capture exceeded 30 seconds");
            }
            finishCapture();
            if (!captureWrite.isDone()) return;
            if (result == ScenarioState.RESULT_WRITTEN) {
                result = ScenarioState.QUIT_REQUESTED;
                minecraft.stop();
                return;
            }
            if (result == ScenarioState.STARTING) {
                if (System.nanoTime() - stageStarted > SETUP_TIMEOUT_NANOS) {
                    throw new IllegalStateException("resource fixture setup exceeded 300 seconds");
                }
                if (!worldReady()) return;
                resetStageDeadline();
                result = ScenarioState.WORLD_READY;
            }
            tickIntegratedServer();
            if (!Files.isRegularFile(control.resolve("resource/state.properties"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (System.nanoTime() - stageStarted > SETUP_TIMEOUT_NANOS) {
                    throw new IllegalStateException("resource fixture initial state exceeded 300 seconds");
                }
                return;
            }
            state = ResourceFixtureControl.readState(control);
            requireStateIdentity();
            if (pending != null) requireObservationDeadline("resource action acknowledgement");
            if (!acknowledgePending(true)) return;
            var resourceCase = cases.get(Math.min(caseIndex, cases.size() - 1));
            if (options.connectedDedicated()) tickConnected(resourceCase); else tickIntegrated(resourceCase);
        } catch (Exception | LinkageError error) {
            fail(error);
        }
    }

    private void tickConnected(ResourceFixtureControl.Case resourceCase) throws Exception {
        boolean overlap = overlap(resourceCase);
        boolean unload = productionUnload(resourceCase);
        if (unload && stage == 2) {
            if (observeHeld(resourceCase, "chunk-reloaded", false))
                request(ResourceFixtureControl.Action.REJOIN_PREPARE, resourceCase, 0);
            return;
        }
        switch (unload && stage > 2 ? stage - 1 : stage) {
            case 0 -> request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
            case 1 -> {
                if (!observeHeld(resourceCase, "held", true)) return;
                request(unload ? ResourceFixtureControl.Action.UNLOAD_RELOAD
                        : ResourceFixtureControl.Action.REJOIN_PREPARE, resourceCase, 0);
            }
            case 2 -> reconnectRequested = true;
            case 3 -> request(ResourceFixtureControl.Action.RECONNECT, resourceCase, 0);
            case 4 -> {
                if (!observeHeld(resourceCase, "rejoined", false)) return;
                request(ResourceFixtureControl.Action.RELEASE, resourceCase, 0);
            }
            case 5 -> {
                if (overlap) {
                    if (!observeWinner(resourceCase)) return;
                    request(ResourceFixtureControl.Action.RELEASE, resourceCase, 1);
                } else if (observeSettled(resourceCase, "completed", false))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 6 -> {
                if (overlap) {
                    if (observeSettled(resourceCase, "completed", false))
                        request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
                } else request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
            }
            case 7 -> {
                if (overlap) request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
                else if (observeHeld(resourceCase, "cancel-held", false))
                    request(ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
            }
            case 8 -> {
                if (overlap) {
                    if (!observeHeld(resourceCase, "cancel-held", false)) return;
                    request(productionRemoval(resourceCase) ? ResourceFixtureControl.Action.REMOVE_PROVIDER
                            : ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
                } else if (observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 9 -> {
                if (overlap && productionRemoval(resourceCase)) {
                    if (!observeProviderRemoved(resourceCase)) return;
                    request(ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
                } else if (overlap) request(ResourceFixtureControl.Action.CANCEL, resourceCase, 1);
                else nextCase(resourceCase);
            }
            case 10 -> {
                if (productionRemoval(resourceCase)) request(ResourceFixtureControl.Action.CANCEL, resourceCase, 1);
                else if (observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 11 -> {
                if (productionRemoval(resourceCase) && observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
                else if (!productionRemoval(resourceCase)) nextCase(resourceCase);
            }
            default -> nextCase(resourceCase);
        }
    }

    private void tickIntegrated(ResourceFixtureControl.Case resourceCase) throws Exception {
        boolean overlap = overlap(resourceCase);
        boolean unload = productionUnload(resourceCase);
        if (unload && stage == 2) {
            if (observeHeld(resourceCase, "chunk-reloaded", false))
                request(ResourceFixtureControl.Action.RELEASE, resourceCase, 0);
            return;
        }
        switch (unload && stage > 2 ? stage - 1 : stage) {
            case 0 -> request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
            case 1 -> {
                if (!observeHeld(resourceCase, "held", true)) return;
                request(unload ? ResourceFixtureControl.Action.UNLOAD_RELOAD
                        : ResourceFixtureControl.Action.RELEASE, resourceCase, 0);
            }
            case 2 -> {
                if (overlap) {
                    if (!observeWinner(resourceCase)) return;
                    request(ResourceFixtureControl.Action.RELEASE, resourceCase, 1);
                } else if (observeSettled(resourceCase, "completed", false))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 3 -> {
                if (overlap) {
                    if (observeSettled(resourceCase, "completed", false))
                        request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
                } else request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
            }
            case 4 -> {
                if (overlap) request(ResourceFixtureControl.Action.CREATE, resourceCase, 0);
                else if (observeHeld(resourceCase, "cancel-held", false))
                    request(ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
            }
            case 5 -> {
                if (overlap) {
                    if (!observeHeld(resourceCase, "cancel-held", false)) return;
                    request(productionRemoval(resourceCase) ? ResourceFixtureControl.Action.REMOVE_PROVIDER
                            : ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
                } else if (observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 6 -> {
                if (overlap && productionRemoval(resourceCase)) {
                    if (!observeProviderRemoved(resourceCase)) return;
                    request(ResourceFixtureControl.Action.CANCEL, resourceCase, 0);
                } else if (overlap) request(ResourceFixtureControl.Action.CANCEL, resourceCase, 1);
                else nextCase(resourceCase);
            }
            case 7 -> {
                if (productionRemoval(resourceCase)) request(ResourceFixtureControl.Action.CANCEL, resourceCase, 1);
                else if (observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
            }
            case 8 -> {
                if (productionRemoval(resourceCase) && observeSettled(resourceCase, "cancelled", true))
                    request(ResourceFixtureControl.Action.RESET, resourceCase, 0);
                else if (!productionRemoval(resourceCase)) nextCase(resourceCase);
            }
            default -> nextCase(resourceCase);
        }
    }

    private boolean observeHeld(ResourceFixtureControl.Case resourceCase, String checkpoint,
            boolean manualLocate) throws Exception {
        requireObservationDeadline(checkpoint + " packet convergence");
        var jobs = jobs();
        if (jobs.isEmpty() || jobs.stream().anyMatch(job -> !job.get("delayed").getAsBoolean()
                || job.get("dispatchCount").getAsInt() != 1 || job.get("heldAmount").getAsLong() <= 0)) return false;
        var expected = jobs.stream().map(job -> job.get("resource").getAsString()).collect(java.util.stream.Collectors.toSet());
        var plates = ProviderHighlightClient.plates();
        if (!plates.stream().map(ProviderHighlightClient.Plate::outputId).collect(java.util.stream.Collectors.toSet())
                .equals(expected)) return false;
        if (manualLocate && foreignLocateFrame < 0
                && !driveNativeLocate(jobs.get(0).get("resource").getAsString())) return false;
        if (manualLocate && ProviderHighlightClient.liveEdges().isEmpty()) return false;
        if (manualLocate && resourceCase != ResourceFixtureControl.Case.ITEM
                && !ProviderHighlightClient.renderBeams().isEmpty())
            throw new IllegalStateException("row locate created a beam during shared-provider case");
        if (manualLocate && resourceCase == ResourceFixtureControl.Case.ITEM && checkpoint.equals("held")) {
            var provider = providerPosition();
            if (provider == null || !minecraft.level.getBlockState(provider.above(2))
                    .is(net.minecraft.world.level.block.Blocks.STONE)) return false;
            checks.put("roof", true);
            if (foreignLocateFrame < 0) {
                if (!ProviderHighlightClient.renderBeams().isEmpty())
                    throw new IllegalStateException("row locate created a beam");
                var foreign = jobs.get(0).get("foreignRecord").getAsString();
                if (foreign.isBlank()) return false;
                minecraft.gui.getChat().clearMessages(false);
                minecraft.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
                minecraft.player.connection.sendCommand("ae2craftingtime locate " + foreign);
                foreignLocateFrame = TestDriverRuntime.renderedFrames;
                return false;
            }
            if (!chatLocateSent) {
                var messages = ((com.ctux.ae2craftingtime.testdriver.mixin.ChatComponentAccessor)
                        minecraft.gui.getChat()).ae2craftingtime_test_driver$messages();
                var expired = net.minecraft.network.chat.Component.translatable(
                        "text.ae2craftingtime.chat.delayed.expired").getString();
                if (messages.stream().noneMatch(message -> expired.equals(message.content().getString()))) return false;
                if (!ProviderHighlightClient.renderBeams().isEmpty())
                    throw new IllegalStateException("foreign owner created a beam");
                checks.put("foreign-owner", true);
                var owned = jobs.get(0).get("chatRecord").getAsString();
                if (owned.isBlank()) return false;
                minecraft.player.connection.sendCommand("ae2craftingtime locate " + owned);
                chatLocateSent = true;
                return false;
            }
            if (ProviderHighlightClient.renderBeams().stream()
                    .noneMatch(beam -> beam.position().equals(provider))) return false;
            var chatEdge = ProviderHighlightClient.liveEdges().stream()
                    .filter(edge -> edge.chatLocate() && edge.positions().contains(provider)).findFirst().orElse(null);
            if (chatEdge == null) return false;
            if (chatBeamStartedAt == 0) {
                chatBeamStartedAt = System.currentTimeMillis();
                chatExpiryMillis = chatEdge.expiresAtMillis();
            }
            checks.put("chat-beam", true);
        }
        if (!manualLocate && options.connectedDedicated() && !checkpoint.equals("chunk-reloaded")
                && !ProviderHighlightClient.liveEdges().isEmpty()) {
            throw new IllegalStateException("rainbow locate state survived reconnect");
        }
        if (!manualLocate && options.connectedDedicated() && resourceCase == ResourceFixtureControl.Case.ITEM
                && checkpoint.equals("rejoined")) {
            if (!ProviderHighlightClient.renderBeams().isEmpty())
                throw new IllegalStateException("chat beam survived reconnect");
            checks.put("reconnect-clears-beam", true);
        }
        if (!worldViewReady()) return false;
        if (!renderPlatesConverged(expected, jobs.size())) return false;
        if (!options.resourceFixtureOnly() && !typedKeysConverged(jobs)) return false;
        if (!stableFor(resourceCase.name() + ":" + checkpoint)) return false;
        checks.put("server-identity", true);
        checks.put("real-dispatch", true);
        checks.put("delayed-plates", true);
        if (!options.resourceFixtureOnly()) checks.put("typed-keys", true);
        if (manualLocate) checks.put("native-locate", true);
        if (!manualLocate) checks.put("lifecycle", true);
        if (!capture(resourceCase, checkpoint, jobs)) return false;
        if (!options.resourceFixtureOnly() && resourceCase == ResourceFixtureControl.Case.WATER
                && checkpoint.equals("held")) {
            if (resourceReload == null) {
                resourceReload = minecraft.reloadResourcePacks();
                return false;
            }
            if (!resourceReload.isDone()) return false;
            resourceReload.join();
            if (!typedKeysConverged(jobs)) throw new IllegalStateException("resource reload lost the typed plate");
            return capture(resourceCase, "resource-reloaded", jobs);
        }
        return true;
    }

    private boolean observeWinner(ResourceFixtureControl.Case resourceCase) throws Exception {
        requireObservationDeadline("winner promotion");
        var jobs = jobs();
        if (jobs.size() != 2 || jobs.get(0).get("heldAmount").getAsLong() != 0
                || jobs.get(1).get("heldAmount").getAsLong() <= 0) return false;
        var expected = Set.of(jobs.get(1).get("resource").getAsString());
        if (!renderPlatesConverged(expected, 1)) return false;
        if (!options.resourceFixtureOnly() && !typedKeysConverged(jobs)) return false;
        if (!options.connectedDedicated() && ProviderHighlightClient.liveEdges().isEmpty()) {
            throw new IllegalStateException("integrated rainbow locate expired before winner promotion");
        }
        if (!stableFor(resourceCase.name() + ":winner-promoted")) return false;
        checks.put("lifecycle", true);
        return capture(resourceCase, "winner-promoted", jobs);
    }

    private boolean observeSettled(ResourceFixtureControl.Case resourceCase, String checkpoint,
            boolean cancelled) throws Exception {
        requireObservationDeadline(checkpoint);
        var jobs = jobs();
        if (jobs.isEmpty() || jobs.stream().anyMatch(job -> job.get("busy").getAsBoolean()
                || job.get("heldAmount").getAsLong() != 0
                || job.get("cancelled").getAsBoolean() != cancelled
                || (!cancelled && job.get("releasedAmount").getAsLong() != job.get("rawAmount").getAsLong()))) {
            return false;
        }
        if (!ProviderHighlightClient.plates().isEmpty() || !ProviderHighlightClient.renderPlates().isEmpty()) return false;
        if (!options.connectedDedicated() && resourceCase == ResourceFixtureControl.Case.ITEM && checkpoint.equals("completed")
                && chatBeamStartedAt > 0) {
            var beam = !ProviderHighlightClient.renderBeams().isEmpty();
            var rainbow = ProviderHighlightClient.liveEdges().stream().anyMatch(
                    edge -> edge.chatLocate() && edge.expiresAtMillis() == chatExpiryMillis);
            if (!beamSeenAfterRecovery) {
                if (!beam || !rainbow) throw new IllegalStateException("chat rainbow/beam ended before plate recovery");
                beamSeenAfterRecovery = true;
                checks.put("beam-recovery", true);
            }
            if (System.currentTimeMillis() < chatExpiryMillis) {
                if (!beam || !rainbow) throw new IllegalStateException("chat pair expired at different times");
                if (!capture(resourceCase, "recovery-pair", jobs)) return false;
                return false;
            }
            if (beam || rainbow) return false;
            checks.put("beam-expiry", true);
        }
        if (options.connectedDedicated() && !ProviderHighlightClient.liveEdges().isEmpty()) {
            throw new IllegalStateException("connected rainbow locate state survived reconnect");
        }
        if (!worldViewReady()) return false;
        if (!stableFor(resourceCase.name() + ":" + checkpoint)) return false;
        checks.put("lifecycle", true);
        return capture(resourceCase, checkpoint, jobs);
    }

    private boolean productionRemoval(ResourceFixtureControl.Case resourceCase) {
        return !options.resourceFixtureOnly() && overlap(resourceCase) && caseIndex == cases.size() - 1;
    }

    private boolean productionUnload(ResourceFixtureControl.Case resourceCase) {
        return !options.resourceFixtureOnly() && resourceCase == ResourceFixtureControl.Case.WATER;
    }

    private boolean observeProviderRemoved(ResourceFixtureControl.Case resourceCase) throws Exception {
        requireObservationDeadline("provider removal");
        if (!state.providers().equals("[]") || !ProviderHighlightClient.plates().isEmpty()
                || !ProviderHighlightClient.renderPlates().isEmpty() || !worldViewReady()
                || !stableFor(resourceCase.name() + ":provider-removed")) return false;
        checks.put("lifecycle", true);
        return capture(resourceCase, "provider-removed", jobs());
    }

    private boolean renderPlatesConverged(Set<String> expected, int plateCount) {
        var providers = JsonParser.parseString(state.providers()).getAsJsonArray();
        if (providers.size() != 1) return false;
        var position = providers.get(0).getAsString();
        var rendered = ProviderHighlightClient.renderPlates();
        return rendered.size() == 1 && expected.contains(rendered.get(0).outputId())
                && position.equals(rendered.get(0).position().getX() + "," + rendered.get(0).position().getY()
                        + "," + rendered.get(0).position().getZ())
                && ProviderHighlightClient.plates().size() == plateCount;
    }

    private BlockPos providerPosition() {
        var providers = JsonParser.parseString(state.providers()).getAsJsonArray();
        if (providers.size() != 1) return null;
        var xyz = providers.get(0).getAsString().split(",", -1);
        if (xyz.length != 3) return null;
        return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
    }

    private boolean typedKeysConverged(List<com.google.gson.JsonObject> jobs) {
        for (var plate : ProviderHighlightClient.plates()) {
            if (plate.displayKey() == null || jobs.stream().noneMatch(job ->
                    plate.outputId().equals(job.get("resource").getAsString())
                    && keyFingerprint(plate.displayKey()).equals(job.get("keyFingerprint").getAsString()))) return false;
        }
        for (var plate : ProviderHighlightClient.renderPlates()) {
            if (plate.displayKey() == null || jobs.stream().noneMatch(job ->
                    plate.outputId().equals(job.get("resource").getAsString())
                    && keyFingerprint(plate.displayKey()).equals(job.get("keyFingerprint").getAsString()))) return false;
        }
        return true;
    }

    private String keyFingerprint(appeng.api.stacks.AEKey key) {
        return CaptureEvidence.sha256(DriverPlatform.encodeResourceKey(key, minecraft));
    }

    private List<com.google.gson.JsonObject> jobs() {
        var array = JsonParser.parseString(state.jobs()).getAsJsonArray();
        var result = new ArrayList<com.google.gson.JsonObject>();
        array.forEach(value -> result.add(value.getAsJsonObject()));
        return result;
    }

    static boolean captureViewReady(boolean screenOpen, boolean overlayOpen) {
        return !screenOpen && !overlayOpen;
    }

    private boolean worldViewReady() {
        if (!captureViewReady(minecraft.screen != null, minecraft.getOverlay() != null)) return false;
        minecraft.gui.getChat().clearMessages(true);
        minecraft.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
        return true;
    }

    private boolean driveNativeLocate(String outputId) {
        if (!ProviderHighlightClient.liveEdges().isEmpty()) {
            if (minecraft.screen != null) minecraft.setScreen(null);
            return captureViewReady(minecraft.screen != null, minecraft.getOverlay() != null);
        }
        if (locateSent) return false;
        if (minecraft.screen == null) {
            if (terminalInteractionSent) return false;
            var values = state.terminal().split(",", -1);
            if (values.length != 3) throw new IllegalStateException("resource terminal position is malformed");
            var terminal = new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                    Integer.parseInt(values[2]));
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(terminal).add(0, 0, -0.5), Direction.NORTH, terminal, false));
            terminalInteractionSent = true;
            return false;
        }
        if (minecraft.screen instanceof MEStorageScreen<?> screen) {
            if (statusButtonSent) return false;
            var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            statusButtonSent = true;
            return false;
        }
        if (minecraft.screen instanceof CraftingStatusScreen) {
            var snapshot = UiObservationStore.latest();
            if (snapshot == null) return false;
            var cpu = snapshot.cpuCards().stream().filter(value -> outputId.equals(value.jobId())).findFirst().orElse(null);
            if (cpu != null && !cpu.selected()) {
                if (cpuSelectionSent) return false;
                DriverPlatform.click(minecraft, cpu.bounds().centerX(), cpu.bounds().centerY());
                cpuSelectionSent = true;
                return false;
            }
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals(outputId)).findFirst().orElse(null);
            if (row == null) return false;
            DriverPlatform.doubleClick(minecraft, row.cell().centerX(), row.cell().centerY());
            locateSent = true;
            return false;
        }
        return false;
    }

    private boolean capture(ResourceFixtureControl.Case resourceCase, String checkpoint,
            List<com.google.gson.JsonObject> jobs) throws Exception {
        String name = ResourceFixtureControl.wireCase(resourceCase) + "-" + checkpoint + ".png";
        if (screenshots.stream().anyMatch(value -> value.get("name").equals(name))) return true;
        if (pendingCapture == null) {
            Files.createDirectories(options.output());
            var path = options.output().resolve(name);
            var window = minecraft.getWindow();
            long observedFrame = TestDriverRuntime.renderedFrames;
            long observedAtMillis = System.currentTimeMillis();
            var observedRainbows = List.copyOf(ProviderHighlightClient.liveEdges());
            var semantic = CaptureEvidence.snapshot(UiObservationStore.latest(), "world",
                    window.getGuiScaledWidth(), window.getGuiScaledHeight(), window.getGuiScale(),
                    observedFrame);
            pendingCapture = new Capture(name, path, semantic, window.getWidth(), window.getHeight(),
                    observedFrame, observedAtMillis,
                    org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER), System.nanoTime(), jobs.stream()
                            .map(value -> (com.google.gson.JsonElement) value.deepCopy()).toList(),
                    List.copyOf(ProviderHighlightClient.plates()), List.copyOf(ProviderHighlightClient.renderPlates()),
                    observedRainbows, List.copyOf(ProviderHighlightClient.renderBeams()));
            captureWrite = DriverScreenshots.capture(minecraft, path);
        }
        return false;
    }

    private void finishCapture() throws Exception {
        if (pendingCapture == null || !captureWrite.isDone()) return;
        captureWrite.join();
        var capture = pendingCapture;
        CaptureEvidence.write(capture.path(), options, capture.semantic(), capture.width(), capture.height(),
                capture.frame(), capture.renderer(), capture.started());
        String hash = CaptureEvidence.sha256(Files.readAllBytes(capture.path()));
        screenshots.add(Map.of("name", capture.name(), "sha256", hash));
        observations.add(Map.of("case", cases.get(caseIndex).name(), "checkpoint", capture.name(),
                "screen", "world", "plates", capture.plates().stream().map(this::plateEvidence).toList(),
                "renderPlates", capture.renderPlates().stream().map(this::renderPlateEvidence).toList(),
                "rainbows", capture.rainbows().stream().map(ResourceFixtureClient::rainbowEvidence).toList(),
                "beams", capture.beams().stream().map(beam -> Map.of(
                        "dimensionId", beam.dimensionId(), "position", positionEvidence(beam.position()))).toList(),
                "serverJobs", capture.jobs(), "frame", capture.frame(),
                "observedAtMillis", capture.observedAtMillis()));
        checks.put("capture-integrity", true);
        pendingCapture = null;
        captureWrite = CompletableFuture.completedFuture(null);
    }

    private Map<String, Object> plateEvidence(ProviderHighlightClient.Plate plate) {
        return Map.of("networkId", plate.networkId(), "dimensionId", plate.dimensionId(),
                "positions", plate.positions().stream().map(ResourceFixtureClient::positionEvidence).toList(),
                "outputId", plate.outputId(), "highlightedAtMillis", plate.highlightedAtMillis(),
                "keyFingerprint", plate.displayKey() == null ? "" : keyFingerprint(plate.displayKey()),
                "keyType", plate.displayKey() == null ? "" : plate.displayKey().getType().getId().toString());
    }

    private Map<String, Object> renderPlateEvidence(ProviderHighlightClient.RenderPlate plate) {
        return Map.of("dimensionId", plate.dimensionId(), "position", positionEvidence(plate.position()),
                "outputId", plate.outputId(),
                "keyFingerprint", plate.displayKey() == null ? "" : keyFingerprint(plate.displayKey()),
                "keyType", plate.displayKey() == null ? "" : plate.displayKey().getType().getId().toString());
    }

    private static Map<String, Object> rainbowEvidence(ProviderHighlightClient.Highlight highlight) {
        return Map.of("networkId", highlight.networkId(), "dimensionId", highlight.dimensionId(),
                "positions", highlight.positions().stream().map(ResourceFixtureClient::positionEvidence).toList(),
                "outputId", highlight.outputId(), "expiresAtMillis", highlight.expiresAtMillis(),
                "chatLocate", highlight.chatLocate());
    }

    private static Map<String, Integer> positionEvidence(BlockPos position) {
        return Map.of("x", position.getX(), "y", position.getY(), "z", position.getZ());
    }

    private void quarantineCapture() {
        captureWrite = quarantineCaptureFuture(captureWrite);
        if (pendingCapture != null) {
            quarantineCaptureArtifacts(pendingCapture.path());
            pendingCapture = null;
        }
    }

    static CompletableFuture<Void> quarantineCaptureFuture(CompletableFuture<Void> future) {
        if (!future.isDone()) future.cancel(true);
        return CompletableFuture.completedFuture(null);
    }

    static void quarantineCaptureArtifacts(Path image) {
        try {
            Files.deleteIfExists(image);
            Files.deleteIfExists(image.resolveSibling(image.getFileName().toString().replace(".png", ".json")));
        } catch (java.io.IOException ignored) { }
    }

    private void nextCase(ResourceFixtureControl.Case resourceCase) throws Exception {
        requireObservationDeadline("rainbow expiry");
        if (!ProviderHighlightClient.liveEdges().isEmpty()) return;
        if (caseIndex + 1 < cases.size()) {
            caseIndex++;
            stage = 0;
            resetLocateState();
            resetStageDeadline();
            return;
        }
        if (state.phase() == ResourceFixtureControl.Phase.CLEAN) {
            if (!jobs().isEmpty() || JsonParser.parseString(state.providers()).getAsJsonArray().size() != 0
                    || !ProviderHighlightClient.plates().isEmpty() || !ProviderHighlightClient.renderPlates().isEmpty()) {
                return;
            }
            if (!stableFor("fixture-cleanup")) return;
            if (!capture(resourceCase, "cleanup", List.of())) return;
            if (!finalEvidencePublished) publishFinalEvidence();
            request(ResourceFixtureControl.Action.COMPLETE, resourceCase, 0);
            return;
        }
        if (state.phase() != ResourceFixtureControl.Phase.COMPLETE) {
            throw new IllegalStateException("resource fixture cleanup was not acknowledged");
        }
        if (!jobs().isEmpty() || JsonParser.parseString(state.providers()).getAsJsonArray().size() != 0
                || !ProviderHighlightClient.plates().isEmpty() || !ProviderHighlightClient.renderPlates().isEmpty()
                || !ProviderHighlightClient.liveEdges().isEmpty()) {
            throw new IllegalStateException("resource fixture retained state after final cleanup");
        }
        requireExpectedCaptures();
        checks.put("cleanup", true);
        if (options.resourceFixtureOnly()) checks.put("fixture-only", true);
        if (checks.containsValue(false)) throw new IllegalStateException("resource fixture checks are incomplete: " + checks);
        writeEvidence("PASS", "");
        result = ScenarioState.RESULT_WRITTEN;
    }

    private void request(ResourceFixtureControl.Action action, ResourceFixtureControl.Case resourceCase, int slot) {
        if (pending != null) return;
        pending = new ResourceFixtureControl.Command(state.epoch(), state.scenario(), PLAYER, state.fixture(),
                state.revision(), ++sequence, action, resourceCase, slot);
        ResourceFixtureControl.writeCommand(control, pending);
    }

    private boolean acknowledgePending(boolean advance) {
        if (pending == null) return true;
        if (state.ack() != pending.sequence()) return false;
        ResourceFixtureControl.requireAcknowledgement(state, pending);
        receipts.add(Map.of("sequence", pending.sequence(), "revision", pending.revision(),
                "stateRevision", state.revision(), "ackRevision", state.ackRevision(),
                "action", pending.action().name(), "case", pending.resourceCase().name(), "slot", pending.slot(),
                "phase", state.phase().name(), "providers", new com.google.gson.Gson().fromJson(
                        state.providers(), List.class), "jobs", new com.google.gson.Gson().fromJson(
                                state.jobs(), List.class)));
        pending = null;
        if (advance) {
            stage++;
            resetStageDeadline();
            if (result == ScenarioState.STARTING) result = ScenarioState.WORLD_READY;
        }
        return true;
    }

    private void tickAbort() {
        if (System.nanoTime() - abortStarted > java.util.concurrent.TimeUnit.SECONDS.toNanos(30)) {
            writeEvidence("FAIL", originalFailure + "; cleanup=FAIL: forced termination before abort acknowledgement");
            result = ScenarioState.FAILED;
            aborting = false;
            return;
        }
        var path = control.resolve("resource/state.properties");
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        state = ResourceFixtureControl.readState(control);
        requireStateIdentity();
        if (!acknowledgePending(false)) return;
        if (!abortSent) {
            var resourceCase = state.resourceCase();
            ResourceFixtureControl.writeAbort(control, new ResourceFixtureControl.Abort(state.epoch(), state.scenario(),
                    state.player(), state.fixture(), state.revision(), originalFailure));
            request(ResourceFixtureControl.Action.ABORT, resourceCase, 0);
            abortSent = true;
            return;
        }
        if (state.phase() != ResourceFixtureControl.Phase.FAILED || !state.failure().startsWith(originalFailure)) return;
        writeEvidence("FAIL", state.failure() + "; abort=ACKNOWLEDGED");
        result = ScenarioState.FAILED;
        aborting = false;
    }

    private void tickIntegratedServer() {
        if (options.connectedDedicated()) return;
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;
        if (integratedServer == null) {
            try {
                var saves = minecraft.gameDirectory.toPath().resolve("saves").normalize();
                var world = saves.resolve(options.world()).normalize();
                if (!world.getParent().equals(saves)
                        || !server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .toRealPath().equals(world.toRealPath())) {
                    throw new IllegalArgumentException("running world is not the requested disposable resource fixture");
                }
                var marker = FixtureMarker.read(world);
                if (!marker.disposableWorldId().equals(options.world())) {
                    throw new IllegalArgumentException("resource fixture world ID mismatch");
                }
                integratedServer = new ResourceFixtureServer(options.scenario(), DriverPlatform.TARGET,
                        new StandardCraftFixture(), marker);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("cannot validate disposable resource fixture", error);
            }
        }
        if (serverTick != null) {
            if (!serverTick.isDone()) return;
            serverTick.join();
            serverTick = null;
        }
        serverTick = server.submit(() -> integratedServer.tick(server));
    }

    private void requireStateIdentity() {
        if (!state.player().equals(PLAYER) || !state.scenario().equals(options.scenario())
                || !state.epoch().equals(expectedEpoch) || !state.fixture().equals(expectedFixture)) {
            throw new IllegalStateException("resource fixture state identity mismatch");
        }
        if (boundEpoch == null) {
            ResourceFixtureControl.requireInitialIdentity(state, expectedEpoch, expectedFixture, PLAYER,
                    options.scenario());
            boundEpoch = state.epoch();
            boundFixture = state.fixture();
        } else if (!state.epoch().equals(boundEpoch) || !state.fixture().equals(boundFixture)) {
            throw new IllegalStateException("resource fixture campaign or fixture identity changed");
        }
        if (pending != null && (!state.epoch().equals(pending.epoch()) || !state.fixture().equals(pending.fixture()))) {
            throw new IllegalStateException("resource fixture state changed during an operation");
        }
    }

    private boolean worldReady() {
        return minecraft.getOverlay() == null && minecraft.screen == null && minecraft.level != null
                && minecraft.player != null && minecraft.gameMode != null
                && (options.connectedDedicated()
                        ? minecraft.getSingleplayerServer() == null && minecraft.getCurrentServer() != null
                        : minecraft.getSingleplayerServer() != null && minecraft.getCurrentServer() == null);
    }

    private boolean stableFor(String checkpoint) {
        if (!stableCheckpoint.equals(checkpoint)) {
            stableCheckpoint = checkpoint;
            stableFrame = TestDriverRuntime.renderedFrames;
            return false;
        }
        return TestDriverRuntime.renderedFrames >= stableFrame
                + stableFramesRequired(options.connectedDedicated(), checkpoint);
    }

    static int stableFramesRequired(boolean connectedDedicated, String checkpoint) {
        return connectedDedicated && checkpoint.endsWith(":rejoined") ? 40 : 2;
    }

    private void requireObservationDeadline(String operation) {
        if (System.nanoTime() - stageStarted > OBSERVATION_TIMEOUT_NANOS) {
            throw new IllegalStateException("resource fixture timed out waiting for " + operation);
        }
    }

    private void resetStageDeadline() {
        stageStarted = System.nanoTime();
        stableCheckpoint = "";
        stableFrame = -1;
    }

    private void resetLocateState() {
        locateSent = false;
        foreignLocateFrame = -1;
        chatLocateSent = false;
        chatBeamStartedAt = 0;
        chatExpiryMillis = 0;
        beamSeenAfterRecovery = false;
        terminalInteractionSent = false;
        statusButtonSent = false;
        cpuSelectionSent = false;
    }

    private void publishFinalEvidence() {
        requireExpectedCaptures();
        ResourceFixtureControl.writeClientEvidence(control, new ResourceFixtureControl.ClientEvidence(
                state.epoch(), state.scenario(), state.player(), state.fixture(), state.revision(), screenshots.size(),
                screenshotManifestDigest()));
        finalEvidencePublished = true;
    }

    private void requireExpectedCaptures() {
        var expected = expectedCaptureNames();
        var actual = screenshots.stream().map(value -> value.get("name")).toList();
        if (!actual.equals(expected) || screenshots.stream().anyMatch(value -> value.get("sha256").isEmpty())) {
            throw new IllegalStateException("resource fixture capture contract mismatch: expected " + expected
                    + ", got " + actual);
        }
    }

    private List<String> expectedCaptureNames() {
        var expected = new ArrayList<String>();
        for (var resourceCase : cases) expected.addAll(ResourceFixtureControl.expectedScreenshots(
                resourceCase, options.connectedDedicated(), !options.resourceFixtureOnly()));
        expected.add(ResourceFixtureControl.wireCase(cases.get(cases.size() - 1)) + "-cleanup.png");
        return List.copyOf(expected);
    }

    private List<ResourceFixtureControl.Case> nativeCases() {
        var result = new ArrayList<>(List.of(ResourceFixtureControl.Case.ITEM, ResourceFixtureControl.Case.WATER,
                ResourceFixtureControl.Case.LAVA));
        if (DriverPlatform.TARGET.equals("1.20.1-forge")) result.add(ResourceFixtureControl.Case.BUCKETLESS);
        result.add(ResourceFixtureControl.Case.FLUID_OVERLAP);
        return List.copyOf(result);
    }

    private void fail(Throwable error) {
        if (aborting || result == ScenarioState.FAILED) return;
        if (options.connectedDedicated()) {
            originalFailure = error.toString();
            if (originalFailure.length() > 3072) originalFailure = originalFailure.substring(0, 3072);
            abortStarted = System.nanoTime();
            aborting = true;
            writeEvidence("FAIL", originalFailure + "; cleanup=PENDING");
            return;
        }
        result = ScenarioState.FAILED;
        String cleanup = "NOT_REQUIRED";
        if (integratedServer != null && minecraft.getSingleplayerServer() != null) {
            try {
                var outcome = minecraft.getSingleplayerServer()
                        .submit(() -> integratedServer.cleanup(error.toString())).join();
                cleanup = outcome.toString();
            } catch (Exception cleanupError) {
                cleanup = "FAIL: " + cleanupError;
                error.addSuppressed(cleanupError);
            }
        }
        writeEvidence("FAIL", error + "; cleanup=" + cleanup);
    }

    private void writeEvidence(String fixtureResult, String failure) {
        try {
            Files.createDirectories(options.output());
            Object clientEvidence = finalEvidencePublished ? ResourceFixtureControl.readClientEvidence(control) : Map.of();
            var evidence = Map.ofEntries(Map.entry("schema", 1), Map.entry("fixtureResult", fixtureResult),
                    Map.entry("productionIconAcceptance", "NOT_RUN"), Map.entry("scenario", options.scenario()),
                    Map.entry("target", DriverPlatform.TARGET), Map.entry("profile", options.profile()),
                    Map.entry("player", PLAYER.toString()), Map.entry("connected", options.connectedDedicated()),
                    Map.entry("clientEvidence", clientEvidence), Map.entry("captureContract", expectedCaptureNames()),
                    Map.entry("screenshotManifestDigest", screenshotManifestDigest()),
                    Map.entry("receipts", receipts), Map.entry("serverState", state == null ? Map.of() : state),
                    Map.entry("integratedServerEvidence",
                            integratedServer == null ? Map.of() : integratedServer.evidence()),
                    Map.entry("failure", failure), Map.entry("clientObservations", observations),
                    Map.entry("screenshots", screenshots), Map.entry("checks", checks));
            var gson = new GsonBuilder().setPrettyPrinting().create();
            writeAtomic(options.output().resolve("resource-fixture-evidence.json"), gson.toJson(evidence));
            if (!options.resourceFixtureOnly()) {
                writeAtomic(options.output().resolve("resource-icon-evidence.json"), gson.toJson(Map.ofEntries(
                        Map.entry("schema", 1), Map.entry("semanticResult", fixtureResult),
                        Map.entry("visualAcceptance", fixtureResult.equals("PASS") ? "REVIEW_REQUIRED" : "FAIL"),
                        Map.entry("target", DriverPlatform.TARGET), Map.entry("profile", options.profile()),
                        Map.entry("scenario", options.scenario()),
                        Map.entry("headSha", System.getProperty("ae2craftingtime.test.headSha", "")),
                        Map.entry("graph", System.getProperty("ae2craftingtime.test.graph", "")),
                        Map.entry("observations", observations), Map.entry("screenshots", screenshots),
                        Map.entry("failure", failure))));
            }
            writeAtomic(options.output().resolve("result.json"), gson.toJson(Map.ofEntries(
                    Map.entry("schema", 1), Map.entry("complete", fixtureResult.equals("PASS")),
                    Map.entry("driver", driverFile), Map.entry("target", DriverPlatform.TARGET),
                    Map.entry("profile", options.profile()), Map.entry("scenario", options.scenario()),
                    Map.entry("result", fixtureResult), Map.entry("language", "en_us"),
                    Map.entry("adapters", com.ctux.ae2craftingtime.integration.IntegrationMixinPlugin.snapshot()),
                    Map.entry("dispatch", DispatchObservation.snapshot()), Map.entry("checks", checks),
                    Map.entry("screenshots", screenshots.stream().map(item -> item.get("name")).toList()),
                    Map.entry("failure", failure))));
        } catch (Exception evidenceError) {
            throw new IllegalStateException("cannot write resource fixture evidence", evidenceError);
        }
    }

    private static void writeAtomic(Path path, String value) throws java.io.IOException {
        var temporary = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String screenshotManifestDigest() {
        var canonical = new StringBuilder();
        screenshots.forEach(value -> canonical.append(value.get("name")).append('\0')
                .append(value.get("sha256").toLowerCase(java.util.Locale.ROOT)).append('\n'));
        return CaptureEvidence.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    boolean reconnectRequested() { return reconnectRequested; }
    void reconnected() {
        reconnectRequested = false;
        stage = ResourceFixtureControl.postReconnectStage(cases.get(caseIndex), !options.resourceFixtureOnly());
        resetStageDeadline();
    }
    ScenarioState state() { return result; }
    boolean evidenceReady() { return captureWrite.isDone(); }
    String checkpoint() {
        var reportedState = result == ScenarioState.WORLD_READY ? "WORLD_READY" : result.name();
        var phase = result == ScenarioState.STARTING ? "SETUP"
                : result == ScenarioState.FAILED ? "FAILED" : "ACTIVE";
        return "state=" + reportedState + " phase=" + phase + " resource-case=" + caseIndex + " stage=" + stage
                + " fixture-phase=" + (state == null ? "pending" : state.phase());
    }
    private static boolean overlap(ResourceFixtureControl.Case value) {
        return value == ResourceFixtureControl.Case.FLUID_OVERLAP
                || value == ResourceFixtureControl.Case.CHEMICAL_OVERLAP;
    }
    private record Capture(String name, Path path, UiSnapshot semantic, int width, int height,
            long frame, long observedAtMillis, String renderer, long started, List<com.google.gson.JsonElement> jobs,
            List<ProviderHighlightClient.Plate> plates, List<ProviderHighlightClient.RenderPlate> renderPlates,
            List<ProviderHighlightClient.Highlight> rainbows, List<ProviderHighlightClient.RenderBeam> beams) { }
}
