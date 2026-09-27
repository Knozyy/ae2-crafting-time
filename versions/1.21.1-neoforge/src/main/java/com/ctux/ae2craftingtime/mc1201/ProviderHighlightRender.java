package com.ctux.ae2craftingtime.mc1201;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Ae2CraftingTime.MOD_ID, value = Dist.CLIENT)
public final class ProviderHighlightRender {
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        var levelDimension = minecraft.level.dimension().location().toString();
        // A broken provider target drops its edge and plate immediately, in
        // this dimension only. Replacement with another block entity and a
        // surviving host without its provider part both count as broken;
        // unloaded chunks stay unknown so reload never clears intact red.
        ProviderHighlightClient.trimPositions(levelDimension,
                pos -> ProviderBlockTargets.keepForHighlight(minecraft.level, pos));
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var consumers = minecraft.renderBuffers().bufferSource();
        var rainbow = ProviderHighlightClient.rainbowRgb();
        var alpha = ProviderHighlightClient.pulseAlpha();
        // Click edges first in their own batch: plate and item writes switch the
        // shared fallback builder to other render types, so a cached lines
        // consumer would not survive until the next position. Each identity
        // keeps its own edge so two locates within 15 seconds stay independent.
        var edges = ProviderHighlightClient.liveEdges();
        var hasEdge = false;
        var lines = consumers.getBuffer(RenderType.lines());
        for (var highlight : edges) {
            if (!levelDimension.equals(highlight.dimensionId())) {
                continue;
            }
            hasEdge = true;
            for (var pos : highlight.positions()) {
                ProviderHighlightShapes.renderThickRainbowBox(poseStack, lines, new AABB(pos).inflate(0.002),
                        rainbow[0], rainbow[1], rainbow[2], alpha);
            }
        }
        if (hasEdge) {
            consumers.endBatch(RenderType.lines());
        }
        // Plates persist while their output still reports a stall.
        for (var plate : ProviderHighlightClient.renderPlates()) {
            if (!levelDimension.equals(plate.dimensionId())) {
                continue;
            }
            var pos = plate.position();
            ProviderHighlightShapes.renderFacePlatesAndIcons(poseStack, consumers, minecraft.level, pos, plate.displayKey(),
                    ProviderFaceIcons.visibleFaces(pos, camera.x, camera.y, camera.z),
                    LightTexture.FULL_BRIGHT, alpha);
        }
        consumers.endBatch(RenderType.solid());
        consumers.endBatch(RenderType.debugFilledBox());
        poseStack.popPose();
    }

    private ProviderHighlightRender() {
    }
}
