package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.integration.IntegrationMixinPlugin;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Opt-in dedicated-server proof using the same real grid, CPU fixtures and dispatch observer. */
public final class DedicatedCpuScenario {
    private final String scenario = System.getProperty("ae2ct.testDriver.serverScenario");
    private final String target = System.getProperty("ae2ct.testDriver.serverTarget");
    private final Path output = Path.of(System.getProperty("ae2ct.testDriver.serverResult"));
    private final StandardCraftFixture gridFixture = new StandardCraftFixture();
    private final FixtureMarker origin = new FixtureMarker(1, "craft-plan", "ae2-crafting-time",
            "dedicated-disposable", new FixtureMarker.Position(0, 80, 0, "NORTH"), "minecraft:smooth_stone");
    private ServerPlayer player;
    private AddonCpuFixture<Object> addon;
    private Object placement;
    private ICraftingCPU cpu;
    private IGrid grid;
    private Future<ICraftingPlan> calculation;
    private String network;
    private String outputId;
    private boolean submitted;
    private boolean ready;
    private boolean done;
    private long connectedAck;
    private String connectedAction = "";
    private StandardCraftFixture connectedSecond;
    private StandardCraftFixture connectedLifecycle;
    private StandardCraftFixture connectedLarge;
    private boolean connectedPrepared;
    private boolean connectedValidated;
    private StandardCraftFixture recurrentFixture;
    private RecurrentPlanFixture recurrentPatterns;
    private WirelessTerminalFixture recurrentWireless;
    private boolean recurrentWirelessReady;
    private boolean recurrentAddonRoute;
    private long recurrentAck;
    private int recurrentAckMenu = -1;
    private long recurrentAckRevision;
    private boolean recurrentVisited;
    private boolean recurrentSwapped;
    private boolean recurrentReplanned;
    private boolean recurrentDisconnected;
    private boolean recurrentComplete;
    private boolean recurrentCaptured;
    private ResourceFixtureServer resourceFixture;
    private Map<String, Object> resourceCleanup = Map.of();
    private String recurrentAction = "";
    private ResourcePrewarmControl prewarm;
    private Object prewarmConnection;
    private int prewarmGeneration;
    private int prewarmTicks;
    private boolean prewarmArmed;
    private long variantAck;
    private String variantAction = "";
    private boolean variantComplete;
    private StandardCraftFixture variantSecond;
    private boolean variantDisconnected;
    private int variantAckMenu = -1;
    private long variantAckRevision;
    private int variantPublishedMenu = Integer.MIN_VALUE;
    private long variantPublishedRevision = Long.MIN_VALUE;
    private long variantPublishedAck = Long.MIN_VALUE;
    private boolean variantGridsReady;
    private boolean variantReplanDiagnosed;
    private appeng.menu.me.crafting.CraftingPlanSummary variantOriginalSummary;
    private long started = System.nanoTime();
    private final boolean suspensionReload = Boolean.getBoolean("ae2craftingtime.test.suspensionReload");
    private final long[] suspensionAck = new long[2];
    private final String[] suspensionActions = new String[] {"", ""};
    private String suspensionJob = "";
    private long suspensionUndispatched = -1;
    private int suspensionStaleTicks;
    private Map<String, Object> suspensionEvidence = Map.of();

