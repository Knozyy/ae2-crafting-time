package com.ctux.ae2craftingtime.testdriver;

import appeng.client.gui.me.crafting.CraftingStatusScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import com.ctux.ae2craftingtime.mc1201.TtcSortButton;
import com.ctux.ae2craftingtime.testdriver.mixin.MEStorageScreenAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Executable A1-A5 regression flow for per-card CPU totals. */
final class CpuListTtcScenario {
    static final long OPEN_RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
    static final java.util.List<String> CHECKS = java.util.List.of(
            "initial-distinct", "unknown-hidden", "idle-hidden", "badge-select", "selected-title", "tooltip",
            "initial-mode", "cpu-sort-cycle", "raw-order", "stable-groups", "offscreen-promoted",
            "item-sort-cycle", "channel-fallback", "server-selection", "wheel-before-draw", "no-first-draw",
            "native-cancel", "stale-hit", "mode-switch-late", "ae2-expiry", "default-reopen", "background-coverage",
            "scroll-down", "scroll-up", "partial", "stalled", "reordered", "finished", "cancelled",
            "replacement", "removed", "drop-expiry", "delayed-expiry", "close-reopen", "second-grid",
            "small-scale", "large-scale", "same-jvm-clear", "process-relaunch", "reconnect", "layout");

    enum Stage { INITIAL, MODE_AE2, MODE_SHORTEST, MODE_LONGEST, CHANNEL_FALLBACK, CHANNEL_RESTORED,
        SELECTED, TOOLTIP, WHEEL, SCROLL_DOWN, SCROLL_UP, AE2_HOLD, AE2_LATE, AE2_EXPIRED, MODE_RESTORED,
        PARTIAL, RESTORE, STALLED, RENAME, FINISH, CANCEL, CANCELLED, REPLACE_EMPTY, REPLACE_STARTED, REPLACE, RESTART, RESTART_CLEARED, RESTART_FRESH,
        REMOVE_SCROLL, REMOVE, DROP, DROP_EXPIRED, HOLD, HOLD_EXPIRED, HOLD_RELEASED,
        REOPEN, REOPENED, SECOND_PREPARE, SECOND_OPEN, SECOND_SCREEN, LARGE_PREPARE, LARGE_OPEN, LARGE_SCREEN,
        LARGE_HOLD, LARGE_SWITCHED,
        RETURN_SECOND, RETURN_OPEN, RETURN_SCREEN,
        SCALE_SMALL, SCALE_LARGE,
        REJOIN_REQUEST, REJOIN_PREPARE, REJOIN_OPEN, REJOIN_EMPTY, REJOIN_REFRESH, REJOIN_FRESH, RELAUNCH_READY,
        RELAUNCH_PREPARE, RELAUNCH_OPEN, RELAUNCH_EMPTY, RELAUNCH_REFRESH, RELAUNCH_FRESH, DONE }

    private final StandardCraftFixture first;
    private final String world;
    private final java.nio.file.Path evidence;
    private final boolean connectedDedicated;
    private final Map<Integer, String> originalJobs = new LinkedHashMap<>();
    private final Map<Integer, String> originalTotals = new LinkedHashMap<>();
    private final java.util.Set<Integer> secondGridKnownSerials = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> captured = new java.util.HashSet<>();
    private StandardCraftFixture second;
    private StandardCraftFixture lifecycle;
    private StandardCraftFixture large;
    private CompletableFuture<Boolean> operation;
    private CompletableFuture<String> observation;
    private Stage stage = Stage.INITIAL;
    private long stageStarted = System.nanoTime();
    private int firstVisibleSerial = -1;
    private int selectedSerial = -1;
    private int finishSerial = -1;
    private int removeSerial = -1;
    private int shortestSerial = -1;
    private long stalledElapsed;
    private String activeAction;
    private boolean actionComplete;
    private long lastFrame = -1;
    private long releasedFrame = -1;
    private long replacedElapsed;
    private boolean opening;
    private long openAttemptedAt;
    private int amountOptionStep;
    private long amountOptionFrame;
    private byte[] amountOptionServerSnapshot;
    private boolean amountOptionSaving;
    private boolean amountOptionMouseCleared;
    private boolean reconnectRequested;
    private boolean continuationWritten;
    private boolean crazyPriorityChanged;
    private final CpuListContinuation continuation;
    private java.util.List<Long> stalledProgress;
    private volatile String authoritativeState;
    private CpuListTtcControl.ServerState partialState;
    private CpuListTtcControl.ServerState beforeCancel;
    private final java.util.List<java.util.List<String>> itemModeOrders = new java.util.ArrayList<>();
    private final java.util.List<java.util.List<String>> itemKnownOrders = new java.util.ArrayList<>();

    CpuListTtcScenario(StandardCraftFixture first, String world, java.nio.file.Path output, boolean connectedDedicated,
            java.util.List<String> resultScreenshots) {
        this.first = first;
        this.world = world;
        this.connectedDedicated = connectedDedicated;
        CpuListInputControl.reset();
        var path = System.getProperty("ae2craftingtime.test.continuation", "");
        this.evidence = output.resolve(checkpointFile(!path.isBlank()));
        if (path.isBlank()) {
            continuation = null;
        } else {
            try {
                continuation = CpuListContinuation.read(java.nio.file.Path.of(path), world,
                        System.getProperty("ae2craftingtime.test.campaign", "local"));
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Cannot read CPU-list relaunch continuation", error);
            }
            first.cpuListScenario = true;
            first.bindTerminal(new net.minecraft.core.BlockPos(
                    continuation.terminalX(), continuation.terminalY(), continuation.terminalZ()));
            second = first;
            authoritativeState = continuation.serverState();
            captured.addAll(continuation.screenshots());
            resultScreenshots.addAll(continuation.screenshots());
            CpuTtcPacketControl.holdLatest();
            stage = Stage.RELAUNCH_PREPARE;
        }
    }

    static String checkpointFile(boolean resumed) {
        return resumed ? "cpu-list-checkpoints.phase-2.jsonl" : "cpu-list-checkpoints.jsonl";
    }

