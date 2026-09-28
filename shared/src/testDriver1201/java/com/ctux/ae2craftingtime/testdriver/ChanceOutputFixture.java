package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Disposable native AE2 job against the pinned Mekanism acacia hanging-sign sawing recipe. */
final class ChanceOutputFixture {
    static final int PROMISED = 100;
    static final int RETURNED = 60;
    static final int REMAINING = PROMISED - RETURNED;
    private BlockPos cpuPos;
    private Future<ICraftingPlan> calculation;
    private boolean submitted;
    private boolean returned;
    private volatile String dispatchDiagnostic = "not sampled";

    String dispatchDiagnostic() { return dispatchDiagnostic; }

    AEItemKey output() {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("mekanism:sawdust"));
        if (item == Items.AIR) throw new IllegalStateException("Mekanism sawdust is missing");
        return AEItemKey.of(item);
    }

    private AEItemKey mainOutput() { return AEItemKey.of(Items.ACACIA_PLANKS); }

    CraftingBlockEntity cpu(ServerPlayer player) {
        return (CraftingBlockEntity) player.serverLevel().getBlockEntity(cpuPos);
    }

    PatternProviderBlockEntity provider(ServerPlayer player) {
        return (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(cpuPos.east(6));
    }

    boolean prepare(int phase, ServerPlayer player, FixtureMarker marker) {
        if (!ServerDriverPlatform.isModLoaded("mekanism"))
            throw new IllegalStateException("Chance output fixture requires Mekanism");
        var level = player.serverLevel();
        if (phase == 0) {
            cpuPos = new BlockPos(marker.terminal().x() + 80, marker.terminal().y(), marker.terminal().z());
            for (var pos : BlockPos.betweenClosed(cpuPos.offset(-2, -1, -2), cpuPos.offset(8, 3, 2)))
                level.setBlockAndUpdate(pos, pos.getY() < cpuPos.getY()
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            DispatchStatusFixture.place(player, cpuPos, "64k_crafting_storage");
            DispatchStatusFixture.place(player, cpuPos.east(2), "creative_energy_cell");
            DispatchStatusFixture.place(player, cpuPos.east(4), "drive");
            var providerPos = cpuPos.east(6);
            DispatchStatusFixture.place(player, providerPos, "pattern_provider");
            level.setBlockAndUpdate(providerPos,
                    level.getBlockState(providerPos).setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.DOWN));
            var sawmill = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse("mekanism:precision_sawmill"));
            if (sawmill == Blocks.AIR) throw new IllegalStateException("Mekanism precision sawmill is missing");
            level.setBlockAndUpdate(providerPos.below(), sawmill.defaultBlockState());
            player.teleportTo(cpuPos.getX() + 0.5, cpuPos.getY(), cpuPos.getZ() + 2.5);
            return true;
        }
        var cpu = cpu(player);
        if (phase == 1) {
            if (!cpu.getMainNode().isReady()) return false;
            if (!cpu.isFormed()) {
                var calculator = new CraftingCPUCalculator(cpu);
                calculator.updateBlockEntities(calculator.createCluster(level, cpuPos, cpuPos), level, cpuPos, cpuPos);
            }
            for (var offset : List.of(2, 4, 6)) {
                var node = ((IInWorldGridNodeHost) level.getBlockEntity(cpuPos.east(offset))).getGridNode(Direction.UP);
                if (node == null) return false;
                if (node.getGrid() != cpu.getMainNode().getGrid())
                    GridHelper.createConnection(cpu.getMainNode().getNode(), node);
            }
            if (!cpu.getCluster().isActive() || !provider(player).getMainNode().isActive()) return false;
            if (calculation == null) {
                var drive = (DriveBlockEntity) level.getBlockEntity(cpuPos.east(4));
                drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
                var input = AEItemKey.of(Items.ACACIA_HANGING_SIGN);
                if (drive.getCellInventory(0).insert(input, PROMISED, Actionable.MODULATE, IActionSource.empty()) != PROMISED)
                    throw new IllegalStateException("Could not store 100 hanging signs");
                provider(player).getLogic().getPatternInv().setItemDirect(0,
                        ServerDriverPlatform.processingPattern(List.of(new GenericStack(input, 1)),
                                List.of(new GenericStack(output(), 1), new GenericStack(mainOutput(), 2))));
                provider(player).getLogic().updatePatterns();
                calculation = cpu.getMainNode().getGrid().getCraftingService().beginCraftingCalculation(level,
                        () -> IActionSource.ofMachine(cpu), output(), PROMISED,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            return true;
        }
        if (!calculation.isDone()) return false;
        if (!submitted) {
            try {
                var plan = calculation.get();
                if (plan.simulation()) {
                    var missing = new ArrayList<String>();
                    for (var entry : plan.missingItems())
                        missing.add(entry.getKey().getId() + "=" + entry.getLongValue());
                    throw new IllegalStateException("Sawmill plan is incomplete: missing=" + missing);
                }
                var result = cpu.getMainNode().getGrid().getCraftingService().submitJob(plan, null,
                        cpu.getCluster(), false, IActionSource.ofMachine(cpu));
                if (!result.successful()) throw new IllegalStateException("Sawmill job rejected: " + result);
            } catch (Exception error) {
                throw new IllegalStateException("Sawmill job calculation failed", error);
            }
            submitted = true;
        }
        MenuOpener.open(CraftingCPUMenu.TYPE, player, MenuLocators.forBlockEntity(cpu));
        return true;
    }

    boolean dispatched(ServerPlayer player) {
        var cpu = cpu(player);
        var chance = ProfilerBridge.chanceOutput(cpu.getCluster(),
                ProfilerBridge.key(ProfilerBridge.networkId(cpu.getMainNode().getGrid()), output()));
        var waiting = cpu.getCluster().craftingLogic.getWaitingFor(output());
        int input = 0;
        for (var slot : sawmillSlots(player.serverLevel().getBlockEntity(cpuPos.east(6).below()))) {
            var stack = stack(slot);
            if (stack.is(Items.ACACIA_HANGING_SIGN)) input += stack.getCount();
        }
        dispatchDiagnostic = "waitingSawdust=" + waiting + " chanceBasisPoints="
                + (chance.isPresent() ? chance.getAsInt() : "absent") + " sawmillInput=" + input;
        if (chance.isEmpty() || chance.getAsInt() != 5000) return false;
        if (waiting < PROMISED) drainSawmillInput(player);
        return waiting == PROMISED;
    }

    boolean returnControlledOutput(ServerPlayer player) {
        if (returned) return true;
        if (!dispatched(player)) return false;
        var main = provider(player).getLogic().getReturnInv().insert(mainOutput(), PROMISED * 2L,
                Actionable.MODULATE, IActionSource.empty());
        if (main != PROMISED * 2L) throw new IllegalStateException("Could not return 200 planks, accepted " + main);
        var accepted = provider(player).getLogic().getReturnInv().insert(output(), RETURNED,
                Actionable.MODULATE, IActionSource.empty());
        if (accepted != RETURNED) throw new IllegalStateException("Could not return 60 sawdust, accepted " + accepted);
        returned = true;
        return true;
    }

    private void drainSawmillInput(ServerPlayer player) {
        // The machine stays unpowered: clear accepted inputs so all 100 real AE2 pushes can be observed.
        var tile = player.serverLevel().getBlockEntity(cpuPos.east(6).below());
        try {
            for (var slot : sawmillSlots(tile)) {
                var stack = stack(slot);
                if (stack.is(Items.ACACIA_HANGING_SIGN))
                    slot.getClass().getMethod("setStack", ItemStack.class).invoke(slot, ItemStack.EMPTY);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot drain the controlled sawmill input", error);
        }
    }

    void cancel(ServerPlayer player) {
        cpu(player).getCluster().craftingLogic.cancel();
    }

    void powerOneRealOperation(ServerPlayer player) {
        var tile = player.serverLevel().getBlockEntity(cpuPos.east(6).below());
        try {
            for (var slot : sawmillSlots(tile)) {
                var stack = stack(slot);
                if (stack.is(Items.ACACIA_HANGING_SIGN))
                    slot.getClass().getMethod("setStack", ItemStack.class)
                            .invoke(slot, new ItemStack(Items.ACACIA_HANGING_SIGN));
            }
            var energy = tile.getClass().getMethod("getEnergyContainer").invoke(tile);
            var maximum = energy.getClass().getMethod("getMaxEnergy").invoke(energy);
            var setter = java.util.Arrays.stream(energy.getClass().getMethods())
                    .filter(method -> method.getName().equals("setEnergy") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isInstance(maximum))
                    .findFirst().orElseThrow();
            setter.invoke(energy, maximum);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot power the real sawmill recipe", error);
        }
    }

    boolean realRecipeProcessed(ServerPlayer player) {
        var tile = player.serverLevel().getBlockEntity(cpuPos.east(6).below());
        int input = 0, planks = 0, sawdust = 0;
        for (var slot : sawmillSlots(tile)) {
            var stack = stack(slot);
            if (stack.is(Items.ACACIA_HANGING_SIGN)) input += stack.getCount();
            if (stack.is(Items.ACACIA_PLANKS)) planks += stack.getCount();
            if (stack.getItem() == output().getItem()) sawdust += stack.getCount();
        }
        if (planks > 2 || sawdust > 1) throw new IllegalStateException("More than one real sawmill operation occurred");
        if (input != 0 || planks != 2) return false;
        System.out.println("AE2CT real Mekanism sawing completed: acacia sign=1, planks=2, sawdust=" + sawdust);
        return true;
    }

    private static List<?> sawmillSlots(Object tile) {
        try {
            return (List<?>) tile.getClass().getMethod("getInventorySlots", Direction.class)
                    .invoke(tile, (Direction) null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the sawmill inventory", error);
        }
    }

    private static ItemStack stack(Object slot) {
        try {
            return (ItemStack) slot.getClass().getMethod("getStack").invoke(slot);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the sawmill slot", error);
        }
    }
}
