package com.ctux.ae2craftingtime.testdriver;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

final class NoProviderScenario {
    static final String SCENARIO = "no-provider-status";
    static final String KEY = "text.ae2craftingtime.no_provider";
    static final List<String> CHECKS = List.of("screen", "real-job", "mixed-row", "pattern-removed", "tooltip",
            "layout", "pattern-restored", "second-provider", "provider-removed",
            "provider-restored", "cancelled");
    private final DispatchStatusFixture fixture = new DispatchStatusFixture(1);
    private int phase;
    private int menuId;
    private long changedAt;
    private CompletableFuture<Boolean> operation;
    private final StableFrames<Integer> frames = new StableFrames<>(3);
    private final StableFrames<Integer> tooltipFrames = new StableFrames<>(3);

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (phase < 3) {
            if (serverStep(minecraft, player -> fixture.prepare(phase, player, marker))) {
                phase++;
            }
            return false;
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingCPUScreen<?> screen) || snapshot == null
                || !snapshot.screen().equals(screen.getClass().getName()) || !frames.observe(phase)) {
            return false;
        }
        if (phase == 3) {
            checks.put("screen", true);
            checks.put("real-job", true);
            checks.put("mixed-row", true);
            if (hasWarning(snapshot)) {
                throw new IllegalStateException("connected provider triggered NO PROVIDER");
            }
            screenshot.accept("no-provider-before.png");
            menuId = screen.getMenu().containerId;
            phase++;
        } else if (phase == 4) {
            if (serverStep(minecraft, player -> {
                fixture.provider(player, 6).getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
                return true;
            })) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 5) {
            if (!hasWarning(snapshot)) {
                return false;
            }
            moveMouse.accept(snapshot.gui().x() + 40, snapshot.gui().y() + 30);
            if (!tooltipReady(snapshot.tooltip()) || !tooltipFrames.observe(phase)) {
                return false;
            }
            var warning = snapshot.text().stream().filter(text -> text.key().equals(KEY)).findFirst().orElseThrow();
            if (!LayoutValidator.warningBadgeValid(snapshot, warning)) {
                throw new IllegalStateException("NO PROVIDER has invalid warning badge layout");
            }
            checks.put("pattern-removed", true);
            checks.put("tooltip", true);
            checks.put("layout", true);
            screenshot.accept("no-provider-en-us.png");
            phase = 7;
        } else if (phase == 7) {
            if (serverStep(minecraft, player -> {
                fixture.provider(player, 6).getLogic().getPatternInv().setItemDirect(0, fixture.pattern());
                return true;
            })) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 8 || phase == 12) {
            if (hasWarning(snapshot)) {
                if (System.nanoTime() - changedAt > 2_000_000_000L) {
                    throw new IllegalStateException("provider recovery did not reach the next status refresh");
                }
                return false;
            }
            if (screen.getMenu().containerId != menuId) {
                throw new IllegalStateException("recovery reopened the menu");
            }
            checks.put(phase == 8 ? "pattern-restored" : "provider-restored", true);
            screenshot.accept(phase == 8 ? "no-provider-pattern-restored.png" : "no-provider-block-restored.png");
            phase++;
        } else if (phase == 9) {
            if (serverStep(minecraft, player -> {
                fixture.provider(player, 8).getLogic().getPatternInv().setItemDirect(0, fixture.pattern());
                fixture.provider(player, 6).getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
                return true;
            })) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 10) {
            if (hasWarning(snapshot)) {
                throw new IllegalStateException("second connected provider did not prevent warning");
            }
            if (System.nanoTime() - changedAt < 2_000_000_000L) {
                return false;
            }
            if (!checks.get("second-provider")) {
                screenshot.accept("no-provider-redundant.png");
                checks.put("second-provider", true);
            }
            if (serverStep(minecraft, player -> {
                player.level().setBlockAndUpdate(fixture.cpuPosition.east(8), Blocks.AIR.defaultBlockState());
                return true;
            })) {
                phase++;
            }
        } else if (phase == 11) {
            if (!hasWarning(snapshot)) {
                return false;
            }
            if (!checks.get("provider-removed")) {
                screenshot.accept("no-provider-block-removed.png");
                checks.put("provider-removed", true);
            }
            if (serverStep(minecraft, player -> restoreProvider(player))) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 13) {
            if (serverStep(minecraft, player -> {
                var cpu = fixture.cpu(player);
                cpu.getCluster().craftingLogic.cancel();
                return ProfilerBridge.missingProviders(cpu.getCluster(), cpu.getMainNode().getGrid()).isEmpty();
            })) {
                changedAt = System.nanoTime();
                phase++;
            }
        } else if (phase == 14 && !hasWarning(snapshot) && System.nanoTime() - changedAt > 1_000_000_000L) {
            checks.put("cancelled", true);
            screenshot.accept("no-provider-cancelled.png");
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
        if (!operation.isDone()) {
            return false;
        }
        var done = operation.join();
        operation = null;
        return done;
    }

    private boolean restoreProvider(ServerPlayer player) {
        if (!(player.level().getBlockEntity(fixture.cpuPosition.east(8)) instanceof PatternProviderBlockEntity)) {
            fixture.place(player, fixture.cpuPosition.east(8), "pattern_provider");
            return false;
        }
        if (!fixture.connect(player, 8)) {
            return false;
        }
        fixture.provider(player, 8).getLogic().getPatternInv().setItemDirect(0, fixture.pattern());
        return true;
    }

}