    public void tick(MinecraftServer server) {
        if (done) return;
        try {
            if (!server.isDedicatedServer()) throw new IllegalStateException("Dedicated test requires a dedicated server");
            if ((!Boolean.getBoolean("ae2craftingtime.test.prewarm") || prewarmArmed)
                    && System.nanoTime() - started > java.util.concurrent.TimeUnit.MINUTES.toNanos(timeoutMinutes(scenario))) {
                throw new IllegalStateException("Dedicated CPU timeout: " + scenario + " " + DispatchObservation.snapshot());
            }
            step(server);
        } catch (Exception | LinkageError failure) {
            if (Boolean.getBoolean("ae2craftingtime.test.prewarm") && !prewarmArmed) {
                failure.printStackTrace(); done = true; server.halt(false); return;
            }
            if (resourceFixture != null) resourceCleanup = resourceFixture.cleanup(failure.toString());
            finish(server, "FAIL", failure.toString());
            failure.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void step(MinecraftServer server) throws Exception {
        var level = server.overworld();
        if (scenario.equals("startup-only")) {
            finish(server, "PASS", "");
            return;
        }
        if (scenario.equals("cpu-list-total-ttc-connected")) {
            stepConnected(server, level);
            return;
        }
        if (scenario.equals("crafting-suspension-connected")) {
            stepSuspension(server, level);
            return;
        }
        if (scenario.equals("recurrent-plan-connected")) {
            stepRecurrentConnected(server, level);
            return;
        }
        if (scenario.equals("delayed-resource-icons-connected") || scenario.equals("appmek-resource-icons-connected")) {
            if (Boolean.getBoolean("ae2craftingtime.test.prewarm") && !prewarm(server)) return;
            if (!connectedValidated) {
                CpuListTtcControl.validateDisposableServer(Path.of(""), target);
                connectedValidated = true;
            }
            if (resourceFixture == null) resourceFixture = new ResourceFixtureServer(scenario, target, gridFixture, origin);
            if (resourceFixture.tick(server)) {
                resourceCleanup = resourceFixture.cleanup("");
                if (!resourceCleanup.get("liveCleanup").equals("PASS")) {
                    throw new IllegalStateException("resource fixture cleanup failed: " + resourceCleanup);
                }
                finish(server, resourceFixture.failed() ? "FAIL" : "PASS", resourceFixture.failure());
            }
            return;
        }
        if (scenario.equals("stored-variant-plan-connected")) {
            stepStoredVariantConnected(server);
            return;
        }
        if (player == null) {
            if (!Set.of("advancedae-cpu", "advancedae-read-recovery", "lightningtech-cpu", "neoeco-cpu", "neoeco-fastpath-cpu").contains(scenario)) {
                throw new IllegalArgumentException("Unsupported dedicated scenario: " + scenario);
            }
            AdapterSmokePolicy.verify(target, scenario.equals("advancedae-read-recovery") ? "advancedae-cpu" : scenario,
                    "en_us", IntegrationMixinPlugin.snapshot());
            String loader = target.endsWith("-forge") ? "minecraftforge" : "neoforged.neoforge";
            player = (ServerPlayer) Class.forName("net." + loader + ".common.util.FakePlayerFactory")
                    .getMethod("get", ServerLevel.class, GameProfile.class).invoke(null, level,
                            new GameProfile(UUID.fromString("a27ed489-e2c1-4fa8-a110-ab742882a210"), "AdapterSmoke"));
            // Keep this disposable fixture ticking without a connected graphical client.
            for (int x = 2; x <= 6; x++) for (int z = -2; z <= 2; z++) level.setChunkForced(x, z, true);
            addon = (AddonCpuFixture<Object>) (scenario.equals("advancedae-read-recovery")
                    ? new NativeCpuFixture() : AddonCpuFixture.create(scenario));
        }
        if (!gridFixture.prepare(player, origin)) return;
        var terminal = gridFixture.terminal;
        var marker = new FixtureMarker(1, "craft-plan", "ae2-crafting-time", "dedicated-disposable",
                new FixtureMarker.Position(terminal.getX(), terminal.getY(), terminal.getZ(), "NORTH"), origin.outputId());
        if (placement == null) { placement = addon.place(player, marker); return; }
        if (!ready) { ready = addon.finish(player, placement); if (!ready) return; }
        if (grid == null) {
            grid = ((IInWorldGridNodeHost) level.getBlockEntity(terminal)).getGridNode(Direction.NORTH).getGrid();
            network = ProfilerBridge.networkId(grid);
            outputId = addon.outputId(placement, marker);
            if (scenario.equals("advancedae-read-recovery")) {
                var host = (appeng.api.storage.ITerminalHost) ((appeng.api.parts.IPartHost)
                        level.getBlockEntity(terminal)).getPart(Direction.NORTH);
                var previousMenu = player.containerMenu;
                player.containerMenu = new appeng.menu.me.crafting.CraftingStatusMenu(1, player.getInventory(), host);
                try {
                    for (int attempt = 0; attempt < 2; attempt++) {
                        var context = com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(player);
                        if (context.grid() != grid || context.craftingCpu() != null
                                || !com.ctux.ae2craftingtime.mc1201.IntegrationLog.disabled("advanced_ae", "selected-cpu")) {
                            throw new IllegalStateException("Selected-CPU recovery did not retain the real grid");
                        }
                    }
                } finally { player.containerMenu = previousMenu; }
            }
        }
        if (cpu == null) { cpu = addon.cpu(player, placement, grid); if (cpu == null) return; }
        if (calculation == null) {
            var key = AEItemKey.of(scenario.equals("neoeco-fastpath-cpu") ? Items.BIRCH_PLANKS : Items.SMOOTH_STONE);
            grid.getStorageService().getInventory().extract(key, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
            ProfilerBridge.clearStats(new ProfileKey(network, outputId));
            // AE2 discovers recipes through the requester's grid node; a player-only source has none.
            var source = IActionSource.ofMachine(gridFixture.cpu(player));
            calculation = grid.getCraftingService().beginCraftingCalculation(level,
                    () -> source,
                    key, scenario.equals("neoeco-fastpath-cpu") ? 64 : 1, CalculationStrategy.REPORT_MISSING_ITEMS);
            return;
        }
        if (!calculation.isDone()) return;
        if (!submitted) {
            var plan = calculation.get();
            if (plan.simulation()) {
                var missing = new java.util.ArrayList<String>();
                for (var item : plan.missingItems()) missing.add(item.getKey().getId() + "=" + item.getLongValue());
                throw new IllegalStateException("Dedicated plan is missing inputs: " + missing);
            }
            DispatchObservation.watch(network, outputId);
            var submission = grid.getCraftingService().submitJob(plan, null, cpu, false, IActionSource.ofPlayer(player));
            if (!submission.successful()) throw new IllegalStateException("Dedicated job rejected: " + submission);
            submitted = true;
        }
        gridFixture.pump(player, true);
        var observed = DispatchObservation.snapshot();
        if (observed.finishes() == 0) return;
        addon.verifyDispatch(observed);
        AdapterSmokePolicy.verifyCpu(target, scenario, observed);
        if (ProfilerBridge.stats(new ProfileKey(network, outputId)).map(stats -> stats.sampleCount()).orElse(0) == 0) {
            throw new IllegalStateException("Dedicated job did not record a fresh sample");
        }
        finish(server, "PASS", "");
    }

    private boolean prewarm(MinecraftServer server) throws java.io.IOException {
        if (prewarm == null) prewarm = ResourcePrewarmControl.configured(CpuListTtcControl.directory());
        if (prewarmArmed) return prewarm.accept(prewarmGeneration, true, false);
        prewarm.waiting(System.currentTimeMillis());
        var joined = server.getPlayerList().getPlayer(ResourcePrewarmControl.PLAYER);
        Object current = joined == null ? null : joined.connection;
        if (current != prewarmConnection) {
            prewarm.invalidateReadiness();
            prewarmConnection = current; prewarmTicks = 0;
            if (current != null) prewarmGeneration = prewarm.attemptForJoin(prewarmGeneration);
        }
        if (current == null) return false;
        if (!prewarm.attemptStillCurrent(prewarmGeneration)) {
            prewarm.invalidateReadiness(); prewarmTicks = 0; return false;
        }
        int previousTicks = prewarmTicks;
        prewarmTicks = ResourcePrewarmControl.advanceReadiness(prewarmTicks, 20, true, true);
        if (previousTicks < 20 && prewarmTicks == 20) {
            var process = ProcessHandle.current();
            prewarm.write("server-ready.json", prewarm.ready(process.pid(), process.info().startInstant().orElseThrow(), prewarmGeneration, 20, 0));
        }
        if (prewarmTicks >= 20 && prewarm.accept(prewarmGeneration, current == prewarmConnection, resourceFixture == null)) {
            prewarmArmed = true; started = System.nanoTime(); return true;
        }
        return false;
    }

    static int timeoutMinutes(String value) {
        return value.endsWith("-connected") ? 40 : 5;
    }

    private void stepStoredVariantConnected(MinecraftServer server) {
        if (!connectedValidated) { CpuListTtcControl.validateDisposableServer(Path.of(""), target); connectedValidated = true; }
        if (variantComplete && server.getPlayerList().getPlayers().isEmpty()) {
            finish(server, "PASS", "");
            return;
        }
        var rolePlayer = server.getPlayerList().getPlayers().stream()
                .filter(value -> value.getName().getString().equals("Ae2ctAlpha")).findFirst().orElse(null);
        if (rolePlayer == null) {
            if (variantAck >= 10) variantDisconnected = true;
            return;
        }
        var expectedUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Ae2ctAlpha".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!rolePlayer.getUUID().equals(expectedUuid)) throw new IllegalStateException("Unexpected variant role identity");
        var active = variantAck < 8 && variantOriginalSummary == null ? gridFixture : variantSecond;
        if (active == gridFixture) {
            gridFixture.storedVariantPlan = true;
            gridFixture.missingPlanInput = true;
            gridFixture.unprofiledPlan = true;
        }
        if (!active.prepare(rolePlayer, origin)) return;
        if (!variantGridsReady) {
            if (variantSecond == null) variantSecond = gridFixture.variantSecondGrid();
            if (!variantSecond.prepare(rolePlayer, origin)) return;
            variantSecond.setStoredVariantStock(rolePlayer, true, false);
            gridFixture.viewTerminal(rolePlayer);
            variantGridsReady = true;
        }
        var menu = rolePlayer.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu confirm ? confirm : null;
        var revision = menu == null ? 0 : ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu)
                .ae2craftingtime$summaryRevision();
        if (!variantReplanDiagnosed && variantAck == 8 && menu != null && menu.getPlan() != null
                && revision > variantAckRevision) {
            variantReplanDiagnosed = true;
            var menuGrid = com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(rolePlayer).grid();
            var terminalGrid = ((IInWorldGridNodeHost) rolePlayer.level().getBlockEntity(gridFixture.terminal))
                    .getGridNode(Direction.NORTH).getGrid();
            var secondGrid = variantSecond.cpu(rolePlayer).getMainNode().getGrid();
            var stock = menuGrid == null ? null : menuGrid.getStorageService().getInventory().getAvailableStacks();
            var secondStock = secondGrid.getStorageService().getInventory().getAvailableStacks();
            System.out.println("AE2CT variant replan-diagnostic revision=" + revision
                    + " target=" + menu.getTarget().getClass().getName()
                    + " menu=terminal:" + (menuGrid == terminalGrid) + " menu=second:" + (menuGrid == secondGrid)
                    + " entries=" + menu.getPlan().getEntries().stream()
                            .map(entry -> entry.getWhat() + ":missing=" + entry.getMissingAmount()).toList()
                    + " menu-stock=" + (stock == null ? "null" : java.util.List.of(
                            stock.get(variantSecond.storedVariantKey(1)), stock.get(variantSecond.storedVariantKey(2)),
                            stock.get(variantSecond.storedVariantKey(3))))
                    + " second-stock=" + java.util.List.of(secondStock.get(variantSecond.storedVariantKey(1)),
                            secondStock.get(variantSecond.storedVariantKey(2)), secondStock.get(variantSecond.storedVariantKey(3)))
                    + " craftable=" + (menuGrid != null && menuGrid.getCraftingService()
                            .isCraftable(AEItemKey.of(Items.SMOOTH_STONE))));
        }
        var command = StoredVariantControl.command();
        if (variantAck == 9 && menu == null && command.action().equals("cancel")
                && command.matches(rolePlayer.getUUID(), variantAckMenu, variantAckRevision, variantAck)) {
            if (!StoredVariantObservation.closed(variantAckMenu)) return;
            variantAck = command.sequence();
            variantAction = command.action();
        } else if (menu != null && menu.getPlan() != null && command.sequence() > variantAck
                && command.matches(rolePlayer.getUUID(), menu.containerId, revision, variantAck)
                && (command.action().equals("switch") && variantOriginalSummary != null
                        || com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(rolePlayer).grid()
                                == active.cpu(rolePlayer).getMainNode().getGrid())) {
            var row = menu.getPlan().getEntries().stream().filter(entry ->
                    active.storedVariantKey(1).equals(entry.getWhat()) && entry.getMissingAmount() > 0)
                    .findFirst().orElse(null);
            if (row == null) throw new IllegalStateException("Connected variant plan lost its exact missing row");
            var expectedAction = variantAck == 0 ? "step-1" : variantAck == 1 ? "step-2"
                    : variantAck == 2 ? "step-3" : variantAck == 3 ? "step-4"
                    : variantAck == 4 ? "step-5" : variantAck == 5 ? "step-6"
                    : variantAck == 6 ? "step-7" : variantAck == 7 ? "switch" : variantAck == 8 ? "replanned"
                    : variantAck == 10 && variantDisconnected ? "complete" : "";
            if (!command.action().equals(expectedAction))
                throw new IllegalStateException("Unexpected variant transition: " + command.action());
            switch (command.action()) {
                case "step-1" -> active.setStoredVariantStock(rolePlayer, true, false);
                case "step-2" -> active.setStoredVariantStock(rolePlayer, false, false);
                case "step-3" -> active.setStoredVariantStock(rolePlayer, true, true);
                case "step-4" -> active.setStoredVariantStock(rolePlayer, true, false);
                case "step-5" -> active.setStoredVariantStock(rolePlayer, false, true);
                case "step-6" -> {
                    active.setStoredVariantStock(rolePlayer, false, false);
                    active.setStoredVariantOtherStock(rolePlayer);
                }
                case "step-7" -> active.setStoredVariantStock(rolePlayer, true, false);
                case "switch" -> {
                    if (variantOriginalSummary == null) {
                        if (!StoredVariantObservation.verifyServer(menu.getPlan())) return;
                        variantOriginalSummary = menu.getPlan();
                        gridFixture.moveVariantTerminal(rolePlayer, variantSecond);
                        return;
                    }
                    if (!gridFixture.variantTerminalReady(rolePlayer, variantSecond)) return;
                }
                case "replanned" -> {
                    if (!StoredVariantObservation.closed(variantOriginalSummary)) return;
                    if (menu.containerId != variantAckMenu || menu.getPlan() == variantOriginalSummary
                            || revision <= variantAckRevision)
                        throw new IllegalStateException("Variant replan did not replace the retained native summary");
                }
                case "complete" -> variantComplete = true;
                default -> throw new IllegalStateException("Unsupported variant transition");
            }
            variantAck = command.sequence();
            variantAction = command.action();
            variantAckMenu = menu.containerId;
            variantAckRevision = revision;
            System.out.println("AE2CT variant recipient=" + rolePlayer.getUUID() + " menu=" + menu.containerId
                    + " revision=" + revision + " action=" + variantAction + " missing=" + row.getMissingAmount());
        }
        if (variantAckMenu != variantPublishedMenu || variantAckRevision != variantPublishedRevision
                || variantAck != variantPublishedAck) {
            StoredVariantControl.publish(variantAck, variantAction,
                    gridFixture.terminal, rolePlayer.getUUID(),
                    variantAckMenu, variantAckRevision);
            variantPublishedMenu = variantAckMenu;
            variantPublishedRevision = variantAckRevision;
            variantPublishedAck = variantAck;
        }
    }

    private void stepRecurrentConnected(MinecraftServer server, ServerLevel level) {
        if (!connectedValidated) { CpuListTtcControl.validateDisposableServer(Path.of(""), target); connectedValidated = true; }
        if (recurrentComplete && recurrentCaptured && server.getPlayerList().getPlayers().isEmpty()) {
            finish(server, "PASS", "");
            return;
        }
        var rolePlayer = server.getPlayerList().getPlayers().stream()
                .filter(value -> value.getName().getString().equals("Ae2ctAlpha")).findFirst().orElse(null);
        if (rolePlayer == null) {
            if (!recurrentComplete && recurrentReplanned) recurrentDisconnected = true;
            return;
        }
        var expectedUuid = UUID.nameUUIDFromBytes("OfflinePlayer:Ae2ctAlpha".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!rolePlayer.getUUID().equals(expectedUuid)) throw new IllegalStateException("Unexpected offline role identity");
        if (recurrentFixture == null) {
            recurrentFixture = gridFixture;
            recurrentFixture.cpuListScenario = false;
            recurrentFixture.recurrentPlan = true;
            recurrentFixture.missingPlanInput = true;
            recurrentFixture.unprofiledPlan = true;
            recurrentAddonRoute = RecurrentCampaign.addonRoute(
                    ServerDriverPlatform.isModLoaded("wcwt"), ServerDriverPlatform.isModLoaded("advanced_ae"));
            if (recurrentAddonRoute) {
                addon = (AddonCpuFixture<Object>) AddonCpuFixture.create("advancedae-cpu");
                recurrentWireless = ServerDriverPlatform.wcwtTerminal();
            }
        }
        if (!recurrentFixture.prepare(rolePlayer, origin)) return;
        // The managed diagnostic provider must survive the deliberate client disconnect.
        level.setChunkForced(recurrentFixture.terminal.getX() >> 4, recurrentFixture.terminal.getZ() >> 4, true);
        level.setChunkForced(recurrentFixture.terminal.east(8).getX() >> 4, recurrentFixture.terminal.getZ() >> 4, true);
        if (recurrentAddonRoute && !prepareRecurrentAddons(rolePlayer)) return;
        if (recurrentPatterns == null) recurrentPatterns = new RecurrentPlanFixture(recurrentFixture);
        if (!recurrentPatterns.prepare(rolePlayer, RecurrentCampaign.plan(recurrentFixture.recurrentPlan))) return;
        var command = RecurrentPlanControl.command("alpha");
        if (command.epoch().equals(CpuListTtcControl.epoch()) && command.role().equals("alpha")
                && command.player().equals(rolePlayer.getUUID().toString()) && command.sequence() > recurrentAck) {
            var commandPhase = RecurrentCampaign.phase(recurrentVisited, recurrentSwapped, recurrentReplanned, recurrentComplete);
            if (RecurrentCampaign.allows(command.action(), commandPhase, recurrentDisconnected)
                    && rolePlayer.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu menu
                    && menu.getPlan() != null
                    && RecurrentCampaign.sameGrid(recurrentFixture.cpu(rolePlayer).getMainNode().getGrid(),
                            com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(rolePlayer).grid())
                    && command.matches(CpuListTtcControl.epoch(), rolePlayer.getUUID(),
                            menu.containerId, ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu).ae2craftingtime$summaryRevision(), recurrentAck)) {
                boolean flagged = menu.getPlan().getEntries().stream().anyMatch(entry ->
                        ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$recurrent());
                if (flagged != recurrentFixture.recurrentPlan) throw new IllegalStateException("Server recurrence outcome differs for alpha");
                recurrentAck = command.sequence();
                recurrentAckMenu = command.menu();
                recurrentAckRevision = command.revision();
                recurrentAction = command.action();
                recurrentCaptured |= command.action().equals("captured");
                System.out.println("AE2CT recurrence recipient=" + rolePlayer.getUUID() + " menu=" + menu.containerId
                        + " revision=" + ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu).ae2craftingtime$summaryRevision()
                        + " role=alpha action=" + command.action() + " recurrent=" + flagged);
            }
        }
        var initial = "initial".equals(recurrentAction);
        if (initial && !recurrentVisited) {
            recurrentPatterns.close();
            recurrentFixture = gridFixture.secondGrid();
            recurrentFixture.cpuListScenario = false;
            recurrentFixture.recurrentPlan = false;
            recurrentFixture.missingPlanInput = true;
            recurrentFixture.unprofiledPlan = true;
            recurrentPatterns = new RecurrentPlanFixture(recurrentFixture);
            recurrentVisited = true;
            return;
        }
        var visited = "grid".equals(recurrentAction);
        if (visited && !recurrentSwapped) {
            recurrentFixture.setRecurrent(rolePlayer, true);
            recurrentSwapped = true;
            // Publish the swap only after prepare installs the new diagnostic patterns.
            return;
        }
        var swapped = recurrentSwapped && "swapped".equals(recurrentAction);
        recurrentReplanned |= swapped;
        var rejoined = "rejoined".equals(recurrentAction);
        recurrentComplete |= recurrentReplanned && recurrentDisconnected && rejoined;
        var phase = RecurrentCampaign.phase(recurrentVisited, recurrentSwapped, recurrentReplanned, recurrentComplete);
        if (RecurrentCampaign.publish(true, recurrentComplete, recurrentCaptured)) {
            RecurrentPlanControl.publish("alpha", recurrentAck, recurrentAction, phase,
                    recurrentFixture.terminal, rolePlayer.getUUID(), recurrentFixture.recurrentPlan,
                    RecurrentCampaign.turn(recurrentAction, phase), recurrentAckMenu, recurrentAckRevision);
        }
    }

