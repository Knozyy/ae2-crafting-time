package com.ctux.ae2craftingtime.testdriver;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.me.crafting.CraftingStatusScreen;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import com.ctux.ae2craftingtime.mc1201.ProviderHighlightShapes;
import com.ctux.ae2craftingtime.mc1201.TtcSortButton;
import com.ctux.ae2craftingtime.testdriver.mixin.CraftAmountScreenAccessor;
import com.ctux.ae2craftingtime.testdriver.mixin.MEStorageScreenAccessor;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounded, real plan -> dispatch -> vanilla processing -> completed output flow. */
final class StandardAe2Scenario {
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("ae2ct-test-driver");
    static final Map<String, List<String>> CHECKS = Map.ofEntries(
            Map.entry("standard-plan-controls", List.of("plan", "plan-sort", "missing-first", "plan-tooltip",
                    "plan-details", "plan-reset", "total-ttc", "layout", "item-resolution")),
            Map.entry("badge-background", List.of("plan-on", "plan-off", "plan-restored",
                    "status-on", "status-off", "status-restored")),
            Map.entry("recurrent-plan", List.of("recurrent-row", "red-warning-style", "recurrent-tooltip", "unchanged-quantity",
                    "layout", "variant-clear")),
            Map.entry("stored-variant-plan", List.of("initial-clear", "live-near", "removed-clear",
                    "exact-clear", "restored-near", "neutral-normal", "variant-tooltip", "unchanged-plan",
                    "variant-sorts", "variant-layout", "exact-only", "other-item", "ordinary-clear",
                    "fluid-clear", "coexistence", "notification-lifecycle", "watcher-cleanup")),
            Map.entry("standard-status-controls", List.of("submitted", "status", "quantity-cases", "addon-key-status", "amount-scales", "amount-options", "server-profiling-off", "status-sort", "status-tooltip", "status-details", "status-reset", "header", "layout")),
            Map.entry("waiting-status", List.of("submitted", "waiting", "first-dispatch", "recovered", "layout")),
            Map.entry("running-status", List.of("submitted", "running", "progress", "header", "layout")),
            Map.entry("cpu-list-total-ttc", CpuListTtcScenario.CHECKS),
            Map.entry("crafting-suspension", List.of("standard-cpus", "real-dispatch", "button-geometry",
                    "paused-no-dispatch", "in-flight-progress", "small-completes", "same-job-resumed",
                    "exact-output", "suspended-title", "three-cycles", "option-visible", "disabled-resumes",
                    "disabled-action-rejected", "profiling-off-recovery", "cancel-while-paused",
                    "cancel-conserved", "final-inflight-completes", "final-output-conserved", "paused-diagnostics-clean", "resume-delay-rearms",
                    "stale-job-rejected", "suspended-badge", "suspended-rows")),
            Map.entry("delayed-status", List.of("submitted", "delayed", "row", "style", "tooltip", "layout", "recovered",
                    "overlap", "stable-selection", "winner-recovery", "rainbow-preserved", "plate-recovered",
                    "final-plate", "completed", "output", "profile-sample", "plate-cleared")),
            Map.entry("craft-lifecycle", List.of("plan", "submitted", "status", "profile-sample", "total-cleared", "completed", "output",
                    "plan-no-data", "plan-partial", "accuracy-full", "accuracy-partial", "details-chat")));
    private enum Stage { PREPARE, TERMINAL, AMOUNT, PLAN_SORT, PLAN_TOOLTIP, PLAN_DETAILS, PLAN_RESET,
        SUBMIT, OPEN_STATUS, ACTIVE, STATUS_AMOUNTS, STATUS_ADDON_AMOUNTS, STATUS_SCALES, STATUS_OPTIONS, STATUS_SORT, STATUS_TOOLTIP, STATUS_DETAILS, STATUS_RESET,
        RESTORE, DELAYED, OVERLAP_POSITION, OVERLAP_HIGHLIGHT, OVERLAP_RELEASE, OVERLAP_RECOVERY, OVERLAP_FINISH,
        OVERLAP_REOPEN,
        PUMP, FINISHED, REOPEN, EMPTY, WORLD_POSITION, WORLD_HIGHLIGHT, WORLD_RELEASE, WORLD_FINISHED,
        GALLERY_PARTIAL_PLAN, GALLERY_PROFILED_PLAN, GALLERY_DETAILS, GALLERY_CHAT, GALLERY_NEXT_JOB,
        STATUS_SERVER_OFF, STATUS_PERSIST, STATUS_RELAUNCH,
        BADGE_PERSIST, BADGE_RELAUNCH }
    private final String leaf;
    private final StandardCraftFixture fixture = new StandardCraftFixture();
    private int suspensionStage;
    private int suspensionCycle = 1;
    private int suspensionStaleMenu;
    private long suspensionStaleContext;
    private boolean suspensionActionSent;
    private boolean suspensionRunningObserved;
    private int suspensionGuardTicks;
    private int suspensionCase;
    private long suspensionFinalRawBefore;
    private String suspensionLargeId;
    private String suspensionPreviousId;
    private long suspensionUndispatched;
    private long suspensionReturned;
    private long suspensionPauseTick;
    private long suspensionResumeTick;
    private boolean suspensionDelayedObserved;
    private boolean suspensionResumedCaptured;
    private final CpuListTtcScenario cpuList;
    private final boolean connectedDedicated;
    private final String world;
    private final java.nio.file.Path output;
    private final List<String> resultScreenshots;
    private final AmountContinuation amountContinuation;
    private List<StandardCraftFixture.AddonQuantityCase> addonQuantityCases = List.of();
    StandardAe2Scenario(String leaf, String world, java.nio.file.Path output, boolean connectedDedicated) {
        this(leaf, world, output, connectedDedicated, new java.util.ArrayList<>());
    }
    StandardAe2Scenario(String leaf, String world, java.nio.file.Path output, boolean connectedDedicated,
            List<String> resultScreenshots) {
        if (!CHECKS.containsKey(leaf)) throw new IllegalArgumentException("Unknown standard leaf: " + leaf);
        this.leaf = leaf;
        this.world = world;
        this.output = output;
        this.resultScreenshots = resultScreenshots;
        var continuationPath = leaf.equals("standard-status-controls")
                && Boolean.getBoolean("ae2craftingtime.test.statusRelaunch")
                ? System.getProperty("ae2craftingtime.test.continuation", "") : "";
        amountContinuation = continuationPath.isBlank() ? null : readAmountContinuation(
                java.nio.file.Path.of(continuationPath), world);
        if (amountContinuation != null) {
            resultScreenshots.addAll(amountContinuation.screenshots());
            phase = Stage.STATUS_RELAUNCH;
        }
        var badgePath = leaf.equals("badge-background")
                && Boolean.getBoolean("ae2craftingtime.test.badgeRelaunch")
                ? System.getProperty("ae2craftingtime.test.continuation", "") : "";
        badgeContinuation = badgePath.isBlank() ? null : readBadgeContinuation(
                java.nio.file.Path.of(badgePath), world);
        if (badgeContinuation != null) {
            resultScreenshots.addAll(badgeContinuation.screenshots());
            phase = Stage.BADGE_RELAUNCH;
        }
        StoredVariantObservation.enable(leaf.equals("stored-variant-plan"));
        this.connectedDedicated = connectedDedicated;
        recurrenceAddonRoute = connectedDedicated && leaf.equals("recurrent-plan") && RecurrentCampaign.addonRoute(
                DriverPlatform.isModLoaded("wcwt"), DriverPlatform.isModLoaded("advanced_ae"));
        cpuList = leaf.equals("cpu-list-total-ttc")
                ? new CpuListTtcScenario(fixture, world, output, connectedDedicated, resultScreenshots) : null;
        if (cpuList != null && cpuList.resumed()) phase = Stage.ACTIVE;
    }
    static boolean supports(String scenario) { return CHECKS.containsKey(scenario); }
    private final StableFrames<Object> frames = new StableFrames<>(8);
    private CompletableFuture<Boolean> operation;
    private Stage phase = Stage.PREPARE;
    private Stage reportedPhase;
    private String reportedCheckpoint;
    private int sort;
    private int badgeStep;
    private int badgeReplanAttempts;
    private long badgeNextReplanAt;
    private boolean badgeEditOpen;
    private boolean badgeAppearanceOpen;
    private boolean badgeSaving;
    private boolean badgeColorEdited;
    private boolean badgeOpacityEdited;
    private long badgeRenderedAfter;
    private List<String> badgeRowsBefore;
    private List<String> badgeTextBefore;
    private boolean badgePlanStocked;
    private final BadgeContinuation badgeContinuation;
    private boolean badgePersistSaving;
    private boolean badgeContinuationWritten;
    private int badgeResumeStep;
    private boolean badgeShadowEdited;
    private boolean badgeResetBadgeEdited;
    private boolean badgeOutsideEdited;
    private int badgeResetCheckPhase;
    private final java.util.Set<String> badgeResetInputs = new java.util.HashSet<>();
    private String optionSeekLabel;
    private boolean optionSeekForward;
    private long badgeResumeRenderedAfter;
    private int badgeScaleStep;
    private int badgeOriginalScale;
    private int quantityCase;
    private boolean quantityHovered;
    private int addonQuantityCase;
    private boolean addonQuantityBadgeCaptured;
    private long addonQuantityBadgeFrame;
    private boolean addonQuantityHovered;
    private appeng.menu.me.crafting.CraftingStatus realStatus;
    private int amountOptionCase;
    private boolean amountOptionOpen;
    private boolean amountOptionSaving;
    private int amountOptionSavingTicks;
    private boolean amountOptionSeenCompact;
    private boolean amountOptionSeenTime;
    private int amountOptionOperationStep;
    private int quantityScaleCase;
    private boolean quantityScaleSet;
    private int originalGuiScale;
    private int defaultAmountFontWidth;
    private int amountFontMode;
    private CompletableFuture<Void> amountFontReload;
    private java.util.Collection<String> originalResourcePacks;
    private boolean amountPersistOpen;
    private boolean amountServerOffApplied;
    private boolean amountServerOffCaptured;
    private boolean amountOriginalProfiling;
    private boolean amountPersistSaving;
    private boolean amountContinuationWritten;
    private boolean amountResumeOpened;
    private boolean amountResumeOffCaptured;
    private boolean amountResumeOnCaptured;
    private boolean amountResumeSaving;
    private boolean amountResumeChecksRestored;
    private long amountOptionRenderedAfter;
    private boolean partialJob;
    private boolean reviewJob;
    private boolean chatCleared;
    private long lastFrame = -1;
    private volatile boolean dispatched;
    private volatile boolean progressed;
    private volatile boolean finalOutputReady;
    private boolean stonePlateObserved;
    private final StableFrames<Boolean> worldFrames = new StableFrames<>(8);
    private final StableFrames<String> overlapFrames = new StableFrames<>(8);
    private String overlapWinner;
    private String overlapSurvivor;
    private final StatsInteraction stats = new StatsInteraction();
    private boolean recurrenceSwapped;
    private boolean recurrenceVisited;
    private String recurrenceCapturedAction = "";
    private boolean recurrenceRejoined;
    private boolean recurrenceReconnectRequested;
    private boolean recurrenceCaptured;
    private final RecurrentPlanFixture recurrenceFixture = new RecurrentPlanFixture(fixture);
    private int recurrenceCase;
    private boolean recurrenceServerVerified;
    private boolean recurrenceHover;
    private final boolean recurrenceAddonRoute;
    private boolean recurrenceWirelessOpened;
    private int variantStep;
    private int variantPendingStep = -1;
    private boolean variantSwitchPending;
    private boolean variantHover;
    private appeng.menu.me.crafting.CraftConfirmMenu variantMenu;
    private appeng.menu.me.crafting.CraftingPlanSummary variantSummary;
    private long variantRevision;
    private long variantMissing;
    private List<List<Long>> variantAmounts;
    private List<Boolean> variantButtons;
    private boolean variantScaleRequested;
    private int variantCapturedStep = -1;
    private int variantLifecycle;
    private boolean variantReconnectRequested;
    private boolean variantCancelCaptured;
    private long variantNextDiagnosticAt;
    private appeng.menu.me.crafting.CraftingPlanSummary variantSecondSummary;
    private long variantSecondRevision;
    private int variantSecondMenu;

    String checkpoint() { return "phase=" + phase + " fixture=" + fixture.checkpoint
            + (leaf.equals("crafting-suspension") ? " suspension=" + suspensionStage : "")
            + (leaf.equals("badge-background") ? " badge=" + badgeStep + " replan=" + badgeReplanAttempts : "")
            + (leaf.equals("recurrent-plan") ? " recurrence=" + recurrenceCase + " sort=" + sort : "")
            + (leaf.equals("stored-variant-plan") ? " variant=" + variantStep + " lifecycle=" + variantLifecycle : "")
            + (cpuList == null ? "" : " " + cpuList.checkpoint()); }

