package dev.sborka.incognita.mixin;

import dev.sborka.incognita.coordinates.reveal.RevealState;

import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    /**
     * Swallows F3+C (copy location), F3+I (copy recreate command) and F3+H (advanced tooltips, which let mods dump raw
     * item data such as bound positions), which all expose real coordinates, while the client is not revealed. Every other F3 combination keeps working.
     */
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void incognita$blockCoordinateCopy(int key, CallbackInfoReturnable<Boolean> cir) {
        if ((key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_I || key == GLFW.GLFW_KEY_H) && !RevealState.isClientRevealed()) {
            cir.setReturnValue(true);
        }
    }
}
