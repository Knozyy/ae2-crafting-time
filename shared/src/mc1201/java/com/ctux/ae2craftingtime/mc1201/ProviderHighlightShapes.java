package com.ctux.ae2craftingtime.mc1201;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.core.ProviderBeamHeight;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Client-side only. Draws the delayed-craft provider highlight on 1.20.1 and
 * 1.21.1: thick rainbow edge boxes plus a red plate with the stuck output's
 * typed resource icon on each camera-facing face.
 *
 * <p>Vanilla {@code RenderType.lines()} width is fixed at one pixel on most
 * drivers, so edge thickness comes from three nested shells (roughly 2-3x
 * the old single stroke). Plates are thin filled boxes with icons rendered
 * item-frame style, all through vanilla statics shared by 1.20.1 and 1.21.1
 * (raw vertex calls differ between the two, so shared code never emits
 * vertices directly). Called only from the per-loader render hooks; never
 * touched on a dedicated server.
 */
public final class ProviderHighlightShapes {
    public static RenderType beam() {
        return BeamState.BEAM;
    }

    private static final class BeamState extends RenderType {
        private static final RenderType BEAM = createBeam();

        private BeamState() {
            super("provider_beam", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP,
                    1536, false, true, () -> {}, () -> {});
        }

        private static RenderType createBeam() {
            return RenderType.create("ae2craftingtime_provider_beam",
                    DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
    private static final double[] SHELL_OFFSETS = {0.002, 0.014, 0.026};
    private static final float PLATE_HALF_SIZE = 0.36f;
    private static final float PLATE_MIN_Z = 0.004f;
    private static final float PLATE_MAX_Z = 0.016f;
    private static final float ITEM_Z = 0.03f;
    private static final float ITEM_SCALE = 0.55f;

    public static void renderThickRainbowBox(PoseStack pose, VertexConsumer consumer, AABB box, float red,
            float green, float blue, float alpha) {
        for (var shell : SHELL_OFFSETS) {
            LevelRenderer.renderLineBox(pose, consumer, box.inflate(shell), red, green, blue, alpha);
        }
    }

    public static void renderBeam(PoseStack pose, VertexConsumer consumer, BlockPos pos, int upperBuildBoundary,
            int renderDistanceChunks, float alpha) {
        LevelRenderer.addChainedFilledBoxVertices(pose, consumer,
                pos.getX() + 0.4, pos.getY() + 1, pos.getZ() + 0.4,
                pos.getX() + 0.6, ProviderBeamHeight.top(pos.getY(), upperBuildBoundary, renderDistanceChunks),
                pos.getZ() + 0.6, 1.0f, 0.15f, 0.15f, alpha);
    }

    /**
     * Draws a red plate with the stuck output's icon on each given face.
     * Faces must already be culled to the camera side. An unavailable key draws
     * only the plate.
     *
     * <p>Each plate is a thin filled box from vanilla
     * {@code LevelRenderer.addChainedFilledBoxVertices} into
     * {@code debugFilledBox} (the only filled-box emitter shared by 1.20.1
     * and 1.21.1). That pipeline is {@code TRIANGLE_STRIP} with culling
     * and has no vanilla callers, so every face is flushed with its own
     * {@code endBatch}: appending the next face to the same strip would
     * continue the strip out of phase and the culled pipeline would drop
     * the plate (the #241 invisible plates). Flushing before the resource icon
     * also means the filled builder is never alive across other-type
     * writes (the #237 crash), so never hold a filled consumer.
     */
    public static void renderFacePlatesAndIcons(PoseStack pose, MultiBufferSource buffers, Level level, BlockPos pos,
            AEKey key, List<Direction> faces, int light, float alpha) {
        var items = Minecraft.getInstance().getItemRenderer();
        for (var face : faces) {
            pose.pushPose();
            orientToFace(pose, pos, face);
            LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                    -PLATE_HALF_SIZE, -PLATE_HALF_SIZE, PLATE_MIN_Z, PLATE_HALF_SIZE, PLATE_HALF_SIZE, PLATE_MAX_Z,
                    1.0f, 0.15f, 0.15f, alpha);
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch(RenderType.debugFilledBox());
            }
            if (key != null && AEKeyRendering.get(key.getType()) != null) {
                pose.pushPose();
                pose.translate(0.0, 0.0, ITEM_Z);
                if (key instanceof AEItemKey item) {
                    pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
                    items.renderStatic(resolveItem(item), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                            pose, buffers, level, 0);
                } else {
                    AEKeyRendering.drawOnBlockFace(pose, buffers, key, ITEM_SCALE, light, level);
                }
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private static void orientToFace(PoseStack pose, BlockPos pos, Direction face) {
        pose.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        switch (face) {
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90.0f));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
            case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            case SOUTH -> {
            }
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90.0f));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        }
        pose.translate(0.0, 0.0, 0.5);
    }

    public static ItemStack resolveItem(AEKey key) {
        return key instanceof AEItemKey item ? item.toStack() : ItemStack.EMPTY;
    }

    private ProviderHighlightShapes() {
    }
}
