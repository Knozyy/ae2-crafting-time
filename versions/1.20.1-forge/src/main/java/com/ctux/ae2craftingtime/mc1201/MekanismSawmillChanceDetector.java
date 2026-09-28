package com.ctux.ae2craftingtime.mc1201;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.service.CraftingService;
import com.ctux.ae2craftingtime.mc1201.mixin.PatternProviderLogicHostAccessor;
import java.util.Map;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.tile.machine.TileEntityPrecisionSawmill;
import net.minecraft.server.level.ServerLevel;

/** Only a direct, single-target sawmill with one matching live recipe is authoritative. */
public final class MekanismSawmillChanceDetector {
    public static Map<AEKey, Integer> detect(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] inputs) {
        if (!(provider instanceof PatternProviderLogic logic) || inputs == null) return Map.of();
        if (logic.getGrid() == null || !(logic.getGrid().getCraftingService() instanceof CraftingService crafting))
            return Map.of();
        var providers = crafting.getProviders(pattern).iterator();
        if (!providers.hasNext() || providers.next() != provider || providers.hasNext()) return Map.of();
        var host = ((PatternProviderLogicHostAccessor) logic).ae2craftingtime$getHost();
        if (host == null || host.getTargets().size() != 1) return Map.of();
        var providerBlock = host.getBlockEntity();
        if (providerBlock == null || !(providerBlock.getLevel() instanceof ServerLevel level)) return Map.of();
        var target = providerBlock.getBlockPos().relative(host.getTargets().iterator().next());
        if (!(level.getBlockEntity(target) instanceof TileEntityPrecisionSawmill)) return Map.of();

        AEItemKey inputKey = null;
        long inputAmount = 0;
        for (var slot : inputs) {
            if (slot == null || slot.isEmpty()) continue;
            if (slot.size() != 1 || !(slot.getFirstKey() instanceof AEItemKey item) || inputKey != null) return Map.of();
            inputKey = item;
            inputAmount = slot.get(item);
        }
        if (inputKey == null || inputAmount <= 0) return Map.of();
        var stack = inputKey.toStack((int) Math.min(inputAmount, 64));
        var recipes = MekanismRecipeType.SAWING.getRecipes(level);
        if (recipes.size() > 512) return Map.of();
        var matching = recipes.stream().filter(recipe -> recipe.test(stack)).toList();
        if (matching.size() != 1) return Map.of();
        var recipe = matching.get(0);
        if (recipe.getInput().getNeededAmount(stack) != inputAmount) return Map.of();
        var chance = recipe.getSecondaryChance();
        if (!(chance > 0 && chance < 1) || recipe.getSecondaryOutputDefinition().size() != 1) return Map.of();
        var secondary = recipe.getSecondaryOutputDefinition().get(0);
        var outputKey = AEItemKey.of(secondary);
        if (outputKey == null || recipe.getMainOutputDefinition().stream()
                .anyMatch(main -> AEItemKey.matches(outputKey, main))) return Map.of();
        for (var expected : pattern.getOutputs()) {
            if (outputKey.getId().equals(expected.what().getId()) && !outputKey.equals(expected.what()))
                return Map.of();
            if (outputKey.equals(expected.what()) && expected.amount() == secondary.getCount()) {
                return Map.of(outputKey, (int) Math.round(chance * 10000));
            }
        }
        return Map.of();
    }

    private MekanismSawmillChanceDetector() { }
}