    boolean reconnectRequested() { return recurrenceReconnectRequested || variantReconnectRequested
            || cpuList != null && cpuList.reconnectRequested(); }
    void reconnected() {
        if (recurrenceReconnectRequested) {
            recurrenceReconnectRequested = false;
            recurrenceRejoined = true;
            phase = Stage.TERMINAL;
            frames.reset();
        } else if (variantReconnectRequested) {
            variantReconnectRequested = false;
            variantHover = false;
            variantLifecycle = 4;
            phase = Stage.TERMINAL;
            frames.reset();
        } else cpuList.reconnected();
    }

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) throws Exception {
        if (leaf.equals("crafting-suspension")) return connectedDedicated
                ? tickConnectedSuspension(minecraft, checks, screenshot)
                : tickSuspension(minecraft, marker, checks, screenshot);
        if (connectedDedicated && leaf.equals("recurrent-plan") && !recurrenceCaptured) {
            var state = RecurrentPlanControl.state();
            if (!state.ready() || !state.epoch().equals(CpuListTtcControl.epoch())
                    || minecraft.player == null || !state.player().equals(minecraft.player.getUUID().toString())
                    || !state.turn().equals(RecurrentPlanControl.role())) return false;
            if (!recurrenceVisited && state.phase().equals("grid")) {
                recurrenceVisited = true;
                minecraft.player.closeContainer();
                fixture.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                phase = Stage.TERMINAL; frames.reset(); return false;
            }
        }
        var currentCheckpoint = checkpoint();
        if (reportedPhase != phase || !currentCheckpoint.equals(reportedCheckpoint)) {
            System.out.println("AE2CT standard checkpoint " + java.time.Instant.now() + " " + currentCheckpoint);
            reportedPhase = phase;
            reportedCheckpoint = currentCheckpoint;
        }
        if (phase == Stage.STATUS_RELAUNCH) return statusRelaunchTick(minecraft, checks, screenshot);
        if (phase == Stage.BADGE_RELAUNCH) return badgeRelaunchTick(minecraft, checks, screenshot);
        if (phase == Stage.BADGE_PERSIST) return badgePersistTick(minecraft, checks, screenshot);
        if (leaf.equals("stored-variant-plan") && connectedDedicated && variantLifecycle == 3) {
            if (minecraft.screen instanceof CraftConfirmScreen) return false;
            if (!StoredVariantControl.request("cancel", minecraft.player.getUUID(), variantSecondMenu,
                    variantSecondRevision)) return false;
            mark(checks, "menu-cancel", true);
            mark(checks, "watcher-cleanup", true);
            if (!variantCancelCaptured) {
                screenshot.accept("stored-variant-cancelled.png");
                variantCancelCaptured = true;
            }
            variantReconnectRequested = true;
            return false;
        }
        if (leaf.equals("stored-variant-plan") && !connectedDedicated && variantStep == 9) {
            var closedMenu = variantMenu.containerId;
            if (!server(minecraft, player -> {
                if (!StoredVariantObservation.closed(closedMenu)) return false;
                if (recurrenceFixture.restoreDetection(
                        com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().features())) {
                    com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.sendTo(player);
                }
                return true;
            })) return false;
            mark(checks, "watcher-cleanup", true);
            return true;
        }
        if (leaf.equals("stored-variant-plan") && variantPendingStep >= 0) {
            var next = variantPendingStep;
            if (connectedDedicated) {
                if (!StoredVariantControl.request("step-" + next, minecraft.player.getUUID(),
                        variantMenu.containerId, variantRevision)) return false;
            } else if (!server(minecraft, player -> {
                fixture.setStoredVariantStock(player, next == 1 || next == 3 || next == 4 || next == 7,
                        next == 3 || next == 5);
                if (next == 6) fixture.setStoredVariantOtherStock(player);
                return true;
            })) return false;
            variantStep = next;
            variantPendingStep = -1;
            variantHover = false;
            moveMouse.accept(0, 0);
            frames.reset();
            return false;
        }
        if (leaf.equals("stored-variant-plan") && variantSwitchPending) {
            if (!StoredVariantControl.request("switch", minecraft.player.getUUID(), variantMenu.containerId,
                    variantRevision)) return false;
            mark(checks, "notification-lifecycle", true);
            variantSwitchPending = false;
            variantLifecycle = 1;
            variantHover = false;
            frames.reset();
            return false;
        }
        // Menu-free close/reopen and reconnect transitions are owned by this state machine.
        if (phase == Stage.ACTIVE && cpuList != null) {
            var complete = cpuList.tick(minecraft, marker, checks, screenshot, moveMouse);
            return complete;
        }
        if (leaf.equals("badge-background") && (phase == Stage.PLAN_SORT || phase == Stage.ACTIVE))
            return badgeTick(minecraft, checks, screenshot);
        if (phase == Stage.PREPARE) {
            fixture.cpuListScenario = leaf.equals("cpu-list-total-ttc");
            fixture.holdFinalOutput = leaf.equals("delayed-status");
            fixture.missingPlanInput = leaf.equals("standard-plan-controls") || leaf.equals("badge-background");
            fixture.recurrentPlan = leaf.equals("recurrent-plan");
            fixture.storedVariantPlan = leaf.equals("stored-variant-plan");
            if (fixture.storedVariantPlan) fixture.missingPlanInput = true;
            if (fixture.recurrentPlan) fixture.missingPlanInput = true;
            if (connectedDedicated && fixture.recurrentPlan) {
                var state = RecurrentPlanControl.state();
                if (!state.ready()) return false;
                fixture.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                phase = Stage.TERMINAL;
                return false;
            }
            if (connectedDedicated && fixture.storedVariantPlan) {
                var state = StoredVariantControl.state();
                if (!state.ready() || !state.epoch().equals(CpuListTtcControl.epoch())
                        || !state.player().equals(minecraft.player.getUUID().toString())) return false;
                fixture.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                phase = Stage.TERMINAL;
                return false;
            }
            fixture.unprofiledPlan = leaf.equals("craft-lifecycle") || leaf.equals("recurrent-plan")
                    || leaf.equals("stored-variant-plan");
            if (connectedDedicated && fixture.cpuListScenario) {
                var state = CpuListTtcControl.state();
                if (!state.ready()) return false;
                fixture.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                phase = Stage.TERMINAL;
                return false;
            }
            if (server(minecraft, player -> {
                if (fixture.storedVariantPlan && !connectedDedicated && recurrenceFixture.enableDetection(
                        com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().features())) {
                    com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.sendTo(player);
                }
                return fixture.prepare(player, marker)
                        && (!fixture.cpuListScenario || fixture.prepareCpuListJobs(player))
                        && (!leaf.equals("recurrent-plan") || recurrenceFixture.prepare(player,
                                RecurrentPlanFixture.CASES.get(recurrenceCase)));
            })) {
                if (leaf.equals("standard-plan-controls")) {
                    mark(checks, "item-resolution", ProviderHighlightShapes.resolveItem(null).isEmpty()
                            && ProviderHighlightShapes.resolveItem(appeng.api.stacks.AEFluidKey.of(
                                    net.minecraft.world.level.material.Fluids.WATER)).isEmpty()
                            && ProviderHighlightShapes.resolveItem(appeng.api.stacks.AEItemKey.of(
                                    net.minecraft.world.item.Items.STONE)).is(net.minecraft.world.item.Items.STONE));
                }
                phase = Stage.values()[phase.ordinal() + 1];
            }
            return false;
        }
        if (phase == Stage.TERMINAL) {
            if (minecraft.screen == null) {
                if (recurrenceAddonRoute && !recurrenceVisited) {
                    var held = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            minecraft.player.getMainHandItem().getItem()).toString();
                    if (!held.equals("wcwt:wireless_comprehensive_work_terminal")) return false;
                    minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
                    return false;
                }
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(fixture.terminal).add(0, 0, -0.5), Direction.NORTH, fixture.terminal, false));
            } else if (minecraft.screen instanceof MEStorageScreen<?> screen && fixture.cpuListScenario) {
                var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                phase = Stage.ACTIVE;
            } else if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                if (recurrenceAddonRoute && !recurrenceVisited) {
                    if (!minecraft.screen.getClass().getName().equals(
                            "com.lhy.wcwt.client.WirelessComprehensiveWorkTerminalScreen")) return false;
                    recurrenceWirelessOpened = true;
                }
                var entry = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$repo().getAllEntries().stream()
                        .filter(row -> row.getWhat().getId().toString().equals("minecraft:smooth_stone") && row.isCraftable())
                        .findFirst().orElse(null);
                if (entry != null && frames.observe(screen.getMenu().containerId)) {
                    DriverPlatform.cloneEntry(screen, entry);
                    frames.reset();
                }
            } else if (minecraft.screen instanceof CraftAmountScreen) {
                phase = Stage.AMOUNT;
            }
            return false;
        }
        if (phase == Stage.AMOUNT) {
            if (minecraft.screen instanceof CraftAmountScreen amount) {
                if (leaf.equals("recurrent-plan")) ((CraftAmountScreenAccessor) amount)
                        .ae2craftingtime_test_driver$amount().setLongValue(
                                connectedDedicated ? RecurrentCampaign.REQUESTED_AMOUNT : recurrenceFixture.requestedAmount());
                var button = ((CraftAmountScreenAccessor) amount).ae2craftingtime_test_driver$next();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            } else if (minecraft.screen instanceof CraftConfirmScreen) phase = Stage.values()[phase.ordinal() + 1];
            return false;
        }
        if (phase == Stage.OPEN_STATUS || phase == Stage.REOPEN) {
            if (phase == Stage.OPEN_STATUS) {
                if (!server(minecraft, player -> fixture.cpu(player).getCluster().isBusy())) return false;
                mark(checks, "submitted", true);
            }
            if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                phase = Stage.values()[phase.ordinal() + 1];
            }

            return false;
        }
        if (phase == Stage.WORLD_POSITION) {
            if (server(minecraft, player -> { fixture.viewFinalProvider(player); return true; })) {
                phase = Stage.WORLD_HIGHLIGHT;
            }
            return false;
        }
        if (phase == Stage.OVERLAP_POSITION) {
            if (server(minecraft, player -> { fixture.viewSharedProvider(player); return true; })) {
                overlapFrames.reset();
                phase = Stage.OVERLAP_HIGHLIGHT;
            }
            return false;
        }
        if (phase == Stage.OVERLAP_HIGHLIGHT) {
            minecraft.player.setYRot(9.462f);
            minecraft.player.setXRot(2);
            var raw = plateOutputsAt(4);
            var rendered = renderOutputsAt(4);
            var state = raw + "|" + rendered + "|" + hasEdge(overlapWinner, 4);
            if (minecraft.screen != null || !overlapFrames.observe(state)) return false;
            if (!raw.equals(java.util.Set.of("minecraft:stone", "minecraft:glass"))
                    || !rendered.equals(java.util.Set.of(overlapWinner)) || !hasEdge(overlapWinner, 4)
                    || hasBeam(4)) return false;
            screenshot.accept("delayed-world-overlap.png");
            mark(checks, "overlap", true);
            mark(checks, "stable-selection", true);
            phase = Stage.OVERLAP_RELEASE;
            return false;
        }
        if (phase == Stage.OVERLAP_RELEASE) {
            if (server(minecraft, player -> {
                fixture.releaseDelayedOutput(overlapWinner);
                fixture.pump(player, true);
                return true;
            })) {
                overlapFrames.reset();
                phase = Stage.OVERLAP_RECOVERY;
            }
            return false;
        }
        if (phase == Stage.OVERLAP_RECOVERY) {
            if (!server(minecraft, player -> { fixture.pump(player, true); return true; })) return false;
            var raw = plateOutputsAt(4);
            var rendered = renderOutputsAt(4);
            var state = raw + "|" + rendered + "|" + hasEdge(overlapWinner, 4);
            if (!overlapFrames.observe(state)) return false;
            if (!raw.equals(java.util.Set.of(overlapSurvivor))
                    || !rendered.equals(java.util.Set.of(overlapSurvivor)) || !hasEdge(overlapWinner, 4)) return false;
            screenshot.accept("delayed-world-winner-recovered.png");
            mark(checks, "winner-recovery", true);
            mark(checks, "rainbow-preserved", true);
            mark(checks, "plate-recovered", true);
            phase = Stage.OVERLAP_FINISH;
            return false;
        }
        if (phase == Stage.OVERLAP_FINISH) {
            if (server(minecraft, player -> {
                fixture.releaseDelayedOutput(overlapSurvivor);
                fixture.pump(player, true);
                fixture.viewTerminal(player);
                return true;
            })) phase = Stage.OVERLAP_REOPEN;
            return false;
        }
        if (phase == Stage.OVERLAP_REOPEN) {
            if (minecraft.screen == null) {
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(fixture.terminal).add(0, 0, -0.5),
                                Direction.NORTH, fixture.terminal, false));
            } else if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                phase = Stage.PUMP;
            }
            return false;
        }
        if (phase == Stage.WORLD_HIGHLIGHT) {
            minecraft.player.setYRot(9.462f);
            minecraft.player.setXRot(2);
            if (minecraft.screen != null || !worldFrames.observe(hasPlate("minecraft:smooth_stone", 8))) return false;
            if (!hasPlate("minecraft:smooth_stone", 8)) return false;
            screenshot.accept("delayed-world-highlight.png");
            worldFrames.reset();
            phase = Stage.WORLD_RELEASE;
            return false;
        }
        if (phase == Stage.WORLD_RELEASE) {
            if (server(minecraft, player -> {
                if (!fixture.finalOutputReady(player) || !fixture.cpu(player).getCluster().isBusy()) {
                    throw new IllegalStateException("Final delayed output was not held in an active craft");
                }
                fixture.holdFinalOutput = false;
                fixture.pump(player, false);
                return true;
            })) phase = Stage.WORLD_FINISHED;
            return false;
        }
        if (phase == Stage.WORLD_FINISHED) {
            if (!server(minecraft, player -> fixture.pump(player, false) == 1
                    && !fixture.cpu(player).getCluster().isBusy() && fixture.observedNewSamples(player))) return false;
            boolean cleared = !hasPlate("minecraft:stone", 4) && !hasPlate("minecraft:smooth_stone", 8);
            if (!worldFrames.observe(cleared) || !cleared) return false;
            mark(checks, "completed", true);
            mark(checks, "output", true);
            mark(checks, "profile-sample", true);
            mark(checks, "plate-cleared", true);
            screenshot.accept("delayed-world-finished.png");
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(fixture.terminal.east(8).north()).add(0, 0, -0.5),
                            Direction.NORTH, fixture.terminal.east(8).north(), false));
            phase = Stage.REOPEN;
            return false;
        }
        if (phase == Stage.GALLERY_CHAT) {
            if (!(minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen)
                    || !worldFrames.observe(true)) return false;
            screenshot.accept(partialJob ? "job-accuracy-partial.png" : "job-accuracy-full.png");
            mark(checks, partialJob ? "accuracy-partial" : "accuracy-full", true);
            if (!partialJob) {
                screenshot.accept("details-chat.png");
                mark(checks, "details-chat", true);
            }
            minecraft.setScreen(null);
            worldFrames.reset();
            if (partialJob) return true;
            phase = Stage.GALLERY_NEXT_JOB;
            return false;
        }
        if (phase == Stage.GALLERY_NEXT_JOB) {
            if (server(minecraft, player -> { fixture.preparePartialJob(player); return true; })) {
                partialJob = true;
                reviewJob = false;
                phase = Stage.TERMINAL;
            }
            return false;
        }
        var snapshot = UiObservationStore.latest();
        boolean optionsVisible = (phase == Stage.STATUS_OPTIONS || phase == Stage.STATUS_PERSIST)
                && minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen;
        if (leaf.equals("stored-variant-plan") && phase == Stage.PLAN_SORT && variantStep > 0
                && System.nanoTime() >= variantNextDiagnosticAt) {
            variantNextDiagnosticAt = System.nanoTime() + 10_000_000_000L;
            var menu = minecraft.screen instanceof CraftConfirmScreen screen ? screen.getMenu() : null;
            var row = snapshot == null ? null : snapshot.rows().stream()
                    .filter(value -> value.outputId().equals("minecraft:iron_pickaxe")).findFirst().orElse(null);
            LOG.info("Variant wait step={} sort={} frame={} lastFrame={} label={} {}", variantStep, sort,
                    snapshot == null ? -1 : snapshot.frame(), lastFrame,
                    row != null && row.description().stream().anyMatch(value -> value.key().equals(
                            "text.ae2craftingtime.plan.stored_variant")),
                    menu == null ? "menu=absent" : StoredVariantObservation.diagnostic(menu));
        }
        if (!optionsVisible) {
            if (snapshot == null || snapshot.frame() == lastFrame) return false;
            lastFrame = snapshot.frame();
            if (phase == Stage.PLAN_SORT && leaf.equals("standard-plan-controls") && !planEstimatesReady(snapshot.rows())) {
                frames.reset();
                return false;
            }
            var planDescriptions = leaf.equals("craft-lifecycle") && minecraft.screen instanceof CraftConfirmScreen
                    ? snapshot.rows().stream().map(UiSnapshot.Row::description).toList() : List.of();
            if (!frames.observe(List.of(phase, sort, CaptureEvidence.readiness(snapshot), planDescriptions))) return false;
        }
        if (phase == Stage.PLAN_SORT && leaf.equals("stored-variant-plan")) {
            if (!(minecraft.screen instanceof CraftConfirmScreen screen)) return false;
            var menu = screen.getMenu();
            var summary = menu.getPlan();
            if (summary == null) return false;
            if (!variantScaleRequested) {
                variantScaleRequested = true;
                minecraft.options.guiScale().set(0);
                DriverPlatform.resizeDisplay(minecraft);
                frames.reset();
                return false;
            }
            if (!snapshot.gui().inside(new Rect(0, 0, snapshot.screenWidth(), snapshot.screenHeight()))) {
                if (snapshot.guiScale() <= 1) throw new IllegalStateException("Variant plan cannot fit native screen");
                minecraft.options.guiScale().set((int) snapshot.guiScale() - 1);
                DriverPlatform.resizeDisplay(minecraft);
                frames.reset();
                return false;
            }
            var key = fixture.storedVariantKey(1);
            var entry = summary.getEntries().stream().filter(row -> key.equals(row.getWhat())).findFirst().orElse(null);
            if (entry == null || entry.getMissingAmount() <= 0) return false;
            if (variantLifecycle != 0) {
                var expectedFreshVariant = variantLifecycle == 2 || variantLifecycle == 4;
                var row = snapshot.rows().stream().filter(value -> value.outputId().equals("minecraft:iron_pickaxe"))
                        .findFirst().orElse(null);
                if (row == null || row.description().stream().anyMatch(value -> value.key().equals(
                        "text.ae2craftingtime.plan.stored_variant")) != expectedFreshVariant
                        || ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$storedVariant() != expectedFreshVariant)
                    return false;
                if (!variantHover) {
                    moveMouse.accept(row.cell().x() + row.cell().width() / 2,
                            row.cell().y() + row.cell().height() / 2);
                    variantHover = true;
                    return false;
                }
                if (snapshot.tooltip().stream().anyMatch(value -> value.key().equals(
                        "text.ae2craftingtime.plan.stored_variant.explanation")) != expectedFreshVariant
                        || snapshot.tooltip().stream().anyMatch(value -> value.key().equals(
                        "text.ae2craftingtime.plan.stored_variant.suggestion")) != expectedFreshVariant) return false;
                if (expectedFreshVariant && !StoredVariantObservation.received(menu)) return false;
                var revision = ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu)
                        .ae2craftingtime$summaryRevision();
                if (variantLifecycle == 1) {
                    if (menu != variantMenu || summary != variantSummary || revision != variantRevision)
                        throw new IllegalStateException("Network switch replaced the retained native summary");
                    variantSecondSummary = summary;
                    variantSecondRevision = revision;
                    variantSecondMenu = menu.containerId;
                    screenshot.accept("stored-variant-network-switch.png");
                    mark(checks, "network-switch", true);
                    variantLifecycle = 5;
                    variantHover = false;
                    frames.reset();
                    return false;
                }
                if (variantLifecycle == 5) {
                    menu.replan();
                    variantLifecycle = 2;
                    variantHover = false;
                    frames.reset();
                    return false;
                }
                if (variantLifecycle == 2) {
                    if (menu.containerId != variantSecondMenu || summary == variantSecondSummary
                            || revision <= variantSecondRevision) return false;
                    if (!StoredVariantControl.request("replanned", minecraft.player.getUUID(), menu.containerId,
                            revision)) return false;
                    screenshot.accept("stored-variant-replanned.png");
                    mark(checks, "native-replan", true);
                    variantSecondRevision = revision;
                    variantLifecycle = 3;
                    minecraft.player.closeContainer();
                    frames.reset();
                    return false;
                }
                if (variantLifecycle == 4) {
                    if (revision <= 0) return false;
                    screenshot.accept("stored-variant-reconnected.png");
                    mark(checks, "reconnected-fresh", true);
                    return StoredVariantControl.request("complete", minecraft.player.getUUID(), menu.containerId,
                            revision);
                }
                return false;
            }
            if (variantMenu == null) {
                variantMenu = menu;
                variantSummary = summary;
                variantRevision = ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu)
                        .ae2craftingtime$summaryRevision();
                variantMissing = entry.getMissingAmount();
                variantAmounts = summary.getEntries().stream().map(value -> List.of(
                        value.getStoredAmount(), value.getCraftAmount(), value.getMissingAmount())).toList();
                variantButtons = minecraft.screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).map(value -> value.active).toList();
            }
            if (menu != variantMenu || summary != variantSummary || entry.getMissingAmount() != variantMissing
                    || ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) menu)
                            .ae2craftingtime$summaryRevision() != variantRevision)
                throw new IllegalStateException("Stored-variant transition replaced the native plan");
            validateVariantContent(variantAmounts, summary.getEntries().stream().map(value -> List.of(
                    value.getStoredAmount(), value.getCraftAmount(), value.getMissingAmount())).toList(),
                    variantButtons, minecraft.screen.children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).map(value -> value.active).toList());
            for (var control : summary.getEntries()) {
                if (control == entry) continue;
                if (((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) control).ae2craftingtime$storedVariant())
                    throw new IllegalStateException("Unrelated row received a stored-variant diagnosis");
                if (control.getMissingAmount() > 0 && control.getWhat() instanceof appeng.api.stacks.AEFluidKey)
                    mark(checks, "fluid-clear", true);
                if (control.getMissingAmount() > 0 && control.getWhat().equals(appeng.api.stacks.AEItemKey.of(
                        net.minecraft.world.item.Items.DIRT))) mark(checks, "ordinary-clear", true);
            }
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals("minecraft:iron_pickaxe"))
                    .findFirst().orElse(null);
            if (row == null) return false;
            var label = row.description().stream().filter(value -> value.key().equals(
                    "text.ae2craftingtime.plan.stored_variant")).findFirst().orElse(null);
            var expected = variantStep == 1 || variantStep == 4 || variantStep == 7;
            if (((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$storedVariant()
                    != expected || (label != null) != expected) return false;
            Integer neutralColor = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground()
                    ? com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current()
                            .color(com.ctux.ae2craftingtime.core.ClientConfig.Color.TOTAL) : null;
            validateStoredVariantLabel(label, expected, neutralColor);
            if (!variantHover) {
                moveMouse.accept(row.cell().x() + row.cell().width() / 2,
                        row.cell().y() + row.cell().height() / 2);
                variantHover = true;
                return false;
            }
            var explanation = snapshot.tooltip().stream().anyMatch(value -> value.key().equals(
                    "text.ae2craftingtime.plan.stored_variant.explanation"));
            var suggestion = snapshot.tooltip().stream().anyMatch(value -> value.key().equals(
                    "text.ae2craftingtime.plan.stored_variant.suggestion"));
            if (explanation != expected || suggestion != expected) return false;
            if (expected) {
                if (!StoredVariantObservation.received(menu)) return false;
                if (!((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$recurrent()
                        || row.description().stream().noneMatch(value -> value.key().equals(
                                "text.ae2craftingtime.plan.recurrent"))
                        || snapshot.tooltip().stream().noneMatch(value -> value.key().equals(
                                "text.ae2craftingtime.plan.recurrent_hint"))) return false;
                mark(checks, "coexistence", true);
                var drawn = snapshot.text().stream().filter(value -> value.key().equals(
                        "text.ae2craftingtime.plan.stored_variant")).toList();
                if (drawn.size() != 1 || drawn.get(0).bounds() == null) return false;
                validateStoredVariantText(row, drawn.get(0));
            }
            validateStoredVariantRow(snapshot, row);
            mark(checks, "variant-layout", true);
            if (variantStep == 1 && sort < 3) {
                screenshot.accept("stored-variant-sort-" + sort + ".png");
                AbstractWidget button = minecraft.screen.children().stream().filter(TtcSortButton.class::isInstance)
                        .map(TtcSortButton.class::cast).findFirst().orElseThrow();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                sort++;
                variantHover = false;
                frames.reset();
                return false;
            }
            if (sort == 3) mark(checks, "variant-sorts", true);
            var checksByStep = new String[] {"initial-clear", "live-near", "removed-clear", "exact-clear",
                    "restored-near", "exact-only", "other-item", "restored-near"};
            mark(checks, checksByStep[variantStep], true);
            mark(checks, "neutral-normal", label == null || !label.bold()
                    && java.util.Objects.equals(label.color(), neutralColor));
            if (expected) mark(checks, "variant-tooltip", true);
            mark(checks, "unchanged-plan", true);
            if (variantCapturedStep != variantStep) {
                screenshot.accept("stored-variant-" + variantStep + ".png");
                variantCapturedStep = variantStep;
            }
            if (variantStep == 7 && connectedDedicated) {
                variantSwitchPending = true;
                return false;
            }
            if (variantStep == 7) {
                if (!server(minecraft, player -> player.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu confirm
                        && StoredVariantObservation.verifyServer(confirm.getPlan()))) return false;
                mark(checks, "notification-lifecycle", true);
                variantStep = 9;
                minecraft.player.closeContainer();
                return false;
            }
            variantPendingStep = variantStep + 1;
            return false;
        }
        if (phase == Stage.PLAN_SORT && leaf.equals("recurrent-plan")) {
            if (!(minecraft.screen instanceof CraftConfirmScreen screen)) return false;
            if (recurrenceAddonRoute && !recurrenceVisited && !recurrenceWirelessOpened) return false;
            if (!RecurrentPlanObservation.verify(screen.getMenu())) return false;
            if (screen.getMenu().getPlan() == null) return false;
            if (screen.getMenu().getPlan().getEntries().stream().anyMatch(value ->
                    ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) value).ae2craftingtime$storedVariant())
                    || snapshot.rows().stream().anyMatch(value -> value.description().stream().anyMatch(text ->
                    text.key().equals("text.ae2craftingtime.plan.stored_variant")))) return false;
            mark(checks, "variant-clear", true);
            if (!connectedDedicated) {
                if (!recurrenceServerVerified) {
                    if (!server(minecraft, recurrenceFixture::validate)) return false;
                    recurrenceServerVerified = true;
                }
                if (!recurrenceFixture.clientReady(screen.getMenu())) return false;
            }
            var row = snapshot.rows().stream().filter(value -> value.description().stream()
                    .anyMatch(text -> text.key().equals("text.ae2craftingtime.plan.recurrent"))).findFirst()
                    .orElseGet(() -> snapshot.rows().stream().findFirst().orElse(null));
            if (row == null) return false;
            var label = row.description().stream().filter(text -> text.key().equals("text.ae2craftingtime.plan.recurrent")).findFirst().orElse(null);
            if (!connectedDedicated && recurrenceFixture.recurrent() && label == null
                    && !RecurrentPlanFixture.CASES.get(recurrenceCase).equals("large")) return false;
            validateRecurrenceBounds(snapshot);
            if (connectedDedicated && RecurrentPlanControl.state().recurrent() != (label != null)) return false;
            if (connectedDedicated && label != null
                    && !label.arguments().equals(List.of(Long.toString(RecurrentCampaign.REQUESTED_AMOUNT)))) return false;
            validateRecurrenceLabel(snapshot, row, label,
                    com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground(),
                    connectedDedicated, recurrenceFixture.reported(), recurrenceFixture.requestedAmount());
            mark(checks, "recurrent-row", true);
            mark(checks, "red-warning-style", true);
            mark(checks, "unchanged-quantity", label == null || label.arguments().size() == 1
                    && label.rendered().endsWith(label.arguments().get(0)));
            mark(checks, "layout", row.cell().inside(snapshot.gui()));
            if (!connectedDedicated && sort++ < 3) {
                screenshot.accept("recurrent-" + RecurrentPlanFixture.CASES.get(recurrenceCase) + "-sort-" + sort + ".png");
                AbstractWidget button = minecraft.screen.children().stream().filter(TtcSortButton.class::isInstance)
                        .map(TtcSortButton.class::cast).findFirst().orElseThrow();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                frames.reset();
                return false;
            }
            recurrenceHover = label != null;
            moveMouse.accept(row.cell().x() + row.cell().width() / 2, row.cell().y() + row.cell().height() / 2);
            phase = Stage.PLAN_TOOLTIP;
            return false;
        }
        if (phase == Stage.PLAN_TOOLTIP && leaf.equals("recurrent-plan")) {
            // Closing restores the original option, so the recurrent tooltip can disappear while the server task finishes.
            if (!connectedDedicated && operation != null)
                return server(minecraft, player -> { recurrenceFixture.close(); return true; });
            if (snapshot.tooltip().stream().anyMatch(text -> text.key().startsWith(
                    "text.ae2craftingtime.plan.stored_variant"))) return false;
            var recurrent = snapshot.tooltip().stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.plan.recurrent_hint"));
            if (recurrenceHover != recurrent) return false;
            if (recurrenceHover && snapshot.tooltip().stream().noneMatch(text ->
                    text.key().equals("text.ae2craftingtime.plan.recurrent") && !text.bold()
                            && java.util.Objects.equals(text.color(), 0xFF5555))) return false;
            if (connectedDedicated) {
                var action = recurrenceCaptured ? "captured" : recurrenceRejoined ? "rejoined" : recurrenceSwapped ? "swapped" : recurrenceVisited ? "grid" : "initial";
                var observedMenu = ((CraftConfirmScreen) minecraft.screen).getMenu();
                if (!action.equals(recurrenceCapturedAction)) {
                    screenshot.accept("recurrent-connected-" + action + ".png");
                    System.out.println("AE2CT recurrence-frame action=" + action + " player=" + minecraft.player.getUUID()
                            + " menu=" + observedMenu.containerId + " revision="
                            + ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) observedMenu).ae2craftingtime$summaryRevision()
                            + " screenshot=recurrent-connected-" + action + ".png");
                    recurrenceCapturedAction = action;
                }
                if (!RecurrentPlanControl.request(action, minecraft.player.getUUID(), observedMenu.containerId,
                        ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu) observedMenu).ae2craftingtime$summaryRevision())) return false;
                var state = RecurrentPlanControl.state();
                if (!recurrenceVisited) return false;
                if (!recurrenceSwapped) {
                    if (!state.phase().equals("swap")) return false;
                    recurrenceSwapped = true;
                    ((CraftConfirmScreen) minecraft.screen).getMenu().replan();
                    phase = Stage.PLAN_SORT; frames.reset(); return false;
                }
                if (!recurrenceRejoined) {
                    if (!state.phase().equals("reconnect")) return false;
                    recurrenceReconnectRequested = true; return false;
                }
                if (!state.phase().equals("complete")) return false;
                if (!recurrenceCaptured) {
                    screenshot.accept("recurrent-plan-tooltip.png");
                    recurrenceCaptured = true;
                    return false;
                }
            }
            mark(checks, "recurrent-tooltip", true);
            if (!connectedDedicated) {
                screenshot.accept("recurrent-" + RecurrentPlanFixture.CASES.get(recurrenceCase) + "-tooltip.png");
                if (recurrenceCase + 1 < RecurrentPlanFixture.CASES.size()
                        && (!RecurrentPlanFixture.CASES.get(recurrenceCase + 1).equals("large") || DriverPlatform.TARGET.equals("1.21.1-neoforge"))) {
                    recurrenceCase++;
                    recurrenceServerVerified = false;
                    sort = 0;
                    minecraft.player.closeContainer();
                    phase = Stage.PREPARE;
                    frames.reset();
                    return false;
                }
            }
            if (!recurrenceCaptured) screenshot.accept("recurrent-plan-tooltip.png");
            if (!connectedDedicated && !server(minecraft, player -> { recurrenceFixture.close(); return true; })) return false;
            return true;
        }
        boolean plan = phase.ordinal() < Stage.OPEN_STATUS.ordinal();
        String prefix = plan ? "plan" : "status";
        if (phase == Stage.PLAN_SORT && !leaf.equals("standard-plan-controls")) {
            if (!(minecraft.screen instanceof CraftConfirmScreen) || snapshot.rows().stream()
                    .filter(row -> row.craftAmount() > 0).count() < 2) return false;
            if (leaf.equals("craft-lifecycle")) {
                if (reviewJob) {
                    phase = Stage.GALLERY_DETAILS;
                    return false;
                }
                if (!galleryPlanReady(snapshot.rows(), partialJob ? 1 : 0)) return false;
                moveMouse.accept(0, 0);
                if (!snapshot.tooltip().isEmpty()) return false;
                if (partialJob) {
                    screenshot.accept("plan-partial-job.png");
                    phase = Stage.SUBMIT;
                } else {
                    screenshot.accept("plan-no-data.png");
                    mark(checks, "plan-no-data", true);
                    phase = Stage.GALLERY_PARTIAL_PLAN;
                }
            } else {
                mark(checks, "plan", true);
                phase = Stage.SUBMIT;
            }
        } else if (phase == Stage.GALLERY_PARTIAL_PLAN) {
            if (!server(minecraft, player -> { fixture.seed(player, net.minecraft.world.item.Items.STONE); return true; })) return false;
            phase = Stage.GALLERY_PROFILED_PLAN;
        } else if (phase == Stage.GALLERY_PROFILED_PLAN) {
            if (!Boolean.TRUE.equals(checks.get("plan-partial"))) {
                if (!galleryPlanReady(snapshot.rows(), 1) || !snapshot.tooltip().isEmpty()) return false;
                screenshot.accept("plan-partial.png");
                mark(checks, "plan-partial", true);
            }
            if (!Boolean.TRUE.equals(checks.get("plan"))) {
                if (!server(minecraft, player -> { fixture.seed(player, net.minecraft.world.item.Items.SMOOTH_STONE); return true; })) return false;
                mark(checks, "plan", true);
                return false;
            }
            if (!planEstimatesReady(snapshot.rows())) return false;
            screenshot.accept("plan-default.png");
            phase = Stage.SUBMIT;
        } else if (phase == Stage.GALLERY_DETAILS) {
            if (!server(minecraft, player -> {
                var key = ProfilerBridge.key(ProfilerBridge.networkId(fixture.cpu(player).getMainNode().getGrid()),
                        appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.SMOOTH_STONE));
                var accuracy = ProfilerBridge.accuracy(key).orElseThrow(() -> new IllegalStateException("Completed job has no accuracy sample"));
                if (!galleryAccuracyReady(accuracy, partialJob)) throw new IllegalStateException("Unexpected real job coverage: " + accuracy);
                return true;
            })) return false;
            if (!chatCleared) {
                minecraft.gui.getChat().clearMessages(false);
                chatCleared = true;
                stats.next();
            }
            if (!stats.click(minecraft, snapshot, "minecraft:smooth_stone", false)) return false;
            var messages = ((com.ctux.ae2craftingtime.testdriver.mixin.ChatComponentAccessor) minecraft.gui.getChat())
                    .ae2craftingtime_test_driver$messages();
            if (messages.stream().noneMatch(message -> message.content().getString()
                    .endsWith("coverage " + (partialJob ? "1/2" : "2/2")))) {
                throw new IllegalStateException("Stats chat did not report the completed job coverage");
            }
            minecraft.player.closeContainer();
            DriverPlatform.openChat(minecraft);
            chatCleared = false;
            phase = Stage.GALLERY_CHAT;
        } else if (phase == Stage.PLAN_SORT || phase == Stage.STATUS_SORT) {
            var rows = snapshot.rows().stream().filter(row -> row.craftAmount() > 0).map(UiSnapshot.Row::outputId).toList();
            if (!rows.containsAll(List.of("minecraft:stone", "minecraft:smooth_stone"))) return false;
            validatePlanMissingOrder(snapshot, plan);
            if (sort == 0) {
                mark(checks, prefix, true);
                var total = plan ? snapshot.text().stream()
                        .filter(t -> t.key().equals("text.ae2craftingtime.total_ttc")).findFirst().orElse(null) : null;
                if (plan && total == null) return false;
                if (plan) validatePlanTotal(snapshot, total,
                        com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground());
                if (plan) {
                    mark(checks, "total-ttc", true);
                    mark(checks, "layout", true);
                }
                moveMouse.accept(snapshot.gui().x() - 8, snapshot.gui().y() - 8);
                screenshot.accept(prefix + "-default.png");
            }
            if (sort != 1) {
                var expected = plan && sort == 2 ? List.of("minecraft:smooth_stone", "minecraft:stone")
                        : List.of("minecraft:stone", "minecraft:smooth_stone");
                validateSortedRows(rows, expected, prefix, sort);
            }
            if (sort > 0) screenshot.accept(prefix + "-sort-" + sort + ".png");
            if (sort++ < 3) {
                AbstractWidget button = minecraft.screen.children().stream().filter(TtcSortButton.class::isInstance)
                        .map(TtcSortButton.class::cast).findFirst().orElseThrow();
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            } else {
                mark(checks, prefix + "-sort", true);
                if (plan) mark(checks, "missing-first", true);
                sort = 0;
                phase = Stage.values()[phase.ordinal() + 1];
            }
        } else if (phase == Stage.PLAN_TOOLTIP || phase == Stage.STATUS_TOOLTIP) {
            var row = snapshot.rows().stream().filter(r -> r.outputId().equals(statsOutput())).findFirst().orElseThrow();
            moveMouse.accept(row.cell().centerX(), row.cell().centerY());
            if (snapshot.tooltip().stream().noneMatch(t -> t.key().equals("text.ae2craftingtime.details_hint"))) return false;
            mark(checks, prefix + "-tooltip", true);
            screenshot.accept(prefix + "-tooltip.png");
            phase = Stage.values()[phase.ordinal() + 1];
        } else if (phase == Stage.PLAN_DETAILS || phase == Stage.PLAN_RESET || phase == Stage.STATUS_DETAILS || phase == Stage.STATUS_RESET) {
            boolean reset = phase == Stage.PLAN_RESET || phase == Stage.STATUS_RESET;
            if (!stats.click(minecraft, snapshot, statsOutput(), reset)) return false;
            if (reset && !server(minecraft, player -> ProfilerBridge.stats(ProfilerBridge.key(
                    ProfilerBridge.networkId(fixture.cpu(player).getMainNode().getGrid()),
                    appeng.api.stacks.AEItemKey.of(plan ? net.minecraft.world.item.Items.STONE : net.minecraft.world.item.Items.SMOOTH_STONE))).isEmpty())) return false;
            mark(checks, prefix + (reset ? "-reset" : "-details"), true);
            screenshot.accept(prefix + (reset ? "-reset.png" : "-details.png"));
            phase = Stage.values()[phase.ordinal() + 1];
            stats.next();
            if (reset && plan) return true;
        } else if (phase == Stage.SUBMIT) {
            moveMouse.accept(0, 0);
            if (leaf.equals("badge-background")) {
                if (!badgePlanStocked) {
                    if (!server(minecraft, player -> { fixture.seed(player); fixture.supplyPlanInput(player); return true; }))
                        return false;
                    badgePlanStocked = true;
                    ((CraftConfirmScreen) minecraft.screen).getMenu().replan();
                    badgeNextReplanAt = System.nanoTime() + 5_000_000_000L;
                    frames.reset();
                    return false;
                }
                if (snapshot.rows().stream().anyMatch(row -> row.missingAmount() > 0)) return false;
            } else if (!leaf.equals("craft-lifecycle") && !server(minecraft, player -> { fixture.seed(player); return true; }))
                return false;
            var start = minecraft.screen.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(w -> w.getMessage().getString().equals("Start"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Crafting Plan Start button is missing"));
            if (!start.active) {
                if (leaf.equals("badge-background") && System.nanoTime() >= badgeNextReplanAt) {
                    var menu = ((CraftConfirmScreen) minecraft.screen).getMenu();
                    if (badgeReplanAttempts++ >= 3) {
                        throw new IllegalStateException("Crafting Plan stayed partial after supplying input; no CPU="
                                + menu.hasNoCPU() + ", simulation="
                                + (menu.getPlan() != null && menu.getPlan().isSimulation()));
                    }
                    menu.replan();
                    badgeNextReplanAt = System.nanoTime() + 10_000_000_000L;
                    frames.reset();
                }
                return false;
            }
            DriverPlatform.click(minecraft, start.getX() + 4, start.getY() + 4);
            phase = Stage.values()[phase.ordinal() + 1];
        } else if (phase == Stage.ACTIVE) {
            if (!(minecraft.screen instanceof CraftingStatusScreen)) return false;
            var waiting = rowText(snapshot, "minecraft:smooth_stone", "text.ae2craftingtime.waiting");
            var running = rowText(snapshot, "minecraft:stone", "text.ae2craftingtime.ttc");
            if (leaf.equals("standard-status-controls")) {
                var client = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().copy();
                client.features().setEnabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS, true);
                com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.apply(client);
                addonQuantityCases = StandardCraftFixture.addonQuantityCases();
                var accessor = (com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen;
                realStatus = accessor.ae2craftingtime_test_driver$status();
                accessor.ae2craftingtime_test_driver$setStatus(StandardCraftFixture.quantityStatus(quantityCase));
                moveMouse.accept(0, 0);
                phase = Stage.STATUS_AMOUNTS;
                frames.reset();
                return false;
            }
            if (leaf.equals("craft-lifecycle")) {
                mark(checks, "status", true);
                screenshot.accept(partialJob ? "status-partial-job.png" : "status-default.png");
                phase = Stage.PUMP;
            } else if (leaf.equals("delayed-status")) { phase = Stage.DELAYED; }
            else if (waiting != null && running != null) {
                validateLayout(snapshot);
                mark(checks, "waiting", true);
                mark(checks, "running", true);
                mark(checks, "layout", true);
                screenshot.accept("status-waiting-running.png");
                phase = Stage.PUMP;
            }
        } else if (phase == Stage.STATUS_AMOUNTS) {
            if (!(minecraft.screen instanceof CraftingStatusScreen)) return false;
            var expected = StandardCraftFixture.quantityStatus(quantityCase).getEntries().get(0);
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals(expected.getWhat().getId().toString())
                    && value.storedAmount() == expected.getStoredAmount()
                    && value.activeAmount() == expected.getActiveAmount()
                    && value.pendingAmount() == expected.getPendingAmount()).findFirst().orElse(null);
            if (row == null) {
                ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.quantityStatus(quantityCase));
                frames.reset();
                return false;
            }
            boolean empty = quantityCase == 7;
            var amountLines = quantityAmountLines(row, empty, quantityCase);
            if (!empty) {
                var summary = amountLines.get(0);
                String amount = quantityCase < 8 ? List.of("4/10/200", "-/10/200", "10/-/200", "4/10/-",
                        "A10", "C10", "S10").get(quantityCase) :
                        String.join("/", expected.getWhat().formatAmount(expected.getStoredAmount(), appeng.api.stacks.AmountFormat.SLOT),
                                expected.getWhat().formatAmount(expected.getActiveAmount(), appeng.api.stacks.AmountFormat.SLOT),
                                expected.getWhat().formatAmount(expected.getPendingAmount(), appeng.api.stacks.AmountFormat.SLOT));
                validateQuantityText(summary, amount, quantityCase);
                var ttc = row.description().stream().filter(text ->
                        com.ctux.ae2craftingtime.core.CraftingRowState.isBadge(text.key())
                        && !text.key().equals("text.ae2craftingtime.status.amounts")).findFirst();
                Integer color = ttc.isPresent() ? ttc.get().color() : null;
                if (color == null && com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground())
                    color = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current()
                            .color(com.ctux.ae2craftingtime.core.ClientConfig.Color.TOTAL);
                validateQuantityLayout(snapshot, summary, color, quantityCase);
            }
            if (!quantityHovered) {
                moveMouse.accept(row.cell().centerX(), row.cell().centerY());
                quantityHovered = true;
                frames.reset();
                return false;
            }
            if (snapshot.tooltip().isEmpty()) return false;
            validateQuantityLegend(snapshot.tooltip(), empty, quantityCase);
            var labels = List.of(appeng.core.localization.GuiText.FromStorage,
                    appeng.core.localization.GuiText.Crafting, appeng.core.localization.GuiText.Scheduled);
            long[] raw = {expected.getStoredAmount(), expected.getActiveAmount(), expected.getPendingAmount()};
            for (int category = 0; category < raw.length; category++) {
                var label = (net.minecraft.network.chat.contents.TranslatableContents)
                        labels.get(category).text("").getContents();
                String full = raw[category] > 0 ? expected.getWhat().formatAmount(raw[category],
                        appeng.api.stacks.AmountFormat.FULL) : null;
                validateQuantityTooltipCategory(snapshot.tooltip(), label.getKey(), full, category, quantityCase);
            }
            screenshot.accept("status-amounts-" + quantityCase + ".png");
            if (++quantityCase < StandardCraftFixture.QUANTITY_CASES) {
                ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.quantityStatus(quantityCase));
                quantityHovered = false;
                moveMouse.accept(0, 0);
                frames.reset();
                return false;
            }
            ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                    .ae2craftingtime_test_driver$setStatus(addonQuantityCases.isEmpty()
                            ? StandardCraftFixture.quantityStatus(8)
                            : StandardCraftFixture.addonQuantityStatus(addonQuantityCases.get(0)));
            originalGuiScale = minecraft.options.guiScale().get();
            mark(checks, "quantity-cases", true);
            if (addonQuantityCases.isEmpty()) {
                writeAddonKeyEvidence();
                mark(checks, "addon-key-status", true);
                phase = Stage.STATUS_SCALES;
            } else {
                moveMouse.accept(0, 0);
                phase = Stage.STATUS_ADDON_AMOUNTS;
            }
            frames.reset();
        } else if (phase == Stage.STATUS_ADDON_AMOUNTS) {
            if (!(minecraft.screen instanceof CraftingStatusScreen)) return false;
            var addon = addonQuantityCases.get(addonQuantityCase);
            var expected = StandardCraftFixture.addonQuantityStatus(addon).getEntries().get(0);
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals(expected.getWhat().getId().toString())
                    && value.storedAmount() == addon.stored() && value.activeAmount() == addon.active()
                    && value.pendingAmount() == addon.pending())
                    .findFirst().orElse(null);
            if (row == null) {
                ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.addonQuantityStatus(addon));
                frames.reset();
                return false;
            }
            var summary = row.description().stream().filter(value -> value.key().equals(
                    "text.ae2craftingtime.status.amounts")).findFirst().orElse(null);
            var key = expected.getWhat();
            var slot = java.util.List.of(addon.stored(), addon.active(), addon.pending()).stream()
                    .map(value -> key.formatAmount(value, appeng.api.stacks.AmountFormat.SLOT)).toList();
            validateAddonSummary(snapshot, summary, slot, addon.name());
            if (!addonQuantityBadgeCaptured) {
                moveMouse.accept(0, 0);
                screenshot.accept("status-addon-" + addon.name() + ".png");
                addonQuantityBadgeCaptured = true;
                addonQuantityBadgeFrame = TestDriverRuntime.renderedFrames + 2;
                frames.reset();
                return false;
            }
            if (TestDriverRuntime.renderedFrames < addonQuantityBadgeFrame) return false;
            if (!addonQuantityHovered) {
                moveMouse.accept(row.cell().centerX(), row.cell().centerY());
                addonQuantityHovered = true;
                frames.reset();
                return false;
            }
            if (snapshot.tooltip().isEmpty()) return false;
            validateAddonLegend(snapshot.tooltip(), addon.name());
            var labels = List.of(appeng.core.localization.GuiText.FromStorage,
                    appeng.core.localization.GuiText.Crafting, appeng.core.localization.GuiText.Scheduled);
            long[] raw = {addon.stored(), addon.active(), addon.pending()};
            for (int category = 0; category < raw.length; category++) {
                String label = ((net.minecraft.network.chat.contents.TranslatableContents)
                        labels.get(category).text("").getContents()).getKey();
                String full = key.formatAmount(raw[category], appeng.api.stacks.AmountFormat.FULL);
                validateAddonTooltip(snapshot.tooltip(), label, full, addon.name());
            }
            screenshot.accept("status-addon-" + addon.name() + "-tooltip.png");
            moveMouse.accept(0, 0);
            if (++addonQuantityCase < addonQuantityCases.size()) {
                addonQuantityBadgeCaptured = false;
                addonQuantityHovered = false;
                ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.addonQuantityStatus(
                                addonQuantityCases.get(addonQuantityCase)));
                frames.reset();
                return false;
            }
            writeAddonKeyEvidence();
            mark(checks, "addon-key-status", true);
            ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                    .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.quantityStatus(8));
            phase = Stage.STATUS_SCALES;
            frames.reset();
        } else if (phase == Stage.STATUS_SCALES) {
            if (!(minecraft.screen instanceof CraftingStatusScreen)) return false;
            if (amountFontReload != null) {
                if (!amountFontReload.isDone()) return false;
                amountFontReload.join();
                amountFontReload = null;
                frames.reset();
                validateAmountFont(amountFontMode, minecraft.font.width(com.ctux.ae2craftingtime.mc1201.TtcText
                        .statusAmounts("4/10/200")), defaultAmountFontWidth);
                if (amountFontMode == 2) {
                    minecraft.options.guiScale().set(originalGuiScale);
                    DriverPlatform.resizeDisplay(minecraft);
                    ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                            .ae2craftingtime_test_driver$setStatus(realStatus);
                    realStatus = null;
                    mark(checks, "amount-scales", true);
                    phase = Stage.STATUS_OPTIONS;
                }
                return false;
            }
            int requested = quantityScaleCase == 2 ? 0 : quantityScaleCase + 1;
            if (!quantityScaleSet) {
                minecraft.options.guiScale().set(requested);
                DriverPlatform.resizeDisplay(minecraft);
                quantityScaleSet = true;
                frames.reset();
                return false;
            }
            var row = snapshot.rows().stream().filter(value -> value.storedAmount() == 1_000_000_000L
                    && value.activeAmount() == 2_000_000_000L && value.pendingAmount() == 3_000_000_000L)
                    .findFirst().orElse(null);
            if (row == null) {
                ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$setStatus(StandardCraftFixture.quantityStatus(8));
                frames.reset();
                return false;
            }
            if (snapshot.guiScale() <= 0 || requested > 0 && snapshot.guiScale() != requested) return false;
            var amount = row.description().stream().filter(text -> text.key().equals("text.ae2craftingtime.status.amounts"))
                    .findFirst().orElse(null);
            var drawn = rowText(snapshot, row.outputId(), "text.ae2craftingtime.status.amounts");
            validateScaledAmount(snapshot, amount, drawn);
            screenshot.accept("status-scale-" + (amountFontMode == 0 ? "default" : "wide") + "-"
                    + (requested == 0 ? "auto" : requested) + ".png");
            if (++quantityScaleCase < 3) {
                quantityScaleSet = false;
                frames.reset();
                return false;
            }
            var packs = minecraft.getResourcePackRepository();
            if (amountFontMode == 0) {
                defaultAmountFontWidth = minecraft.font.width(com.ctux.ae2craftingtime.mc1201.TtcText
                        .statusAmounts("4/10/200"));
                originalResourcePacks = List.copyOf(packs.getSelectedIds());
                packs.reload();
                if (!packs.getAvailableIds().contains("file/ae2ct-status-wide")
                        || !packs.addPack("file/ae2ct-status-wide"))
                    throw new IllegalStateException("Disposable uniform-font pack was not staged");
                amountFontMode = 1;
                quantityScaleCase = 0;
                quantityScaleSet = false;
            } else {
                packs.setSelected(originalResourcePacks);
                amountFontMode = 2;
            }
            amountFontReload = minecraft.reloadResourcePacks();
            frames.reset();
        } else if (phase == Stage.STATUS_OPTIONS) {
            boolean compact = amountOptionCase == 1 || amountOptionCase == 3 || amountOptionCase == 4;
            boolean time = amountOptionCase == 0 || amountOptionCase >= 3;
            if (minecraft.screen instanceof CraftingStatusScreen statusScreen) {
                if (amountOptionSaving) {
                    var features = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features();
                    validateAmountOptionFeatures(features, compact, time, amountOptionCase);
                    var row = snapshot.rows().stream().filter(value -> value.storedAmount() > 0
                            || value.activeAmount() > 0 || value.pendingAmount() > 0).findFirst().orElse(null);
                    if (row == null) return false;
                    validateAmountOptionSummary(row, compact, amountOptionCase);
                    var nativeKeys = List.of(appeng.core.localization.GuiText.FromStorage,
                            appeng.core.localization.GuiText.Crafting, appeng.core.localization.GuiText.Scheduled)
                            .stream().map(label -> ((net.minecraft.network.chat.contents.TranslatableContents)
                                    label.text("").getContents()).getKey()).toList();
                    validateAmountOptionCategories(row, nativeKeys, compact, time, amountOptionCase);
                    screenshot.accept("status-options-" + amountOptionCase + ".png");
                    amountOptionCase++;
                    amountOptionSaving = false;
                    amountOptionSavingTicks = 0;
                    amountOptionOpen = false;
                    amountOptionSeenCompact = false;
                    amountOptionSeenTime = false;
                    amountOptionOperationStep = 0;
                    amountOptionRenderedAfter = 0;
                    if (amountOptionCase == 7) {
                        mark(checks, "amount-options", true);
                        phase = Stage.STATUS_SORT;
                        frames.reset();
                        return false;
                    }
                }
                if (!amountOptionOpen) {
                    minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(statusScreen));
                    amountOptionOpen = true;
                    amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                    frames.reset();
                } else if (!amountOptionSaving && TestDriverRuntime.renderedFrames >= amountOptionRenderedAfter) {
                    throw new IllegalStateException("Amount options screen closed before save: case=" + amountOptionCase);
                }
                return false;
            }
            if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)
                    || TestDriverRuntime.renderedFrames < amountOptionRenderedAfter) return false;
            if (amountOptionSaving) {
                if (++amountOptionSavingTicks > 100)
                    throw new IllegalStateException("Amount option save did not close screen: case=" + amountOptionCase
                            + " controls=" + minecraft.screen.children().stream()
                            .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                            .map(net.minecraft.client.gui.components.Button.class::cast)
                            .map(button -> button.getMessage().getString() + " active=" + button.active)
                            .toList());
                return false;
            }
            String compactLabel = net.minecraft.client.resources.language.I18n.get(
                    "config.ae2craftingtime.compactStatusAmounts");
            String timeLabel = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.statusRows");
            String enabledLabel = net.minecraft.client.resources.language.I18n.get("options.on");
            if (amountOptionCase >= 4) {
                String colorLabel = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.ttcColors");
                String label = switch (amountOptionOperationStep) {
                    case 2, 3, 8 -> colorLabel;
                    case 4, 5, 9 -> timeLabel;
                    default -> compactLabel;
                };
                var button = seekOptionButton(minecraft, label + ": ");
                if (button == null) return false;
                boolean enabled = button.getMessage().getString().endsWith(enabledLabel);
                validateAmountResetInput(amountOptionCase, amountOptionOperationStep, label, enabled);
                switch (amountOptionOperationStep) {
                    case 0 -> {
                        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                        amountOptionOperationStep = 1;
                    }
                    case 1 -> {
                        amountOptionOperationStep = amountOptionCase == 5 ? 2 : 6;
                    }
                    case 2, 4 -> {
                        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                        amountOptionOperationStep++;
                    }
                    case 3, 5 -> {
                        amountOptionOperationStep++;
                    }
                    case 6 -> {
                        if (amountOptionCase == 5 && !enabled) {
                            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                            amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                            return false;
                        }
                        String actionKey = amountOptionCase == 4 ? "gui.cancel" : amountOptionCase == 5
                                ? "config.ae2craftingtime.reset_group" : "config.ae2craftingtime.reset_all";
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(actionKey));
                        amountOptionOperationStep = 7;
                        if (amountOptionCase == 4) amountOptionSaving = true;
                    }
                    case 7, 8, 9 -> {
                        amountOptionOperationStep = amountOptionCase == 5 ? amountOptionOperationStep + 1 : 10;
                    }
                    case 10 -> {
                        validateAmountResetSaved(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features(),
                                amountOptionCase);
                        amountOptionSaving = true;
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.done"));
                    }
                }
                amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                frames.reset();
                return false;
            }
            for (var child : minecraft.screen.children()) {
                if (!(child instanceof net.minecraft.client.gui.components.Button button)) continue;
                String label = button.getMessage().getString();
                if (label.startsWith(compactLabel + ": ")) {
                    amountOptionSeenCompact = true;
                    if (label.endsWith(enabledLabel) != compact) {
                        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                        amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                        frames.reset();
                        return false;
                    }
                } else if (label.startsWith(timeLabel + ": ")) {
                    amountOptionSeenTime = true;
                    if (label.endsWith(enabledLabel) != time) {
                        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                        amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                        frames.reset();
                        return false;
                    }
                }
            }
            var action = minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals(amountOptionSeenCompact && amountOptionSeenTime
                            ? net.minecraft.client.resources.language.I18n.get("gui.done") : ">"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Amount options control is missing"));
            if (amountOptionSeenCompact && amountOptionSeenTime) amountOptionSaving = true;
            DriverPlatform.click(minecraft, action.getX() + 4, action.getY() + 4);
            amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            frames.reset();
        } else if (phase == Stage.STATUS_SERVER_OFF) {
            if (!amountServerOffApplied) {
                if (!server(minecraft, player -> {
                    var config = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current();
                    amountOriginalProfiling = config.features().enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING);
                    config.features().setEnabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING, false);
                    ProfilerBridge.configure(config);
                    com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.sendTo(player);
                    return true;
                })) return false;
                amountServerOffApplied = true;
                frames.reset();
                return false;
            }
            if (!amountServerOffCaptured) {
                if (com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.profilingEnabled()) return false;
                var row = snapshot.rows().stream().filter(value -> value.storedAmount() > 0
                        || value.activeAmount() > 0 || value.pendingAmount() > 0).findFirst().orElse(null);
                if (row == null) return false;
                var descriptions = snapshot.rows().stream().flatMap(value -> value.description().stream()).toList();
                var nativeKeys = List.of(appeng.core.localization.GuiText.FromStorage,
                        appeng.core.localization.GuiText.Crafting, appeng.core.localization.GuiText.Scheduled)
                        .stream().map(label -> ((net.minecraft.network.chat.contents.TranslatableContents)
                                label.text("").getContents()).getKey()).toList();
                long[] raw = {row.storedAmount(), row.activeAmount(), row.pendingAmount()};
                for (int category = 0; category < raw.length; category++) {
                    String key = nativeKeys.get(category);
                    if (raw[category] > 0 && row.description().stream().noneMatch(text -> text.key().equals(key)))
                        return false;
                }
                if (descriptions.stream().anyMatch(text ->
                        com.ctux.ae2craftingtime.core.CraftingRowState.isBadge(text.key()))) return false;
                screenshot.accept("status-server-profiling-off.png");
                amountServerOffCaptured = true;
            }
            if (!server(minecraft, player -> {
                var config = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current();
                config.features().setEnabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING, amountOriginalProfiling);
                ProfilerBridge.configure(config);
                com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.sendTo(player);
                return true;
            })) return false;
            mark(checks, "server-profiling-off", true);
            if (!Boolean.getBoolean("ae2craftingtime.test.statusRelaunch")) return true;
            phase = Stage.STATUS_PERSIST;
            frames.reset();
        } else if (phase == Stage.STATUS_PERSIST) {
            if (minecraft.screen instanceof CraftingStatusScreen statusScreen) {
                if (amountPersistSaving) {
                    if (com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features()
                            .enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS))
                        throw new IllegalStateException("Compact amounts were not saved off before relaunch");
                    var row = snapshot.rows().stream().filter(value -> value.activeAmount() > 0
                            || value.pendingAmount() > 0 || value.storedAmount() > 0).findFirst().orElse(null);
                    if (row == null || row.description().stream().anyMatch(value -> value.key().equals(
                            "text.ae2craftingtime.status.amounts"))) return false;
                    screenshot.accept("status-saved-off.png");
                    if (checks.values().stream().anyMatch(value -> !value))
                        throw new IllegalStateException("Cannot relaunch with incomplete status checks: " + checks);
                    if (!amountContinuationWritten) {
                        writeAmountContinuation(new AmountContinuation(1, world,
                                System.getProperty("ae2craftingtime.test.campaign", "local"), configHash(minecraft),
                                checks.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList(),
                                List.copyOf(resultScreenshots)));
                        amountContinuationWritten = true;
                        minecraft.stop();
                    }
                    return false;
                }
                if (!amountPersistOpen) {
                    minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(statusScreen));
                    amountPersistOpen = true;
                    frames.reset();
                }
                return false;
            }
            if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)) return false;
            if (amountPersistSaving) return false;
            var label = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.compactStatusAmounts");
            var toggle = optionButton(minecraft, label + ": ");
            if (toggle == null) {
                clickOptionButton(minecraft, ">");
                frames.reset();
                return false;
            }
            if (toggle.getMessage().getString().endsWith(net.minecraft.client.resources.language.I18n.get("options.on"))) {
                DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
                frames.reset();
                return false;
            }
            clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.done"));
            amountPersistSaving = true;
            frames.reset();
        } else if (phase == Stage.RESTORE) {
            moveMouse.accept(0, 0);
            if (server(minecraft, player -> { fixture.seed(player); return true; })) phase = Stage.PUMP;
        } else if (phase == Stage.DELAYED) {
            stonePlateObserved |= hasPlate("minecraft:stone", 4);
            var warning = rowText(snapshot, "minecraft:stone", "text.ae2craftingtime.ttc_delayed");
            if (warning == null) return false;
            validateDelayedWarning(warning);
            validateLayout(snapshot);
            if (!checks.get("delayed")) {
                screenshot.accept("status-delayed.png");
                mark(checks, "delayed", true);
            }
            mark(checks, "row", true);
            mark(checks, "style", true);
            mark(checks, "layout", true);
            if (!checks.get("tooltip")) {
                var row = snapshot.rows().stream().filter(r -> r.outputId().equals("minecraft:stone")).findFirst().orElseThrow();
                moveMouse.accept(row.cell().centerX(), row.cell().centerY());
                if (!delayedTooltip(snapshot)) return false;
                screenshot.accept("delayed-tooltip.png");
                mark(checks, "tooltip", true);
            }
            if (!stonePlateObserved) return false;
            var raw = plateOutputsAt(4);
            var rendered = renderOutputsAt(4);
            if (!raw.equals(java.util.Set.of("minecraft:stone", "minecraft:glass")) || rendered.size() != 1) return false;
            if (overlapWinner == null) {
                overlapWinner = rendered.iterator().next();
                overlapSurvivor = overlapWinner.equals("minecraft:stone") ? "minecraft:glass" : "minecraft:stone";
                overlapFrames.reset();
            }
            if (!rendered.equals(java.util.Set.of(overlapWinner)) || !overlapFrames.observe(overlapWinner)) return false;
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals(overlapWinner)).findFirst().orElseThrow();
            DriverPlatform.doubleClick(minecraft, row.cell().centerX(), row.cell().centerY());
            phase = Stage.OVERLAP_POSITION;
            return false;
        } else if (phase == Stage.PUMP) {
            boolean complete = server(minecraft, player -> {
                long output = fixture.pump(player, true);
                dispatched = fixture.cpu(player).getCluster().craftingLogic.getWaitingFor(
                        appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.SMOOTH_STONE)) > 0;
                progressed = fixture.returnedStone;
                finalOutputReady = fixture.finalOutputReady(player);
                return output == 1 && !fixture.cpu(player).getCluster().isBusy() && fixture.observedNewSamples(player);
            });
            if (leaf.equals("delayed-status") && progressed && dispatched) {
                if (operation == null && finalOutputReady
                        && rowText(snapshot, "minecraft:smooth_stone", "text.ae2craftingtime.ttc_delayed") != null
                        && hasPlate("minecraft:smooth_stone", 8)) {
                    mark(checks, "final-plate", true);
                    screenshot.accept("delayed-final-held.png");
                    minecraft.player.closeContainer();
                    phase = Stage.WORLD_POSITION;
                    return false;
                }
            }
            if (leaf.equals("waiting-status") && dispatched && progressed
                    && rowText(snapshot, "minecraft:smooth_stone", "text.ae2craftingtime.ttc") != null
                    && rowText(snapshot, "minecraft:smooth_stone", "text.ae2craftingtime.waiting") == null) {
                mark(checks, "first-dispatch", true);
                mark(checks, "recovered", true);
                screenshot.accept("waiting-recovered.png");
                return true;
            }
            if (leaf.equals("running-status") && progressed && dispatched
                    && rowText(snapshot, "minecraft:smooth_stone", "text.ae2craftingtime.ttc") != null
                    && snapshot.text().stream().anyMatch(t -> t.key().equals("native-title")
                            && t.bounds() != null && t.bounds().y() < snapshot.gui().y() + 19 && t.bounds().inside(snapshot.gui()))) {
                validateLayout(snapshot);
                mark(checks, "progress", true);
                mark(checks, "header", true);
                screenshot.accept("running-progress.png");
                return true;
            }
            var header = snapshot.text().stream().filter(t -> t.bounds() != null && t.bounds().y() < snapshot.gui().y() + 19
                    && t.key().equals("native-title")).findFirst();
            if (leaf.equals("standard-status-controls") && header.isPresent() && !Boolean.TRUE.equals(checks.get("header"))) {
                validateStatusHeader(snapshot, header.get());
                mark(checks, "header", true);
                mark(checks, "layout", true);
                screenshot.accept("status-progress.png");
                phase = Stage.STATUS_SERVER_OFF;
                frames.reset();
                return false;
            }
            if (complete) {
                mark(checks, "output", true);
                mark(checks, "profile-sample", true);
                phase = Stage.values()[phase.ordinal() + 1];
            }
        } else if (phase == Stage.FINISHED) {
            // Older AE2 can retain its last incremental row after the CPU becomes idle.
            // Preserve that view, then reopen through the actual return/status buttons.
            validateCompletedStatus(snapshot, leaf);
            if (leaf.equals("craft-lifecycle")) mark(checks, "total-cleared", true);
            if (leaf.equals("craft-lifecycle")) screenshot.accept(partialJob ? "status-partial-finished.png" : "status-finished-job.png");
            var button = minecraft.screen.children().stream().filter(appeng.client.gui.widgets.TabButton.class::isInstance)
                    .map(appeng.client.gui.widgets.TabButton.class::cast).filter(w -> w.visible).findFirst().orElseThrow();
            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            phase = Stage.values()[phase.ordinal() + 1];
        } else if (phase == Stage.EMPTY && minecraft.screen instanceof CraftingStatusScreen
                && snapshot.rows().stream().noneMatch(row -> row.craftAmount() > 0)) {
            mark(checks, "completed", true);
            if (leaf.equals("delayed-status")) {
                if (snapshot.text().stream().anyMatch(t -> t.key().equals("text.ae2craftingtime.ttc_delayed"))) return false;
                mark(checks, "recovered", true);
                screenshot.accept("delayed-recovered.png");
            } else { screenshot.accept(partialJob ? "status-partial-completed.png" : "status-completed.png"); }
            if (leaf.equals("craft-lifecycle")) {
                minecraft.player.closeContainer();
                reviewJob = true;
                phase = Stage.TERMINAL;
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean tickConnectedSuspension(Minecraft minecraft, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (!DriverPlatform.TARGET.equals("1.20.1-forge"))
            throw new IllegalStateException("Connected suspension requires Forge 1.20.1");
        var role = System.getProperty("ae2craftingtime.test.role", "");
        if (!role.equals("alpha") && !role.equals("beta"))
            throw new IllegalStateException("Connected suspension needs Alpha/Beta role");
        boolean reload = Boolean.getBoolean("ae2craftingtime.test.suspensionReload");
        var state = CpuListTtcControl.state();
        if (!state.ready() || !state.epoch().equals(CpuListTtcControl.epoch()) || state.serverState().isBlank())
            return false;
        var serverJob = new com.google.gson.Gson().fromJson(state.serverState(),
                StandardCraftFixture.SuspensionState.class);
        fixture.checkpoint = "suspension=" + state.phase() + " " + state.serverState();
        if (reload && suspensionStage == 8)
            return state.phase().equals("completed") && serverJob != null && serverJob.networkOutput() == 64
                    && CpuListTtcControl.request("complete-observed");
        if (serverJob == null || serverJob.jobId().isBlank()) return false;
        if (suspensionLargeId == null) suspensionLargeId = serverJob.jobId();
        if (!suspensionLargeId.equals(serverJob.jobId()))
            throw new IllegalStateException("Connected suspension changed job identity");
        if (fixture.terminal == null) fixture.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
        if (suspensionStage == 0) {
            if (minecraft.screen == null) {
                openSuspensionCpu(minecraft, 0);
                return false;
            }
            var selected = selectedSuspensionSnapshot(minecraft);
            if (selected == null || !selected.hasJob() || !selected.jobId().toString().equals(suspensionLargeId)) return false;
            mark(checks, reload ? "same-loaded-job" : "same-live-job", true);
            if (!reload) {
                mark(checks, "native-menu", suspensionButton(minecraft, "Suspend") != null);
                screenshot.accept("crafting-suspension-connected-running.png");
            } else {
                mark(checks, "paused-visible", selected.suspended() && serverJob.suspended());
                screenshot.accept("crafting-suspension-connected-loaded.png");
            }
            phase = Stage.ACTIVE;
            suspensionStage = 1;
            return false;
        }
        if (!reload) {
            if (suspensionStage == 1 && !suspensionRunningObserved) {
                if (!CpuListTtcControl.request("running-observed")) return false;
                suspensionRunningObserved = true;
            }
            if (suspensionStage == 1 && role.equals("alpha")) {
                if (!state.phase().equals("pause-ready")) return false;
                var button = suspensionButton(minecraft, "Suspend");
                if (button == null) return false;
                DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                suspensionStage = 2;
                return false;
            }
            if (suspensionStage <= 2) {
                var selected = selectedSuspensionSnapshot(minecraft);
                if (selected == null || !selected.suspended() || !serverJob.suspended()) return false;
                mark(checks, "paused-visible", selected.jobId().toString().equals(suspensionLargeId));
                screenshot.accept("crafting-suspension-connected-paused.png");
                suspensionStage = 3;
            }
            if (suspensionStage == 3 && CpuListTtcControl.request("pause-observed")) {
                mark(checks, "paused-saved", true);
                return true;
            }
            return false;
        }
        if (suspensionStage == 1) {
            if (!CpuListTtcControl.request("loaded")) return false;
            suspensionStage = 2;
            return false;
        }
        if (role.equals("beta") && suspensionStage == 2) {
            var menu = minecraft.player.containerMenu;
            if (!(menu instanceof appeng.menu.me.crafting.CraftingCPUMenu)) return false;
            suspensionStaleMenu = menu.containerId;
            suspensionStaleContext = com.ctux.ae2craftingtime.mc1201.StatsRequestContext.cpuContext(
                    (appeng.menu.me.crafting.CraftingCPUMenu) menu);
            minecraft.player.closeContainer();
            suspensionStage = 3;
            return false;
        }
        if (role.equals("beta") && suspensionStage == 3) {
            if (minecraft.screen == null) { openSuspensionCpu(minecraft, 1); return false; }
            if (!(minecraft.player.containerMenu instanceof appeng.menu.me.crafting.CraftingCPUMenu)) return false;
            if (!suspensionActionSent) {
                sendStaleSuspension(suspensionStaleMenu, suspensionStaleContext,
                        java.util.UUID.fromString(suspensionLargeId), false);
                suspensionActionSent = true;
            }
            suspensionStage = 4;
            return false;
        }
        if (role.equals("beta") && suspensionStage == 4) {
            if (!serverJob.suspended() || !CpuListTtcControl.request("stale-sent")) return false;
            mark(checks, "stale-rejected", true);
            minecraft.player.closeContainer();
            suspensionStage = 5;
            return false;
        }
        if (role.equals("beta") && suspensionStage == 5) {
            if (minecraft.screen == null) { openSuspensionCpu(minecraft, 0); return false; }
            var selected = selectedSuspensionSnapshot(minecraft);
            if (selected == null || !selected.hasJob()
                    || !selected.jobId().toString().equals(suspensionLargeId)) return false;
            suspensionStage = 6;
            return false;
        }
        if (role.equals("alpha") && suspensionStage == 2) {
            if (!state.phase().equals("resume-ready")) return false;
            var selected = selectedSuspensionSnapshot(minecraft);
            if (selected == null || !selected.suspended()) return false;
            mark(checks, "stale-rejected", serverJob.suspended());
            var button = suspensionButton(minecraft, "Resume");
            if (button == null) return false;
            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            suspensionStage = 6;
            return false;
        }
        if (suspensionStage == 6) {
            var selected = selectedSuspensionSnapshot(minecraft);
            if (selected == null || selected.suspended() || serverJob.suspended() || !serverJob.busy()) return false;
            mark(checks, "same-job-resumed", selected.jobId().toString().equals(suspensionLargeId));
            mark(checks, "resumed-visible", suspensionButton(minecraft, "Suspend") != null);
            screenshot.accept("crafting-suspension-connected-resumed.png");
            suspensionStage = 7;
        }
        if (suspensionStage == 7 && CpuListTtcControl.request("resumed")) suspensionStage = 8;
        return false;
    }

    private void openSuspensionCpu(Minecraft minecraft, int index) {
        var cpu = fixture.terminal.west(2 + index * 2);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cpu).add(0, 0, -0.5), Direction.NORTH, cpu, false));
    }

    private static net.minecraft.client.gui.components.Button suspensionButton(Minecraft minecraft, String text) {
        if (minecraft.screen == null) return null;
        return minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(text)).findFirst().orElse(null);
    }

    private static net.minecraft.client.gui.components.Button suspensionButtonStarting(Minecraft minecraft, String text) {
        if (minecraft.screen == null) return null;
        return minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(text)).findFirst().orElse(null);
    }

    private static com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot selectedSuspensionSnapshot(
            Minecraft minecraft) {
        if (!(minecraft.player.containerMenu instanceof appeng.menu.me.crafting.CraftingCPUMenu menu)) return null;
        try {
            return (com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot) menu.getClass()
                    .getMethod("ae2craftingtime$suspensionSnapshot").invoke(menu);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Forge suspension menu state unavailable", error);
        }
    }

    static boolean suspensionCaptureReady(com.ctux.ae2craftingtime.core.CraftingSuspension.Snapshot selected,
            UiSnapshot observed, boolean completed) {
        if (selected == null || selected.suspended() || observed == null
                || !observed.screen().equals("appeng.client.gui.me.crafting.CraftingCPUScreen")) return false;
        if (observed.text().stream().anyMatch(text -> text.key().equals("gui.ae2craftingtime.suspended")))
            return false;
        return completed ? !selected.hasJob() && observed.rows().isEmpty() : !selected.enabled();
    }

    private static void sendStaleSuspension(int container, long context, java.util.UUID job, boolean desired) {
        try {
            var packet = Class.forName("com.ctux.ae2craftingtime.mc1201.net.CraftingSuspensionC2S");
            var request = new com.ctux.ae2craftingtime.core.CraftingSuspension.Request(container, context, job, desired);
            var message = packet.getConstructor(com.ctux.ae2craftingtime.core.CraftingSuspension.Request.class)
                    .newInstance(request);
            com.ctux.ae2craftingtime.mc1201.StatsNetwork.class.getMethod("sendToServer", packet)
                    .invoke(null, message);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot send retained stale Forge suspension request", error);
        }
    }

    private boolean suspensionPausedStable(ServerPlayer player) {
        var large = fixture.suspensionState(player, 0);
        if (!suspensionPausedJobReady(large, suspensionLargeId)) return false;
        var tick = player.level().getGameTime();
        if (suspensionPauseTick == 0) {
            if (suspensionStage == 3) {
                validateSuspensionInFlightReturn(large);
                suspensionReturned = large.remaining();
            }
            suspensionPauseTick = tick;
            suspensionUndispatched = large.undispatched();
            fixture.pumpSuspension(player);
            return false;
        }
        fixture.pumpSuspension(player);
        large = fixture.suspensionState(player, 0);
        validateSuspensionDispatch(large.undispatched(), suspensionUndispatched);
        return tick - suspensionPauseTick >= 10;
    }

    private boolean tickSuspension(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (!DriverPlatform.TARGET.equals("1.20.1-forge"))
            throw new IllegalStateException("Crafting suspension requires Forge 1.20.1");
        if (suspensionStage == 0) {
            fixture.configureSuspension();
            if (server(minecraft, player -> fixture.prepare(player, marker) && fixture.submitSuspensionLarge(player))) {
                mark(checks, "standard-cpus", true);
                phase = Stage.ACTIVE;
                suspensionStage++;
            }
            return false;
        }
        if (suspensionStage == 1) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var large = fixture.suspensionState(player, 0);
                if (large.undispatched() >= 64 || large.furnaceInput() == 0 || large.networkOutput() == 0)
                    return false;
                suspensionLargeId = large.jobId();
                return !suspensionLargeId.isEmpty();
            })) return false;
            mark(checks, "real-dispatch", true);
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 2) {
            if (minecraft.screen == null) {
                var cpu = fixture.terminal.west(2);
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(cpu).add(0, 0, -0.5), Direction.NORTH, cpu, false));
                return false;
            }
            if (!(minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?> screen)) return false;
            var cancel = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Cancel")).findFirst().orElse(null);
            var suspend = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Suspend")).findFirst().orElse(null);
            if (cancel == null || suspend == null) return false;
            mark(checks, "button-geometry", suspend.getX() == cancel.getX() - 60
                    && suspend.getY() == cancel.getY() && suspend.getWidth() == 50 && suspend.getHeight() == 20);
            if (suspensionCycle == 1) screenshot.accept("crafting-suspension-running.png");
            suspensionPauseTick = 0;
            DriverPlatform.click(minecraft, suspend.getX() + 4, suspend.getY() + 4);
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 3) {
            if (!server(minecraft, this::suspensionPausedStable)) return false;
            mark(checks, "paused-no-dispatch", true);
            var screen = minecraft.screen;
            var observed = UiObservationStore.latest();
            boolean title = observed != null && observed.text().stream()
                    .anyMatch(line -> line.key().equals("gui.ae2craftingtime.suspended")
                            && line.bounds() != null && line.bounds().inside(observed.gui()));
            if (!title) return false;
            var titleText = observed.text().stream()
                    .filter(line -> line.key().equals("gui.ae2craftingtime.suspended")).findFirst().orElseThrow();
            mark(checks, "suspended-badge", recurrentBadgeMatches(observed.badges(), titleText.bounds(),
                    com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground()));
            mark(checks, "suspended-rows", observed.text().stream().noneMatch(line ->
                    line.key().startsWith("text.ae2craftingtime.")
                            && !line.key().contains("amount")));
            mark(checks, "suspended-title", screen != null && screen.children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .anyMatch(button -> button.getMessage().getString().equals("Resume")) && title);
            if (suspensionCycle == 1) screenshot.accept("crafting-suspension-paused.png");
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 4) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var large = fixture.suspensionState(player, 0);
                validateSuspensionPausedJob(large, suspensionLargeId, suspensionUndispatched);
                if (large.remaining() >= suspensionReturned) return false;
                mark(checks, "in-flight-progress", true);
                if (!fixture.submitSuspensionSmall(player)) return false;
                var small = fixture.suspensionState(player, 1);
                return !small.jobId().isEmpty() && !small.jobId().equals(suspensionLargeId);
            })) return false;
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 5) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var large = fixture.suspensionState(player, 0);
                var small = fixture.suspensionState(player, 1);
                validateSuspensionDispatch(large.undispatched(), suspensionUndispatched);
                var delayTicks = Math.max(400,
                        com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current()
                                .minimumNoProgressSeconds() * 20L) + 20;
                if (player.level().getGameTime() - suspensionPauseTick < delayTicks) return false;
                var key = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(
                        com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(
                                fixture.suspensionCpu(player, 0).getGrid()),
                        appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT));
                validateSuspensionDiagnostics(com.ctux.ae2craftingtime.mc1201.ProfilerBridge.isStillDelayed(key),
                        () -> com.ctux.ae2craftingtime.mc1201.ProfilerBridge.remainingJobSeconds(
                                fixture.suspensionCpu(player, 0)).isPresent());
                return !small.busy() && large.busy() && large.jobId().equals(suspensionLargeId);
            })) return false;
            mark(checks, "small-completes", true);
            mark(checks, "paused-diagnostics-clean", !hasPlate("minecraft:iron_ingot", 4)
                    && !hasPlate("minecraft:iron_ingot", 8));
            if (suspensionCycle == 1) screenshot.accept("crafting-suspension-small-complete.png");
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 6) {
            var resume = minecraft.screen.children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Resume"))
                    .findFirst().orElse(null);
            if (resume == null) return false;
            DriverPlatform.click(minecraft, resume.getX() + 4, resume.getY() + 4);
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 7) {
            if (suspensionCycle == 1 && suspensionResumeTick > 0 && !suspensionResumedCaptured) {
                var selected = selectedSuspensionSnapshot(minecraft);
                if (selected == null || selected.suspended()) return false;
                screenshot.accept("crafting-suspension-resumed.png");
                suspensionResumedCaptured = true;
            }
            if (!server(minecraft, player -> {
                if (suspensionCycle != 1 || suspensionDelayedObserved) fixture.pumpSuspension(player);
                var large = fixture.suspensionState(player, 0);
                validateSuspensionResumedJob(large, suspensionLargeId);
                if (large.busy() && !large.suspended()) mark(checks, "same-job-resumed", true);
                if (suspensionCycle == 1 && !suspensionDelayedObserved) {
                    if (large.suspended()) return false;
                    var tick = player.level().getGameTime();
                    if (suspensionResumeTick == 0) suspensionResumeTick = tick;
                    var key = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(
                            com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(
                                    fixture.suspensionCpu(player, 0).getGrid()),
                            appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT));
                    var delayed = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.isStillDelayed(key);
                    validateSuspensionDelay(delayed, tick - suspensionResumeTick,
                            () -> com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current()
                                    .minimumNoProgressSeconds() * 20L - 5);
                    if (!delayed) return false;
                    mark(checks, "resume-delay-rearms", true);
                    suspensionDelayedObserved = true;
                }
                if (suspensionCycle < 3) return large.busy() && !large.suspended();
                if (large.busy()) return false;
                mark(checks, "exact-output", large.networkOutput() == 66);
                return true;
            })) return false;
            if (suspensionCycle < 3) {
                suspensionStage = 8;
                return false;
            }
            suspensionStage = 20;
            return false;
        }
        if (suspensionStage == 8) {
            if (!server(minecraft, player -> {
                var large = fixture.suspensionState(player, 0);
                return large.busy() && !large.suspended();
            })) return false;
            var suspend = minecraft.screen.children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Suspend"))
                    .findFirst().orElse(null);
            if (suspend == null) return false;
            suspensionPauseTick = 0;
            DriverPlatform.click(minecraft, suspend.getX() + 4, suspend.getY() + 4);
            suspensionStage++;
            return false;
        }
        if (suspensionStage == 9) {
            if (!server(minecraft, this::suspensionPausedStable)) return false;
            var resume = minecraft.screen.children().stream()
                    .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast)
                    .filter(button -> button.getMessage().getString().equals("Resume"))
                    .findFirst().orElse(null);
            if (resume == null) return false;
            DriverPlatform.click(minecraft, resume.getX() + 4, resume.getY() + 4);
            suspensionCycle++;
            suspensionStage = suspensionCycle < 3 ? 8 : 10;
            return false;
        }
        if (suspensionStage == 10) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var large = fixture.suspensionState(player, 0);
                return large.busy() && !large.suspended();
            })) return false;
            var suspend = suspensionButton(minecraft, "Suspend");
            if (suspend == null) return false;
            suspensionPauseTick = 0;
            DriverPlatform.click(minecraft, suspend.getX() + 4, suspend.getY() + 4);
            suspensionStage = 11;
            return false;
        }
        if (suspensionStage == 11) {
            if (!server(minecraft, this::suspensionPausedStable)) return false;
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen(minecraft.screen));
            suspensionStage = 12;
            return false;
        }
        if (suspensionStage == 12) {
            if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen)) return false;
            var option = suspensionButtonStarting(minecraft, "Allow crafting suspension: ");
            if (option == null || !option.active) return false;
            mark(checks, "option-visible", true);
            DriverPlatform.click(minecraft, option.getX() + 4, option.getY() + 4);
            suspensionStage = 13;
            return false;
        }
        if (suspensionStage == 13) {
            var done = suspensionButton(minecraft, "Done");
            if (done == null) return false;
            DriverPlatform.click(minecraft, done.getX() + 4, done.getY() + 4);
            suspensionStage = 14;
            return false;
        }
        if (suspensionStage == 14) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                return !com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.enabled(
                            com.ctux.ae2craftingtime.core.OptionFeature.CRAFTING_SUSPENSION)
                        && !fixture.suspensionState(player, 0).suspended();
            })) return false;
            mark(checks, "disabled-resumes", true);
            mark(checks, "three-cycles", suspensionCycle == 3);
            if (!(minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?>)) return false;
            if (!suspensionCaptureReady(selectedSuspensionSnapshot(minecraft), UiObservationStore.latest(), false))
                return false;
            screenshot.accept("crafting-suspension-disabled.png");
            var menu = (appeng.menu.me.crafting.CraftingCPUMenu) minecraft.player.containerMenu;
            sendStaleSuspension(menu.containerId,
                    com.ctux.ae2craftingtime.mc1201.StatsRequestContext.cpuContext(menu),
                    java.util.UUID.fromString(suspensionLargeId), true);
            suspensionGuardTicks = 0;
            suspensionStage = 15;
            return false;
        }
        if (suspensionStage == 15) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                return !fixture.suspensionState(player, 0).suspended();
            })) return false;
            if (++suspensionGuardTicks < 5) return false;
            mark(checks, "disabled-action-rejected", true);
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen(minecraft.screen));
            suspensionStage = 16;
            return false;
        }
        if (suspensionStage == 16) {
            var option = suspensionButtonStarting(minecraft, "Allow crafting suspension: ");
            if (option == null || !option.active) return false;
            DriverPlatform.click(minecraft, option.getX() + 4, option.getY() + 4);
            suspensionStage = 17;
            return false;
        }
        if (suspensionStage == 17) {
            var profiling = suspensionButtonStarting(minecraft, "Profiling and TTC: ");
            if (profiling == null || !profiling.active) return false;
            DriverPlatform.click(minecraft, profiling.getX() + 4, profiling.getY() + 4);
            suspensionStage = 18;
            return false;
        }
        if (suspensionStage == 18) {
            var done = suspensionButton(minecraft, "Done");
            if (done == null) return false;
            DriverPlatform.click(minecraft, done.getX() + 4, done.getY() + 4);
            suspensionStage = 19;
            return false;
        }
        if (suspensionStage == 19) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var cfg = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current();
                return cfg.features().enabled(com.ctux.ae2craftingtime.core.OptionFeature.CRAFTING_SUSPENSION)
                        && !cfg.features().enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING)
                        && !fixture.suspensionState(player, 0).suspended();
            })) return false;
            if (!(minecraft.screen instanceof appeng.client.gui.me.crafting.CraftingCPUScreen<?>)) return false;
            var suspend = suspensionButton(minecraft, "Suspend");
            if (suspend == null || !suspend.visible || !suspend.active) return false;
            DriverPlatform.click(minecraft, suspend.getX() + 4, suspend.getY() + 4);
            suspensionStage = 40;
            return false;
        }
        if (suspensionStage == 40) {
            if (!server(minecraft, player -> fixture.suspensionState(player, 0).suspended())) return false;
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen(minecraft.screen));
            suspensionStage = 41;
            return false;
        }
        if (suspensionStage == 41) {
            var option = suspensionButtonStarting(minecraft, "Allow crafting suspension: ");
            if (option == null || !option.active) return false;
            DriverPlatform.click(minecraft, option.getX() + 4, option.getY() + 4);
            suspensionStage = 42;
            return false;
        }
        if (suspensionStage == 42) {
            var done = suspensionButton(minecraft, "Done");
            if (done == null) return false;
            DriverPlatform.click(minecraft, done.getX() + 4, done.getY() + 4);
            suspensionStage = 43;
            return false;
        }
        if (suspensionStage == 43) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var cfg = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current();
                return !cfg.features().enabled(com.ctux.ae2craftingtime.core.OptionFeature.CRAFTING_SUSPENSION)
                        && !cfg.features().enabled(com.ctux.ae2craftingtime.core.OptionFeature.PROFILING)
                        && !fixture.suspensionState(player, 0).suspended();
            })) return false;
            mark(checks, "profiling-off-recovery", true);
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen(minecraft.screen));
            suspensionStage = 44;
            return false;
        }
        if (suspensionStage == 44) {
            var option = suspensionButtonStarting(minecraft, "Allow crafting suspension: ");
            if (option == null || !option.active) return false;
            DriverPlatform.click(minecraft, option.getX() + 4, option.getY() + 4);
            suspensionStage = 45;
            return false;
        }
        if (suspensionStage == 45) {
            var done = suspensionButton(minecraft, "Done");
            if (done == null) return false;
            DriverPlatform.click(minecraft, done.getX() + 4, done.getY() + 4);
            suspensionStage = 7;
            return false;
        }
        if (suspensionStage == 20) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                if (suspensionCase == 0) {
                    suspensionPreviousId = suspensionLargeId;
                    fixture.beginSuspensionCase(player, 4);
                    suspensionCase = 1;
                }
                return fixture.submitSuspensionLarge(player);
            })) return false;
            suspensionStage = 21;
            return false;
        }
        if (suspensionStage == 21) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var job = fixture.suspensionState(player, 0);
                if (job.busy() && job.undispatched() < 4 && job.furnaceInput() > 0) {
                    suspensionLargeId = job.jobId();
                    return true;
                }
                return false;
            })) return false;
            validateSuspensionReplacement(suspensionLargeId, suspensionPreviousId);
            if (!(minecraft.player.containerMenu instanceof appeng.menu.me.crafting.CraftingCPUMenu)) {
                openSuspensionCpu(minecraft, 0);
                return false;
            }
            var menu = (appeng.menu.me.crafting.CraftingCPUMenu) minecraft.player.containerMenu;
            sendStaleSuspension(menu.containerId,
                    com.ctux.ae2craftingtime.mc1201.StatsRequestContext.cpuContext(menu),
                    java.util.UUID.fromString(suspensionPreviousId), true);
            suspensionGuardTicks = 0;
            suspensionStage = 22;
            return false;
        }
        if (suspensionStage == 22) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var job = fixture.suspensionState(player, 0);
                return job.busy() && !job.suspended() && job.jobId().equals(suspensionLargeId);
            })) return false;
            if (++suspensionGuardTicks < 5) return false;
            mark(checks, "stale-job-rejected", true);
            var button = suspensionButton(minecraft, "Suspend");
            if (button == null) return false;
            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            suspensionStage = 23;
            return false;
        }
        if (suspensionStage == 23) {
            if (!server(minecraft, player -> fixture.suspensionState(player, 0).suspended())) return false;
            var cancel = suspensionButton(minecraft, "Cancel");
            if (cancel == null || !cancel.active) return false;
            DriverPlatform.click(minecraft, cancel.getX() + 4, cancel.getY() + 4);
            suspensionStage = 24;
            return false;
        }
        if (suspensionStage == 24) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                return !fixture.suspensionState(player, 0).busy();
            })) return false;
            var cleared = selectedSuspensionSnapshot(minecraft);
            if (cleared == null || cleared.hasJob() || cleared.suspended()) return false;
            mark(checks, "cancel-while-paused", true);
            screenshot.accept("crafting-suspension-cancelled.png");
            suspensionStage = 25;
            return false;
        }
        if (suspensionStage == 25) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var job = fixture.suspensionState(player, 0);
                return job.furnaceInput() == 0 && job.furnaceOutput() == 0
                        && job.networkRaw() + job.networkOutput() == 4;
            })) return false;
            mark(checks, "cancel-conserved", true);
            suspensionStage = 26;
            return false;
        }
        if (suspensionStage == 26) {
            if (!server(minecraft, player -> {
                if (suspensionCase == 1) {
                    fixture.beginSuspensionCase(player, 1);
                    suspensionFinalRawBefore = fixture.suspensionState(player, 0).networkRaw();
                    suspensionCase = 2;
                }
                return fixture.submitSuspensionLarge(player);
            })) return false;
            suspensionStage = 27;
            return false;
        }
        if (suspensionStage == 27) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var job = fixture.suspensionState(player, 0);
                if (job.busy() && job.undispatched() == 0 && job.furnaceInput() > 0) {
                    suspensionLargeId = job.jobId();
                    return true;
                }
                return false;
            })) return false;
            suspensionStage = 28;
            return false;
        }
        if (suspensionStage == 28) {
            var button = suspensionButton(minecraft, "Suspend");
            if (button == null) return false;
            DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
            suspensionStage = 29;
            return false;
        }
        if (suspensionStage == 29) {
            if (!server(minecraft, player -> fixture.suspensionState(player, 0).suspended())) return false;
            suspensionStage = 30;
            return false;
        }
        if (suspensionStage == 30) {
            if (!server(minecraft, player -> {
                fixture.pumpSuspension(player);
                var finalJob = fixture.suspensionState(player, 0);
                if (finalJob.busy() || finalJob.suspended() || finalJob.profilerSuspended()) return false;
                mark(checks, "final-output-conserved", finalJob.networkOutput() == 1
                        && finalJob.networkRaw() == suspensionFinalRawBefore - 1
                        && finalJob.furnaceInput() == 0 && finalJob.furnaceOutput() == 0);
                return true;
            })) return false;
            mark(checks, "final-inflight-completes", true);
            if (!suspensionCaptureReady(selectedSuspensionSnapshot(minecraft), UiObservationStore.latest(), true))
                return false;
            screenshot.accept("crafting-suspension-complete.png");
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.ServerOptionsScreen(minecraft.screen));
            suspensionStage = 31;
            return false;
        }
        if (suspensionStage == 31) {
            var profiling = suspensionButtonStarting(minecraft, "Profiling and TTC: ");
            if (profiling == null || !profiling.active) return false;
            DriverPlatform.click(minecraft, profiling.getX() + 4, profiling.getY() + 4);
            suspensionStage = 32;
            return false;
        }
        if (suspensionStage == 32) {
            var done = suspensionButton(minecraft, "Done");
            if (done == null) return false;
            DriverPlatform.click(minecraft, done.getX() + 4, done.getY() + 4);
            suspensionStage = 33;
            return false;
        }
        if (suspensionStage == 33) {
            return server(minecraft, player -> com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.enabled(
                    com.ctux.ae2craftingtime.core.OptionFeature.PROFILING));
        }
        return false;
    }

    static boolean suspensionPausedJobReady(StandardCraftFixture.SuspensionState state, String jobId) {
        return state.busy() && state.suspended() && state.profilerSuspended() && state.jobId().equals(jobId);
    }

    static void validateSuspensionInFlightReturn(StandardCraftFixture.SuspensionState state) {
        if (state.waiting() == 0 || state.furnaceInput() + state.furnaceOutput() == 0)
            throw new IllegalStateException("No in-flight furnace return at pause acknowledgement");
    }

    static void validateSuspensionDispatch(long undispatched, long previous) {
        if (undispatched != previous) throw new IllegalStateException("Paused CPU dispatched another pattern");
    }

    static void validateSuspensionPausedJob(StandardCraftFixture.SuspensionState state, String jobId, long undispatched) {
        if (!suspensionPausedJobReady(state, jobId) || state.undispatched() != undispatched)
            throw new IllegalStateException("Paused CPU changed before in-flight return");
    }

    static void validateSuspensionDiagnostics(boolean delayed, java.util.function.BooleanSupplier hasEstimate) {
        if (delayed || hasEstimate.getAsBoolean())
            throw new IllegalStateException("Paused job retained a delayed warning or total estimate");
    }

    static void validateSuspensionResumedJob(StandardCraftFixture.SuspensionState state, String jobId) {
        if (state.busy() && !state.jobId().equals(jobId)) throw new IllegalStateException("Resume replaced the large job");
    }

    static void validateSuspensionDelay(boolean delayed, long elapsed, java.util.function.LongSupplier threshold) {
        if (delayed && elapsed < threshold.getAsLong())
            throw new IllegalStateException("Resume inherited the paused delay timer");
    }

    static void validateSuspensionReplacement(String jobId, String previous) {
        if (jobId.equals(previous)) throw new IllegalStateException("Replacement job reused the prior UUID");
    }

    static void validateBadgeBeforeRelaunch(com.ctux.ae2craftingtime.core.ClientConfig config) {
        if (config.badgeBackground() || config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE) != 0x245A7D
                || config.badgeOpacity() != 96)
            throw new IllegalStateException("Badge Off/custom appearance was not saved for relaunch");
    }

    static void validateRestoredBadge(com.ctux.ae2craftingtime.core.ClientConfig config,
            String actualHash, String priorHash) {
        if (!config.badgeBackground() || config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE) != 0x245A7D
                || config.badgeOpacity() != 96 || actualHash.equals(priorHash))
            throw new IllegalStateException("Badge On/custom appearance did not restore after relaunch");
    }

    static void validateBadgePredecessorChecks(BadgeContinuation continuation, List<String> required) {
        if (!java.util.Set.copyOf(continuation.checks()).equals(java.util.Set.copyOf(required)))
            throw new IllegalStateException("Badge relaunch predecessor or saved config differs");
    }

    static void validateBadgePredecessorConfig(BadgeContinuation continuation, String actualHash) {
        if (!actualHash.equals(continuation.configSha256()))
            throw new IllegalStateException("Badge relaunch predecessor or saved config differs");
    }

    static void validateAmountPredecessorChecks(AmountContinuation continuation, List<String> required) {
        if (!java.util.Set.copyOf(continuation.checks()).equals(java.util.Set.copyOf(required)))
            throw new IllegalStateException("Status relaunch predecessor omitted required checks");
    }

    static void validateAmountPredecessorConfig(AmountContinuation continuation, String actualHash,
            com.ctux.ae2craftingtime.core.FeatureOptions features) {
        if (!actualHash.equals(continuation.configSha256())
                || features.enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS))
            throw new IllegalStateException("Saved compact-off config changed before relaunch check");
    }

    static void validateAmountResetInput(int amountOptionCase, int step, String label, boolean enabled) {
        switch (step) {
            case 0 -> {
                if (enabled != (amountOptionCase != 6))
                    throw new IllegalStateException("Wrong pre-reset compact value: case=" + amountOptionCase);
            }
            case 1 -> {
                if (enabled != (amountOptionCase == 6))
                    throw new IllegalStateException("Compact edit did not apply: case=" + amountOptionCase);
            }
            case 2, 4 -> {
                if (enabled != (step == 4))
                    throw new IllegalStateException("Wrong pre-reset option value: " + label);
            }
            case 3, 5 -> {
                if (enabled != (step == 3))
                    throw new IllegalStateException("Option edit did not apply: " + label);
            }
            case 7, 8, 9 -> {
                if (enabled != (step == 9))
                    throw new IllegalStateException("Reset did not restore model default: " + label);
            }
            case 6, 10 -> { }
            default -> throw new IllegalStateException("Unexpected reset step " + step);
        }
    }

    static void validateAmountResetSaved(com.ctux.ae2craftingtime.core.FeatureOptions features, int amountOptionCase) {
        if (features.enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS)
                != (amountOptionCase == 5))
            throw new IllegalStateException("Reset changed live compact option before Done: case=" + amountOptionCase);
    }

    static void validateAmountOptionFeatures(com.ctux.ae2craftingtime.core.FeatureOptions features,
            boolean compact, boolean time, int amountOptionCase) {
        if (features.enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS) != compact
                || features.enabled(com.ctux.ae2craftingtime.core.OptionFeature.STATUS_ROWS) != time)
            throw new IllegalStateException("Saved amount option combination " + amountOptionCase + " differs");
    }

    static void validateAmountOptionSummary(UiSnapshot.Row row, boolean compact, int amountOptionCase) {
        boolean hasSummary = row.description().stream().anyMatch(value -> value.key().equals(
                "text.ae2craftingtime.status.amounts"));
        if (hasSummary != compact)
            throw new IllegalStateException("Amount option combination " + amountOptionCase + " rendered " + row.description());
    }

    static void validateAmountOptionCategories(UiSnapshot.Row row, List<String> nativeKeys,
            boolean compact, boolean time, int amountOptionCase) {
        long[] raw = {row.storedAmount(), row.activeAmount(), row.pendingAmount()};
        for (int category = 0; category < raw.length; category++) {
            String key = nativeKeys.get(category);
            int visible = (int) row.description().stream().filter(value -> value.key().equals(key)).count();
            if (visible != (!compact && raw[category] > 0 ? 1 : 0))
                throw new IllegalStateException("Amount option combination " + amountOptionCase
                        + " native category " + key + " count " + visible);
        }
        if (!time && row.description().stream().anyMatch(value ->
                com.ctux.ae2craftingtime.core.CraftingRowState.isBadge(value.key())
                        && !value.key().equals("text.ae2craftingtime.status.amounts")))
            throw new IllegalStateException("TTC-off amount option rendered a status badge");
    }

    static void validateBadgeContent(List<String> rows, List<String> text,
            List<String> expectedRows, List<String> expectedText) {
        if (!rows.equals(expectedRows) || !text.equals(expectedText))
            throw new IllegalStateException("Badge switch changed native rows or mod text");
    }

    static void validateBadgeAppearance(com.ctux.ae2craftingtime.core.ClientConfig config, int step, String phase) {
        if (config.badgeBackground() != (step != 3))
            throw new IllegalStateException("Badge background state differs at " + phase + " step " + step);
        if (config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE) != 0x245A7D
                || config.badgeOpacity() != 96)
            throw new IllegalStateException("Custom badge appearance was lost");
    }

    static void validateAppearanceInput(String label, String expected, String actual) {
        if (expected == null || !actual.equals(expected))
            throw new IllegalStateException("Appearance reset value differs: " + label);
    }

    static void validateAppearanceInputCount(int count) {
        if (count != com.ctux.ae2craftingtime.core.ClientConfig.appearanceColors().size() + 1)
            throw new IllegalStateException("Appearance reset did not expose every input");
    }

    static void validateVariantContent(List<List<Long>> expectedAmounts, List<List<Long>> amounts,
            List<Boolean> expectedButtons, List<Boolean> buttons) {
        if (!expectedAmounts.equals(amounts) || !expectedButtons.equals(buttons))
            throw new IllegalStateException("Stored-variant transition changed quantities or native button state");
    }

    static void validateDelayedWarning(UiSnapshot.ObservedText warning) {
        if (warning.bold() || !Integer.valueOf(0xFF5555).equals(warning.color()))
            throw new IllegalStateException("DELAYED must be normal red on the active stone row");
    }

    static void validateStatusHeader(UiSnapshot snapshot, UiSnapshot.ObservedText header) {
        if (!header.bounds().inside(snapshot.gui()) || !LayoutValidator.validateBadges(snapshot).isEmpty())
            throw new IllegalStateException("status header " + header.bounds() + " GUI " + snapshot.gui()
                    + " badge layout: " + LayoutValidator.validateBadges(snapshot));
    }

    static void validateCompletedStatus(UiSnapshot snapshot, String leaf) {
        if (leaf.equals("craft-lifecycle") && snapshot.text().stream()
                .anyMatch(t -> t.key().equals("text.ae2craftingtime.ttc") && t.bounds() != null
                        && t.bounds().y() < snapshot.gui().y() + 19))
            throw new IllegalStateException("completed crafting status still shows total TTC");
    }

    static void validatePlanMissingOrder(UiSnapshot snapshot, boolean plan) {
        if (plan && !missingFirst(snapshot.rows()))
            throw new IllegalStateException("Crafting Plan missing rows are not first: " + snapshot.rows());
    }

    static void validatePlanTotal(UiSnapshot snapshot, UiSnapshot.ObservedText total, boolean background) {
        if (total.bounds() == null || !recurrentBadgeMatches(snapshot.badges(), total.bounds(), background)
                || !LayoutValidator.validateBadges(snapshot).isEmpty())
            throw new IllegalStateException("plan badge layout: " + LayoutValidator.validateBadges(snapshot));
    }

    static void validateSortedRows(List<String> rows, List<String> expected, String prefix, int sort) {
        if (!rows.subList(0, 2).equals(expected))
            throw new IllegalStateException(prefix + " sort " + sort + " row order is " + rows + ", expected " + expected);
    }

    static void validateAddonSummary(UiSnapshot snapshot, UiSnapshot.ObservedText summary,
            List<String> slot, String addon) {
        if (summary == null || !summary.arguments().equals(List.of(String.join("/", slot)))
                || summary.bold() || !LayoutValidator.validateBadges(snapshot).isEmpty())
            throw new IllegalStateException("Addon " + addon + " lost native SLOT amounts or badge bounds");
    }

    static void validateAddonLegend(List<UiSnapshot.ObservedText> tooltip, String addon) {
        if (tooltip.stream().noneMatch(value -> value.key().equals("text.ae2craftingtime.status.amounts_legend")))
            throw new IllegalStateException("Addon " + addon + " lost amount legend");
    }

    static void validateAddonTooltip(List<UiSnapshot.ObservedText> tooltip, String label, String full, String addon) {
        if (tooltip.stream().noneMatch(value -> value.key().equals(label) && value.arguments().contains(full)))
            throw new IllegalStateException("Addon " + addon + " lost native FULL tooltip " + label);
    }

    static void validateAmountFont(int mode, int width, int defaultWidth) {
        if (mode == 1 && width <= defaultWidth)
            throw new IllegalStateException("Uniform status font is not wider than the default font");
    }

    static void validateScaledAmount(UiSnapshot snapshot, UiSnapshot.ObservedText amount, UiSnapshot.ObservedText drawn) {
        if (amount == null || drawn == null || !amount.rendered().equals(drawn.rendered())
                || !LayoutValidator.validateBadges(snapshot).isEmpty())
            throw new IllegalStateException("Scaled amount badge escapes its native cell: " + amount + " rendered " + drawn);
    }

    static List<UiSnapshot.ObservedText> quantityAmountLines(UiSnapshot.Row row, boolean empty, int quantityCase) {
        var amountLines = row.description().stream()
                .filter(text -> text.key().equals("text.ae2craftingtime.status.amounts")).toList();
        if (amountLines.size() != (empty ? 0 : 1))
            throw new IllegalStateException("Synthetic native amount case " + quantityCase + " has " + amountLines);
        return amountLines;
    }

    static void validateQuantityText(UiSnapshot.ObservedText summary, String amount, int quantityCase) {
        if (!summary.arguments().equals(List.of(amount)) || summary.bold())
            throw new IllegalStateException("Synthetic native amount case " + quantityCase + " text " + summary);
    }

    static void validateQuantityLayout(UiSnapshot snapshot, UiSnapshot.ObservedText summary,
            Integer color, int quantityCase) {
        if (!java.util.Objects.equals(summary.color(), color) || !LayoutValidator.validateBadges(snapshot).isEmpty())
            throw new IllegalStateException("Synthetic native amount case " + quantityCase + " color/layout " + summary);
    }

    static void validateQuantityLegend(List<UiSnapshot.ObservedText> tooltip, boolean empty, int quantityCase) {
        if (tooltip.stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.status.amounts_legend")) == empty)
            throw new IllegalStateException("Synthetic native amount case " + quantityCase + " lost tooltip legend");
    }

    static void validateQuantityTooltipCategory(List<UiSnapshot.ObservedText> tooltip, String key,
            String full, int category, int quantityCase) {
        if (tooltip.stream().anyMatch(text -> text.key().equals(key)
                && full != null && text.arguments().contains(full)) != (full != null))
            throw new IllegalStateException("Synthetic native amount case " + quantityCase
                    + " lost full tooltip category " + category + "=" + full);
    }

    static void validateStoredVariantLabel(UiSnapshot.ObservedText label, boolean expected, Integer neutralColor) {
        if (expected && (label.bold() || !java.util.Objects.equals(label.color(), neutralColor)))
            throw new IllegalStateException("Stored-variant label is not normal neutral text");
    }

    static void validateStoredVariantText(UiSnapshot.Row row, UiSnapshot.ObservedText text) {
        if (!text.bounds().inside(row.cell()))
            throw new IllegalStateException("Stored-variant text exceeds its row at narrow layout");
    }

    static void validateStoredVariantRow(UiSnapshot snapshot, UiSnapshot.Row row) {
        if (!row.cell().inside(snapshot.gui()))
            throw new IllegalStateException("Variant row escapes native plan layout");
    }

    static void validateRecurrenceBounds(UiSnapshot snapshot) {
        if (snapshot.text().stream().filter(text -> text.key().equals("text.ae2craftingtime.plan.recurrent"))
                .anyMatch(text -> text.bounds() == null || snapshot.rows().stream().noneMatch(value -> text.bounds().inside(value.cell()))))
            throw new IllegalStateException("Recurrent text escapes its native table cell");
    }

    static void validateRecurrenceLabel(UiSnapshot snapshot, UiSnapshot.Row row,
            UiSnapshot.ObservedText label, boolean background, boolean connectedDedicated,
            boolean reported, long requested) {
        if (label != null && (label.bold() || !java.util.Objects.equals(label.color(), 0xFF5555)
                || label.arguments().size() != 1 || !label.rendered().endsWith(label.arguments().get(0))))
            throw new IllegalStateException("Recurrence label lost its red warning style or amount");
        if (label != null && snapshot.text().stream()
                .filter(text -> text.key().equals("text.ae2craftingtime.plan.recurrent"))
                .noneMatch(text -> !text.bold() && java.util.Objects.equals(text.color(), 0xFF5555)
                        && text.bounds() != null && recurrentBadgeMatches(snapshot.badges(), text.bounds(),
                                background)))
            throw new IllegalStateException("Recurrent label or badge differs from client options");
        if (!connectedDedicated && reported && label != null
                && !label.arguments().equals(List.of(Long.toString(requested))))
            throw new IllegalStateException("Recurrence label lost requested quantity " + requested);
        if (!row.cell().inside(snapshot.gui())) throw new IllegalStateException("Recurrence row escapes plan layout");
    }

    static boolean recurrentBadgeMatches(List<Rect> badges, Rect text, boolean enabled) {
        return badges.stream().anyMatch(badge -> text.inside(badge)) == enabled;
    }

    static boolean galleryPlanReady(List<UiSnapshot.Row> rows, int knownRows) {
        var ids = List.of("minecraft:stone", "minecraft:smooth_stone");
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            var row = rows.stream().filter(value -> value.outputId().equals(id) && value.craftAmount() > 0).findFirst();
            if (row.isEmpty()) return false;
            boolean known = index < knownRows;
            if (row.get().description().stream().noneMatch(text -> known ? CraftPlanScenario.isResolvedTtc(text)
                    : text.key().equals("text.ae2craftingtime.ttc")
                            && text.arguments().contains("text.ae2craftingtime.collecting_data"))) return false;
        }
        return true;
    }

    static boolean galleryAccuracyReady(com.ctux.ae2craftingtime.core.TtcAccuracyStats accuracy, boolean partial) {
        return accuracy.sampleCount() == 1 && accuracy.fullyCoveredSampleCount() == (partial ? 0 : 1)
                && accuracy.lastKnownRows() == (partial ? 1 : 2) && accuracy.lastTotalRows() == 2
                && accuracy.lastPredictedSeconds() > 0 && accuracy.lastActualWallSeconds() > 0;
    }

    static boolean missingFirst(List<UiSnapshot.Row> rows) {
        var foundMissing = false;
        var foundNonMissing = false;
        for (var row : rows) {
            if (row.missingAmount() > 0) {
                foundMissing = true;
                if (foundNonMissing) return false;
            } else {
                foundNonMissing = true;
            }
        }
        return foundMissing;
    }

    private boolean statusRelaunchTick(Minecraft minecraft, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (!amountResumeChecksRestored) {
            validateAmountPredecessorChecks(amountContinuation, CHECKS.get(leaf));
            for (var check : amountContinuation.checks()) checks.put(check, true);
            validateAmountPredecessorConfig(amountContinuation, configHash(minecraft),
                    com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features());
            amountResumeChecksRestored = true;
        }
        if (amountResumeSaving && !(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)) {
            if (!com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features()
                    .enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS))
                throw new IllegalStateException("Compact amounts did not restore after relaunch");
            screenshot.accept("status-relaunch-restored.png");
            return true;
        }
        if (!amountResumeOpened) {
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(minecraft.screen));
            amountResumeOpened = true;
            amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)
                || TestDriverRuntime.renderedFrames < amountOptionRenderedAfter) return false;
        var label = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.compactStatusAmounts");
        var toggle = optionButton(minecraft, label + ": ");
        if (toggle == null) {
            clickOptionButton(minecraft, ">");
            amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        boolean enabled = toggle.getMessage().getString().endsWith(
                net.minecraft.client.resources.language.I18n.get("options.on"));
        if (!amountResumeOffCaptured) {
            if (enabled) throw new IllegalStateException("Relaunched Options screen did not show compact amounts off");
            screenshot.accept("status-relaunch-off.png");
            amountResumeOffCaptured = true;
            DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
            amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (!enabled) return false;
        if (!amountResumeOnCaptured) {
            screenshot.accept("status-relaunch-on.png");
            amountResumeOnCaptured = true;
        }
        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.done"));
        amountResumeSaving = true;
        return false;
    }

    private boolean badgeTick(Minecraft minecraft, Map<String, Boolean> checks, Consumer<String> screenshot) {
        boolean status = phase == Stage.ACTIVE;
        if (status && badgeStep == 4 && badgeScaleStep > 0 && badgeScaleStep < 4)
            return badgeScaleTick(minecraft, screenshot);
        if (!status && badgeStep == 0 && !badgeEditOpen) {
            if (!(minecraft.screen instanceof CraftConfirmScreen)) return false;
            var plan = UiObservationStore.latest();
            if (plan == null || !plan.screen().equals(minecraft.screen.getClass().getName())
                    || !planEstimatesReady(plan.rows())) return false;
        }
        if ((!status && badgeStep == 0) || badgeStep == 2 || badgeStep == 4) {
            int action = badgeStep == 0 ? 0 : badgeStep == 2 ? 1 : 2;
            if (badgeEditTick(minecraft, action)) {
                badgeStep++;
                badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            }
            return false;
        }
        if (TestDriverRuntime.renderedFrames < badgeRenderedAfter) return false;
        if (status ? !(minecraft.screen instanceof CraftingStatusScreen)
                : !(minecraft.screen instanceof CraftConfirmScreen)) return false;
        var snapshot = UiObservationStore.latest();
        if (snapshot == null || !snapshot.screen().equals(minecraft.screen.getClass().getName())
                || snapshot.rows().stream().filter(row -> row.craftAmount() > 0).count() < 2
                || snapshot.badges().isEmpty() == (badgeStep != 3) || !LayoutValidator.validateBadges(snapshot).isEmpty()) return false;
        if (!status && (!planEstimatesReady(snapshot.rows()) || !missingFirst(snapshot.rows()))) return false;
        if (!frames.observe(List.of(phase, badgeStep, CaptureEvidence.readiness(snapshot)))) return false;
        var rows = snapshot.rows().stream().map(row -> row.outputId() + ":" + row.craftAmount()).toList();
        var text = snapshot.text().stream().filter(value ->
                (com.ctux.ae2craftingtime.core.CraftingRowState.isBadge(value.key())
                        || value.key().equals("native-status-text")
                        && (badgeStep == 3 ? badgeTextBefore != null
                                && badgeTextBefore.contains(value.key() + ":" + value.bounds())
                                : snapshot.badges().stream().anyMatch(badge -> value.bounds().inside(badge))))
                        && snapshot.rows().stream().anyMatch(row -> value.bounds().inside(row.cell())))
                .map(value -> value.key() + ":" + value.bounds()).toList();
        if (text.isEmpty()) return false;
        if (badgeStep == 1 || status && badgeStep == 0) {
            badgeRowsBefore = rows;
            badgeTextBefore = text;
        } else {
            validateBadgeContent(rows, text, badgeRowsBefore, badgeTextBefore);
        }
        validateBadgeAppearance(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current(), badgeStep, phase.toString());
        String name = (status ? "status" : "plan") + "-badge-"
                + (badgeStep == 3 ? "off" : badgeStep == 5 ? "restored" : "on");
        screenshot.accept(name + ".png");
        mark(checks, (status ? "status" : "plan") + "-"
                + (badgeStep == 3 ? "off" : badgeStep == 5 ? "restored" : "on"), true);
        if (badgeStep == 5) {
            badgeStep = 0;
            badgeRowsBefore = null;
            badgeTextBefore = null;
            if (status) {
                if (!Boolean.getBoolean("ae2craftingtime.test.badgeRelaunch")) return true;
                phase = Stage.BADGE_PERSIST;
                return false;
            }
            phase = Stage.SUBMIT;
        } else {
            if (status && badgeStep == 3 && Boolean.getBoolean("ae2craftingtime.test.badgeRelaunch")) {
                badgeOriginalScale = minecraft.options.guiScale().get();
                badgeScaleStep = 1;
            }
            badgeStep = badgeStep == 3 ? 4 : 2;
        }
        frames.reset();
        return false;
    }

    private boolean badgeScaleTick(Minecraft minecraft, Consumer<String> screenshot) {
        if (badgeScaleStep == 1) {
            minecraft.options.guiScale().set(1);
            DriverPlatform.resizeDisplay(minecraft);
            badgeScaleStep = 2;
            frames.reset();
            return false;
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingStatusScreen) || snapshot == null
                || !snapshot.screen().equals(minecraft.screen.getClass().getName())
                || snapshot.rows().stream().filter(row -> row.craftAmount() > 0).count() < 2
                || !snapshot.badges().isEmpty() || !LayoutValidator.validateBadges(snapshot).isEmpty()
                || !frames.observe(List.of(badgeScaleStep, CaptureEvidence.readiness(snapshot)))) return false;
        if (badgeScaleStep == 2) {
            if (snapshot.guiScale() != 1) return false;
            screenshot.accept("status-badge-off-small.png");
            minecraft.options.guiScale().set(badgeOriginalScale);
            DriverPlatform.resizeDisplay(minecraft);
            badgeScaleStep = 3;
            frames.reset();
            return false;
        }
        if (snapshot.guiScale() <= 1) return false;
        screenshot.accept("status-badge-off-auto.png");
        badgeScaleStep = 4;
        frames.reset();
        return false;
    }

    private boolean badgeEditTick(Minecraft minecraft, int action) {
        if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)) {
            if (badgeEditOpen) {
                var config = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current();
                if (config.badgeBackground() != (action != 1)
                        || config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE) != 0x245A7D
                        || config.badgeOpacity() != 96)
                    throw new IllegalStateException("Badge option save did not apply");
                badgeEditOpen = false;
                badgeAppearanceOpen = false;
                badgeSaving = false;
                badgeColorEdited = false;
                badgeOpacityEdited = false;
                frames.reset();
                return true;
            }
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(minecraft.screen));
            badgeEditOpen = true;
            badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (TestDriverRuntime.renderedFrames < badgeRenderedAfter) return false;
        if (badgeSaving) return false;
        if (!badgeAppearanceOpen) {
            clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                    "config.ae2craftingtime.group.appearance"));
            badgeAppearanceOpen = true;
            badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (action == 0 && (!badgeColorEdited || !badgeOpacityEdited)) {
            var inputs = minecraft.screen.children().stream()
                    .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).toList();
            for (var input : inputs) {
                if (!badgeColorEdited && input.getMessage().getString().equals(
                        net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.color.badge"))) {
                    input.setValue("#245A7D");
                    badgeColorEdited = true;
                } else if (!badgeOpacityEdited && input.getMessage().getString().equals(
                        net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.badgeOpacity"))) {
                    input.setValue("96");
                    badgeOpacityEdited = true;
                }
            }
            if (!badgeColorEdited || !badgeOpacityEdited) {
                clickOptionButton(minecraft, ">");
                badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                return false;
            }
        }
        String label = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.badgeBackground");
        var toggle = optionButton(minecraft, label + ": ");
        if (toggle == null) {
            clickOptionButton(minecraft, "<");
            badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        boolean enabled = toggle.getMessage().getString().endsWith(
                net.minecraft.client.resources.language.I18n.get("options.on"));
        if (enabled != (action != 1)) {
            DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
            badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.done"));
        badgeSaving = true;
        badgeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
        return false;
    }

    private boolean badgePersistTick(Minecraft minecraft, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (!badgePersistSaving) {
            if (badgeEditTick(minecraft, 1)) badgePersistSaving = true;
            return false;
        }
        if (!(minecraft.screen instanceof CraftingStatusScreen)) return false;
        var config = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current();
        validateBadgeBeforeRelaunch(config);
        screenshot.accept("badge-saved-off.png");
        if (checks.values().stream().anyMatch(value -> !value))
            throw new IllegalStateException("Cannot relaunch with incomplete badge checks: " + checks);
        if (!badgeContinuationWritten) {
            writeBadgeContinuation(new BadgeContinuation(1, world,
                    System.getProperty("ae2craftingtime.test.campaign", "local"), configHash(minecraft),
                    checks.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList(),
                    List.copyOf(resultScreenshots)));
            badgeContinuationWritten = true;
            minecraft.stop();
        }
        return false;
    }

    private boolean badgeRelaunchTick(Minecraft minecraft, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (badgeResumeStep == 0) {
            validateBadgePredecessorChecks(badgeContinuation, CHECKS.get(leaf));
            validateBadgePredecessorConfig(badgeContinuation, configHash(minecraft));
            for (var check : badgeContinuation.checks()) checks.put(check, true);
            validateSavedBadgeOff(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current());
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(null));
            badgeResumeStep = 1;
            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (TestDriverRuntime.renderedFrames < badgeResumeRenderedAfter) return false;
        if (badgeResumeStep >= 20) {
            if (badgeResumeStep == 29) {
                if (minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen) return false;
                var config = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current();
                if (config.planSort() != 2 || config.statusSort() != 2)
                    throw new IllegalStateException("Controls reset did not save both default sort modes");
                return true;
            }
            if (badgeResumeStep == 20) {
                clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.group.controls"));
            } else if (badgeResumeStep == 25 || badgeResumeStep == 28) {
                clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        badgeResumeStep == 25 ? "config.ae2craftingtime.reset_group" : "gui.done"));
            } else {
                boolean plan = badgeResumeStep == 21 || badgeResumeStep == 22 || badgeResumeStep == 26;
                String label = net.minecraft.client.resources.language.I18n.get(
                        plan ? "config.ae2craftingtime.planSort" : "config.ae2craftingtime.statusSort") + ": ";
                var sort = seekOptionButton(minecraft, label);
                if (sort == null) return false;
                int expected = badgeResumeStep == 22 || badgeResumeStep == 24 ? 0 : 2;
                if (!sort.getMessage().getString().equals(label
                        + com.ctux.ae2craftingtime.mc1201.TtcText.sortMode(expected).getString()))
                    throw new IllegalStateException("Controls sort differs at step " + badgeResumeStep);
                if (badgeResumeStep == 21 || badgeResumeStep == 23)
                    DriverPlatform.click(minecraft, sort.getX() + 4, sort.getY() + 4);
                if (badgeResumeStep == 27) screenshot.accept("controls-reset-group.png");
            }
            badgeResumeStep++;
            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (badgeResumeStep == 7 || badgeResumeStep == 11) {
            if (badgeResetCheckPhase == 0) {
                var badge = seekOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.badgeBackground") + ": ");
                if (badge == null) return false;
                if (badge.getMessage().getString().endsWith(net.minecraft.client.resources.language.I18n.get("options.on")))
                    throw new IllegalStateException("Reset did not restore badge Off");
                validateSavedBadgeOff(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current());
                badgeResetCheckPhase = 1;
            }
            if (badgeResetCheckPhase == 1) {
                var shadow = seekOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.textShadow") + ": ");
                if (shadow == null) return false;
                if (shadow.getMessage().getString().endsWith(net.minecraft.client.resources.language.I18n.get("options.on")))
                    throw new IllegalStateException("Reset did not restore text shadow Off");
                screenshot.accept(badgeResumeStep == 7 ? "badge-reset-group.png" : "badge-reset-all.png");
                badgeResetCheckPhase = 2;
            }
            if (badgeResetCheckPhase == 2) {
                if (!badgeAssertAppearanceDefaults(minecraft)) return false;
                clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.group.displays"));
                badgeResetCheckPhase = 3;
                badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                return false;
            }
            var colors = seekOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                    "config.ae2craftingtime.ttcColors") + ": ");
            if (colors == null) return false;
            if (colors.getMessage().getString().endsWith(net.minecraft.client.resources.language.I18n.get("options.on"))
                    != (badgeResumeStep == 7))
                throw new IllegalStateException("Reset changed another group or Reset All failed");
            clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.cancel"));
            badgeResetCheckPhase = 0;
            badgeResumeStep++;
            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (badgeResumeStep == 4 || badgeResumeStep == 8 || badgeResumeStep == 12) {
            if (minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen) return false;
            validateSavedBadgeOff(com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current());
            if (!configHash(minecraft).equals(badgeContinuation.configSha256()))
                throw new IllegalStateException("Cancel changed saved badge options");
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(null));
            badgeResumeStep++;
        } else if (badgeResumeStep == 16) {
            if (minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen) return false;
            var config = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current();
            validateRestoredBadge(config, configHash(minecraft), badgeContinuation.configSha256());
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(null));
            badgeResumeStep = 17;
        } else if (badgeResumeStep == 19) {
            if (minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen) return false;
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(null));
            badgeResumeStep = 20;
        } else {
            if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)) return false;
            if (badgeResumeStep == 1 || badgeResumeStep == 5 || badgeResumeStep == 9
                    || badgeResumeStep == 13 || badgeResumeStep == 17) {
                if (badgeResumeStep == 5) {
                    var colors = seekOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                            "config.ae2craftingtime.ttcColors") + ": ");
                    if (colors == null) return false;
                    boolean colorsOn = colors.getMessage().getString().endsWith(
                            net.minecraft.client.resources.language.I18n.get("options.on"));
                    if (!badgeOutsideEdited) {
                        if (colorsOn) throw new IllegalStateException("TTC colors did not start Off");
                        DriverPlatform.click(minecraft, colors.getX() + 4, colors.getY() + 4);
                        badgeOutsideEdited = true;
                        badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                        return false;
                    }
                    if (!colorsOn) throw new IllegalStateException("Other-group edit did not apply");
                }
                clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.group.appearance"));
            } else {
                var toggle = optionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.badgeBackground") + ": ");
                if (toggle == null) return false;
                boolean enabled = toggle.getMessage().getString().endsWith(
                        net.minecraft.client.resources.language.I18n.get("options.on"));
                switch (badgeResumeStep) {
                    case 2 -> {
                        if (enabled) throw new IllegalStateException("Native badge toggle was not Off after relaunch");
                        screenshot.accept("badge-relaunch-off.png");
                        DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
                    }
                    case 3 -> {
                        if (!enabled) return false;
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.cancel"));
                    }
                    case 6 -> {
                        if (enabled != badgeResetBadgeEdited)
                            throw new IllegalStateException("Unexpected badge value before section reset");
                        var shadow = optionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                                "config.ae2craftingtime.textShadow") + ": ");
                        if (shadow == null) throw new IllegalStateException("Text shadow option is missing");
                        if (!badgeShadowEdited) {
                            if (shadow.getMessage().getString().endsWith(
                                    net.minecraft.client.resources.language.I18n.get("options.on")))
                                throw new IllegalStateException("Text shadow did not start Off");
                            DriverPlatform.click(minecraft, shadow.getX() + 4, shadow.getY() + 4);
                            badgeShadowEdited = true;
                            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                            return false;
                        }
                        if (!shadow.getMessage().getString().endsWith(
                                net.minecraft.client.resources.language.I18n.get("options.on")))
                            throw new IllegalStateException("Text shadow edit did not apply before reset");
                        if (!badgeResetBadgeEdited) {
                            DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
                            badgeResetBadgeEdited = true;
                            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
                            return false;
                        }
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                                "config.ae2craftingtime.reset_group"));
                    }
                    case 10 -> {
                        if (enabled) throw new IllegalStateException("Cancel did not discard Appearance reset");
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get(
                                "config.ae2craftingtime.reset_all"));
                    }
                    case 14 -> {
                        if (enabled) throw new IllegalStateException("Cancel did not discard Reset all");
                        DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
                    }
                    case 15 -> {
                        if (!enabled) return false;
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.done"));
                    }
                    case 18 -> {
                        if (!enabled) throw new IllegalStateException("Saved badge option was not On after Done");
                        screenshot.accept("badge-relaunch-restored.png");
                        clickOptionButton(minecraft, net.minecraft.client.resources.language.I18n.get("gui.cancel"));
                    }
                    default -> throw new IllegalStateException("Unexpected badge relaunch step " + badgeResumeStep);
                }
            }
            badgeResumeStep++;
        }
        badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
        return false;
    }

    static void validateSavedBadgeOff(com.ctux.ae2craftingtime.core.ClientConfig config) {
        if (config.badgeBackground() || config.color(com.ctux.ae2craftingtime.core.ClientConfig.Color.BADGE) != 0x245A7D
                || config.badgeOpacity() != 96)
            throw new IllegalStateException("Saved Off/custom badge settings changed after relaunch");
    }

    private boolean badgeAssertAppearanceDefaults(Minecraft minecraft) {
        for (var child : minecraft.screen.children()) {
            if (!(child instanceof net.minecraft.client.gui.components.EditBox input)) continue;
            String label = input.getMessage().getString();
            String expected = null;
            for (var color : com.ctux.ae2craftingtime.core.ClientConfig.appearanceColors()) {
                if (label.equals(net.minecraft.client.resources.language.I18n.get(
                        "config.ae2craftingtime.color." + color.name().toLowerCase(java.util.Locale.ROOT))))
                    expected = String.format(java.util.Locale.ROOT, "#%06X", color.defaultRgb());
            }
            if (label.equals(net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.badgeOpacity")))
                expected = "176";
            validateAppearanceInput(label, expected, input.getValue());
            badgeResetInputs.add(label);
        }
        if (optionButton(minecraft, ">") != null) {
            clickOptionButton(minecraft, ">");
            badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        validateAppearanceInputCount(badgeResetInputs.size());
        badgeResetInputs.clear();
        return true;
    }

    private net.minecraft.client.gui.components.Button seekOptionButton(Minecraft minecraft, String label) {
        var button = optionButton(minecraft, label);
        if (button != null) {
            optionSeekLabel = null;
            return button;
        }
        if (!label.equals(optionSeekLabel)) {
            optionSeekLabel = label;
            optionSeekForward = false;
        }
        if (!optionSeekForward && optionButton(minecraft, "<") == null) optionSeekForward = true;
        String direction = optionSeekForward ? ">" : "<";
        if (optionButton(minecraft, direction) == null)
            throw new IllegalStateException("Option is missing from all pages: " + label);
        clickOptionButton(minecraft, direction);
        amountOptionRenderedAfter = TestDriverRuntime.renderedFrames + 3;
        badgeResumeRenderedAfter = TestDriverRuntime.renderedFrames + 3;
        return null;
    }

    private static net.minecraft.client.gui.components.Button optionButton(Minecraft minecraft, String label) {
        return minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(label)).findFirst().orElse(null);
    }

    private static void clickOptionButton(Minecraft minecraft, String label) {
        var button = minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(value -> value.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Option button missing: " + label));
        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
    }

    private static String configHash(Minecraft minecraft) {
        return configHash(minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml"));
    }

    static String configHash(java.nio.file.Path path) {
        try {
            return CaptureEvidence.sha256(java.nio.file.Files.readAllBytes(path));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot hash saved client options", error);
        }
    }

    private void writeAddonKeyEvidence() {
        var result = new com.google.gson.JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("appbotLoaded", DriverPlatform.isModLoaded("appbot"));
        result.addProperty("appmekLoaded", DriverPlatform.isModLoaded("appmek"));
        var captured = new com.google.gson.JsonArray();
        for (var addon : addonQuantityCases) captured.add(addon.name());
        result.add("captured", captured);
        writeAddonKeyEvidence(output.resolve("status-addon-keys.json"), result);
    }

    static void writeAddonKeyEvidence(java.nio.file.Path path, com.google.gson.JsonObject result) {
        try {
            java.nio.file.Files.writeString(path, result.toString());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot retain addon status key evidence", error);
        }
    }

    void writeAmountContinuation(AmountContinuation value) {
        var path = output.resolve("status-amounts-continuation.json");
        try {
            var temp = path.resolveSibling(path.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temp, new com.google.gson.Gson().toJson(value));
            java.nio.file.Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot save status amount relaunch continuation", error);
        }
    }

    private static AmountContinuation readAmountContinuation(java.nio.file.Path path, String world) {
        try {
            var value = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(path), AmountContinuation.class);
            if (value == null || value.schema() != 1 || !world.equals(value.world())
                    || !System.getProperty("ae2craftingtime.test.campaign", "local").equals(value.campaign())
                    || value.checks() == null || value.screenshots() == null || value.configSha256() == null)
                throw new IllegalStateException("Status amount relaunch continuation identity differs");
            return value;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read status amount relaunch continuation", error);
        }
    }

    record AmountContinuation(int schema, String world, String campaign, String configSha256,
            List<String> checks, List<String> screenshots) {}

    void writeBadgeContinuation(BadgeContinuation value) {
        var path = output.resolve("badge-background-continuation.json");
        try {
            var temp = path.resolveSibling(path.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temp, new com.google.gson.Gson().toJson(value));
            java.nio.file.Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot save badge-background relaunch continuation", error);
        }
    }

    private static BadgeContinuation readBadgeContinuation(java.nio.file.Path path, String world) {
        try {
            var value = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(path), BadgeContinuation.class);
            if (value == null || value.schema() != 1 || !world.equals(value.world())
                    || !System.getProperty("ae2craftingtime.test.campaign", "local").equals(value.campaign())
                    || value.checks() == null || value.screenshots() == null || value.configSha256() == null)
                throw new IllegalStateException("Badge relaunch continuation identity differs");
            return value;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read badge relaunch continuation", error);
        }
    }

    record BadgeContinuation(int schema, String world, String campaign, String configSha256,
            List<String> checks, List<String> screenshots) {}

    static UiSnapshot.ObservedText rowText(UiSnapshot snapshot, String output, String key) {
        var row = snapshot.rows().stream().filter(r -> r.outputId().equals(output)).findFirst();
        if (row.isEmpty()) return null;
        var translated = snapshot.text().stream().filter(t -> t.key().equals(key) && t.bounds() != null
                && t.bounds().inside(row.get().cell())).findFirst();
        if (translated.isPresent()) return translated.get();
        if (!key.equals("text.ae2craftingtime.waiting") && !key.equals("text.ae2craftingtime.ttc")
                && !key.equals("text.ae2craftingtime.ttc_delayed")) return null;
        return snapshot.text().stream().filter(t -> t.key().equals("native-status-text") && t.bounds() != null
                && t.bounds().inside(row.get().cell()) && snapshot.badges().stream()
                        .anyMatch(badge -> t.bounds().inside(badge))
                && (key.equals("text.ae2craftingtime.waiting")
                        ? t.rendered().equals(com.ctux.ae2craftingtime.mc1201.TtcText.waiting().getString())
                        : key.equals("text.ae2craftingtime.ttc_delayed")
                                ? t.rendered().equals(com.ctux.ae2craftingtime.mc1201.TtcText.ttcDelayed().getString())
                                : t.rendered().startsWith(com.ctux.ae2craftingtime.core.TtcSymbols.Symbol.TIME.glyph()
                                        + " ~")))
                .findFirst().orElse(null);
    }

    private boolean hasPlate(String output, int providerOffset) {
        return ProviderHighlightClient.plates().stream().anyMatch(plate -> plate.outputId().equals(output)
                && plate.positions().contains(fixture.terminal.east(providerOffset)));
    }

    private java.util.Set<String> plateOutputsAt(int providerOffset) {
        return ProviderHighlightClient.plates().stream()
                .filter(plate -> plate.positions().contains(fixture.terminal.east(providerOffset)))
                .map(ProviderHighlightClient.Plate::outputId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private java.util.Set<String> renderOutputsAt(int providerOffset) {
        return ProviderHighlightClient.renderPlates().stream()
                .filter(plate -> plate.position().equals(fixture.terminal.east(providerOffset)))
                .map(ProviderHighlightClient.RenderPlate::outputId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean hasEdge(String output, int providerOffset) {
        return ProviderHighlightClient.liveEdges().stream().anyMatch(edge -> edge.outputId().equals(output)
                && edge.positions().contains(fixture.terminal.east(providerOffset)));
    }

    private boolean hasBeam(int providerOffset) {
        return ProviderHighlightClient.renderBeams().stream()
                .anyMatch(beam -> beam.position().equals(fixture.terminal.east(providerOffset)));
    }

    static void validateLayout(UiSnapshot snapshot) {
        if (!statusBadgesValid(snapshot,
                com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground())) {
            throw new IllegalStateException("Invalid standard status badge layout");
        }
        var header = snapshot.text().stream().filter(t -> t.key().equals("native-title")
                && t.bounds() != null && t.bounds().y() < snapshot.gui().y() + 19).findFirst();
        if (header.isPresent() && !header.get().bounds().inside(snapshot.gui())) {
            throw new IllegalStateException("Standard status header escapes GUI");
        }
    }

    static boolean statusBadgesValid(UiSnapshot snapshot, boolean enabled) {
        return LayoutValidator.validateBadges(snapshot).isEmpty() && snapshot.badges().isEmpty() != enabled;
    }

    private static boolean delayedTooltip(UiSnapshot snapshot) {
        var key = new com.ctux.ae2craftingtime.core.ProfileKey("minecraft:stone");
        var stall = com.ctux.ae2craftingtime.mc1201.ClientStats.CACHE.stall(key);
        if (stall.isEmpty()) return false;
        var diagnostic = stall.get();
        // Compare rendered numbers to the synchronized diagnostic, not a seeded warning.
        var seconds = (long) Math.ceil(diagnostic.idleTicks() / 20.0);
        var expected = ", " + com.ctux.ae2craftingtime.mc1201.TtcComponents.text("text.ae2craftingtime.stall.delayed").getString() + ": "
                + com.ctux.ae2craftingtime.mc1201.TtcComponents.text("text.ae2craftingtime.value.whole_seconds", seconds).getString() + ", "
                + net.minecraft.client.resources.language.I18n.get("text.ae2craftingtime.stall.typical") + ": "
                + com.ctux.ae2craftingtime.mc1201.TtcComponents.time(
                        com.ctux.ae2craftingtime.core.TimeEstimate.formatTicks(diagnostic.typicalDurationTicks())).getString();
        return snapshot.tooltip().stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.stats.ttc")
                && text.rendered().startsWith(com.ctux.ae2craftingtime.mc1201.TtcComponents.text(
                        "text.ae2craftingtime.stats.ttc").getString() + ": ")
                && text.rendered().endsWith(expected))
                && snapshot.tooltip().stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.stall.improvements"))
                && WarningTooltipChecks.hasControls(snapshot.tooltip());
    }

    static boolean planEstimatesReady(List<UiSnapshot.Row> rows) {
        return List.of("minecraft:stone", "minecraft:smooth_stone").stream().allMatch(id -> rows.stream()
                .anyMatch(row -> row.outputId().equals(id)
                        && row.description().stream().anyMatch(CraftPlanScenario::isResolvedTtc)));
    }

    static List<UiSnapshot.ObservedText> cpuCardTotals(UiSnapshot snapshot) {
        return snapshot.text().stream().filter(t -> t.key().equals("text.ae2craftingtime.ttc")
                && t.bounds() != null && t.bounds().y() >= snapshot.gui().y() + 19).toList();
    }

    static void mark(Map<String, Boolean> checks, String key, boolean value) {
        if (checks.containsKey(key)) checks.put(key, value);
    }

    void releaseKeys() { stats.releaseKeys(); }

    static boolean focus(long window) {
        var user = User32.INSTANCE;
        var nativeWindow = new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(
                org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window)));
        return focusNativeWindow(nativeWindow, user::GetForegroundWindow,
                () -> new DWORD(com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentThreadId()),
                foreground -> user.GetWindowThreadProcessId(foreground, null),
                (current, foreground) -> user.AttachThreadInput(current, foreground, true),
                () -> org.lwjgl.glfw.GLFW.glfwFocusWindow(window),
                (current, foreground) -> user.AttachThreadInput(current, foreground, false));
    }

    static boolean focusNativeWindow(com.sun.jna.platform.win32.WinDef.HWND nativeWindow,
            java.util.function.Supplier<com.sun.jna.platform.win32.WinDef.HWND> foregroundWindow,
            java.util.function.Supplier<DWORD> threadId,
            java.util.function.ToIntFunction<com.sun.jna.platform.win32.WinDef.HWND> windowThread,
            java.util.function.BiPredicate<DWORD, DWORD> attach, Runnable requestFocus,
            java.util.function.BiConsumer<DWORD, DWORD> detach) {
        var foreground = foregroundWindow.get();
        if (nativeWindow.equals(foreground)) return true;
        // A scheduled client can be visible while another desktop window still owns input.
        var currentThread = threadId.get();
        var foregroundThread = new DWORD(windowThread.applyAsInt(foreground));
        boolean attached = !currentThread.equals(foregroundThread)
                && attach.test(currentThread, foregroundThread);
        try {
            requestFocus.run();
        } finally {
            if (attached) detach.accept(currentThread, foregroundThread);
        }
        return false;
    }

    private String statsOutput() {
        // Reset the waiting status row so the running furnace keeps its real in-flight sample.
        return phase.ordinal() < Stage.OPEN_STATUS.ordinal() ? "minecraft:stone" : "minecraft:smooth_stone";
    }

    private boolean server(Minecraft minecraft, Function<ServerPlayer, Boolean> action) {
        if (operation == null) {
            var server = minecraft.getSingleplayerServer();
            var id = minecraft.player.getUUID();
            operation = server.submit(() -> action.apply(server.getPlayerList().getPlayer(id)));
        }
        if (!operation.isDone()) return false;
        boolean done = operation.join();
        operation = null;
        return done;
    }
}
