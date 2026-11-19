package dev.sborka.incognita.mixin.sky;

import dev.sborka.incognita.weather.sky.render.SkyClient;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    /**
     * Replaces the vanilla sky (gradient, sunrise band, sun, moon, stars) with the procedural sky. When it is
     * unavailable (shader pack, fabulous graphics, another dimension type, a render error) the vanilla sky runs
     * unchanged.
     */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void incognita$renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick,
                                     Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        if (SkyClient.render(frustumMatrix, projectionMatrix, partialTick, camera, isFoggy, skyFogSetup)) {
            ci.cancel();
        }
    }
}
