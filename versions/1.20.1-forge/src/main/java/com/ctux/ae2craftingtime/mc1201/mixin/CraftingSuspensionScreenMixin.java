package com.ctux.ae2craftingtime.mc1201.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftingCPUScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionMenuState;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.TtcBadge;
import com.ctux.ae2craftingtime.mc1201.net.CraftingSuspensionC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingCPUScreen.class)
public abstract class CraftingSuspensionScreenMixin<T extends CraftingCPUMenu> extends AEBaseScreen<T> {
    @Shadow(remap = false) private Button cancel;
    @Unique private Button ae2craftingtime$suspend;

    protected CraftingSuspensionScreenMixin(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void ae2craftingtime$renderButton(GuiGraphics graphics, int mouseX, int mouseY, float partial,
            CallbackInfo ci) {
        if (ae2craftingtime$suspend == null || !children().contains(ae2craftingtime$suspend)) {
            ae2craftingtime$suspend = addRenderableWidget(Button.builder(Component.empty(), button -> {
                var state = ((CraftingSuspensionMenuState) menu).ae2craftingtime$suspensionSnapshot();
                if (state != null && state.supported() && state.enabled() && state.hasJob())
                    StatsNetwork.sendToServer(new CraftingSuspensionC2S(
                            new com.ctux.ae2craftingtime.core.CraftingSuspension.Request(
                                    state.containerId(), state.cpuContext(), state.jobId(), !state.suspended())));
            }).bounds(cancel.getX() - 60, cancel.getY(), 50, 20).build());
        }
        ae2craftingtime$suspend.setX(cancel.getX() - 60);
        ae2craftingtime$suspend.setY(cancel.getY());
        var state = ((CraftingSuspensionMenuState) menu).ae2craftingtime$suspensionSnapshot();
        ae2craftingtime$suspend.visible = state != null && state.supported() && state.enabled() && state.hasJob();
        ae2craftingtime$suspend.active = ae2craftingtime$suspend.visible;
        if (ae2craftingtime$suspend.visible)
            ae2craftingtime$suspend.setMessage(Component.translatable(state.suspended()
                    ? "gui.ae2craftingtime.resume" : "gui.ae2craftingtime.suspend"));
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"), remap = false, require = 0)
    private void ae2craftingtime$suspendedTitle(CallbackInfo ci) {
        var state = ((CraftingSuspensionMenuState) menu).ae2craftingtime$suspensionSnapshot();
        if (state != null && state.suspended())
            setTextContent(TEXT_ID_DIALOG_TITLE, com.ctux.ae2craftingtime.mc1201.RowTextColor.neutral(
                    Component.translatable("gui.ae2craftingtime.suspended"),
                    com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current()));
    }

    @Inject(method = "drawFG", at = @At("HEAD"), remap = false, require = 0)
    private void ae2craftingtime$suspendedBadge(GuiGraphics graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, CallbackInfo ci) {
        var state = ((CraftingSuspensionMenuState) menu).ae2craftingtime$suspensionSnapshot();
        if (state != null && state.suspended()) {
            var title = Component.translatable("gui.ae2craftingtime.suspended");
            TtcBadge.fillRoundedRect(graphics, 6, 5, 10 + getMinecraft().font.width(title),
                    9 + getMinecraft().font.lineHeight, TtcBadge.BACKGROUND);
        }
    }
}
