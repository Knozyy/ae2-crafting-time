package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class ProviderDispatchStatusScenario {
    static final String NO_TARGET = "no-target-status";
    static final String INPUT_BLOCKED = "input-blocked-status";
    static final String LOCKED = "locked-status";
    static final String NO_CHANNEL = "no-channel-status";
    private static final String MIXED = "text.ae2craftingtime.dispatch_status.scheduled_only";
    private static final List<String> BASE_CHECKS = List.of("screen", "real-job", "tooltip", "layout");
    private final String scenario;
    private final String key;
    private final DispatchStatusFixture fixture;
    private final StableFrames<Integer> frames = new StableFrames<>(3);
    private final StableFrames<Integer> tooltipFrames = new StableFrames<>(3);
    private CompletableFuture<Boolean> operation;
    private int phase;
    private long changedAt;
    private final StatsInteraction stats = new StatsInteraction();
    private int controlPhase;

    ProviderDispatchStatusScenario(String scenario) {
        if (!supports(scenario)) throw new IllegalArgumentException("unsupported provider status scenario: " + scenario);
        this.scenario = scenario;
        key = "text.ae2craftingtime." + scenario.replace("-status", "").replace('-', '_');
        fixture = new DispatchStatusFixture(INPUT_BLOCKED.equals(scenario) ? 2 : 1,
                LOCKED.equals(scenario) ? LockCraftingMode.LOCK_WHILE_LOW : LockCraftingMode.NONE,
                INPUT_BLOCKED.equals(scenario) || NO_CHANNEL.equals(scenario),
                !NO_CHANNEL.equals(scenario) && (INPUT_BLOCKED.equals(scenario) || advancedFixture()) ? 4096 : 64,
                NO_CHANNEL.equals(scenario));
    }

    static boolean supports(String scenario) {
        return NO_TARGET.equals(scenario) || INPUT_BLOCKED.equals(scenario) || LOCKED.equals(scenario)
                || NO_CHANNEL.equals(scenario);
    }

    static boolean statusRowsReady(List<UiSnapshot.Row> rows) {
        return !rows.isEmpty();
    }

    static List<String> checks(String scenario) {
        var checks = new java.util.ArrayList<>(BASE_CHECKS);
        if (advancedFixture()) checks.add("advanced-cpu");
        else if (!NO_CHANNEL.equals(scenario)) checks.add("mixed-row");
        if (NO_CHANNEL.equals(scenario)) checks.addAll(List.of(
                "channel-starved", "no-samples", "healthy-alternative", "power-loss-suppressed",
                "reboot-boundary", "reboot-recovered", "missing-input-suppressed", "infinite-mode-suppressed",
                "channel-mode-restored", "channel-restored", "job-completed"));
        if (NO_TARGET.equals(scenario)) checks.addAll(List.of("target-removed", "target-restored"));
        if (INPUT_BLOCKED.equals(scenario)) checks.addAll(List.of(
                "blocking-mode", "warning-details", "warning-reset", "blocking-recovered", "zero-insertion", "partial-capacity"));
        if (LOCKED.equals(scenario)) {
            checks.addAll(List.of("lock-while-low", "low-recovered"));
            if (!advancedFixture()) checks.addAll(List.of("lock-while-high", "high-recovered",
                    "pulse-lock", "pulse-recovered", "result-lock", "result-returned"));
        }
        return List.copyOf(checks);
    }

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (phase < 3) {
            if (serverStep(minecraft, player -> fixture.prepare(phase, player, marker))) phase++;
            return false;
        }
        if (NO_CHANNEL.equals(scenario) && phase == 15) {
            if (!serverStep(minecraft, fixture::finishDispatchedOutput)) return false;
            checks.put("job-completed", true);
            return true;
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingCPUScreen<?> screen) || snapshot == null
                || !snapshot.screen().equals(screen.getClass().getName()) || !statusRowsReady(snapshot.rows())
                || !frames.observe(phase)) return false;
        if (phase == 3) {
            checks.put("screen", true);
            checks.put("real-job", true);
            if (advancedFixture()) checks.put("advanced-cpu", true);
            else if (!NO_CHANNEL.equals(scenario)) checks.put("mixed-row", true);
            if (NO_CHANNEL.equals(scenario)) {
                if (operation == null && !observeWarning(snapshot, checks, screenshot, moveMouse, "no-channel-en-us.png")) return false;
                if (serverStep(minecraft, player -> {
                    if (!fixture.initialChannelJob(player)) throw new IllegalStateException("channel fixture lacks initial job/node/no-sample evidence");
                    return fixture.installHealthyAlternative(player);
                })) {
                    checks.put("channel-starved", true);
                    checks.put("no-samples", true);
                    changedAt = System.nanoTime();
                    phase++;
                }
                return false;
            }
            if (NO_TARGET.equals(scenario)) {
                if (operation == null && hasWarning(snapshot)) {
                    throw new IllegalStateException("NO TARGET appeared before target removal");
                }
                if (serverStep(minecraft, player -> {
                    player.level().setBlockAndUpdate(fixture.targetPosition(), Blocks.AIR.defaultBlockState());
                    return true;
                })) phase++;
                return false;
            }
            if (LOCKED.equals(scenario)) {
                if (serverStep(minecraft, player -> {
                    fixture.provider(player).getLogic().getConfigManager().putSetting(
                            Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW);
                    return true;
                })) phase++;
                return false;
            }
            if (!observeWarning(snapshot, checks, screenshot, moveMouse, scenario + "-en-us.png")) return false;
            checks.put("blocking-mode", true);
            phase++;
        } else if (NO_CHANNEL.equals(scenario)) {
            if (phase == 4 && (operation != null || recovered(snapshot))
                    && serverStep(minecraft, player -> fixture.healthyAlternativeDispatched(player)
                            && fixture.removeHealthyAlternative(player))) {
                checks.put("healthy-alternative", true);
                screenshot.accept("no-channel-alternative.png");
                phase++;
            } else if (phase == 5 && (operation != null || hasWarning(snapshot)) && serverStep(minecraft, player -> fixture.setPower(player, false))) {
                changedAt = System.nanoTime();
                phase++;
            } else if (phase == 6 && (operation != null || recovered(snapshot))
                    && serverStep(minecraft, player -> fixture.providerPowered(player, false)
                            && fixture.setPower(player, true))) {
                checks.put("power-loss-suppressed", true);
                screenshot.accept("no-channel-power-loss.png");
                phase++;
            } else if (phase == 7 && (operation != null || hasWarning(snapshot))
                    && serverStep(minecraft, fixture::reboot)) {
                changedAt = System.nanoTime();
                phase++;
            } else if (phase == 8 && (operation != null || hasWarning(snapshot))
                    && serverStep(minecraft, fixture::providerPastRebootBoundary)) {
                checks.put("reboot-boundary", true);
                screenshot.accept("no-channel-reboot.png");
                phase++;
            } else if (phase == 9 && (operation != null || hasWarning(snapshot)) && serverStep(minecraft, player -> fixture.setInputs(player, false))) {
                checks.put("reboot-recovered", true);
                changedAt = System.nanoTime();
                phase++;
            } else if (phase == 10 && (operation != null || recovered(snapshot)) && serverStep(minecraft, player -> fixture.setInputs(player, true))) {
                checks.put("missing-input-suppressed", true);
                screenshot.accept("no-channel-missing-input.png");
                phase++;
            } else if (phase == 11 && (operation != null || hasWarning(snapshot))
                    && serverStep(minecraft, player -> fixture.setInfiniteChannels(player, true))) {
                changedAt = System.nanoTime();
                phase++;
            } else if (phase == 12 && (operation != null || recovered(snapshot))
                    && serverStep(minecraft, player -> fixture.channelMode(player, true) && fixture.channelRestored(player)
                            && fixture.setInfiniteChannels(player, false))) {
                checks.put("infinite-mode-suppressed", true);
                screenshot.accept("no-channel-infinite.png");
                phase++;
            } else if (phase == 13 && (operation != null || hasWarning(snapshot))
                    && serverStep(minecraft, player -> fixture.channelMode(player, false)
                            && fixture.restoreChannel(player))) {
                checks.put("channel-mode-restored", true);
                changedAt = System.nanoTime();
                phase++;
            } else if (phase == 14 && (operation != null || recovered(snapshot))
                    && serverStep(minecraft, fixture::recoveredDispatch)) {
                checks.put("channel-restored", true);
                screenshot.accept("no-channel-restored.png");
                phase++;
            }
        } else if (NO_TARGET.equals(scenario)) {
            return tickNoTarget(minecraft, snapshot, checks, screenshot, moveMouse);
        } else if (INPUT_BLOCKED.equals(scenario)) {
            return tickInputBlocked(minecraft, snapshot, checks, screenshot);
        } else {
            return tickLocked(minecraft, snapshot, checks, screenshot, moveMouse);
        }
        return false;
    }

    private boolean tickNoTarget(Minecraft minecraft, UiSnapshot snapshot, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (phase == 4) {
            if (operation == null
                    && !observeWarning(snapshot, checks, screenshot, moveMouse, "no-target-en-us.png")) return false;
            checks.put("target-removed", true);
            if (serverStep(minecraft, player -> {
                player.level().setBlockAndUpdate(fixture.targetPosition(), Blocks.CHEST.defaultBlockState());
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        } else if (phase == 5 && recovered(snapshot)) {
            checks.put("target-restored", true);
            screenshot.accept("no-target-restored.png");
            return true;
        }
        return false;
    }

    private boolean tickInputBlocked(Minecraft minecraft, UiSnapshot snapshot, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (phase == 4 && !warningControls(minecraft, snapshot, checks, screenshot)) return false;
        if (phase == 4 && serverStep(minecraft, player -> {
            fixture.provider(player).getLogic().getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
            ((Container) player.level().getBlockEntity(fixture.targetPosition())).clearContent();
            return true;
        })) { changedAt = System.nanoTime(); phase++; }
        else if (phase == 5 && (operation != null || recovered(snapshot))) {
            checks.put("blocking-recovered", true);
            if (operation == null) screenshot.accept("input-blocked-recovered.png");
            if (serverStep(minecraft, player -> {
                var target = (Container) player.level().getBlockEntity(fixture.targetPosition());
                long pending = pendingInput(fixture.provider(player).getLogic());
                System.out.println("AE2CT input-fixture pending=" + pending + " slots=" + target.getContainerSize());
                for (int slot = 0; slot < target.getContainerSize(); slot++) {
                    target.setItem(slot, new ItemStack(Items.COBBLESTONE,
                            occupiedSlotCount(slot, target.getContainerSize(), pending)));
                }
                return true;
            })) phase++;
        } else if (phase == 6 && (operation != null || hasWarning(snapshot))) {
            checks.put("zero-insertion", true);
            if (operation == null) screenshot.accept("input-blocked-zero-insertion.png");
            if (serverStep(minecraft, player -> {
                ((Container) player.level().getBlockEntity(fixture.targetPosition())).setItem(0, new ItemStack(Items.COBBLESTONE, 63));
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        } else if (phase == 7 && recovered(snapshot)) {
            checks.put("partial-capacity", true);
            screenshot.accept("input-blocked-partial-capacity.png");
            return true;
        }
        return false;
    }

    private boolean warningControls(Minecraft minecraft, UiSnapshot snapshot, Map<String, Boolean> checks,
            Consumer<String> screenshot) {
        if (controlPhase == 0) {
            if (serverStep(minecraft, player -> {
                var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(fixture.provider(player).getMainNode().getGrid());
                var tick = player.level().getGameTime();
                for (var item : List.of(Items.DIAMOND, Items.EMERALD)) {
                    com.ctux.ae2craftingtime.mc1201.ProfilerBridge.start(network, this, AEItemKey.of(item), 1, tick);
                    com.ctux.ae2craftingtime.mc1201.ProfilerBridge.complete(network, this, AEItemKey.of(item), 1, tick + 20);
                }
                return true;
            })) controlPhase++;
        } else if (controlPhase == 1 || controlPhase == 4) {
            if (serverStep(minecraft, player -> {
                var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(fixture.provider(player).getMainNode().getGrid());
                var diamond = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.stats(
                        com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(network, AEItemKey.of(Items.DIAMOND)));
                var emerald = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.stats(
                        com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(network, AEItemKey.of(Items.EMERALD)));
                return diamond.isPresent() == (controlPhase == 1) && emerald.isPresent();
            })) controlPhase++;
        } else if (controlPhase == 2 || controlPhase == 3) {
            if (!hasWarning(snapshot)) return false;
            boolean reset = controlPhase == 3;
            var row = snapshot.rows().stream().filter(value -> value.outputId().equals("minecraft:diamond"))
                    .findFirst().orElseThrow();
            if (!stats.click(minecraft, snapshot, "minecraft:diamond", reset, row.craftAmount())) return false;
            checks.put(reset ? "warning-reset" : "warning-details", true);
            screenshot.accept(reset ? "input-blocked-reset.png" : "input-blocked-details.png");
            stats.next();
            controlPhase++;
        }
        return controlPhase == 5;
    }

    static int occupiedSlotCount(int slot, int slots, long pending) {
        if (slots <= 0 || slot < 0 || slot >= slots || pending < 0 || pending > (long) slots * 64) {
            throw new IllegalArgumentException("pending input exceeds fixture capacity or slot is invalid");
        }
        return 64 - (int) Math.min(64, Math.max(0, pending - (long) slot * 64));
    }

    private static long pendingInput(appeng.helpers.patternprovider.PatternProviderLogic logic) {
        try {
            var field = appeng.helpers.patternprovider.PatternProviderLogic.class.getDeclaredField("sendList");
            field.setAccessible(true);
            long pending = 0;
            for (var entry : (List<?>) field.get(logic)) {
                if (!(entry instanceof appeng.api.stacks.GenericStack stack)
                        || !stack.what().equals(AEItemKey.of(Items.COBBLESTONE)) || stack.amount() < 0) {
                    throw new IllegalStateException("unexpected queued fixture input: " + entry);
                }
                pending = Math.addExact(pending, stack.amount());
            }
            return pending;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot inspect queued fixture input", error);
        }
    }

    private boolean tickLocked(Minecraft minecraft, UiSnapshot snapshot, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        var power = fixture.targetPosition().south(2);
        if (phase == 4) {
            if (operation == null
                    && !observeWarning(snapshot, checks, screenshot, moveMouse, "locked-en-us.png")) return false;
            checks.put("lock-while-low", true);
            if (serverStep(minecraft, player -> {
                player.level().setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        }
        else if (phase == 5 && (operation != null || recovered(snapshot))) {
            checks.put("low-recovered", true);
            if (advancedFixture()) return true;
            if (serverStep(minecraft, player -> {
                fixture.provider(player).getLogic().getConfigManager().putSetting(
                        Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_HIGH);
                return true;
            })) phase++;
        } else if (phase == 6 && (operation != null || hasWarning(snapshot))) {
            checks.put("lock-while-high", true);
            if (serverStep(minecraft, player -> {
                player.level().setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        } else if (phase == 7 && (operation != null || recovered(snapshot))) {
            checks.put("high-recovered", true);
            if (serverStep(minecraft, player -> {
                fixture.provider(player).getLogic().getConfigManager().putSetting(
                        Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_PULSE);
                // Notify the provider of LOW before the later rising edge (AE2 15 caches redstone state).
                player.level().setBlockAndUpdate(power, Blocks.STONE.defaultBlockState());
                return true;
            })) phase++;
        } else if (phase == 8 && (operation != null || hasWarning(snapshot))) {
            checks.put("pulse-lock", true);
            if (serverStep(minecraft, player -> {
                var target = (Container) player.level().getBlockEntity(fixture.targetPosition());
                for (int slot = 0; slot < target.getContainerSize(); slot++) {
                    target.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
                player.level().setBlockAndUpdate(power, Blocks.REDSTONE_BLOCK.defaultBlockState());
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        } else if (phase == 9 && (operation != null || recovered(snapshot))) {
            checks.put("pulse-recovered", true);
            if (serverStep(minecraft, player -> {
                player.level().setBlockAndUpdate(power, Blocks.AIR.defaultBlockState());
                var logic = fixture.provider(player).getLogic();
                logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.NO);
                ((Container) player.level().getBlockEntity(fixture.targetPosition())).clearContent();
                logic.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
                return true;
            })) phase++;
        } else if (phase == 10 && (operation != null || hasWarning(snapshot))) {
            checks.put("result-lock", true);
            if (operation == null) screenshot.accept("locked-result-wait.png");
            if (serverStep(minecraft, player -> {
                var target = (Container) player.level().getBlockEntity(fixture.targetPosition());
                for (int slot = 0; slot < target.getContainerSize(); slot++) {
                    target.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
                fixture.provider(player).getLogic().getReturnInv().insert(AEItemKey.of(Items.DIAMOND), 1,
                        Actionable.MODULATE, IActionSource.empty());
                return true;
            })) { changedAt = System.nanoTime(); phase++; }
        } else if (phase == 11 && recovered(snapshot)) {
            checks.put("result-returned", true);
            screenshot.accept("locked-recovered.png");
            return true;
        }
        return false;
    }

    private boolean observeWarning(UiSnapshot snapshot, Map<String, Boolean> checks, Consumer<String> screenshot,
            BiConsumer<Integer, Integer> moveMouse, String screenshotName) {
        if (!hasWarning(snapshot)) return false;
        var warning = snapshot.text().stream().filter(text -> text.key().equals(key)).findFirst().orElseThrow();
        moveMouse.accept(warning.bounds().x() + warning.bounds().width() / 2,
                warning.bounds().y() + warning.bounds().height() / 2);
        if (!tooltipReady(snapshot.tooltip()) || !tooltipFrames.observe(phase)) return false;
        if (!warning.bounds().inside(snapshot.gui())
                || snapshot.badges().stream().noneMatch(badge -> warning.bounds().inside(badge))) {
            throw new IllegalStateException(key + " has no contained rendered badge");
        }
        checks.put("tooltip", true);
        if (NO_CHANNEL.equals(scenario) && (warning.bold() || warning.color() == null
                || (warning.color() & 0xffffff) != 0xff5555)) {
            throw new IllegalStateException("NO CHANNEL must render normal red");
        }
        checks.put("layout", true);
        screenshot.accept(screenshotName);
        return true;
    }

    private boolean recovered(UiSnapshot snapshot) {
        if (hasWarning(snapshot)) {
            if (changedAt != 0 && System.nanoTime() - changedAt > 5_000_000_000L) {
                throw new IllegalStateException(key + " recovery missed the next status refresh");
            }
            return false;
        }
        return true;
    }

    boolean tooltipReady(List<UiSnapshot.ObservedText> tooltip) {
        var expected = new java.util.ArrayList<>(List.of(key, key + ".explanation", key + ".suggestion"));
        if (!NO_CHANNEL.equals(scenario) && (!advancedFixture()
                || tooltip.stream().anyMatch(text -> text.key().equals(MIXED)))) {
            expected.add(MIXED);
        }
        return WarningTooltipChecks.hasBodyAndControls(tooltip, expected);
    }

    private static boolean advancedFixture() {
        return Boolean.getBoolean("ae2craftingtime.test.advancedStatus");
    }

    private boolean hasWarning(UiSnapshot snapshot) {
        return snapshot.text().stream().anyMatch(text -> text.key().equals(key));
    }

    private boolean serverStep(Minecraft minecraft, Function<ServerPlayer, Boolean> action) {
        if (operation == null) {
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            operation = server.submit(() -> action.apply(server.getPlayerList().getPlayer(playerId)));
        }
        if (!operation.isDone()) return false;
        var done = operation.join();
        operation = null;
        return done;
    }

    String cleanup(Minecraft minecraft) {
        if (!NO_CHANNEL.equals(scenario)) return null;
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) return "channel fixture cleanup has no integrated player/server";
        var playerId = minecraft.player.getUUID();
        try {
            server.submit(() -> fixture.restoreChannelMode(server.getPlayerList().getPlayer(playerId)))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return "channel fixture cleanup failed: " + ReportText.failure(error);
        }
    }

}