    private boolean prepareRecurrentAddons(ServerPlayer rolePlayer) {
        var marker = new FixtureMarker(1, "craft-plan", "ae2-crafting-time", "dedicated-disposable",
                new FixtureMarker.Position(recurrentFixture.terminal.getX(), recurrentFixture.terminal.getY(),
                        recurrentFixture.terminal.getZ(), "NORTH"), origin.outputId());
        if (placement == null) { placement = addon.place(rolePlayer, marker); return false; }
        if (!ready) { ready = addon.finish(rolePlayer, placement); if (!ready) return false; }
        if (grid == null) grid = recurrentFixture.cpu(rolePlayer).getMainNode().getGrid();
        if (cpu == null) { cpu = addon.cpu(rolePlayer, placement, grid); if (cpu == null) return false; }
        if (!recurrentWirelessReady) {
            if (recurrentWireless.setup(rolePlayer, marker) == null) return false;
            recurrentWirelessReady = true;
        }
        return true;
    }

    private void stepConnected(MinecraftServer server, ServerLevel level) {
        if (!connectedValidated) {
            CpuListTtcControl.validateDisposableServer(Path.of(""), target);
            connectedValidated = true;
        }
        player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;
        if (!connectedPrepared) {
            gridFixture.cpuListScenario = true;
            if (!gridFixture.prepare(player, origin) || !gridFixture.prepareCpuListJobs(player)) return;
            connectedPrepared = true;
        }
        var command = CpuListTtcControl.command();
        var active = connectedSecond == null ? gridFixture : connectedSecond;
        if (command.epoch().equals(CpuListTtcControl.epoch()) && command.sequence() > connectedAck) {
            boolean complete = switch (command.action()) {
                case "partial" -> { gridFixture.makeCpuListPartial(player); yield true; }
                case "restore" -> { gridFixture.restoreCpuListSamples(player); yield true; }
                case "rename" -> { gridFixture.renameCpuList(player); yield true; }
                case "crazy-priority" -> gridFixture.raiseCrazyPriority(player);
                case "finish" -> gridFixture.finishFirstCpu(player);
                case "cancel" -> { gridFixture.cancelSecondCpu(player); yield true; }
                case "replace" -> gridFixture.replaceSecondCpu(player);
                case "restart" -> gridFixture.restartSecondCpu(player);
                case "remove" -> { gridFixture.removeThirdCpu(player); yield true; }
                case "second-grid" -> {
                    if (connectedSecond == null) connectedSecond = gridFixture.secondGrid();
                    if (!connectedSecond.prepare(player, origin) || !connectedSecond.prepareCpuListJobs(player)) yield false;
                    connectedSecond.renameCpuList(player);
                    connectedLifecycle = connectedSecond;
                    yield true;
                }
                case "large-grid" -> {
                    if (connectedLarge == null) connectedLarge = gridFixture.largeCpuGrid();
                    if (!connectedLarge.prepare(player, origin) || !connectedLarge.prepareCpuListJobs(player)) yield false;
                    connectedSecond = connectedLarge;
                    yield true;
                }
                case "return-second" -> {
                    connectedSecond = connectedLifecycle;
                    if (connectedSecond == null) yield false;
                    player.teleportTo(connectedSecond.terminal.getX() + 0.5, connectedSecond.terminal.getY() - 1,
                            connectedSecond.terminal.getZ() - 2.5);
                    yield true;
                }
                case "rejoin-prepare", "relaunch-prepare" -> {
                    active.refreshCpuIdentities(player);
                    yield active.prepare(player, origin);
                }
                case "reconnect" -> true;
                case "complete" -> {
                    connectedAck = command.sequence();
                    connectedAction = command.action();
                    CpuListTtcControl.publish(connectedAck, connectedAction, "complete", active.terminal,
                            active.cpuListServerEstimates(player), active.cpuListServerState(player));
                    finish(server, "PASS", "");
                    yield false;
                }
                default -> command.action().isEmpty();
            };
            if (!complete) return;
            connectedAck = command.sequence();
            connectedAction = command.action();
            active = connectedSecond == null ? gridFixture : connectedSecond;
        }
        CpuListTtcControl.publish(connectedAck, connectedAction,
                connectedSecond == null ? "first-grid" : "second-grid",
                active.terminal, active.cpuListServerEstimates(player), active.cpuListServerState(player));
    }

