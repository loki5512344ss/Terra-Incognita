package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Flywheel {@code /flywheel debug info} prints (and copies) the render origin, a real block position. Replaced while masking. */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.FlwDebugInfo", remap = false)
public abstract class FlwDebugInfoMixin {
    @Inject(method = "getDebugCommandInfo", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void incognita$hideOrigin(CallbackInfoReturnable<Component> cir) {
        try {
            if (CompatMasking.clientMasks()) {
                cir.setReturnValue(Component.literal("Flywheel debug info is hidden by Incognita (it contains world coordinates)."));
            }
        } catch (Throwable ignored) {
            // fail open
        }
    }
}
