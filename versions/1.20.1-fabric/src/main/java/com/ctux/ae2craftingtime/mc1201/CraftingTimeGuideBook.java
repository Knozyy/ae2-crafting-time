package com.ctux.ae2craftingtime.mc1201;

import appeng.core.AppEng;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class CraftingTimeGuideBook {
    private static final ResourceLocation RECIPE = new ResourceLocation(Ae2CraftingTime.MOD_ID, "guide_book");
    private static final ResourceLocation PAGE = new ResourceLocation(Ae2CraftingTime.MOD_ID, "index.md");
    private static final String MARKER = "ae2craftingtime:guide";

    private CraftingTimeGuideBook() {
    }

    public static void markRecipeResult(ResourceLocation id, ItemStack result) {
        if (shouldMark(id.toString(), result.is(Items.BOOK))) {
            result.getOrCreateTag().putBoolean(MARKER, true);
            result.setHoverName(Component.translatable("item.ae2craftingtime.guide"));
        }
    }

    static boolean isGuide(ItemStack stack) {
        return shouldOpen(stack.is(Items.BOOK), stack.hasTag() && stack.getTag().getBoolean(MARKER));
    }

    static boolean shouldMark(String recipeId, boolean vanillaBook) {
        return vanillaBook && RECIPE.toString().equals(recipeId);
    }

    static boolean shouldOpen(boolean vanillaBook, boolean marked) {
        return vanillaBook && marked;
    }

    static InteractionResultHolder<ItemStack> use(Player player, Level level, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!isGuide(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            AppEng.instance().openGuideAtPreviousPage(PAGE);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
