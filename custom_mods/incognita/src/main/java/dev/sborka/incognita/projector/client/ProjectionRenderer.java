package dev.sborka.incognita.projector.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sborka.incognita.Incognita;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the translucent ghost: blocks that are already right are skipped, wrong blocks are tinted red.
 * Blocks without a plain model (block-entity renderers) get a blue outline instead of a ghost.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class ProjectionRenderer {
    private static final float GHOST_ALPHA = 0.5f;
    private static final double MAX_DISTANCE_SQR = 96.0 * 96.0;

    private ProjectionRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ProjectionState.holdingProjector(mc.player)) {
            return;
        }
        BlockPos anchor = ProjectionState.anchor();
        ProjectionTransform.Result result = ProjectionState.result();
        if (anchor == null || result == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer ghost = new AlphaConsumer(buffers.getBuffer(RenderType.translucent()), GHOST_ALPHA);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (ProjectionTransform.Placed placed : result.blocks()) {
            BlockPos pos = anchor.offset(placed.offset());
            if (pos.distToCenterSqr(cam) > MAX_DISTANCE_SQR) {
                continue;
            }
            BlockState actual = mc.level.getBlockState(pos);
            if (actual == placed.state()) {
                continue;
            }
            boolean wrong = !actual.isAir();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            BlockState state = placed.state();
            if (state.getRenderShape() == RenderShape.MODEL) {
                BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                int light = LevelRenderer.getLightColor(mc.level, pos);
                mc.getBlockRenderer().getModelRenderer().renderModel(poseStack.last(), ghost, state, model,
                    1.0f, wrong ? 0.35f : 1.0f, wrong ? 0.35f : 1.0f, light, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            } else {
                LevelRenderer.renderLineBox(poseStack, lines, 0, 0, 0, 1, 1, 1, 0.3f, 0.5f, 1.0f, 1.0f);
            }
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.translucent());
        buffers.endBatch(RenderType.lines());
    }

    /** Multiplies the alpha of every vertex, so the normal block renderer can draw a see-through block. */
    private static final class AlphaConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        AlphaConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alphaIn) {
            delegate.setColor(red, green, blue, Math.round(alphaIn * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
