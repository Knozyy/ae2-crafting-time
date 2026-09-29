package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class NoSpaceScenario {
    static final String SCENARIO = "no-space-status";
    static final String KEY = "text.ae2craftingtime.no_space";
    static final List<String> CHECKS = List.of("screen", "external-machine", "warning", "tooltip", "layout",
            "details-ignored", "reset-ignored", "recovered");
    private BlockPos cpuPosition;
    private int phase;
    private CompletableFuture<Boolean> operation;
    private int menuId;
    private final StableFrames<Integer> frames = new StableFrames<>(3);
    private final StableFrames<Integer> tooltipFrames = new StableFrames<>(3);
    private final StatsInteraction stats = new StatsInteraction();

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (phase < 2) {
            if (!serverStep(minecraft, player -> prepare(player, marker))) {
                return false;
            }
            phase++;
            return false;
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingCPUScreen<?> screen) || snapshot == null
                || !snapshot.screen().equals(screen.getClass().getName())) {
            return false;
        }
        if (!frames.observe(phase)) {
            return false;
        }
        if (phase == 2) {
            checks.put("screen", true);
            if (screen.getMenu().isCantStoreItems() || hasWarning(snapshot)) {
                throw new IllegalStateException("full external furnace triggered a storage warning");
            }
            checks.put("external-machine", true);
            screenshot.accept("no-space-before.png");
            menuId = screen.getMenu().containerId;
            phase++;
        } else if (phase == 3) {
            if (serverStep(minecraft, player -> {
                var cpu = (CraftingBlockEntity) player.level().getBlockEntity(cpuPosition);
                var network = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(cpu.getMainNode().getGrid());
                var tick = player.level().getGameTime();
                com.ctux.ae2craftingtime.mc1201.ProfilerBridge.start(network, this, AEItemKey.of(Items.FURNACE), 1, tick);
                com.ctux.ae2craftingtime.mc1201.ProfilerBridge.complete(network, this, AEItemKey.of(Items.FURNACE), 1, tick + 20);
                cpu.getCluster().craftingLogic.getInventory().insert(AEItemKey.of(Items.FURNACE), 64,
                        Actionable.MODULATE);
                return true;
            })) {
                phase++;
            }
        } else if (phase == 4) {
            if (!screen.getMenu().isCantStoreItems() || !hasWarning(snapshot)) {
                return false;
            }
            moveMouse.accept(snapshot.gui().x() + 40, snapshot.gui().y() + 30);
            if (!tooltipReady(snapshot.tooltip())) {
                return false;
            }
            if (!tooltipFrames.observe(phase)) {
                return false;
            }
            var warning = snapshot.text().stream().filter(text -> text.key().equals(KEY)).findFirst().orElseThrow();
            if (!LayoutValidator.warningBadgeValid(snapshot, warning)) {
                throw new IllegalStateException("NO SPACE has invalid warning badge layout");
            }
            checks.put("warning", true);
            checks.put("tooltip", true);
            checks.put("layout", true);
            screenshot.accept("no-space-en-us.png");
            phase++;
        } else if (phase == 5) {
            var reset = Boolean.TRUE.equals(checks.get("details-ignored"));
            if (!stats.clickWithoutStats(minecraft, snapshot, "minecraft:furnace", reset)) {
                return false;
            }
            checks.put(reset ? "reset-ignored" : "details-ignored", true);
            if (reset) {
                phase++;
            } else {
                stats.next();
            }
        } else if (phase == 6) {
            if (serverStep(minecraft, player -> {
                var cpu = (CraftingBlockEntity) player.level().getBlockEntity(cpuPosition);
                var key = com.ctux.ae2craftingtime.mc1201.ProfilerBridge.key(
                        com.ctux.ae2craftingtime.mc1201.ProfilerBridge.networkId(cpu.getMainNode().getGrid()),
                        AEItemKey.of(Items.FURNACE));
                if (com.ctux.ae2craftingtime.mc1201.ProfilerBridge.stats(key).isEmpty()) {
                    throw new IllegalStateException("Stored-only NO SPACE reset its retained sample");
                }
                var drive = (DriveBlockEntity) player.level().getBlockEntity(cpuPosition.east(2));
                drive.getInternalInventory().setItemDirect(0, cell());
                return true;
            })) {
                phase++;
            }
        } else if (phase == 7 && !screen.getMenu().isCantStoreItems() && !hasWarning(snapshot)) {
            if (screen.getMenu().containerId != menuId) {
                throw new IllegalStateException("recovery reopened the menu");
            }
            checks.put("recovered", true);
            screenshot.accept("no-space-recovered.png");
            return true;
        }
        return false;
    }

    static boolean tooltipReady(List<UiSnapshot.ObservedText> tooltip) {
        return WarningTooltipChecks.hasBodyAndControls(tooltip,
                List.of(KEY, KEY + ".explanation", KEY + ".suggestion"));
    }

    private static boolean hasWarning(UiSnapshot snapshot) {
        return snapshot.text().stream().anyMatch(text -> text.key().equals(KEY));
    }

    private boolean serverStep(Minecraft minecraft, java.util.function.Function<ServerPlayer, Boolean> action) {
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

    private boolean prepare(ServerPlayer player, FixtureMarker marker) {
        var level = player.level();
        if (phase == 0) {
            cpuPosition = new BlockPos(marker.terminal().x() + 40, marker.terminal().y(), marker.terminal().z());
            for (var pos : BlockPos.betweenClosed(cpuPosition.offset(-2, -1, -2), cpuPosition.offset(4, 3, 2))) {
                level.setBlockAndUpdate(pos, pos.getY() < cpuPosition.getY()
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            place(player, cpuPosition, "1k_crafting_storage");
            place(player, cpuPosition.east(), "creative_energy_cell");
            place(player, cpuPosition.east(2), "drive");
            level.setBlockAndUpdate(cpuPosition.north(), Blocks.FURNACE.defaultBlockState());
            ((FurnaceBlockEntity) level.getBlockEntity(cpuPosition.north())).setItem(2, new ItemStack(Items.STONE, 64));
            player.teleportTo(cpuPosition.getX() + 0.5, cpuPosition.getY(), cpuPosition.getZ() + 2.5);
            return true;
        }
        var cpu = (CraftingBlockEntity) level.getBlockEntity(cpuPosition);
        var drive = (DriveBlockEntity) level.getBlockEntity(cpuPosition.east(2));
        if (!cpu.getMainNode().isReady() || !drive.getMainNode().isReady()) {
            return false;
        }
        if (!cpu.isFormed()) {
            var calculator = new CraftingCPUCalculator(cpu);
            calculator.updateBlockEntities(calculator.createCluster(level, cpuPosition, cpuPosition),
                    level, cpuPosition, cpuPosition);
        }
        var cpuNode = cpu.getMainNode().getNode();
        for (var pos : List.of(cpuPosition.east(), cpuPosition.east(2))) {
            var host = (IInWorldGridNodeHost) level.getBlockEntity(pos);
            var node = host.getGridNode(Direction.UP);
            if (node == null) {
                return false;
            }
            if (node.getGrid() != cpuNode.getGrid()) {
                GridHelper.createConnection(cpuNode, node);
            }
        }
        if (!cpu.getCluster().isActive() || !drive.isPowered()) {
            return false;
        }
        drive.getInternalInventory().setItemDirect(0, cell());
        var inventory = drive.getCellInventory(0);
        inventory.insert(AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        if (inventory.insert(AEItemKey.of(Items.FURNACE), 1, Actionable.SIMULATE, IActionSource.empty()) != 0) {
            throw new IllegalStateException("fixture cell still accepts CPU contents");
        }
        MenuOpener.open(CraftingCPUMenu.TYPE, player, MenuLocators.forBlockEntity(cpu));
        return true;
    }

    private static ItemStack cell() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.tryParse("ae2:item_storage_cell_1k")));
    }

    private static void place(ServerPlayer player, BlockPos pos, String id) {
        player.level().setBlockAndUpdate(pos,
                BuiltInRegistries.BLOCK.getValue(Identifier.tryParse("ae2:" + id)).defaultBlockState());
    }
}
