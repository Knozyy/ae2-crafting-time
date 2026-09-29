package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import com.ctux.ae2craftingtime.core.CraftingBlockReason;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class NoPowerScenario {
    static final String SCENARIO = "no-power-status";
    static final String KEY = "text.ae2craftingtime.no_power";
    static final List<String> CHECKS = List.of("screen", "real-job", "external-unpowered", "active-network",
            "mixed-row", "tooltip", "layout", "power-restored", "cancelled", "inactive-cpu");
    private final DispatchStatusFixture fixture = new DispatchStatusFixture(64);
    private final StableFrames<Integer> frames = new StableFrames<>(3);
    private final StableFrames<Integer> tooltipFrames = new StableFrames<>(3);
    private int phase;
    private int loggedPhase = -1;
    private long loggedTick = -1;
    private int menuId;
    private long changedAt;
    private String recoveryState;
    private long recoveredActiveAmount;
    private long powerRestoredTick;
    private long recoveryResolvedAt;
    private CompletableFuture<Boolean> operation;
    private CompletableFuture<Void> powerPulse;

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (loggedPhase != phase) {
            System.out.println("AE2CT power fixture phase " + phase);
            loggedPhase = phase;
        }
        if (phase < 3) {
            if (serverStep(minecraft, player -> {
                if (phase == 1) {
                    var grid = fixture.cpu(player).getMainNode().getGrid();
                    if (grid != null) grid.getEnergyService().injectPower(100_000, Actionable.MODULATE);
                }
                var ready = fixture.prepare(phase, player, marker);
                if (ready && phase == 0) {
                    DispatchStatusFixture.place(player, fixture.cpuPosition.east(2), "energy_cell");
                    player.level().setBlockAndUpdate(fixture.cpuPosition.east(6).north(), Blocks.AIR.defaultBlockState());
                    player.level().setBlockAndUpdate(fixture.cpuPosition.east(6).below(),
                            Blocks.FURNACE.defaultBlockState());
                }
                return ready;
            })) {
                phase++;
                changedAt = System.nanoTime();
            }
            return false;
        }
        // Real grid energy only: keep idle demand supplied while the 64-AE dispatch cannot fit.
        if (phase >= 5 && phase <= 7 && (powerPulse == null || powerPulse.isDone())) {
            if (powerPulse != null) powerPulse.join();
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            powerPulse = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var cpu = fixture.cpu(player).getCluster();
                var energy = cpu.getGrid().getEnergyService();
                var tick = player.level().getGameTime();
                if (tick - loggedTick >= 20) {
                    System.out.println("AE2CT power fixture: idle=" + energy.getIdlePowerUsage()
                            + ", simulated=" + energy.extractAEPower(64, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                            + ", reasons=" + ProfilerBridge.blockReasons(cpu, cpu.getGrid(), tick)
                            + ", menuCpu=" + (com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(player).craftingCpu() == cpu)
                            + ", active=" + cpu.isActive() + ", providerBusy=" + fixture.provider(player, 6).getLogic().isBusy());
                    loggedTick = tick;
                }
                energy.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                energy.injectPower(PowerMultiplier.CONFIG.multiply(48), Actionable.MODULATE);
                if (energy.getIdlePowerUsage() >= 48) {
                    throw new IllegalStateException("fixture idle demand exceeds low-power budget: " + energy.getIdlePowerUsage());
                }
            });
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingCPUScreen<?> screen) || snapshot == null
                || !snapshot.screen().equals(screen.getClass().getName()) || !frames.observe(phase)) return false;
        if (phase == 3) {
            if (hasWarning(snapshot)) throw new IllegalStateException("unfuelled furnace triggered NO POWER");
            if (System.nanoTime() - changedAt < 2_000_000_000L) return false;
            checks.put("screen", true);
            checks.put("real-job", true);
            checks.put("external-unpowered", true);
            screenshot.accept("no-power-external-unpowered.png");
            menuId = screen.getMenu().containerId;
            phase++;
        } else if (phase == 4) {
            if (serverStep(minecraft, player -> {
                var energy = fixture.cpu(player).getMainNode().getGrid().getEnergyService();
                energy.extractAEPower(Double.MAX_VALUE, Actionable.MODULATE, PowerMultiplier.ONE);
                energy.injectPower(PowerMultiplier.CONFIG.multiply(48), Actionable.MODULATE);
                if (energy.getIdlePowerUsage() >= 48) {
                    throw new IllegalStateException("fixture idle demand exceeds low-power budget: " + energy.getIdlePowerUsage());
                }
                ((Container) player.level().getBlockEntity(fixture.cpuPosition.east(6).below())).clearContent();
                return true;
            })) phase++;
        } else if (phase == 5) {
            if (!hasWarning(snapshot)) return false;
            moveMouse.accept(snapshot.gui().x() + 40, snapshot.gui().y() + 30);
            if (!tooltipReady(snapshot.tooltip()) || !tooltipFrames.observe(phase)) return false;
            var warning = snapshot.text().stream().filter(text -> text.key().equals(KEY)).findFirst().orElseThrow();
            if (!LayoutValidator.warningBadgeValid(snapshot, warning)) {
                throw new IllegalStateException("NO POWER has invalid warning badge layout");
            }
            if (!serverStep(minecraft, player -> {
                var cpu = fixture.cpu(player).getCluster();
                System.out.println("AE2CT power fixture: idle=" + cpu.getGrid().getEnergyService().getIdlePowerUsage()
                        + ", simulated=" + cpu.getGrid().getEnergyService().extractAEPower(64, Actionable.SIMULATE, PowerMultiplier.CONFIG));
                if (!cpu.isActive() || cpu.craftingLogic.getWaitingFor(AEItemKey.of(Items.DIAMOND)) != 1) {
                    throw new IllegalStateException("expected active CPU with one active and 63 pending outputs");
                }
                return true;
            })) return false;
            checks.put("active-network", true);
            checks.put("mixed-row", true);
            checks.put("tooltip", true);
            checks.put("layout", true);
            screenshot.accept("no-power-en-us.png");
            phase = 7;
        } else if (phase == 7) {
            if (!serverStep(minecraft, player -> fixture.cpu(player).getCluster().isActive())) return false;
            phase++;
        } else if (phase == 8) {
            if (powerPulse != null && !powerPulse.isDone()) return false;
            if (serverStep(minecraft, player -> {
                var energy = fixture.cpu(player).getMainNode().getGrid().getEnergyService();
                energy.injectPower(100_000, Actionable.MODULATE);
                powerRestoredTick = player.level().getGameTime();
                System.out.println("AE2CT power restored: active=" + fixture.cpu(player).getCluster().isActive()
                        + ", simulated=" + energy.extractAEPower(64, Actionable.SIMULATE, PowerMultiplier.CONFIG));
                return true;
            })) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 9) {
            if (!serverStep(minecraft, player -> {
                var cpu = fixture.cpu(player).getCluster();
                recoveredActiveAmount = cpu.craftingLogic.getWaitingFor(AEItemKey.of(Items.DIAMOND));
                var tick = player.level().getGameTime();
                var reasons = ProfilerBridge.blockReasons(cpu, cpu.getGrid(), tick);
                var output = ProfilerBridge.key(ProfilerBridge.networkId(cpu.getGrid()), AEItemKey.of(Items.DIAMOND));
                var reason = reasons.get(output);
                var energy = cpu.getGrid().getEnergyService();
                recoveryState = "server=" + reasons
                        + ", tickDelta=" + (tick - powerRestoredTick)
                        + ", active=" + cpu.isActive()
                        + ", energy=" + energy.extractAEPower(64, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                        + ", idle=" + energy.getIdlePowerUsage()
                        + ", waiting=" + recoveredActiveAmount;
                if (reason == CraftingBlockReason.NO_POWER && tick - powerRestoredTick >= 20) {
                    throw new IllegalStateException("power blocker survived its 20-tick expiry after restoration: "
                            + recoveryState);
                }
                if (reason != CraftingBlockReason.NO_POWER && recoveredActiveAmount > 1 && recoveryResolvedAt == 0) {
                    recoveryResolvedAt = System.nanoTime();
                }
                return true;
            })) return false;
            if (hasWarning(snapshot)) {
                if (recoveryResolvedAt != 0 && System.nanoTime() - recoveryResolvedAt > 2_000_000_000L) throw new IllegalStateException("power recovery missed refresh: " + recoveryState
                        + ", client=" + com.ctux.ae2craftingtime.mc1201.ClientStats.blockReason(
                                new com.ctux.ae2craftingtime.core.ProfileKey("minecraft:diamond")));
                return false;
            }
            if (screen.getMenu().containerId != menuId) throw new IllegalStateException("recovery reopened menu");
            if (recoveredActiveAmount <= 1 || recoveryResolvedAt == 0) return false;
            checks.put("power-restored", true);
            screenshot.accept("no-power-restored.png");
            phase++;
        } else if (phase == 10) {
            if (serverStep(minecraft, player -> {
                var cpu = fixture.cpu(player);
                cpu.getCluster().craftingLogic.cancel();
                var grid = cpu.getMainNode().getGrid();
                var output = ProfilerBridge.key(ProfilerBridge.networkId(grid), AEItemKey.of(Items.DIAMOND));
                var clear = ProfilerBridge.blockReasons(cpu.getCluster(), grid,
                        player.level().getGameTime()).get(output) == null;
                cpu.getMainNode().getGrid().getEnergyService().extractAEPower(Double.MAX_VALUE,
                        Actionable.MODULATE, PowerMultiplier.ONE);
                return clear;
            })) {
                checks.put("cancelled", true);
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 11) {
            if (hasWarning(snapshot)) throw new IllegalStateException("inactive CPU triggered NO POWER");
            if (System.nanoTime() - changedAt < 2_000_000_000L) return false;
            if (!serverStep(minecraft, player -> !fixture.cpu(player).getCluster().isActive())) return false;
            checks.put("inactive-cpu", true);
            screenshot.accept("no-power-inactive.png");
            return true;
        }
        return false;
    }

    static boolean tooltipReady(List<UiSnapshot.ObservedText> tooltip) {
        return List.of(KEY, KEY + ".explanation", KEY + ".suggestion").stream()
                .allMatch(key -> tooltip.stream().anyMatch(text -> text.key().equals(key)));
    }

    private static boolean hasWarning(UiSnapshot snapshot) {
        return snapshot.text().stream().anyMatch(text -> text.key().equals(KEY));
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
}