    static void refreshCpuIdentitiesForReconnect(StandardCraftFixture fixture) {
        fixture.refreshCpuIdentities();
    }

    private void stepSuspension(MinecraftServer server, ServerLevel level) {
        if (!"1.20.1-forge".equals(target))
            throw new IllegalStateException("Crafting suspension connected proof requires Forge 1.20.1");
        CpuListTtcControl.validateDisposableServer(Path.of(""), target);
        var players = server.getPlayerList().getPlayers();
        if (!suspensionEvidence.isEmpty() && players.isEmpty()) {
            finish(server, "PASS", "");
            return;
        }
        if (players.size() != 2) return;
        var alpha = players.stream().filter(candidate -> candidate.getName().getString().equals("Ae2ctAlpha"))
                .findFirst().orElse(null);
        var beta = players.stream().filter(candidate -> candidate.getName().getString().equals("Ae2ctBeta"))
                .findFirst().orElse(null);
        if (alpha == null || beta == null) throw new IllegalStateException("Distinct Alpha/Beta players required");
        if (!connectedPrepared) {
            gridFixture.configureSuspension();
            if (suspensionReload) {
                gridFixture.bindTerminal(new net.minecraft.core.BlockPos(origin.terminal().x() + 60,
                        origin.terminal().y(), origin.terminal().z() + 1024));
                level.setChunkForced(gridFixture.terminal.getX() >> 4, gridFixture.terminal.getZ() >> 4, true);
                level.setChunkForced(gridFixture.terminal.east(12).getX() >> 4,
                        gridFixture.terminal.getZ() >> 4, true);
                if (level.getBlockEntity(gridFixture.terminal) == null
                        || !gridFixture.suspensionRestoredReady(alpha)) return;
                var loaded = gridFixture.suspensionState(alpha, 0);
                var expected = System.getProperty("ae2craftingtime.test.suspensionJob", "");
                if (!loaded.busy() || !loaded.suspended() || !loaded.jobId().equals(expected))
                    throw new IllegalStateException("Loaded suspension job does not match continuation: " + loaded);
                suspensionJob = loaded.jobId();
                suspensionUndispatched = loaded.undispatched();
            } else {
                if (!gridFixture.prepare(alpha, origin) || !gridFixture.submitSuspensionLarge(alpha)) return;
                var active = gridFixture.suspensionState(alpha, 0);
                if (active.undispatched() >= 64 || active.furnaceInput() == 0) return;
                suspensionJob = active.jobId();
            }
            if (suspensionJob.isEmpty()) throw new IllegalStateException("Suspension job has no native UUID");
            for (var connected : players) connected.connection.teleport(gridFixture.terminal.getX() + 0.5,
                    gridFixture.terminal.getY() - 1, gridFixture.terminal.getZ() - 2.5, -53.13f, 2f);
            connectedPrepared = true;
        }
        gridFixture.pumpSuspension(alpha);
        var current = gridFixture.suspensionState(alpha, 0);
        if (current.busy() && !current.jobId().equals(suspensionJob))
            throw new IllegalStateException("Suspension job UUID changed during connected execution");
        if (current.suspended() && suspensionUndispatched < 0)
            suspensionUndispatched = current.undispatched();
        if (current.suspended() && current.undispatched() != suspensionUndispatched)
            throw new IllegalStateException("Paused job dispatched additional patterns");
        var root = CpuListTtcControl.directory();
        var phase = suspensionReload ? "loaded" : "running";
        for (int i = 0; i < 2; i++) {
            var role = i == 0 ? "alpha" : "beta";
            var path = root.resolve(role);
            var command = CpuListTtcControl.command(path);
            if (command.epoch().equals(CpuListTtcControl.epoch()) && command.sequence() > suspensionAck[i]) {
                var expected = suspensionReload ? switch (suspensionActions[i]) {
                    case "" -> "loaded";
                    case "loaded" -> i == 1 ? "stale-sent" : "resumed";
                    case "stale-sent" -> "resumed";
                    case "resumed" -> "complete-observed";
                    default -> "";
                } : suspensionActions[i].isEmpty() ? "running-observed" : "pause-observed";
                if (command.action().equals(expected)) {
                    boolean valid = switch (expected) {
                        case "running-observed" -> current.busy() && !current.suspended();
                        case "pause-observed", "loaded" -> current.busy() && current.suspended();
                        case "stale-sent" -> current.busy() && current.suspended() && ++suspensionStaleTicks >= 3;
                        case "resumed" -> current.busy() && !current.suspended();
                        case "complete-observed" -> !current.busy() && current.networkOutput() == 64;
                        default -> false;
                    };
                    if (valid && (expected.equals("pause-observed") || expected.equals("complete-observed"))) {
                        var peer = CpuListTtcControl.command(root.resolve(i == 0 ? "beta" : "alpha"));
                        valid = peer.epoch().equals(CpuListTtcControl.epoch()) && peer.action().equals(expected);
                    }
                    if (valid) { suspensionAck[i] = command.sequence(); suspensionActions[i] = expected; }
                }
            }
            if (!suspensionReload && suspensionActions[0].equals("running-observed")
                    && suspensionActions[1].equals("running-observed")) phase = "pause-ready";
            if (suspensionReload && suspensionActions[0].equals("loaded") && suspensionActions[1].equals("stale-sent"))
                phase = "resume-ready";
            if (suspensionReload && !current.busy()
                    && java.util.Set.of("resumed", "complete-observed").contains(suspensionActions[0])
                    && java.util.Set.of("resumed", "complete-observed").contains(suspensionActions[1]))
                phase = "completed";
            CpuListTtcControl.publish(path, suspensionAck[i], suspensionActions[i], phase,
                    gridFixture.terminal, "", new GsonBuilder().create().toJson(current));
        }
        if (!suspensionReload && suspensionActions[0].equals("pause-observed")
                && suspensionActions[1].equals("pause-observed")) {
            suspensionEvidence = Map.of("phase", 1, "jobId", suspensionJob,
                    "undispatched", current.undispatched(), "remaining", current.remaining(),
                    "networkOutput", current.networkOutput(), "suspended", current.suspended());
        }
        if (suspensionReload && suspensionActions[0].equals("complete-observed")
                && suspensionActions[1].equals("complete-observed")) {
            if (current.networkOutput() != 64) throw new IllegalStateException("Wrong native output count: " + current);
            suspensionEvidence = Map.of("phase", 2, "jobId", suspensionJob,
                    "networkOutput", current.networkOutput(), "suspended", current.suspended(),
                    "undispatched", current.undispatched());
        }
    }

