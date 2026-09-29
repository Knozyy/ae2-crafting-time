package com.ctux.ae2craftingtime.testdriver.mixin;

import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Retain the mutable status description through rendering, including production warnings. */
@Mixin(value = CraftingStatusTableRenderer.class, priority = 900)
public abstract class CraftingStatusTableRendererObservationMixin {
    @Inject(method = "getEntryDescription", at = @At("RETURN"), remap = false)
    private void ae2craftingtime_test_driver$description(CraftingStatusEntry entry,
            CallbackInfoReturnable<List<Component>> cir) {
        UiObservationStore.description(entry, cir.getReturnValue());
    }
}
