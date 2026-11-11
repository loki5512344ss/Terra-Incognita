package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create Radar "Monitor Target" display source. The text is stored in the display target's block entity and shown to
 * every viewer, so there is no player to consult: always masked.
 */
@Pseudo
@Mixin(targets = "com.happysg.radar.block.monitor.MonitorTargetDisplayBehavior", remap = false)
public abstract class MonitorTargetDisplayBehaviorMixin {
    @WrapOperation(
        method = "provideText",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Vec3i;toShortString()Ljava/lang/String;"),
        remap = false,
        require = 0)
    private String incognita$maskTarget(Vec3i pos, Operation<String> original) {
        return CompatMasking.anonymousShortPos(original.call(pos));
    }
}
