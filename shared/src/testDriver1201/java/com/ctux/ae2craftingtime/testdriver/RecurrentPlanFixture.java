package com.ctux.ae2craftingtime.testdriver;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import com.ctux.ae2craftingtime.core.FeatureOptions;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.PlanRecurrence;
import com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime;
import com.ctux.ae2craftingtime.testdriver.mixin.CraftConfirmMenuAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

final class RecurrentPlanFixture implements ICraftingProvider {
    static final List<String> CASES = List.of("two", "self", "three", "ordinary", "seed", "less", "alternative",
            "eligible", "mixed", "variants", "substitute", "emitter", "amount", "fluid", "reported-1", "reported-100", "large");
    private final StandardCraftFixture fixture;
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private IManagedGridNode node;
    private String configured = "";
    private Set<AEKey> emitted = Set.of();
    private Set<AEKey> expected = Set.of();
    private boolean successful;
    private AEKey substitute;
    private List<java.util.concurrent.Future<appeng.api.networking.crafting.ICraftingPlan>> boundaryPlans;
    private MinecraftServer server;
    private Boolean originalDetection;

    RecurrentPlanFixture(StandardCraftFixture fixture) { this.fixture = fixture; }

    boolean prepare(ServerPlayer player, String name) {
        server = player.level().getServer();
        if (enableDetection(ServerOptionsRuntime.current().features())) {
            for (var connected : server.getPlayerList().getPlayers()) ServerOptionsRuntime.sendTo(connected);
        }
        if (!configured.equals(name)) {
            configured = name;
            patterns.clear();
            emitted = Set.of();
            var a = AEItemKey.of(Items.SMOOTH_STONE);
            var b = AEItemKey.of(Items.STONE);
            var c = AEItemKey.of(Items.COBBLESTONE);
            var sand = AEItemKey.of(Items.SAND);
            for (int offset : new int[] {4, 8}) {
                var provider = (PatternProviderBlockEntity) player.level().getBlockEntity(fixture.terminal.east(offset));
                provider.getLogic().getPatternInv().setItemDirect(0, net.minecraft.world.item.ItemStack.EMPTY);
                provider.getLogic().updatePatterns();
            }
            var inventory = fixture.cpu(player).getMainNode().getGrid().getStorageService().getInventory();
            var stored = new KeyCounter();
            inventory.getAvailableStacks(stored);
            for (var entry : stored) inventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
            successful = Set.of("seed", "less", "alternative", "substitute", "emitter").contains(name);
            expected = switch (name) {
                case "two", "three", "mixed" -> Set.of(b);
                case "self" -> Set.of(a);
                default -> Set.of();
            };
            switch (name) {
                case "reported-1", "reported-100" -> {
                    pattern(player, a, b);
                    pattern(player, b, c);
                    pattern(player, c, b);
                    expected = Set.of(c);
                }
                case "amount", "fluid" -> {
                    AEKey missing = name.equals("fluid")
                            ? appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER) : b;
                    var encoded = ServerDriverPlatform.processingPattern(List.of(new GenericStack(missing, 1_000_000)), new GenericStack(a, 1));
                    patterns.add(java.util.Objects.requireNonNull(PatternDetailsHelper.decodePattern(encoded, player.level())));
                    pattern(player, missing, a);
                    expected = Set.of(missing);
                }
                case "self" -> pattern(player, a, a);
                case "three" -> {
                    pattern(player, a, b);
                    pattern(player, b, c);
                    pattern(player, c, a);
                    expected = Set.of(c);
                }
                case "ordinary" -> { pattern(player, a, b); pattern(player, b, c); }
                case "mixed" -> { pattern(player, a, b, c); pattern(player, b, a); }
                case "variants", "substitute" -> {
                    var first = new net.minecraft.world.item.ItemStack(Items.IRON_PICKAXE);
                    first.setDamageValue(1);
                    var second = first.copy();
                    second.setDamageValue(2);
                    var x = AEItemKey.of(first);
                    var y = AEItemKey.of(second);
                    if (name.equals("variants")) {
                        pattern(player, a, x, y);
                        pattern(player, x, a);
                        expected = Set.of(x);
                    } else {
                        substitute = y;
                        pattern(player, a, x);
                        pattern(player, y, sand);
                        inventory.insert(sand, 1, Actionable.MODULATE, IActionSource.empty());
                    }
                }
                case "large" -> {
                    var keys = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                            .filter(item -> item != Items.AIR && item != Items.SMOOTH_STONE)
                            .limit(258).map(AEItemKey::of).toList();
                    for (int i = 0; i < 129; i++) {
                        var output = i == 0 ? a : keys.get(i - 1);
                        var next = keys.get(i);
                        var missing = keys.get(129 + i);
                        pattern(player, output, next, missing);
                    }
                    pattern(player, keys.get(128), a);
                    expected = Set.of(keys.get(128));
                }
                default -> {
                    pattern(player, a, b);
                    pattern(player, b, a);
                    if (name.equals("seed") || name.equals("less")) inventory.insert(b, 1, Actionable.MODULATE, IActionSource.empty());
                    if (name.equals("alternative") || name.equals("eligible")) pattern(player, b, sand);
                    if (name.equals("alternative")) inventory.insert(sand, 1, Actionable.MODULATE, IActionSource.empty());
                    if (name.equals("emitter")) emitted = Set.of(b);
                    if (name.equals("two")) expected = Set.of(b);
                }
            }
            if (node == null) {
                node = GridHelper.createManagedNode(this, (owner, changed) -> {})
                        .setInWorldNode(false).addService(ICraftingProvider.class, this);
                node.create(player.level(), fixture.terminal);
                GridHelper.createConnection(node.getNode(), fixture.cpu(player).getMainNode().getNode());
            }
            ICraftingProvider.requestUpdate(node);
            return false;
        }
        return node.isActive() && node.getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.SMOOTH_STONE));
    }

    boolean validate(ServerPlayer player) {
        if (!(player.containerMenu instanceof appeng.menu.me.crafting.CraftConfirmMenu menu)) return false;
        var plan = ((CraftConfirmMenuAccessor) menu).ae2craftingtime_test_driver$result();
        if (plan == null || menu.getPlan() == null) return false;
        if (configured.equals("less")) {
            var service = node.getGrid().getCraftingService();
            if (boundaryPlans == null) {
                var source = IActionSource.ofMachine(fixture.cpu(player));
                var a = AEItemKey.of(Items.SMOOTH_STONE);
                var cancelled = service.beginCraftingCalculation(player.level(), () -> source, a, 1024,
                        appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
                validateCancellation(cancelled.cancel(true), cancelled.isCancelled());
                boundaryPlans = List.of(
                        service.beginCraftingCalculation(player.level(), () -> source, a, 2,
                                appeng.api.networking.crafting.CalculationStrategy.CRAFT_LESS),
                        service.beginCraftingCalculation(player.level(), () -> source, a, 2,
                                appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS));
                return false;
            }
            if (boundaryPlans.stream().anyMatch(future -> !future.isDone())) return false;
            var less = await(boundaryPlans.get(0));
            var full = await(boundaryPlans.get(1));
            validateAttempts(less.simulation(), less.finalOutput().amount(),
                    ((PlanRecurrence) less).ae2craftingtime$recurrentKeys(), full.simulation(),
                    ((PlanRecurrence) full).ae2craftingtime$recurrentKeys(), AEItemKey.of(Items.STONE));
        }
        var actual = ((PlanRecurrence) plan).ae2craftingtime$recurrentKeys();
        validateOutcome(configured, expected, successful, actual, plan.simulation());
        validateSummary(configured, menu.getPlan().getEntries().size(), menu.getPlan().getEntries().stream()
                .anyMatch(entry -> requestedMissing(expected, entry.getWhat(), entry.getMissingAmount(), requestedAmount())),
                requestedAmount());
        for (var entry : menu.getPlan().getEntries()) {
            validateFlag(expected, entry.getWhat(), entry.getMissingAmount(),
                    ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$recurrent());
        }
        System.out.println("AE2CT recurrence case=" + configured + " simulation=" + plan.simulation()
                + " missing=" + plan.missingItems() + " rows=" + menu.getPlan().getEntries().size());
        return true;
    }

    static void validateCancellation(boolean cancelled, boolean observed) {
        if (!cancelled || !observed) throw new IllegalStateException("Cancellation was not observed");
    }

    static <T> T await(java.util.concurrent.Future<T> future) {
        try {
            return future.get();
        } catch (java.util.concurrent.ExecutionException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    static void validateAttempts(boolean lessSimulation, long lessOutput, Set<?> lessKeys,
            boolean fullSimulation, Set<?> fullKeys, Object missing) {
        if (lessSimulation || lessOutput != 1 || !lessKeys.isEmpty()
                || !fullSimulation || !fullKeys.equals(Set.of(missing)))
            throw new IllegalStateException("Concurrent or CRAFT_LESS attempts leaked recurrence");
    }

    static void validateOutcome(String name, Set<?> expected, boolean successful, Set<?> actual, boolean simulation) {
        if (!actual.equals(expected) || simulation == successful)
            throw new IllegalStateException(name + " expected recurrence=" + expected + " success=" + successful
                    + " actual recurrence=" + actual + " simulation=" + simulation);
    }

    static void validateSummary(String name, int rows, boolean quantityPresent, long requested) {
        if (name.equals("large") && rows <= 256)
            throw new IllegalStateException("Large recurrence plan did not cross the chunk boundary");
        if (name.startsWith("reported-") && !quantityPresent)
            throw new IllegalStateException("Reported regression lost missing quantity " + requested);
    }

    static boolean requestedMissing(Set<?> expected, Object key, long missing, long requested) {
        return expected.contains(key) && missing == requested;
    }

    static void validateFlag(Set<?> expected, Object key, long missing, boolean flag) {
        if (flag != (missing > 0 && expected.contains(key)))
            throw new IllegalStateException("Summary diagnosis or positive-missing intersection differs");
    }

    boolean recurrent() { return !expected.isEmpty(); }
    boolean reported() { return configured.startsWith("reported-"); }
    long requestedAmount() { return configured.equals("reported-100") ? 100 : 1; }

    void close() {
        if (restoreDetection(ServerOptionsRuntime.current().features())) {
            for (var connected : server.getPlayerList().getPlayers()) ServerOptionsRuntime.sendTo(connected);
        }
        if (node != null) node.destroy();
        node = null;
        configured = "";
        server = null;
    }

    boolean enableDetection(FeatureOptions options) {
        boolean first = originalDetection == null;
        if (originalDetection == null) originalDetection = options.enabled(OptionFeature.RECURRENT_DETECTION);
        options.setEnabled(OptionFeature.RECURRENT_DETECTION, true);
        return first;
    }

    boolean restoreDetection(FeatureOptions options) {
        if (originalDetection == null) return false;
        options.setEnabled(OptionFeature.RECURRENT_DETECTION, originalDetection);
        originalDetection = null;
        return true;
    }

    boolean clientReady(appeng.menu.me.crafting.CraftConfirmMenu menu) {
        if (menu.getPlan() == null) return false;
        var flags = menu.getPlan().getEntries().stream().filter(entry ->
                ((com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry) entry).ae2craftingtime$recurrent())
                .map(appeng.menu.me.crafting.CraftingPlanSummaryEntry::getWhat).collect(java.util.stream.Collectors.toSet());
        return flags.equals(expected);
    }

    private void pattern(ServerPlayer player, AEKey output, AEKey... inputs) {
        var encoded = ServerDriverPlatform.processingPattern(java.util.Arrays.stream(inputs)
                .map(key -> new GenericStack(key, 1)).toList(), new GenericStack(output, 1));
        var decoded = java.util.Objects.requireNonNull(PatternDetailsHelper.decodePattern(encoded, player.level()));
        patterns.add(configured.equals("substitute") && output.equals(AEItemKey.of(Items.SMOOTH_STONE))
                ? ServerDriverPlatform.substitutePattern(decoded, substitute) : decoded);
    }

    @Override public List<IPatternDetails> getAvailablePatterns() { return List.copyOf(patterns); }
    @Override public Set<AEKey> getEmitableItems() { return emitted; }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        throw new IllegalStateException("Recurrence fixture must never submit a craft");
    }
    @Override public boolean isBusy() { return false; }
}