    private void finish(MinecraftServer server, String result, String error) {
        done = true;
        try {
            if (recurrentPatterns != null) recurrentPatterns.close();
            var json = new GsonBuilder().setPrettyPrinting().create().toJson(Map.ofEntries(
                    Map.entry("target", target), Map.entry("scenario", scenario), Map.entry("result", result),
                    Map.entry("error", error), Map.entry("adapters", IntegrationMixinPlugin.snapshot()),
                    Map.entry("dispatch", DispatchObservation.snapshot()), Map.entry("addonRoute",
                            Map.of("enabled", recurrentAddonRoute, "wcwt", recurrentWirelessReady,
                                    "quantumCpu", cpu != null)),
                    Map.entry("resourceCleanup", resourceCleanup), Map.entry("resourceEvidence",
                            resourceFixture == null ? Map.of() : resourceFixture.evidence()),
                    Map.entry("suspensionEvidence", suspensionEvidence),
                    Map.entry("finishedAt", java.time.Instant.now().toString())));
            var temporary = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".tmp");
            try {
                try (var stream = Files.newOutputStream(temporary, java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    stream.write(json.getBytes(StandardCharsets.UTF_8));
                }
                Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception failure) { throw new IllegalStateException("Cannot save dedicated test evidence", failure); }
        finally { server.halt(false); }
    }
}
