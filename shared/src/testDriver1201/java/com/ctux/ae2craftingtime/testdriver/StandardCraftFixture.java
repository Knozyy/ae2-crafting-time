package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEParts;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import com.ctux.ae2craftingtime.mc1201.ProfilerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

final class StandardCraftFixture {
    static final int QUANTITY_CASES = 10;

    record AddonQuantityCase(String name, appeng.api.stacks.AEKey key, long stored, long active, long pending) {}

    static java.util.List<AddonQuantityCase> addonQuantityCases() {
        var cases = new java.util.ArrayList<AddonQuantityCase>();
        if (DriverPlatform.isModLoaded("appbot")) {
            try {
                var key = (appeng.api.stacks.AEKey) Class.forName("appbot.ae2.ManaKey").getField("KEY").get(null);
                long unit = key.getAmountPerUnit();
                cases.add(new AddonQuantityCase("mana", key, Math.multiplyExact(unit, 4),
                        Math.multiplyExact(unit, 10), Math.multiplyExact(unit, 200)));
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Installed Applied Botanics mana key is unavailable", error);
            }
        }
        if (DriverPlatform.isModLoaded("appmek")) {
            try {
                var keys = (java.util.List<?>) Class.forName("com.ctux.ae2craftingtime.testdriver.AppliedMekanisticsFixture")
                        .getDeclaredMethod("resourceKeys", String.class).invoke(null, "OXYGEN");
                cases.add(new AddonQuantityCase("chemical", (appeng.api.stacks.AEKey) keys.get(0), 4, 10, 200));
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Installed Applied Mekanistics chemical key is unavailable", error);
            }
        }
        return java.util.List.copyOf(cases);
    }

    static appeng.menu.me.crafting.CraftingStatus addonQuantityStatus(AddonQuantityCase addon) {
        return new appeng.menu.me.crafting.CraftingStatus(true, 0, 0, 0,
                java.util.List.of(new appeng.menu.me.crafting.CraftingStatusEntry(900010L, addon.key(),
                        addon.stored(), addon.active(), addon.pending())));
    }

