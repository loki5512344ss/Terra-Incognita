package dev.sborka.incognita.mixin.rain;

import dev.sborka.incognita.weather.precip.PrecipitationClient;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererRainMixin {
    /**
     * Replaces the vanilla rain and snow quads with the wind blown particles of the weather renderer. If that
     * renderer is unavailable (disabled in the config, a shader pack, a render error) vanilla runs unchanged.
     */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void incognita$renderSnowAndRain(LightTexture lightTexture, float partialTick, double camX, double camY,
                                             double camZ, CallbackInfo ci) {
        if (PrecipitationClient.replacesVanilla()) {
            ci.cancel();
        }
    }
}
