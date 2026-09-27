package com.ctux.ae2craftingtime.mc1201.mixin;

import com.ctux.ae2craftingtime.mc1201.CraftingTimeGuideBook;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShapelessRecipe.class)
public abstract class CraftingTimeGuideRecipeMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void markGuideResult(ResourceLocation id, String group, CraftingBookCategory category,
            ItemStack result, NonNullList<Ingredient> ingredients, CallbackInfo ci) {
        CraftingTimeGuideBook.markRecipeResult(id, result);
    }
}
