package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create train collision message "In dim near [x, y, z]". The recipients are not known at this call site, so the position is
 * always masked here (the message also passes through the player-aware server rewriter, which would honour /incognita reveal).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.entity.TrainStatus", remap = false)
public abstract class TrainStatusMixin {
    @WrapOperation(
        method = "lambda$crash$2",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;toShortString()Ljava/lang/String;"),
        remap = false,
        require = 0)
    private String incognita$maskCollisionPosition(BlockPos pos, Operation<String> original) {
        return CompatMasking.anonymousShortPos(original.call(pos));
    }
}