    static appeng.menu.me.crafting.CraftingStatus quantityStatus(int index) {
        long[][] amounts = {{4, 10, 200}, {0, 10, 200}, {10, 0, 200}, {4, 10, 0},
                {10, 0, 0}, {0, 10, 0}, {0, 0, 10}, {0, 0, 0},
                {1_000_000_000L, 2_000_000_000L, 3_000_000_000L}, {500, 1500, 2500}};
        var values = amounts[index];
        var key = index == 9 ? appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER)
                : AEItemKey.of(Items.STONE);
        return new appeng.menu.me.crafting.CraftingStatus(true, 0, 0, 0,
                java.util.List.of(new appeng.menu.me.crafting.CraftingStatusEntry(900000L + index, key,
                        values[0], values[1], values[2])));
    }

    BlockPos terminal;
    private boolean initialized;
    boolean returnedStone;
    boolean holdFinalOutput;
    boolean missingPlanInput;
    boolean unprofiledPlan;
    boolean cpuListScenario;
    boolean recurrentPlan;
    boolean resourceFixture;
    boolean storedVariantPlan;
    boolean suspensionScenario;
    private java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> suspensionLargePlan;
    private java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> suspensionSmallPlan;
    private boolean suspensionLargeSubmitted;
    private boolean suspensionSmallSubmitted;
    private int suspensionLargeAmount = 64;
    private long suspensionArchivedOutput;
    private java.util.List<java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan>> cpuListPlans;
    private boolean cpuListSubmitted;
    private java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan> replacementPlan;
    private boolean replacementSubmitted;
    /** Stable identities survive removal so later actions never reinterpret shifted fixture slots. */
    private java.util.List<CraftingBlockEntity> cpuListIdentities;
    private int sampleMultiplier = 1;
    private boolean restarting;
    private int originShift;
    private int cpuCount = 8;
    private int busyCpuCount = 5;
    private int[] initialSamples;
    private String delayedRelease;
    private boolean preparedFinalOutput;
    String checkpoint = "new";

    void bindTerminal(BlockPos value) { terminal = value; }
    void configureResourceFixture() {
        resourceFixture = true;
        // Production lifecycle checks must leave the always-loaded spawn area.
        originShift = Boolean.getBoolean("ae2craftingtime.test.resourceFixtureOnly") ? 0 : 1024;
        unprofiledPlan = true;
        holdFinalOutput = true;
        cpuListScenario = true;
        cpuCount = 2;
        busyCpuCount = 2;
    }
    void configureSuspension() {
        suspensionScenario = true;
        cpuListScenario = true;
        cpuCount = 2;
        busyCpuCount = 2;
        unprofiledPlan = true;
        originShift = 1024;
    }

    void enableSuspension(ServerPlayer player) {
        var feature = com.ctux.ae2craftingtime.core.OptionFeature.CRAFTING_SUSPENSION;
        if (com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.enabled(feature)) return;
        var config = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().copy();
        config.features().setEnabled(feature, true);
        try {
            com.ctux.ae2craftingtime.core.ServerConfigFile.save(player.server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("serverconfig/ae2craftingtime-server.toml"), config);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not enable suspension in the fixture world", error);
        }
        com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current().features().setEnabled(feature, true);
        for (var connected : player.server.getPlayerList().getPlayers())
            com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.sendTo(connected);
    }
    void refreshCpuIdentities() { cpuListIdentities = null; }
    void refreshCpuIdentities(ServerPlayer player) {
        var previous = cpuListIdentities;
        cpuListIdentities = null;
        var current = cpuListCpus(player);
        if (previous != null) {
            for (var currentCpu : current) {
                previous.stream().filter(oldCpu -> oldCpu.getBlockPos().equals(currentCpu.getBlockPos()))
                        .findFirst().ifPresent(oldCpu -> ProfilerBridge.rebindJobEstimate(
                                oldCpu.getCluster(), currentCpu.getCluster()));
            }
        }
        cpuListIdentities = current;
    }

    boolean prepare(ServerPlayer player, FixtureMarker marker) {
        var level = player.serverLevel();
        if (terminal == null) {
            checkpoint = "placing";
            terminal = new BlockPos(marker.terminal().x() + 60, marker.terminal().y(), marker.terminal().z() + originShift);
            for (var pos : BlockPos.betweenClosed(terminal.offset(cpuListScenario ? -(cpuCount * 2) : -3, -2, -3),
                    terminal.offset(cpuListScenario ? 13 : 9, 3, 3))) {
                level.setBlockAndUpdate(pos, pos.getY() == terminal.getY() - 2
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            PartHelper.setPart(level, terminal, null, player,
                    AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
            PartHelper.setPart(level, terminal, Direction.NORTH, player, AEParts.CRAFTING_TERMINAL.asItem());
            DispatchStatusFixture.place(player, terminal.west(2), resourceFixture ? "256k_crafting_storage" : "16k_crafting_storage");
            if (cpuListScenario) {
                for (int index = 1; index < cpuCount; index++)
                    DispatchStatusFixture.place(player, terminal.west(2 + index * 2),
                            resourceFixture ? "256k_crafting_storage" : "16k_crafting_storage");
            }
            DispatchStatusFixture.place(player, terminal.east(2), "drive");
            DispatchStatusFixture.place(player, terminal.below(), "creative_energy_cell");
            if (cpuListScenario) DispatchStatusFixture.place(player, terminal.south(2), "controller");
            for (int offset : new int[] {4, 8}) {
                DispatchStatusFixture.place(player, terminal.east(offset), "pattern_provider");
                level.setBlockAndUpdate(terminal.east(offset).below(),
                        (holdFinalOutput ? Blocks.CHEST : Blocks.FURNACE).defaultBlockState());
            }
            if (resourceFixture) {
                // Opaque roof over the real provider: the chat beam must remain visible above it.
                level.setBlockAndUpdate(terminal.east(4).above(2), Blocks.STONE.defaultBlockState());
            }
            if (cpuListScenario) {
                DispatchStatusFixture.place(player, terminal.east(12), "pattern_provider");
                level.setBlockAndUpdate(terminal.east(12).below(), Blocks.FURNACE.defaultBlockState());
            }
            if (holdFinalOutput) {
                var obstruction = terminal.east(8).north();
                PartHelper.setPart(level, obstruction, null, player, AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
                PartHelper.setPart(level, obstruction, Direction.NORTH, player, AEParts.CRAFTING_TERMINAL.asItem());
            }
            if (Boolean.getBoolean("ae2craftingtime.test.persistentFixture")) {
                var cable = AEParts.COVERED_DENSE_CABLE.item(appeng.api.util.AEColor.TRANSPARENT);
                for (int offset = 1; offset < (cpuListScenario ? cpuCount * 2 : 2); offset += 2) {
                    PartHelper.setPart(level, terminal.west(offset), null, player, cable);
                }
                for (int offset = 1; offset < (cpuListScenario ? 12 : 8); offset++) {
                    var position = terminal.east(offset);
                    if (level.isEmptyBlock(position)) PartHelper.setPart(level, position, null, player, cable);
                }
                if (cpuListScenario) PartHelper.setPart(level, terminal.south(), null, player, cable);
            }
            // Face the provider four blocks east and three south; send absolute rotation to the client.
            player.connection.teleport(terminal.getX() + 0.5, terminal.getY() - 1, terminal.getZ() - 2.5,
                    -53.13f, 2f);
            return false;
        }
        checkpoint = "terminal-node";
        var node = ((IInWorldGridNodeHost) level.getBlockEntity(
                cpuListScenario ? terminal.south(2) : terminal)).getGridNode(
                        cpuListScenario ? Direction.UP : Direction.NORTH);
        if (node == null) return false;
        checkpoint = "cpu";
        var cpu = cpu(player);
        if (!cpu.isFormed()) {
            var calculator = new CraftingCPUCalculator(cpu);
            var pos = terminal.west(2);
            calculator.updateBlockEntities(calculator.createCluster(level, pos, pos), level, pos, pos);
        }
        if (cpuListScenario) {
            var index = 0;
            for (var extra : cpuListCpus(player)) {
                if (!extra.isFormed()) {
                    var calculator = new CraftingCPUCalculator(extra);
                    var pos = terminal.west(2 + index * 2);
                    calculator.updateBlockEntities(calculator.createCluster(level, pos, pos), level, pos, pos);
                }
                extra.setName(index < 5 ? java.util.List.of("Alpha CPU",
                        "Beta CPU with a deliberately long English tooltip name", "Gamma CPU", "Delta CPU",
                        "Zulu shortest CPU").get(index)
                        : "Idle CPU " + (index + 1));
                index++;
            }
            if (index == cpuCount && cpuListIdentities == null) {
                cpuListIdentities = cpuListCpus(player);
            }
        }
        var nodes = new java.util.ArrayList<>(java.util.List.of(terminal.west(2), terminal.east(2), terminal.below(),
                terminal.east(4), terminal.east(8)));
        if (cpuListScenario) nodes.add(0, terminal);
        if (cpuListScenario) nodes.add(terminal.east(12));
        if (cpuListScenario) for (int index = 1; index < cpuCount; index++) nodes.add(terminal.west(2 + index * 2));
        for (var pos : nodes) {
            checkpoint = "node " + pos;
            var other = ((IInWorldGridNodeHost) level.getBlockEntity(pos)).getGridNode(Direction.UP);
            if (other == null) return false;
            if (node.getGrid() != other.getGrid()) GridHelper.createConnection(node, other);
        }
        checkpoint = "cpu-active";
        if (!cpu.getCluster().isActive()) return false;
        checkpoint = "patterns";
        if (!initialized) {
            var drive = (DriveBlockEntity) level.getBlockEntity(terminal.east(2));
            drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
            if (resourceFixture) ServerDriverPlatform.installResourceStorage(drive, false);
            if (!missingPlanInput) {
                drive.getCellInventory(0).insert(AEItemKey.of(suspensionScenario ? Items.RAW_IRON : Items.COBBLESTONE),
                        suspensionScenario ? 66 : cpuListScenario ? 4096 : 2, Actionable.MODULATE,
                        IActionSource.empty());
                if (cpuListScenario) drive.getCellInventory(0).insert(AEItemKey.of(Items.SAND), 256,
                        Actionable.MODULATE, IActionSource.empty());
                if (holdFinalOutput) drive.getCellInventory(0).insert(AEItemKey.of(Items.SAND), 2,
                        Actionable.MODULATE, IActionSource.empty());
            }
            if (!resourceFixture) {
                if (suspensionScenario) {
                    pattern(player, 4, Items.RAW_IRON, Items.IRON_INGOT);
                    pattern(player, 8, Items.RAW_IRON, Items.IRON_INGOT);
                    for (int offset : new int[] {4, 8}) {
                        var provider = (PatternProviderBlockEntity) level.getBlockEntity(terminal.east(offset));
                        provider.getLogic().getConfigManager().putSetting(
                                appeng.api.config.Settings.BLOCKING_MODE, appeng.api.config.YesNo.YES);
                    }
                } else {
                pattern(player, 4, 0, recurrentPlan ? Items.SMOOTH_STONE : Items.COBBLESTONE, Items.STONE);
                if (holdFinalOutput) {
                    pattern(player, 4, 1, Items.SAND, Items.GLASS);
                    pattern(player, 8, java.util.List.of(Items.STONE, Items.GLASS), Items.SMOOTH_STONE);
                } else {
                    if (storedVariantPlan) storedVariantPattern(player);
                    else pattern(player, 8, Items.STONE, Items.SMOOTH_STONE);
                }
                if (cpuListScenario) pattern(player, 12, Items.SAND, Items.GLASS);
                }
            }
            if (!unprofiledPlan && !resourceFixture) seed(player);
            initialized = true;
        }
        checkpoint = "craftable";
        return suspensionScenario ? node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.IRON_INGOT))
                : resourceFixture
                ? resourceCpus(player).size() == 2 && resourceCpus(player).stream().allMatch(candidate -> candidate.getCluster().isActive())
                : node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.SMOOTH_STONE));
    }

    void seed(ServerPlayer player) {
        seed(player, Items.STONE);
        if (holdFinalOutput) seed(player, Items.GLASS);
        seed(player, Items.SMOOTH_STONE);
    }

    void supplyPlanInput(ServerPlayer player) {
        var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
        drive.getCellInventory(0).insert(AEItemKey.of(Items.COBBLESTONE), 2, Actionable.MODULATE,
                IActionSource.empty());
    }

    void setRecurrent(ServerPlayer player, boolean value) {
        recurrentPlan = value;
        pattern(player, 4, value ? Items.SMOOTH_STONE : Items.COBBLESTONE, Items.STONE);
    }

    void seed(ServerPlayer player, net.minecraft.world.item.Item item) {
        var grid = cpu(player).getMainNode().getGrid();
        var network = ProfilerBridge.networkId(grid);
        var tick = player.serverLevel().getGameTime();
        var key = AEItemKey.of(item);
        ProfilerBridge.start(network, this, key, 1, tick);
        ProfilerBridge.complete(network, this, key, 1,
                tick + sampleMultiplier * (item == Items.STONE ? 100 : 40));
    }

    void preparePartialJob(ServerPlayer player) {
        var grid = cpu(player).getMainNode().getGrid();
        grid.getStorageService().getInventory().extract(AEItemKey.of(Items.SMOOTH_STONE),
                Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        ProfilerBridge.clearStats(ProfilerBridge.key(ProfilerBridge.networkId(grid), AEItemKey.of(Items.SMOOTH_STONE)));
        initialSamples = null;
        returnedStone = false;
    }

    private void pattern(ServerPlayer player, int offset, net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        pattern(player, offset, 0, input, output);
    }

    boolean submitSuspensionLarge(ServerPlayer player) {
        var cpus = cpuListCpus(player);
        if (cpus.size() != 2 || cpus.stream().anyMatch(cpu -> !cpu.getCluster().isActive())) return false;
        for (int offset : new int[] {4, 8}) {
            var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset));
            if (provider == null || !provider.getLogic().isBlocking()
                    || !(player.serverLevel().getBlockEntity(terminal.east(offset).below()) instanceof FurnaceBlockEntity))
                throw new IllegalStateException("Suspension needs two blocking native furnace providers");
        }
        var service = cpus.get(0).getMainNode().getGrid().getCraftingService();
        if (suspensionLargePlan == null) {
            suspensionLargePlan = service.beginCraftingCalculation(player.serverLevel(),
                    () -> IActionSource.ofMachine(cpus.get(0)), AEItemKey.of(Items.IRON_INGOT), suspensionLargeAmount,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            return false;
        }
        if (!suspensionLargePlan.isDone()) return false;
        if (!suspensionLargeSubmitted) {
            try {
                var plan = suspensionLargePlan.get();
                var result = service.submitJob(plan, null, cpus.get(0).getCluster(), false,
                        IActionSource.ofPlayer(player));
                if (!result.successful()) {
                    var raw = AEItemKey.of(Items.RAW_IRON);
                    var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
                    throw new IllegalStateException("Large suspension job rejected: " + result
                            + " simulation=" + plan.simulation() + " missing="
                            + plan.missingItems().keySet().stream()
                                    .map(key -> key + "=" + plan.missingItems().get(key)).toList()
                            + " patternCount=" + plan.patternTimes().size()
                            + " rawCell=" + drive.getCellInventory(0).getAvailableStacks().get(raw)
                            + " rawGrid=" + cpus.get(0).getMainNode().getGrid().getStorageService().getInventory()
                                    .getAvailableStacks().get(raw));
                }
            } catch (Exception failure) { throw new IllegalStateException("Large suspension plan failed", failure); }
            suspensionLargeSubmitted = true;
        }
        return cpus.get(0).getCluster().isBusy();
    }

    void beginSuspensionCase(ServerPlayer player, int amount) {
        if (amount < 1 || suspensionCpu(player, 0).isBusy())
            throw new IllegalStateException("Suspension case needs an idle standard CPU");
        var storage = suspensionCpu(player, 0).getGrid().getStorageService().getInventory();
        suspensionArchivedOutput += storage.extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                Actionable.MODULATE, IActionSource.empty());
        var raw = AEItemKey.of(Items.RAW_IRON);
        long available = storage.extract(raw, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        if (available < amount) {
            var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
            if (drive.getCellInventory(0).insert(raw, amount - available, Actionable.MODULATE,
                    IActionSource.empty()) != amount - available)
                throw new IllegalStateException("Could not stock next native suspension case");
        }
        suspensionLargeAmount = amount;
        suspensionLargePlan = null;
        suspensionLargeSubmitted = false;
    }

    long suspensionArchivedOutput() { return suspensionArchivedOutput; }

    boolean submitSuspensionSmall(ServerPlayer player) {
        var cpus = cpuListCpus(player);
        var service = cpus.get(1).getMainNode().getGrid().getCraftingService();
        if (suspensionSmallPlan == null) {
            suspensionSmallPlan = service.beginCraftingCalculation(player.serverLevel(),
                    () -> IActionSource.ofMachine(cpus.get(1)), AEItemKey.of(Items.IRON_INGOT), 2,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            return false;
        }
        if (!suspensionSmallPlan.isDone()) return false;
        if (!suspensionSmallSubmitted) {
            try {
                var result = service.submitJob(suspensionSmallPlan.get(), null, cpus.get(1).getCluster(), false,
                        IActionSource.ofPlayer(player));
                if (!result.successful()) throw new IllegalStateException("Small suspension job rejected: " + result);
            } catch (Exception failure) { throw new IllegalStateException("Small suspension plan failed", failure); }
            suspensionSmallSubmitted = true;
        }
        return cpus.get(1).getCluster().isBusy();
    }

    appeng.me.cluster.implementations.CraftingCPUCluster suspensionCpu(ServerPlayer player, int index) {
        return cpuListCpus(player).get(index).getCluster();
    }

    boolean suspensionRestoredReady(ServerPlayer player) {
        var cpus = cpuListCpus(player);
        if (cpus.size() != 2 || cpus.stream().anyMatch(cpu -> cpu.getCluster() == null
                || !cpu.getCluster().isActive() || cpu.getCluster().getGrid() == null)) return false;
        for (int offset : new int[] {4, 8}) {
            if (!(player.serverLevel().getBlockEntity(terminal.east(offset)) instanceof PatternProviderBlockEntity)
                    || !(player.serverLevel().getBlockEntity(terminal.east(offset).below()) instanceof FurnaceBlockEntity))
                return false;
        }
        return true;
    }

    long pumpSuspension(ServerPlayer player) {
        var storage = suspensionCpu(player, 0).getGrid().getStorageService().getInventory();
        for (int offset : new int[] {4, 8}) {
            var furnace = (FurnaceBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset).below());
            if (furnace.getItem(1).isEmpty()) furnace.setItem(1, new ItemStack(Items.COAL, 8));
            var output = furnace.getItem(2);
            if (output.is(Items.IRON_INGOT)) {
                var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset));
                long inserted = storage.insert(AEItemKey.of(Items.IRON_INGOT), output.getCount(), Actionable.MODULATE,
                        IActionSource.ofMachine(provider));
                furnace.removeItem(2, (int) inserted);
                furnace.setChanged();
            }
        }
        return storage.extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.empty());
    }

    record SuspensionState(String jobId, boolean busy, boolean suspended, boolean profilerSuspended, long undispatched,
            long waiting, long remaining, long networkOutput, long networkRaw,
            int furnaceInput, int furnaceOutput) { }

    SuspensionState suspensionState(ServerPlayer player, int index) {
        var cluster = suspensionCpu(player, index);
        var logic = cluster.craftingLogic;
        var tag = new net.minecraft.nbt.CompoundTag();
        try {
            // The Forge-only suspension case uses AE2 15's one-argument serializer.
            logic.getClass().getMethod("writeToNBT", net.minecraft.nbt.CompoundTag.class).invoke(logic, tag);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect the Forge crafting job", error);
        }
        var job = tag.getCompound("job");
        long undispatched = 0;
        var tasks = job.getList("tasks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < tasks.size(); i++) undispatched += tasks.getCompound(i).getLong("#craftingProgress");
        int input = 0, output = 0;
        for (int offset : new int[] {4, 8}) {
            var furnace = (FurnaceBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset).below());
            input += furnace.getItem(0).getCount();
            output += furnace.getItem(2).getCount();
        }
        var storage = cluster.getGrid().getStorageService().getInventory();
        var key = AEItemKey.of(Items.IRON_INGOT);
        var state = new SuspensionState(logic.getLastLink() == null ? "" : logic.getLastLink().getCraftingID().toString(),
                cluster.isBusy(), job.getBoolean("ae2craftingtime:suspended"), ProfilerBridge.isSuspended(cluster),
                undispatched, logic.getWaitingFor(key), job.getLong("remainingAmount"),
                storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()),
                storage.extract(AEItemKey.of(Items.RAW_IRON), Long.MAX_VALUE,
                        Actionable.SIMULATE, IActionSource.empty()), input, output);
        checkpoint = "suspension=" + state;
        return state;
    }

    private void pattern(ServerPlayer player, int offset, int slot, net.minecraft.world.item.Item input,
            net.minecraft.world.item.Item output) {
        var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset));
        provider.getLogic().getPatternInv().setItemDirect(slot, ServerDriverPlatform.processingPattern(
                new GenericStack(AEItemKey.of(input), 1), new GenericStack(AEItemKey.of(output), 1)));
        provider.getLogic().updatePatterns();
    }

    private void pattern(ServerPlayer player, int offset, java.util.List<net.minecraft.world.item.Item> inputs,
            net.minecraft.world.item.Item output) {
        var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset));
        provider.getLogic().getPatternInv().setItemDirect(0, ServerDriverPlatform.processingPattern(inputs.stream()
                .map(item -> new GenericStack(AEItemKey.of(item), 1)).toList(),
                new GenericStack(AEItemKey.of(output), 1)));
        provider.getLogic().updatePatterns();
    }

    AEItemKey storedVariantKey(int damage) {
        var stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    private void storedVariantPattern(ServerPlayer player) {
        var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(8));
        provider.getLogic().getPatternInv().setItemDirect(0, ServerDriverPlatform.processingPattern(
                java.util.List.of(new GenericStack(storedVariantKey(1), 1),
                        new GenericStack(AEItemKey.of(Items.DIRT), 1),
                        new GenericStack(appeng.api.stacks.AEFluidKey.of(
                                net.minecraft.world.level.material.Fluids.WATER), 1)),
                new GenericStack(AEItemKey.of(Items.SMOOTH_STONE), 1)));
        provider.getLogic().getPatternInv().setItemDirect(1, ServerDriverPlatform.processingPattern(
                new GenericStack(storedVariantKey(1), 1), new GenericStack(storedVariantKey(1), 1)));
        provider.getLogic().updatePatterns();
    }

    void setStoredVariantStock(ServerPlayer player, boolean near, boolean exact) {
        var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
        var storage = drive.getCellInventory(0);
        for (var damage : new int[] {1, 2, 3})
            storage.extract(storedVariantKey(damage), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        if (near) {
            if (storage.insert(storedVariantKey(2), 1, Actionable.MODULATE, IActionSource.empty()) != 1)
                throw new IllegalStateException("Could not store near-match pickaxe");
            if (storage.insert(storedVariantKey(3), 1, Actionable.MODULATE, IActionSource.empty()) != 1)
                throw new IllegalStateException("Could not store second near-match pickaxe");
        }
        if (exact && storage.insert(storedVariantKey(1), 1, Actionable.MODULATE, IActionSource.empty()) != 1)
            throw new IllegalStateException("Could not store exact pickaxe");
        var actual = storage.getAvailableStacks();
        if (actual.get(storedVariantKey(1)) != (exact ? 1 : 0)
                || actual.get(storedVariantKey(2)) != (near ? 1 : 0)
                || actual.get(storedVariantKey(3)) != (near ? 1 : 0))
            throw new IllegalStateException("Stored-variant authoritative stock does not match the requested transition");
        var node = ((IInWorldGridNodeHost) player.serverLevel().getBlockEntity(terminal)).getGridNode(Direction.NORTH);
        var visible = node.getGrid().getStorageService().getInventory().getAvailableStacks();
        org.apache.logging.log4j.LogManager.getLogger("ae2ct-test-driver").info(
                "Variant stock cell=[{},{},{}] grid=[{},{},{}]", actual.get(storedVariantKey(1)),
                actual.get(storedVariantKey(2)), actual.get(storedVariantKey(3)), visible.get(storedVariantKey(1)),
                visible.get(storedVariantKey(2)), visible.get(storedVariantKey(3)));
        System.out.println("AE2CT variant storage exact=" + actual.get(storedVariantKey(1))
                + " near-2=" + actual.get(storedVariantKey(2)) + " near-3=" + actual.get(storedVariantKey(3)));
    }

    void setStoredVariantOtherStock(ServerPlayer player) {
        var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
        if (drive.getCellInventory(0).insert(AEItemKey.of(Items.IRON_SWORD), 1,
                Actionable.MODULATE, IActionSource.empty()) != 1)
            throw new IllegalStateException("Could not store unrelated sword");
    }

    CraftingBlockEntity cpu(ServerPlayer player) {
        return (CraftingBlockEntity) player.serverLevel().getBlockEntity(terminal.west(2));
    }

    java.util.List<CraftingBlockEntity> resourceCpus(ServerPlayer player) {
        return cpuListCpus(player).subList(0, 2);
    }

    void configureResourcePatterns(ServerPlayer player, java.util.List<appeng.api.stacks.AEKey> outputs,
            boolean chemical, boolean clearSamples) {
        var drive = (DriveBlockEntity) player.serverLevel().getBlockEntity(terminal.east(2));
        ServerDriverPlatform.installResourceStorage(drive, chemical);
        var provider = (PatternProviderBlockEntity) player.serverLevel().getBlockEntity(terminal.east(4));
        var inventory = provider.getLogic().getPatternInv();
        var network = ProfilerBridge.networkId(cpu(player).getMainNode().getGrid());
        if (clearSamples) outputs.forEach(key -> ProfilerBridge.clearStats(ProfilerBridge.key(network, key)));
        for (int slot = 0; slot < inventory.size(); slot++) inventory.setItemDirect(slot, ItemStack.EMPTY);
        for (int slot = 0; slot < outputs.size(); slot++) {
            var input = slot == 0 ? AEItemKey.of(Items.COBBLESTONE) : AEItemKey.of(Items.SAND);
            cpu(player).getMainNode().getGrid().getStorageService().getInventory().insert(
                    input, 1, Actionable.MODULATE, IActionSource.empty());
            inventory.setItemDirect(slot, ServerDriverPlatform.processingPattern(
                    new GenericStack(input, 1), new GenericStack(outputs.get(slot), outputs.get(slot).getAmountPerUnit())));
        }
        provider.getLogic().updatePatterns();
    }

    boolean consumeResourceInput(ServerPlayer player, int slot) {
        return removeOne(target(player, 4), slot == 0 ? Items.COBBLESTONE : Items.SAND);
    }

    java.util.List<BlockPos> resourceProviders() { return java.util.List.of(terminal.east(4)); }

    boolean removeResourceProvider(ServerPlayer player) {
        var pos = terminal.east(4);
        player.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return player.serverLevel().getBlockEntity(pos) == null;
    }


    void cleanupResourceFixture(ServerPlayer player, java.util.List<appeng.api.stacks.AEKey> outputs,
            boolean clearSamples) {
        if (terminal == null) return;
        for (int index = 0; index < 2; index++) {
            var blockEntity = player.serverLevel().getBlockEntity(terminal.west(2 + index * 2));
            if (blockEntity instanceof CraftingBlockEntity cpu && cpu.getCluster() != null
                    && cpu.getCluster().isBusy()) cpu.getCluster().craftingLogic.cancel();
        }
        var providerBlockEntity = player.serverLevel().getBlockEntity(terminal.east(4));
        if (providerBlockEntity instanceof PatternProviderBlockEntity provider) {
            var inventory = provider.getLogic().getPatternInv();
            for (int slot = 0; slot < inventory.size(); slot++) inventory.setItemDirect(slot, ItemStack.EMPTY);
            provider.getLogic().updatePatterns();
        }
        var targetBlockEntity = player.serverLevel().getBlockEntity(terminal.east(4).below());
        if (targetBlockEntity instanceof net.minecraft.world.Container target) {
            target.clearContent();
            target.setChanged();
        }
        var cpuBlockEntity = player.serverLevel().getBlockEntity(terminal.west(2));
        if (!(cpuBlockEntity instanceof CraftingBlockEntity owner) || owner.getMainNode().getGrid() == null) return;
        var grid = owner.getMainNode().getGrid();
        for (var output : outputs) grid.getStorageService().getInventory().extract(
                output, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        grid.getStorageService().getInventory().extract(
                AEItemKey.of(Items.COBBLESTONE), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        grid.getStorageService().getInventory().extract(
                AEItemKey.of(Items.SAND), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        if (clearSamples) {
            var network = ProfilerBridge.networkId(grid);
            outputs.forEach(key -> ProfilerBridge.clearStats(ProfilerBridge.key(network, key)));
        }
    }

    boolean resourceDelayed(ServerPlayer player, appeng.api.stacks.AEKey key) {
        var grid = cpu(player).getMainNode().getGrid();
        return ProfilerBridge.isStillDelayed(ProfilerBridge.key(ProfilerBridge.networkId(grid), key));
    }

    void teardownResourceFixture(ServerPlayer player) {
        if (terminal == null) return;
        var level = player.serverLevel();
        for (var pos : BlockPos.betweenClosed(terminal.offset(-(cpuCount * 2), -2, -3), terminal.offset(13, 3, 3))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        terminal = null;
        initialized = false;
        cpuListIdentities = null;
    }

    boolean prepareCpuListJobs(ServerPlayer player) {
        if (sampleMultiplier == 1) {
            sampleMultiplier = 3_600;
            var network = ProfilerBridge.networkId(cpu(player).getMainNode().getGrid());
            ProfilerBridge.clearStats(ProfilerBridge.key(network, AEItemKey.of(Items.STONE)));
            ProfilerBridge.clearStats(ProfilerBridge.key(network, AEItemKey.of(Items.SMOOTH_STONE)));
            seed(player);
        }
        var cpus = cpuListCpus(player);
        var activeCpuCount = cpus.stream().filter(cpu -> cpu.getCluster().isActive()).count();
        checkpoint = "cpu-list-active=" + activeCpuCount + "/" + cpus.size();
        if (activeCpuCount != cpus.size()) return false;
        var service = cpu(player).getMainNode().getGrid().getCraftingService();
        if (cpuListPlans == null) {
            var jobs = jobsForCpuList();
            var source = IActionSource.ofMachine(cpu(player));
            cpuListPlans = jobs.stream().map(job ->
                    service.beginCraftingCalculation(player.serverLevel(), () -> source,
                            AEItemKey.of(job.item()), job.amount(),
                            appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS)).toList();
            checkpoint = "cpu-list-calculating";
            return false;
        }
        checkpoint = "cpu-list-calculating";
        if (cpuListPlans.stream().anyMatch(plan -> !plan.isDone())) return false;
        if (!cpuListSubmitted) {
            checkpoint = "cpu-list-submitting";
            for (var i = 0; i < busyCpuCount; i++) {
                try {
                    var result = service.submitJob(cpuListPlans.get(i).get(), null, cpus.get(i).getCluster(), false,
                            IActionSource.ofMachine(cpus.get(i)));
                    if (!result.successful()) throw new IllegalStateException("multi-CPU submission failed: " + result);
                } catch (Exception error) {
                    throw new IllegalStateException("multi-CPU calculation failed", error);
                }
            }
            cpuListSubmitted = true;
        }
        var observedBusy = cpus.subList(0, busyCpuCount).stream().filter(cpu -> cpu.getCluster().isBusy()).count();
        checkpoint = "cpu-list-busy=" + observedBusy + "/" + busyCpuCount;
        return observedBusy == busyCpuCount;
    }

    void makeCpuListPartial(ServerPlayer player) {
        var grid = cpu(player).getMainNode().getGrid();
        ProfilerBridge.clearStats(ProfilerBridge.key(ProfilerBridge.networkId(grid), AEItemKey.of(Items.SMOOTH_STONE)));
    }

    void restoreCpuListSamples(ServerPlayer player) { seed(player, Items.SMOOTH_STONE); }

    void renameCpuList(ServerPlayer player) {
        var cpus = cpuListCpus(player);
        cpus.get(0).setName("Zulu CPU with a deliberately long English tooltip name");
        cpus.get(1).setName("Alpha CPU with a deliberately long English tooltip name");
        cpus.get(2).setName("Дуже довга назва процесора для перевірки макета");
    }

    boolean finishFirstCpu(ServerPlayer player) {
        pump(player, true);
        return !cpuListCpus(player).get(0).getCluster().isBusy();
    }

    void cancelSecondCpu(ServerPlayer player) { cpuListCpus(player).get(1).getCluster().craftingLogic.cancel(); }

    boolean replaceSecondCpu(ServerPlayer player) {
        var replacementCpu = cpuListCpus(player).get(1);
        var service = cpu(player).getMainNode().getGrid().getCraftingService();
        if (replacementPlan == null) {
            replacementPlan = service.beginCraftingCalculation(player.serverLevel(),
                    () -> IActionSource.ofMachine(replacementCpu), AEItemKey.of(Items.SMOOTH_STONE), 8,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            return false;
        }
        if (!replacementPlan.isDone()) return false;
        if (!replacementSubmitted) {
            try {
                var result = service.submitJob(replacementPlan.get(), null, replacementCpu.getCluster(), false,
                        IActionSource.ofMachine(replacementCpu));
                if (!result.successful()) throw new IllegalStateException("replacement submission failed: " + result);
            } catch (Exception error) { throw new IllegalStateException("replacement calculation failed", error); }
            replacementSubmitted = true;
        }
        return replacementCpu.getCluster().isBusy();
    }

    void removeThirdCpu(ServerPlayer player) {
        player.serverLevel().setBlockAndUpdate(terminal.west(6), Blocks.AIR.defaultBlockState());
    }

    boolean restartSecondCpu(ServerPlayer player) {
        if (!restarting) {
            cancelSecondCpu(player);
            replacementPlan = null;
            replacementSubmitted = false;
            restarting = true;
        }
        return replaceSecondCpu(player);
    }

    StandardCraftFixture secondGrid() {
        var fixture = new StandardCraftFixture();
        fixture.cpuListScenario = true;
        fixture.sampleMultiplier = sampleMultiplier * 4;
        fixture.originShift = 24;
        return fixture;
    }

    StandardCraftFixture variantSecondGrid() {
        var fixture = new StandardCraftFixture();
        fixture.originShift = 24;
        fixture.storedVariantPlan = true;
        fixture.missingPlanInput = true;
        fixture.unprofiledPlan = true;
        return fixture;
    }

    void moveVariantTerminal(ServerPlayer player, StandardCraftFixture other) {
        var terminalNode = variantTerminalNode(player);
        var previous = terminalNode.getGrid();
        for (var connection : java.util.List.copyOf(terminalNode.getConnections())) connection.destroy();
        GridHelper.createConnection(terminalNode, other.cpu(player).getMainNode().getNode());
        if (terminalNode.getGrid() == previous)
            throw new IllegalStateException("Fixture failed to replace the active terminal grid");
    }

    boolean variantTerminalReady(ServerPlayer player, StandardCraftFixture other) {
        var terminalNode = variantTerminalNode(player);
        var grid = terminalNode.getGrid();
        if (grid == null || grid != other.cpu(player).getMainNode().getGrid()
                || !other.cpu(player).getCluster().isActive() || grid.getCraftingService().getCpus().isEmpty())
            return false;
        var stock = grid.getStorageService().getInventory().getAvailableStacks();
        return stock.get(other.storedVariantKey(1)) == 0 && stock.get(other.storedVariantKey(2)) == 1
                && stock.get(other.storedVariantKey(3)) == 1;
    }

    private appeng.api.networking.IGridNode variantTerminalNode(ServerPlayer player) {
        var part = ((IPartHost) player.level().getBlockEntity(terminal)).getPart(Direction.NORTH);
        if (!(part instanceof appeng.parts.reporting.CraftingTerminalPart terminalPart)
                || terminalPart.getActionableNode() == null)
            throw new IllegalStateException("Fixture crafting terminal part is missing its actionable node");
        return terminalPart.getActionableNode();
    }

    StandardCraftFixture largeCpuGrid() {
        var fixture = new StandardCraftFixture();
        fixture.cpuListScenario = true;
        fixture.sampleMultiplier = sampleMultiplier * 2;
        fixture.originShift = 120;
        fixture.cpuCount = 33;
        fixture.busyCpuCount = 33;
        return fixture;
    }

    String cpuListServerEstimates(ServerPlayer player) {
        return liveCpuListCpus(player).stream().filter(cpu -> cpu.getCluster().isBusy()).map(cpu ->
                ProfilerBridge.remainingJobSeconds(cpu.getCluster()).stream().mapToObj(Long::toString)
                        .findFirst().orElse("unknown")).collect(java.util.stream.Collectors.joining(","));
    }

    boolean raiseCrazyPriority(ServerPlayer player) {
        var target = liveCpuListCpus(player).stream().map(CraftingBlockEntity::getCluster)
                .filter(cluster -> {
                    var status = cluster.getJobStatus();
                    var job = status == null ? null : status.crafting();
                    return job != null && job.what().equals(AEItemKey.of(Items.STONE)) && job.amount() == 1;
                }).findFirst().orElseThrow();
        try {
            target.getClass().getMethod("setPrio", int.class).invoke(target, Integer.MAX_VALUE);
            target.markDirty();
            return true;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Crazy AE2 Addons server priority contract is unavailable", failure);
        }
    }

    String cpuListServerState(ServerPlayer player) {
        var grid = cpu(player).getMainNode().getGrid();
        var serials = player.containerMenu instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
                ? ((com.ctux.ae2craftingtime.mc1201.mixin.CraftingStatusMenuAccessor) menu)
                        .ae2craftingtime$getCpuSerialMap()
                : java.util.Map.<appeng.api.networking.crafting.ICraftingCPU, Integer>of();
        var cpus = cpuListCpus(player);
        var rows = new java.util.ArrayList<CpuListTtcControl.CpuState>();
        for (int index = 0; index < cpus.size(); index++) {
            var block = cpus.get(index);
            boolean live = player.serverLevel().getBlockEntity(block.getBlockPos()) == block;
            var cluster = live ? block.getCluster() : null;
            var status = cluster == null ? null : cluster.getJobStatus();
            var job = status == null ? null : status.crafting();
            var estimate = status == null ? java.util.OptionalLong.empty() : ProfilerBridge.remainingJobSeconds(cluster);
            rows.add(new CpuListTtcControl.CpuState(block.getBlockPos().toShortString(),
                    cluster == null ? -1 : serials.getOrDefault(cluster, -1),
                    job == null ? null : job.what().getId().toString(), job == null ? 0 : job.amount(),
                    live, status != null, estimate.isPresent() ? estimate.getAsLong() : null,
                    status == null ? 0 : status.elapsedTimeNanos(), status == null ? 0 : status.progress()));
        }
        var network = ProfilerBridge.networkId(grid);
        var selected = player.containerMenu instanceof appeng.menu.me.crafting.CraftingStatusMenu menu
                ? menu.getSelectedCpuSerial() : -1;
        return new com.google.gson.Gson().toJson(new CpuListTtcControl.ServerState(network.toString(),
                player.containerMenu.containerId, selected,
                ProfilerBridge.stats(ProfilerBridge.key(network, AEItemKey.of(Items.STONE))).isPresent(),
                ProfilerBridge.stats(ProfilerBridge.key(network, AEItemKey.of(Items.SMOOTH_STONE))).isPresent(), rows));
    }

    private java.util.List<CraftingBlockEntity> cpuListCpus(ServerPlayer player) {
        if (cpuListIdentities != null) return cpuListIdentities;
        return java.util.stream.IntStream.range(0, cpuCount)
                .mapToObj(index -> player.serverLevel().getBlockEntity(terminal.west(2 + index * 2)))
                .filter(CraftingBlockEntity.class::isInstance).map(CraftingBlockEntity.class::cast).toList();
    }

    private java.util.List<CraftingBlockEntity> liveCpuListCpus(ServerPlayer player) {
        return cpuListCpus(player).stream().filter(cpu -> player.serverLevel().getBlockEntity(cpu.getBlockPos()) == cpu)
                .toList();
    }

    static java.util.List<CpuJob> cpuListJobs() {
        return java.util.List.of(new CpuJob("minecraft:glass", 4), new CpuJob("minecraft:smooth_stone", 8),
                new CpuJob("minecraft:smooth_stone", 12), new CpuJob("minecraft:smooth_stone", 12),
                new CpuJob("minecraft:stone", 1));
    }

    private java.util.List<CpuJob> jobsForCpuList() {
        if (busyCpuCount == 5) return cpuListJobs();
        return java.util.stream.IntStream.range(0, busyCpuCount)
                .mapToObj(index -> new CpuJob("minecraft:smooth_stone", 32L + index)).toList();
    }

    static int[] pumpOffsets(boolean cpuListScenario) {
        return cpuListScenario ? new int[] { 4, 8, 12 } : new int[] { 4, 8 };
    }

    record CpuJob(String itemId, long amount) {
        net.minecraft.world.item.Item item() {
            return switch (itemId) {
                case "minecraft:glass" -> Items.GLASS;
                case "minecraft:smooth_stone" -> Items.SMOOTH_STONE;
                case "minecraft:stone" -> Items.STONE;
                default -> throw new IllegalArgumentException("Unsupported CPU-list job output: " + itemId);
            };
        }
    }

    private int[] sampleCounts(ServerPlayer player) {
        var network = ProfilerBridge.networkId(cpu(player).getMainNode().getGrid());
        return java.util.stream.Stream.of(Items.STONE, Items.SMOOTH_STONE)
                .mapToInt(item -> ProfilerBridge.stats(ProfilerBridge.key(network, AEItemKey.of(item)))
                        .map(stats -> stats.sampleCount()).orElse(0)).toArray();
    }

    boolean observedNewSamples(ServerPlayer player) {
        var current = sampleCounts(player);
        return initialSamples != null && current[0] > initialSamples[0] && current[1] > initialSamples[1];
    }

    boolean finalOutputReady(ServerPlayer player) {
        if (holdFinalOutput) return contains(target(player, 8), Items.SMOOTH_STONE);
        var furnace = (FurnaceBlockEntity) player.serverLevel().getBlockEntity(terminal.east(8).below());
        return furnace.getItem(2).is(Items.SMOOTH_STONE) && furnace.getItem(2).getCount() == 1;
    }

    void releaseDelayedOutput(String outputId) {
        if (!java.util.Set.of("minecraft:stone", "minecraft:glass").contains(outputId)) {
            throw new IllegalArgumentException("Unsupported delayed output: " + outputId);
        }
        delayedRelease = outputId;
    }

    void viewFinalProvider(ServerPlayer player) {
        player.teleportTo(terminal.getX() + 9, terminal.getY() - 1, terminal.getZ() - 2.5);
        player.setYRot(9.462f);
        player.setXRot(2);
    }

    long pump(ServerPlayer player, boolean fuel) {
        if (fuel && initialSamples == null) initialSamples = sampleCounts(player);
        var storage = cpu(player).getMainNode().getGrid().getStorageService().getInventory();
        if (holdFinalOutput || preparedFinalOutput) return pumpDelayed(player, storage);
        for (int offset : pumpOffsets(cpuListScenario)) {
            var furnace = (FurnaceBlockEntity) player.serverLevel().getBlockEntity(terminal.east(offset).below());
            if (fuel && furnace.getItem(1).isEmpty()) furnace.setItem(1, new ItemStack(Items.COAL));
            var output = furnace.getItem(2);
            if (!output.isEmpty() && !(offset == 8 && holdFinalOutput)) {
                long inserted = storage.insert(AEItemKey.of(output), output.getCount(), Actionable.MODULATE, IActionSource.ofMachine(cpu(player)));
                if (offset == 4 && inserted > 0) returnedStone = true;
                furnace.removeItem(2, (int) inserted);
                furnace.setChanged();
            }
        }
        return storage.extract(AEItemKey.of(Items.SMOOTH_STONE), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    void viewSharedProvider(ServerPlayer player) {
        player.teleportTo(terminal.getX() + 5, terminal.getY() - 1, terminal.getZ() - 2.5);
        player.setYRot(9.462f);
        player.setXRot(2);
    }

    void viewTerminal(ServerPlayer player) {
        player.teleportTo(terminal.getX() + 0.5, terminal.getY() - 1, terminal.getZ() - 2.5);
    }

    private long pumpDelayed(ServerPlayer player, appeng.api.storage.MEStorage storage) {
        if (delayedRelease != null) {
            var output = delayedRelease.equals("minecraft:stone") ? Items.STONE : Items.GLASS;
            var input = output == Items.STONE ? Items.COBBLESTONE : Items.SAND;
            if (removeOne(target(player, 4), input)) {
                var inserted = storage.insert(AEItemKey.of(output), 1, Actionable.MODULATE,
                        IActionSource.ofMachine(cpu(player)));
                if (inserted != 1) throw new IllegalStateException("Could not return delayed " + delayedRelease);
                returnedStone |= output == Items.STONE;
                delayedRelease = null;
            }
        }
        var finalTarget = target(player, 8);
        if (!preparedFinalOutput && removeOne(finalTarget, Items.STONE)) {
            if (!removeOne(finalTarget, Items.GLASS)) {
                addOne(finalTarget, Items.STONE);
            } else {
                addOne(finalTarget, Items.SMOOTH_STONE);
                preparedFinalOutput = true;
            }
        }
        if (preparedFinalOutput && !holdFinalOutput && removeOne(finalTarget, Items.SMOOTH_STONE)) {
            var inserted = storage.insert(AEItemKey.of(Items.SMOOTH_STONE), 1, Actionable.MODULATE,
                    IActionSource.ofMachine(cpu(player)));
            if (inserted != 1) throw new IllegalStateException("Could not return final delayed output");
        }
        return storage.extract(AEItemKey.of(Items.SMOOTH_STONE), Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    private net.minecraft.world.Container target(ServerPlayer player, int offset) {
        return (net.minecraft.world.Container) player.serverLevel().getBlockEntity(terminal.east(offset).below());
    }

    private static boolean contains(net.minecraft.world.Container target, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < target.getContainerSize(); slot++) if (target.getItem(slot).is(item)) return true;
        return false;
    }

    private static boolean removeOne(net.minecraft.world.Container target, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            if (target.getItem(slot).is(item)) {
                target.removeItem(slot, 1);
                target.setChanged();
                return true;
            }
        }
        return false;
    }

    private static void addOne(net.minecraft.world.Container target, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            if (target.getItem(slot).isEmpty()) {
                target.setItem(slot, new ItemStack(item));
                target.setChanged();
                return;
            }
        }
        throw new IllegalStateException("Delayed processor has no output slot");
    }
}