    String checkpoint() { return "cpu-list=" + stage; }
    boolean resumed() { return continuation != null; }
    boolean reconnectRequested() { return reconnectRequested; }
    void reconnected() {
        reconnectRequested = false;
        if (second != null) second.refreshCpuIdentities();
        next(Stage.REJOIN_PREPARE);
    }

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> capture, BiConsumer<Integer, Integer> moveMouse) {
        Consumer<String> screenshot = name -> { if (captured.add(name)) capture.accept(name); };
        if (continuation != null) continuation.checks().forEach(check -> mark(checks, check));
        var snapshot = UiObservationStore.latest();
        if (requiresObservation(stage)) {
            if (!(minecraft.screen instanceof CraftingStatusScreen) || snapshot == null
                    || snapshot.frame() == lastFrame) return false;
            lastFrame = snapshot.frame();
            if (!observe(minecraft)) return false;
            retain(snapshot);
        }

        switch (stage) {
            case INITIAL -> {
                if (snapshot.cpuCards().size() != 6) return false;
                var known = snapshot.cpuCards().stream().filter(card -> card.ttc() != null).toList();
                if (known.size() != 4 || known.stream().map(card -> card.ttc().rendered()).distinct().count() != 3) return false;
                if (!totalsMatch(snapshot)) return false;
                if (snapshot.cpuCards().stream().filter(card -> card.jobId() == null).anyMatch(card -> card.ttc() != null))
                    throw new IllegalStateException("idle CPU rendered a total");
                if (snapshot.cpuCards().stream().filter(card -> "minecraft:glass".equals(card.jobId())).anyMatch(card -> card.ttc() != null))
                    throw new IllegalStateException("unknown busy CPU rendered a total");
                known.forEach(card -> {
                    originalJobs.put(card.serial(), job(card));
                    originalTotals.put(card.serial(), card.ttc().rendered());
                });
                firstVisibleSerial = firstVisibleSerial(snapshot.cpuCards());
                selectedSerial = serialForJob(snapshot.cpuCards(), "minecraft:smooth_stone", 8);
                finishSerial = serialForJob(snapshot.cpuCards(), "minecraft:glass", 4);
                removeSerial = removalSerial(snapshot.cpuCards());
                shortestSerial = serialForJob(snapshot.cpuCards(), "minecraft:stone", 1);
                if (snapshot.rawCpuSerials().size() != 8 || snapshot.rawCpuSerials().indexOf(shortestSerial) < 6) {
                    throw new IllegalStateException("shortest CPU was not off-screen in raw AE2 order");
                }
                assertCpuOrder(snapshot, true);
                validateLayout(snapshot);
                if (!sortState(snapshot).contains("longest")) return false;
                if (!Boolean.TRUE.equals(CpuListInputControl.noFirstDraw())) return false;
                mark(checks, "initial-distinct", "unknown-hidden", "idle-hidden", "initial-mode", "no-first-draw", "layout");
                screenshot.accept("cpu-list-total-ttc-unselected-large.png");
                var badge = selectionBadge(snapshot.cpuCards(), selectedSerial);
                releasedFrame = snapshot.frame();
                DriverPlatform.clickAndRelease(minecraft, badge.centerX(), badge.centerY());
                next(Stage.SELECTED);
            }
            case SELECTED -> {
                if (snapshot.frame() <= releasedFrame) return false;
                var selected = card(snapshot, selectedSerial);
                if (!selected.selected() || selected.ttc() == null || serverState().selectedSerial() != selectedSerial) return false;
                var title = title(snapshot);
                if (title == null || !title.rendered().equals("TTC: " + selected.ttc().rendered())) return false;
                if (snapshot.text().stream().noneMatch(text -> text.bounds() != null
                        && text.bounds().inside(selected.nameArea()) && text.rendered().endsWith("...")
                        && selected.name().startsWith(text.rendered().substring(0, text.rendered().length() - 3)))) return false;
                mark(checks, "badge-select", "selected-title", "server-selection");
                screenshot.accept("cpu-list-total-ttc-selected-large.png");
                crazyPriorityChanged = DriverPlatform.isModLoaded("crazyae2addons");
                if (crazyPriorityChanged && !server(minecraft, "crazy-priority", first::raiseCrazyPriority)) return false;
                clickSort(minecraft);
                next(Stage.MODE_AE2);
            }
            case MODE_AE2 -> {
                var expected = snapshot.rawCpuSerials().subList(snapshot.scroll(), snapshot.scroll() + 6);
                var actual = snapshot.cpuCards().stream().map(UiSnapshot.CpuCard::serial).toList();
                if (!nativeOrderReady(crazyPriorityChanged, actual, expected, shortestSerial)
                        || !itemRowsReady(snapshot.rows())) return false;
                observeItemMode(snapshot);
                mark(checks, "raw-order");
                if (crazyPriorityChanged) mark(checks, "crazy-priority");
                screenshot.accept("cpu-list-total-ttc-ae2-order.png");
                clickSort(minecraft);
                next(Stage.MODE_SHORTEST);
            }
            case MODE_SHORTEST -> {
                if (!totalsMatch(snapshot)) return false;
                assertCpuOrder(snapshot, false);
                if (snapshot.cpuCards().get(0).serial() != shortestSerial) return false;
                observeItemMode(snapshot);
                mark(checks, "stable-groups", "offscreen-promoted");
                screenshot.accept("cpu-list-total-ttc-shortest-first.png");
                clickSort(minecraft);
                next(Stage.MODE_LONGEST);
            }
            case MODE_LONGEST -> {
                if (!totalsMatch(snapshot)) return false;
                assertCpuOrder(snapshot, true);
                observeItemMode(snapshot);
                if (itemModeOrders.size() != 3 || !SortObservation.valid(itemModeOrders.get(0),
                        itemModeOrders.get(1), itemModeOrders.get(2), itemKnownOrders.get(1), itemKnownOrders.get(2))) {
                    throw new IllegalStateException("selected CPU item rows did not complete the TTC sort cycle");
                }
                mark(checks, "cpu-sort-cycle", "item-sort-cycle");
                screenshot.accept("cpu-list-total-ttc-longest-first.png");
                CpuTtcPacketControl.channelAvailable(false);
                next(Stage.CHANNEL_FALLBACK);
            }
            case CHANNEL_FALLBACK -> {
                var expected = snapshot.rawCpuSerials().subList(snapshot.scroll(), snapshot.scroll() + 6);
                var fallbackTitle = title(snapshot);
                var actual = snapshot.cpuCards().stream().map(UiSnapshot.CpuCard::serial).toList();
                if (!nativeOrderReady(crazyPriorityChanged, actual, expected, shortestSerial)
                        || snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)
                        || fallbackTitle == null
                        || !SortObservation.sortableIds(snapshot.rows()).equals(itemModeOrders.get(2))) return false;
                mark(checks, "channel-fallback");
                screenshot.accept("cpu-list-total-ttc-channel-fallback.png");
                CpuTtcPacketControl.channelAvailable(null);
                next(Stage.CHANNEL_RESTORED);
            }
            case CHANNEL_RESTORED -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)) return false;
                var selected = card(snapshot, selectedSerial);
                moveMouse.accept(selected.badge().centerX(), selected.badge().centerY());
                next(Stage.TOOLTIP);
            }
            case TOOLTIP -> {
                var selected = card(snapshot, selectedSerial);
                if (!selected.name().contains("deliberately long")
                        || snapshot.tooltip().stream().noneMatch(text -> text.rendered().equals(selected.name()))) return false;
                mark(checks, "tooltip");
                screenshot.accept("cpu-list-total-ttc-tooltip.png");
                moveMouse.accept(0, 0);
                CpuListInputControl.armWheel(snapshot.cpuCards().get(0).serial());
                next(Stage.WHEEL);
            }
            case WHEEL -> {
                if (CpuListInputControl.wheelResult() == null) return false;
                if (!CpuListInputControl.wheelResult().equals(firstVisibleSerial))
                    throw new IllegalStateException("wheel-before-draw hit a row that was not displayed");
                if (serverState().selectedSerial() != firstVisibleSerial) return false;
                mark(checks, "wheel-before-draw");
                var badge = selectionBadge(snapshot.cpuCards(), selectedSerial);
                DriverPlatform.clickAndRelease(minecraft, badge.centerX(), badge.centerY());
                next(Stage.SCROLL_DOWN);
            }
            case SCROLL_DOWN -> {
                if (snapshot.cpuCards().stream().anyMatch(card -> card.serial() == firstVisibleSerial)
                        || snapshot.cpuCards().stream().noneMatch(card -> card.name().equals("Idle CPU 7"))
                        || !totalsMatch(snapshot)) return false;
                mark(checks, "scroll-down");
                screenshot.accept("cpu-list-total-ttc-scroll-down.png");
                CpuListScrollControl.scrollTo(0);
                next(Stage.SCROLL_UP);
            }
            case SCROLL_UP -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.serial() == firstVisibleSerial)
                        || snapshot.cpuCards().stream().anyMatch(card -> card.name().equals("Idle CPU 7"))
                        || !totalsMatch(snapshot) || serverState().selectedSerial() != selectedSerial) return false;
                mark(checks, "scroll-up");
                screenshot.accept("cpu-list-total-ttc-scroll-up.png");
                CpuTtcPacketControl.holdLatest();
                next(Stage.AE2_HOLD);
            }
            case AE2_HOLD -> {
                if (!CpuTtcPacketControl.hasHeld()) return false;
                clickSort(minecraft);
                CpuTtcPacketControl.releaseHeld();
                CpuTtcPacketControl.drop();
                next(Stage.AE2_LATE);
            }
            case AE2_LATE -> {
                var expected = snapshot.rawCpuSerials().subList(snapshot.scroll(), snapshot.scroll() + 6);
                var actual = snapshot.cpuCards().stream().map(UiSnapshot.CpuCard::serial).toList();
                if (!nativeOrderReady(crazyPriorityChanged, actual, expected, shortestSerial)) return false;
                mark(checks, "mode-switch-late");
                next(Stage.AE2_EXPIRED);
            }
            case AE2_EXPIRED -> {
                if (elapsedMillis() < 3_600 || snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)) return false;
                mark(checks, "ae2-expiry");
                CpuTtcPacketControl.resume();
                clickSort(minecraft);
                clickSort(minecraft);
                next(Stage.MODE_RESTORED);
            }
            case MODE_RESTORED -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)
                        || !sortState(snapshot).contains("longest")) return false;
                if (server(minecraft, "partial", player -> { first.makeCpuListPartial(player); return true; })) next(Stage.PARTIAL);
            }
            case PARTIAL -> {
                var state = serverState();
                if (partialState == null && state.stoneProfile() && !state.smoothProfile()
                        && state.cpus().stream().filter(cpu -> "minecraft:smooth_stone".equals(cpu.jobId()))
                                .noneMatch(cpu -> cpu.seconds() == null)) partialState = state;
                if (partialState == null
                        || snapshot.cpuCards().stream().filter(card -> "minecraft:smooth_stone".equals(card.jobId()))
                                .noneMatch(card -> card.ttc() != null)
                        || !totalsMatch(snapshot, partialState)) return false;
                mark(checks, "partial");
                screenshot.accept("cpu-list-total-ttc-partial.png");
                if (server(minecraft, "restore", player -> { first.restoreCpuListSamples(player); return true; })) {
                    stalledElapsed = snapshot.cpuCards().stream().filter(card -> card.jobId() != null)
                            .mapToLong(UiSnapshot.CpuCard::elapsedNanos).max().orElse(0);
                    stalledProgress = serverState().cpus().stream().map(CpuListTtcControl.CpuState::progress).toList();
                    next(Stage.STALLED);
                }
            }
            case STALLED -> {
                if (elapsedMillis() < 1200 || snapshot.cpuCards().stream().filter(card -> card.jobId() != null)
                        .mapToLong(UiSnapshot.CpuCard::elapsedNanos).max().orElse(0) <= stalledElapsed) return false;
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)) return false;
                if (!serverState().cpus().stream().map(CpuListTtcControl.CpuState::progress).toList().equals(stalledProgress))
                    throw new IllegalStateException("held jobs progressed during the stalled checkpoint");
                if (!totalsMatch(snapshot)) return false;
                mark(checks, "stalled");
                screenshot.accept("cpu-list-total-ttc-stalled.png");
                if (server(minecraft, "rename", player -> { first.renameCpuList(player); return true; })) next(Stage.RENAME);
            }
            case RENAME -> {
                if (snapshot.cpuCards().stream().filter(card -> card.serial() == selectedSerial)
                        .noneMatch(card -> card.name().startsWith("Alpha CPU with"))) return false;
                for (var card : snapshot.cpuCards()) if (originalJobs.containsKey(card.serial())
                        && !originalJobs.get(card.serial()).equals(job(card)))
                    throw new IllegalStateException("rename/reorder changed serial-job binding");
                mark(checks, "reordered");
                screenshot.accept("cpu-list-total-ttc-reordered.png");
                next(Stage.FINISH);
            }
            case FINISH -> {
                if (!server(minecraft, "finish", first::finishFirstCpu)) return false;
                if (snapshot.cpuCards().stream().filter(card -> card.serial() == finishSerial)
                        .anyMatch(card -> card.jobId() != null || card.ttc() != null)) return false;
                mark(checks, "finished");
                screenshot.accept("cpu-list-total-ttc-finished.png");
                next(Stage.CANCEL);
            }
            case CANCEL -> {
                beforeCancel = serverState();
                var button = ((com.ctux.ae2craftingtime.testdriver.mixin.CraftingStatusAccessor) minecraft.screen)
                        .ae2craftingtime_test_driver$cancel();
                DriverPlatform.clickAndRelease(minecraft, button.getX() + 2, button.getY() + 2);
                next(Stage.CANCELLED);
            }
            case CANCELLED -> {
                var selected = snapshot.cpuCards().stream().filter(card -> card.serial() == selectedSerial).findFirst();
                if (selected.isPresent() && (selected.get().jobId() != null || selected.get().ttc() != null)) return false;
                var after = serverState();
                if (after.cpus().stream().filter(cpu -> cpu.serial() == selectedSerial).anyMatch(CpuListTtcControl.CpuState::busy)
                        || !otherJobsUnchanged(beforeCancel, after, selectedSerial)) return false;
                mark(checks, "cancelled", "native-cancel");
                screenshot.accept("cpu-list-total-ttc-cancelled.png");
                next(Stage.REPLACE_EMPTY);
            }
            case REPLACE_EMPTY -> {
                if (card(snapshot, selectedSerial).ttc() != null) throw new IllegalStateException("cancelled job retained stale total");
                if (server(minecraft, "replace", first::replaceSecondCpu)) next(Stage.REPLACE_STARTED);
            }
            case REPLACE_STARTED -> {
                var replacement = card(snapshot, selectedSerial);
                if (!"minecraft:smooth_stone".equals(replacement.jobId()) || replacement.amount() != 8) return false;
                next(Stage.REPLACE);
            }
            case REPLACE -> {
                var replacement = card(snapshot, selectedSerial);
                if (!"minecraft:smooth_stone".equals(replacement.jobId()) || replacement.amount() != 8
                        || replacement.ttc() == null || replacement.elapsedNanos() < 2_000_000_000L) return false;
                replacedElapsed = replacement.elapsedNanos();
                CpuTtcPacketControl.hold();
                next(Stage.RESTART);
            }
            case RESTART -> {
                if (server(minecraft, "restart", first::restartSecondCpu)) next(Stage.RESTART_CLEARED);
            }
            case RESTART_CLEARED -> {
                var replacement = card(snapshot, selectedSerial);
                if (!"minecraft:smooth_stone".equals(replacement.jobId()) || replacement.amount() != 8
                        || replacement.elapsedNanos() >= replacedElapsed) return false;
                if (replacement.ttc() != null) throw new IllegalStateException("same-output same-amount restart retained stale TTC");
                screenshot.accept("cpu-list-total-ttc-restart-cleared.png");
                CpuTtcPacketControl.resume();
                next(Stage.RESTART_FRESH);
            }
            case RESTART_FRESH -> {
                if (card(snapshot, selectedSerial).ttc() == null || !totalsMatch(snapshot)) return false;
                mark(checks, "replacement");
                screenshot.accept("cpu-list-total-ttc-replacement.png");
                CpuListScrollControl.scrollTo(0);
                next(Stage.REMOVE_SCROLL);
            }
            case REMOVE_SCROLL -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.serial() == removeSerial)) {
                    CpuListScrollControl.scrollTo(snapshot.scroll() + 1);
                    return false;
                }
                CpuListInputControl.armStale(removeSerial);
                next(Stage.REMOVE);
            }
            case REMOVE -> {
                if (!server(minecraft, "remove", player -> { first.removeThirdCpu(player); return true; })) return false;
                if (snapshot.cpuCards().stream().anyMatch(card -> card.serial() == removeSerial)
                        || !Boolean.TRUE.equals(CpuListInputControl.staleSuppressed())
                        || serverState().selectedSerial() != selectedSerial) return false;
                mark(checks, "removed", "stale-hit");
                screenshot.accept("cpu-list-total-ttc-removed.png");
                CpuTtcPacketControl.drop();
                next(Stage.DROP);
            }
            case DROP -> { if (elapsedMillis() >= 3600) next(Stage.DROP_EXPIRED); }
            case DROP_EXPIRED -> {
                if (snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)) return false;
                mark(checks, "drop-expiry");
                screenshot.accept("cpu-list-total-ttc-drop-expiry.png");
                CpuTtcPacketControl.resume();
                next(Stage.HOLD);
            }
            case HOLD -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)) return false;
                CpuTtcPacketControl.hold();
                next(Stage.HOLD_EXPIRED);
            }
            case HOLD_EXPIRED -> {
                var nowMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                if (!CpuTtcPacketControl.hasHeld() || !heldRequestExpired(
                        CpuTtcPacketControl.requestCapture(), CpuTtcPacketControl.heldSequence(), nowMillis)) return false;
                if (snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)) return false;
                CpuTtcPacketControl.releaseHeld();
                CpuTtcPacketControl.drop();
                releasedFrame = snapshot.frame();
                next(Stage.HOLD_RELEASED);
            }
            case HOLD_RELEASED -> {
                if (snapshot.frame() <= releasedFrame) return false;
                if (snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null))
                    throw new IllegalStateException("superseded delayed response repopulated cache");
                CpuTtcPacketControl.resume();
                mark(checks, "delayed-expiry");
                screenshot.accept("cpu-list-total-ttc-delayed-expiry.png");
                minecraft.player.closeContainer();
                next(Stage.REOPEN);
            }
            case REOPEN -> {
                openTerminal(minecraft, first);
                if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                    var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                    DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                    next(Stage.REOPENED);
                }
            }
            case REOPENED -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)
                        || !sortState(snapshot).contains("longest")) return false;
                mark(checks, "close-reopen", "default-reopen");
                screenshot.accept("cpu-list-total-ttc-close-reopen.png");
                minecraft.player.closeContainer();
                second = first.secondGrid();
                next(Stage.SECOND_PREPARE);
            }
            case SECOND_PREPARE -> {
                if (server(minecraft, "second-grid", player -> {
                    if (!second.prepare(player, marker) || !second.prepareCpuListJobs(player)) return false;
                    return true;
                })) {
                    if (connectedDedicated) {
                        var state = CpuListTtcControl.state();
                        second.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                    }
                    next(Stage.SECOND_OPEN);
                }
            }
            case SECOND_OPEN -> {
                openTerminal(minecraft, second);
                if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                    var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                    DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                    next(Stage.SECOND_SCREEN);
                }
            }
            case SECOND_SCREEN -> {
                if (secondGridKnownSerials.isEmpty() && snapshot.scroll() != 0) {
                    secondScreenReady(snapshot, secondGridKnownSerials);
                    return false;
                }
                if (!totalsMatch(snapshot)) return false;
                if (snapshot.cpuCards().stream().filter(card -> card.ttc() != null)
                        .anyMatch(card -> originalTotals.get(card.serial()) != null
                                && originalTotals.get(card.serial()).equals(card.ttc().rendered())))
                    throw new IllegalStateException("second grid accepted first-grid total for an overlapping serial");
                if (!secondScreenReady(snapshot, secondGridKnownSerials)) return false;
                mark(checks, "second-grid");
                screenshot.accept("cpu-list-total-ttc-second-grid.png");
                minecraft.player.closeContainer();
                lifecycle = second;
                large = second.largeCpuGrid();
                second = large;
                next(Stage.LARGE_PREPARE);
            }
            case LARGE_PREPARE -> {
                if (server(minecraft, "large-grid", player ->
                        second.prepare(player, marker) && second.prepareCpuListJobs(player))) {
                    if (connectedDedicated) {
                        var state = CpuListTtcControl.state();
                        second.bindTerminal(new net.minecraft.core.BlockPos(state.x(), state.y(), state.z()));
                    }
                    CpuTtcPacketControl.beginRequestCapture();
                    next(Stage.LARGE_OPEN);
                }
            }
            case LARGE_OPEN -> {
                openTerminal(minecraft, second);
                if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                    var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                    DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                    next(Stage.LARGE_SCREEN);
                }
            }
            case LARGE_SCREEN -> {
                var state = serverState();
                if (snapshot.scroll() != 0 || snapshot.rawCpuSerials().size() != 33
                        || state.cpus().stream().filter(CpuListTtcControl.CpuState::busy).count() != 33
                        || state.cpus().stream().anyMatch(cpu -> cpu.busy() && cpu.seconds() == null)) return false;
                var requests = CpuTtcPacketControl.requestCapture();
                if (requests.batches().size() < 2
                        || requests.batches().stream().anyMatch(batch -> batch.serials().size() > 32
                                || new java.util.HashSet<>(batch.serials()).size() != batch.serials().size())
                        || !requestCadenceValid(requests.batches())
                        || !new java.util.HashSet<>(requests.uniqueSerials()).containsAll(snapshot.rawCpuSerials())) return false;
                mark(checks, "background-coverage");
                screenshot.accept("cpu-list-total-ttc-33-background.png");
                CpuTtcPacketControl.holdLatest();
                next(Stage.LARGE_HOLD);
            }
            case LARGE_HOLD -> {
                if (!CpuTtcPacketControl.hasHeld()) return false;
                clickSort(minecraft);
                CpuTtcPacketControl.releaseHeld();
                CpuTtcPacketControl.drop();
                CpuListScrollControl.scrollTo(10);
                next(Stage.LARGE_SWITCHED);
            }
            case LARGE_SWITCHED -> {
                if (!visibleWindowAt(snapshot.rawCpuSerials(), snapshot.cpuCards(), 10)
                        || snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)) return false;
                mark(checks, "mode-switch-late");
                CpuTtcPacketControl.resume();
                minecraft.player.closeContainer();
                second = lifecycle;
                next(Stage.RETURN_SECOND);
            }
            case RETURN_SECOND -> {
                if (server(minecraft, "return-second", player -> {
                    player.teleportTo(second.terminal.getX() + 0.5, second.terminal.getY() - 1,
                            second.terminal.getZ() - 2.5);
                    return true;
                })) next(Stage.RETURN_OPEN);
            }
            case RETURN_OPEN -> {
                openTerminal(minecraft, second);
                if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                    var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                    DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                    next(Stage.RETURN_SCREEN);
                }
            }
            case RETURN_SCREEN -> {
                if (snapshot.rawCpuSerials().size() != 8 || !totalsMatch(snapshot)) return false;
                minecraft.options.guiScale().set(1);
                DriverPlatform.resizeDisplay(minecraft);
                next(Stage.SCALE_SMALL);
            }
            case SCALE_SMALL -> {
                if (snapshot.guiScale() != 1 || snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)) return false;
                validateLayout(snapshot);
                mark(checks, "small-scale");
                screenshot.accept("cpu-list-total-ttc-smallest-scale.png");
                minecraft.options.guiScale().set(0);
                DriverPlatform.resizeDisplay(minecraft);
                next(Stage.SCALE_LARGE);
            }
            case SCALE_LARGE -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null)) return false;
                if (!fits(snapshot)) {
                    if (snapshot.guiScale() <= 1) throw new IllegalStateException("No GUI scale fits the CPU-list screen");
                    minecraft.options.guiScale().set((int) snapshot.guiScale() - 1);
                    DriverPlatform.resizeDisplay(minecraft);
                    return false;
                }
                validateLayout(snapshot);
                mark(checks, "large-scale");
                screenshot.accept("cpu-list-total-ttc-largest-scale.png");
                minecraft.player.closeContainer();
                CpuTtcPacketControl.holdLatest();
                next(Stage.REJOIN_REQUEST);
            }
            case REJOIN_REQUEST -> {
                reconnectRequested = true;
            }
            case REJOIN_PREPARE, RELAUNCH_PREPARE -> {
                var relaunch = stage == Stage.RELAUNCH_PREPARE;
                var action = relaunch ? "relaunch-prepare" : "rejoin-prepare";
                var prepared = server(minecraft, action, player -> {
                    var ready = second.prepare(player, marker);
                    if (ready && relaunch) second.viewTerminal(player);
                    return ready;
                });
                if (prepared) {
                    next(stage == Stage.REJOIN_PREPARE ? Stage.REJOIN_OPEN : Stage.RELAUNCH_OPEN);
                }
            }
            case REJOIN_OPEN, RELAUNCH_OPEN -> {
                if (minecraft.player == null || minecraft.gameMode == null) {
                    return false;
                }
                openTerminal(minecraft, second);
                if (minecraft.screen instanceof MEStorageScreen<?> screen) {
                    var button = ((MEStorageScreenAccessor) screen).ae2craftingtime_test_driver$statusButton();
                    next(stage == Stage.REJOIN_OPEN ? Stage.REJOIN_EMPTY : Stage.RELAUNCH_EMPTY);
                    DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
                }
            }
            case REJOIN_EMPTY, RELAUNCH_EMPTY -> {
                if (snapshot.cpuCards().isEmpty() || !CpuTtcPacketControl.hasHeld()) return false;
                if (snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null)) {
                    throw new IllegalStateException("reconnected menu inherited TTC before a fresh response");
                }
                if (stage == Stage.REJOIN_EMPTY) {
                    mark(checks, "same-jvm-clear");
                    screenshot.accept("cpu-list-total-ttc-disconnect-cleared.png");
                    CpuTtcPacketControl.releaseHeld();
                    next(Stage.REJOIN_REFRESH);
                } else {
                    if (!samePhysicalJobs(new com.google.gson.Gson().fromJson(
                            continuation.serverState(), CpuListTtcControl.ServerState.class), serverState())) {
                        throw new IllegalStateException("relaunch changed the physical CPU, network, or job identity");
                    }
                    mark(checks, "process-relaunch");
                    screenshot.accept("cpu-list-total-ttc-relaunch-cleared.png");
                    CpuTtcPacketControl.releaseHeld();
                    next(Stage.RELAUNCH_REFRESH);
                }
            }
            case REJOIN_REFRESH, RELAUNCH_REFRESH -> {
                var state = serverState();
                if (requiresJobRefresh(connectedDedicated, state)) {
                    var action = stage == Stage.REJOIN_REFRESH ? "rejoin-refresh" : "relaunch-refresh";
                    if (!server(minecraft, action, second::restartSecondCpu)) return false;
                }
                next(stage == Stage.REJOIN_REFRESH ? Stage.REJOIN_FRESH : Stage.RELAUNCH_FRESH);
            }
            case REJOIN_FRESH -> {
                if (snapshot.cpuCards().stream().noneMatch(card -> card.ttc() != null) || !totalsMatch(snapshot)) return false;
                screenshot.accept("cpu-list-total-ttc-rejoin-fresh.png");
                next(Stage.RELAUNCH_READY);
            }
            case RELAUNCH_READY -> {
                if (!continuationWritten) {
                    var state = connectedDedicated ? CpuListTtcControl.state() : null;
                    var serverSequence = state == null ? 0 : state.ack();
                    var clientSequence = Math.max(serverSequence + 1, CpuListTtcControl.clientSequence() + 1);
                    try {
                        CpuListContinuation.write(evidence.resolveSibling("cpu-list-continuation.json"),
                                new CpuListContinuation(1, "relaunch-ready", world,
                                        System.getProperty("ae2craftingtime.test.campaign", "local"),
                                        authoritativeState, second.terminal.getX(), second.terminal.getY(), second.terminal.getZ(),
                                        checks.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList(),
                                        captured.stream().sorted().toList(), serverSequence, clientSequence));
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Cannot save CPU-list relaunch continuation", error);
                    }
                    continuationWritten = true;
                    minecraft.stop();
                }
            }
            case RELAUNCH_FRESH -> {
                var nextStage = afterRelaunchFreshObservation(snapshot, serverState());
                if (nextStage != Stage.DONE) return false;
                mark(checks, "reconnect");
                screenshot.accept("cpu-list-total-ttc-reconnect.png");
                next(nextStage);
            }
            case DONE -> {
                if (connectedDedicated && !nonOperatorAmounts(minecraft, screenshot, moveMouse)) return false;
                if (connectedDedicated && !CpuListTtcControl.request("complete")) return false;
                return true;
            }
            default -> { }
        }
        return false;
    }

    private boolean nonOperatorAmounts(Minecraft minecraft, Consumer<String> screenshot,
            BiConsumer<Integer, Integer> moveMouse) {
        if (amountOptionStep == 2) return true;
        if (TestDriverRuntime.renderedFrames < amountOptionFrame) return false;
        var snapshot = com.ctux.ae2craftingtime.mc1201.ClientServerOptions.snapshot();
        if (snapshot == null) return false;
        if (snapshot.editable()) throw new IllegalStateException("Compact option check requires a real non-operator connection");
        var encoded = com.ctux.ae2craftingtime.core.ServerOptionsWire.encode(snapshot);
        if (amountOptionServerSnapshot == null) amountOptionServerSnapshot = encoded;
        if (!java.util.Arrays.equals(encoded, amountOptionServerSnapshot))
            throw new IllegalStateException("Client compact option changed the server options");
        boolean expected = amountOptionStep == 1;
        if (!(minecraft.screen instanceof com.ctux.ae2craftingtime.mc1201.OptionsScreen)) {
            if (amountOptionSaving) {
                if (com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().features()
                        .enabled(com.ctux.ae2craftingtime.core.OptionFeature.COMPACT_STATUS_AMOUNTS) != expected)
                    throw new IllegalStateException("Non-operator compact option did not save");
                screenshot.accept("status-nonop-saved-" + (expected ? "on" : "off") + ".png");
                amountOptionSaving = false;
                if (++amountOptionStep == 2) {
                    var result = new com.google.gson.JsonObject();
                    result.addProperty("schema", 1);
                    result.addProperty("world", world);
                    result.addProperty("campaign", System.getProperty("ae2craftingtime.test.campaign", "local"));
                    result.addProperty("editable", false);
                    result.addProperty("offSaved", true);
                    result.addProperty("onSaved", true);
                    result.addProperty("serverSnapshotSha256", CaptureEvidence.sha256(encoded));
                    try {
                        java.nio.file.Files.writeString(evidence.resolveSibling("status-nonop-options.json"), result.toString());
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Cannot retain non-operator options evidence", error);
                    }
                    return true;
                }
            }
            minecraft.setScreen(new com.ctux.ae2craftingtime.mc1201.OptionsScreen(minecraft.screen));
            amountOptionFrame = TestDriverRuntime.renderedFrames + 3;
            return false;
        }
        if (TestDriverRuntime.renderedFrames < amountOptionFrame) return false;
        var label = net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.compactStatusAmounts") + ": ";
        var buttons = minecraft.screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).toList();
        var toggle = buttons.stream().filter(button -> button.getMessage().getString().startsWith(label)).findFirst().orElse(null);
        String action;
        if (toggle == null) action = ">";
        else {
            if (!toggle.active) throw new IllegalStateException("Non-operator compact option is disabled");
            boolean enabled = toggle.getMessage().getString().endsWith(net.minecraft.client.resources.language.I18n.get("options.on"));
            if (enabled != expected) {
                DriverPlatform.click(minecraft, toggle.getX() + 4, toggle.getY() + 4);
                amountOptionMouseCleared = false;
                amountOptionFrame = TestDriverRuntime.renderedFrames + 3;
                return false;
            }
            if (!amountOptionMouseCleared) {
                moveMouse.accept(0, 0);
                amountOptionMouseCleared = true;
                amountOptionFrame = TestDriverRuntime.renderedFrames + 2;
                return false;
            }
            screenshot.accept("status-nonop-option-" + (expected ? "on" : "off") + ".png");
            amountOptionMouseCleared = false;
            action = net.minecraft.client.resources.language.I18n.get("gui.done");
            amountOptionSaving = true;
        }
        var button = buttons.stream().filter(value -> value.getMessage().getString().equals(action)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Non-operator option control missing: " + action));
        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
        amountOptionFrame = TestDriverRuntime.renderedFrames + 3;
        return false;
    }

    private boolean server(Minecraft minecraft, String name, Function<ServerPlayer, Boolean> action) {
        if (!name.equals(activeAction)) {
            activeAction = name;
            actionComplete = false;
            operation = null;
        }
        if (actionComplete) return true;
        if (connectedDedicated) {
            actionComplete = CpuListTtcControl.request(name);
            if (actionComplete) authoritativeState = CpuListTtcControl.state().serverState();
            return actionComplete;
        }
        if (operation == null) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("integrated fixture operation used without integrated server");
            var id = minecraft.player.getUUID();
            operation = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                boolean result = action.apply(player);
                if (result) authoritativeState = (second == null ? first : second).cpuListServerState(player);
                return result;
            });
        }
        if (!operation.isDone()) return false;
        boolean result = operation.join();
        operation = null;
        if (result) actionComplete = true;
        return result;
    }

    private void openTerminal(Minecraft minecraft, StandardCraftFixture fixture) {
        if (minecraft.screen != null) return;
        var range = DriverPlatform.blockInteractionRange(minecraft);
        if (minecraft.player.position().distanceToSqr(Vec3.atCenterOf(fixture.terminal)) > range * range) return;
        var now = System.nanoTime();
        if (!shouldAttemptTerminalOpen(opening, openAttemptedAt, now)) return;
        opening = true;
        openAttemptedAt = now;
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(fixture.terminal).add(0, 0, -0.5), Direction.NORTH,
                    fixture.terminal, false));
    }

    static boolean shouldAttemptTerminalOpen(boolean opening, long attemptedAt, long now) {
        return !opening || now - attemptedAt >= OPEN_RETRY_NANOS;
    }

    private void next(Stage value) { stage = value; stageStarted = System.nanoTime(); opening = false; }
    private long elapsedMillis() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stageStarted); }
    private static String job(UiSnapshot.CpuCard card) { return card.jobId() + ":" + card.amount(); }
    private static UiSnapshot.CpuCard card(UiSnapshot snapshot, int serial) {
        return snapshot.cpuCards().stream().filter(value -> value.serial() == serial).findFirst()
                .orElseThrow(() -> new IllegalStateException("CPU serial " + serial + " is not visible"));
    }
    static Rect selectionBadge(java.util.List<UiSnapshot.CpuCard> cards, int serial) {
        return cards.stream().filter(value -> value.serial() == serial).findFirst()
                .orElseThrow(() -> new IllegalStateException("CPU serial " + serial + " is not visible")).badge();
    }
    static int firstVisibleSerial(java.util.List<UiSnapshot.CpuCard> cards) {
        return cards.stream().findFirst().orElseThrow(() -> new IllegalStateException("CPU list is empty")).serial();
    }
    static boolean visibleWindowAt(java.util.List<Integer> rawSerials,
            java.util.List<UiSnapshot.CpuCard> cards, int start) {
        if (cards.isEmpty() || start < 0 || start + cards.size() > rawSerials.size()) return false;
        return cards.stream().map(UiSnapshot.CpuCard::serial).toList()
                .equals(rawSerials.subList(start, start + cards.size()));
    }
    static int serialForJob(java.util.List<UiSnapshot.CpuCard> cards, String jobId, long amount) {
        return cards.stream().filter(value -> jobId.equals(value.jobId()) && value.amount() == amount)
                .mapToInt(UiSnapshot.CpuCard::serial).findFirst()
                .orElseThrow(() -> new IllegalStateException("CPU job " + jobId + " x" + amount + " is not visible"));
    }
    static int removalSerial(java.util.List<UiSnapshot.CpuCard> cards) {
        return cards.stream().filter(value -> value.name().equals("Gamma CPU")).mapToInt(UiSnapshot.CpuCard::serial)
                .findFirst().orElseThrow(() -> new IllegalStateException("third fixture CPU is not visible"));
    }
    private static UiSnapshot.ObservedText title(UiSnapshot snapshot) {
        return snapshot.text().stream().filter(text -> text.key().equals("native-title")
                && text.bounds() != null && text.bounds().y() < snapshot.gui().y() + 19).findFirst().orElse(null);
    }
    private static void validateLayout(UiSnapshot snapshot) {
        if (!fits(snapshot)) throw new IllegalStateException("CPU-list screen does not fit the viewport");
        for (var card : snapshot.cpuCards()) {
            var name = snapshot.text().stream().filter(text -> text.bounds() != null
                    && text.bounds().overlaps(card.nameArea()) && renderedName(card.name(), text.rendered()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "CPU name was not observed for serial " + card.serial()));
            if (!name.bounds().inside(card.nameArea()))
                throw new IllegalStateException("CPU name escapes its card for serial " + card.serial());
            if (card.ttc() == null) continue;
            if (card.badge() == null || !card.badge().inside(card.bounds()) || !card.ttc().bounds().inside(card.badge())
                    || card.badge().overlaps(card.infoArea()) || card.badge().overlaps(card.progressArea()))
                throw new IllegalStateException("CPU card layout collision for serial " + card.serial());
            if (name.bounds().overlaps(card.badge()))
                throw new IllegalStateException("CPU name overlaps its TTC badge");
        }
    }

    private static boolean renderedName(String name, String rendered) {
        return name.equals(rendered) || rendered.endsWith("...")
                && name.startsWith(rendered.substring(0, rendered.length() - 3));
    }
    private static void mark(Map<String, Boolean> checks, String... names) {
        for (var name : names) if (checks.containsKey(name)) checks.put(name, true);
    }

    private static String sortState(UiSnapshot snapshot) {
        return snapshot.widgets().stream().filter(widget -> widget.type().endsWith("TtcSortButton"))
                .map(UiSnapshot.Widget::state).findFirst().orElse("").toLowerCase(java.util.Locale.ROOT);
    }

    private void observeItemMode(UiSnapshot snapshot) {
        var order = SortObservation.sortableIds(snapshot.rows());
        if (order.isEmpty()) throw new IllegalStateException("selected CPU exposed no crafting-plan rows");
        if (!itemModeOrders.isEmpty()
                && !new java.util.HashSet<>(itemModeOrders.get(0)).equals(new java.util.HashSet<>(order))) {
            throw new IllegalStateException("CPU sort mode changed the selected job's item rows");
        }
        itemModeOrders.add(order);
        itemKnownOrders.add(snapshot.rows().stream()
                .filter(row -> row.description().stream()
                        .anyMatch(text -> text.key().equals("text.ae2craftingtime.ttc")))
                .map(UiSnapshot.Row::outputId).toList());
    }

    static boolean otherJobsUnchanged(CpuListTtcControl.ServerState before,
            CpuListTtcControl.ServerState after, int excludedSerial) {
        if (before == null || after == null) return false;
        return comparableJobs(before, excludedSerial).equals(comparableJobs(after, excludedSerial));
    }

    private static java.util.Map<Integer, String> comparableJobs(
            CpuListTtcControl.ServerState state, int excludedSerial) {
        return state.cpus().stream().filter(cpu -> cpu.serial() != excludedSerial).collect(java.util.stream.Collectors.toMap(
                CpuListTtcControl.CpuState::serial,
                cpu -> cpu.position() + "|" + cpu.jobId() + "|" + cpu.amount() + "|" + cpu.live()
                        + "|" + cpu.busy() + "|" + cpu.progress()));
    }

    private static void clickSort(Minecraft minecraft) {
        var button = minecraft.screen.children().stream().filter(TtcSortButton.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).findFirst().orElseThrow();
        DriverPlatform.click(minecraft, button.getX() + 4, button.getY() + 4);
    }

    private void assertCpuOrder(UiSnapshot snapshot, boolean descending) {
        var state = serverState();
        var rawIndex = new java.util.HashMap<Integer, Integer>();
        for (int index = 0; index < snapshot.rawCpuSerials().size(); index++)
            rawIndex.put(snapshot.rawCpuSerials().get(index), index);
        Long previous = null;
        var previousGroup = -1;
        var previousRawIndex = -1;
        for (var card : snapshot.cpuCards()) {
            var cpu = state.cpus().stream().filter(value -> value.serial() == card.serial()).findFirst().orElseThrow();
            var group = !cpu.busy() ? 2 : cpu.seconds() == null ? 1 : 0;
            if (group < previousGroup) throw new IllegalStateException("CPU TTC groups are out of order");
            if (group == 0 && previous != null) {
                var comparison = Long.compare(cpu.seconds(), previous);
                if (descending ? comparison > 0 : comparison < 0) {
                    throw new IllegalStateException("CPU TTC values are out of order");
                }
            }
            var currentRawIndex = rawIndex.getOrDefault(card.serial(), -1);
            if (currentRawIndex < 0) throw new IllegalStateException("rendered CPU is absent from raw AE2 order");
            if (group == previousGroup && (group != 0 || java.util.Objects.equals(cpu.seconds(), previous))
                    && currentRawIndex < previousRawIndex)
                throw new IllegalStateException("equal CPU TTC group did not preserve AE2 order");
            previousGroup = group;
            previous = group == 0 ? cpu.seconds() : null;
            previousRawIndex = currentRawIndex;
        }
    }

    private static boolean fits(UiSnapshot snapshot) {
        var viewport = new Rect(0, 0, snapshot.screenWidth(), snapshot.screenHeight());
        return snapshot.gui().inside(viewport) && snapshot.cpuCards().stream().allMatch(card -> card.bounds().inside(viewport));
    }

    static boolean requestCadenceValid(java.util.List<CpuTtcPacketControl.ObservedRequest> requests) {
        for (int index = 1; index < requests.size(); index++) {
            if (requests.get(index).sequence() <= requests.get(index - 1).sequence()
                    || requests.get(index).sentAtMillis() - requests.get(index - 1).sentAtMillis() < 1_000) return false;
        }
        return true;
    }

    static boolean heldRequestExpired(CpuTtcPacketControl.RequestCapture capture, long sequence, long nowMillis) {
        return sequence >= 0 && capture.batches().stream().filter(request -> request.sequence() == sequence)
                .anyMatch(request -> nowMillis - request.sentAtMillis()
                        >= com.ctux.ae2craftingtime.core.CpuTtcCache.REQUEST_TIMEOUT_MILLIS);
    }

    static boolean itemRowsReady(java.util.List<UiSnapshot.Row> rows) {
        var active = rows.stream().filter(row -> row.craftAmount() > 0).toList();
        return active.size() >= 2 && active.stream().anyMatch(row -> row.description().stream()
                .anyMatch(text -> text.key().equals("text.ae2craftingtime.ttc")));
    }

    static boolean nativeOrderReady(boolean crazyPriorityChanged, java.util.List<Integer> actual,
            java.util.List<Integer> rawWindow, int prioritySerial) {
        return crazyPriorityChanged ? !actual.isEmpty() && actual.get(0) == prioritySerial : actual.equals(rawWindow);
    }

    private CpuListTtcControl.ServerState serverState() {
        return new com.google.gson.Gson().fromJson(authoritativeState, CpuListTtcControl.ServerState.class);
    }

    private boolean totalsMatch(UiSnapshot snapshot) {
        return totalsMatch(snapshot, serverState());
    }

    private static boolean totalsMatch(UiSnapshot snapshot, CpuListTtcControl.ServerState state) {
        for (var card : snapshot.cpuCards()) {
            var cpu = state.cpus().stream().filter(value -> value.serial() == card.serial()).findFirst().orElse(null);
            if (cpu == null || !java.util.Objects.equals(cpu.jobId(), card.jobId()) || cpu.amount() != card.amount()) return false;
            var expected = com.ctux.ae2craftingtime.core.TimeEstimate.formatTotal(java.util.List.of(
                    cpu.seconds() == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(cpu.seconds())))
                    .map(value -> com.ctux.ae2craftingtime.mc1201.TtcText.ttc(value).getString()).orElse(null);
            if (!java.util.Objects.equals(expected, card.ttc() == null ? null : card.ttc().rendered())) return false;
        }
        return true;
    }

    static boolean samePhysicalJobs(CpuListTtcControl.ServerState before, CpuListTtcControl.ServerState after) {
        if (before == null || after == null || !java.util.Objects.equals(before.network(), after.network())) return false;
        var first = before.cpus().stream().map(CpuListTtcScenario::physicalJob).sorted().toList();
        var second = after.cpus().stream().map(CpuListTtcScenario::physicalJob).sorted().toList();
        return first.equals(second);
    }

    static boolean secondScreenReady(UiSnapshot snapshot, java.util.Set<Integer> knownSerials) {
        if (knownSerials.isEmpty() && snapshot.scroll() != 0) {
            CpuListScrollControl.scrollTo(0);
            return false;
        }
        snapshot.cpuCards().stream().filter(card -> card.ttc() != null)
                .map(UiSnapshot.CpuCard::serial).forEach(knownSerials::add);
        if (knownSerials.size() < 4) {
            if (snapshot.scroll() == 0) CpuListScrollControl.scrollTo(1);
            return false;
        }
        if (knownSerials.size() > 4) throw new IllegalStateException("second grid exposed more than four known totals");
        if (snapshot.scroll() != 0) {
            CpuListScrollControl.scrollTo(0);
            return false;
        }
        return true;
    }

    static boolean requiresJobRefresh(boolean connectedDedicated, CpuListTtcControl.ServerState state) {
        return !connectedDedicated && state != null
                && state.cpus().stream().anyMatch(CpuListTtcControl.CpuState::busy)
                && state.cpus().stream().filter(CpuListTtcControl.CpuState::busy)
                        .noneMatch(cpu -> cpu.seconds() != null);
    }

    static Stage afterRelaunchFreshObservation(UiSnapshot snapshot, CpuListTtcControl.ServerState state) {
        return snapshot.cpuCards().stream().anyMatch(card -> card.ttc() != null) && totalsMatch(snapshot, state)
                ? Stage.DONE : Stage.RELAUNCH_FRESH;
    }

    private static String physicalJob(CpuListTtcControl.CpuState state) {
        return state.position() + "|" + state.jobId() + "|" + state.amount() + "|" + state.live() + "|" + state.busy();
    }

    private boolean observe(Minecraft minecraft) {
        if (connectedDedicated) {
            authoritativeState = CpuListTtcControl.state().serverState();
            return !authoritativeState.isBlank();
        }
        if (observation == null) {
            var server = minecraft.getSingleplayerServer();
            if (server == null || minecraft.player == null) return false;
            var id = minecraft.player.getUUID();
            observation = server.submit(() -> (second == null ? first : second)
                    .cpuListServerState(server.getPlayerList().getPlayer(id)));
        }
        if (!observation.isDone()) return false;
        authoritativeState = observation.join();
        observation = null;
        return true;
    }

    private static boolean requiresObservation(Stage value) {
        return switch (value) {
            case INITIAL, SELECTED, MODE_AE2, MODE_SHORTEST, MODE_LONGEST, CHANNEL_FALLBACK, CHANNEL_RESTORED,
                    TOOLTIP, WHEEL, SCROLL_DOWN, SCROLL_UP, AE2_HOLD, AE2_LATE, AE2_EXPIRED, MODE_RESTORED,
                    PARTIAL, STALLED, RENAME, FINISH, CANCEL, CANCELLED,
                    REPLACE_EMPTY, REPLACE_STARTED, REPLACE, RESTART_CLEARED, RESTART_FRESH, REMOVE_SCROLL,
                    REMOVE, DROP_EXPIRED, HOLD, HOLD_EXPIRED, HOLD_RELEASED, REOPENED, SECOND_SCREEN, LARGE_SCREEN,
                    LARGE_HOLD, LARGE_SWITCHED,
                    RETURN_SCREEN, SCALE_SMALL,
                    SCALE_LARGE, REJOIN_EMPTY, REJOIN_FRESH, RELAUNCH_EMPTY, RELAUNCH_FRESH -> true;
            default -> false;
        };
    }

    private void retain(UiSnapshot snapshot) {
        try {
            java.nio.file.Files.createDirectories(evidence.getParent());
            var value = new com.google.gson.Gson().toJson(java.util.Map.of(
                    "stage", stage.name(), "frame", snapshot.frame(), "screen", snapshot.screen(),
                    "menu", snapshot.menu(), "scroll", snapshot.scroll(), "rawCpuSerials", snapshot.rawCpuSerials(),
                    "clientCards", snapshot.cpuCards(), "requests", CpuTtcPacketControl.requestCapture(),
                    "serverState", serverState()));
            java.nio.file.Files.writeString(evidence, value + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot retain CPU-list checkpoint evidence", error);
        }
    }
}
