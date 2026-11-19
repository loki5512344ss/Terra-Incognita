package dev.sborka.incognita.mixin;

import dev.sborka.incognita.weather.clouds.render.CloudsClient;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudsMixin {
    /**
     * The vanilla clouds never render: this always cancels and draws the Incognita clouds instead. When those cannot
     * draw (unsupported GPU, shader pack, a render error) the sky simply has no clouds.
     */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void incognita$renderClouds(PoseStack poseStack, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
                                        float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        ci.cancel();
        CloudsClient.render(poseStack, frustumMatrix, projectionMatrix, partialTick, camX, camY, camZ);
    }
}
