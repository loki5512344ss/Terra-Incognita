package dev.sborka.incognita.lighting.mixin;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.lighting.sodium.SodiumTint;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 0.8 block mesher. {@code bufferQuad} is the last step before a quad becomes vertices, after Sodium's own
 * light pipeline ran, so the lightmap and color seen here are final vanilla values we adjust. Soft: with
 * {@code @Pseudo} and {@code require = 0} the mod loads without Sodium or when the method changed.
 *
 * <p>The block position is a protected field of the target's superclass {@code AbstractBlockRenderContext}. Mixin cannot
 * {@code @Shadow} inherited fields, so this mixin extends that superclass and reads {@code pos} directly.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin extends AbstractBlockRenderContext {
    private static boolean incognita$failed;

    @Inject(method = "bufferQuad", at = @At("HEAD"), remap = false, require = 0)
    private void incognita$tint(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        if (incognita$failed) {
            return;
        }
        try {
            SodiumTint.apply(quad, this.pos);
        } catch (Throwable t) {
            incognita$failed = true;
            Incognita.LOGGER.error("Colored light disabled after an error in the Sodium hook", t);
        }
    }
}
