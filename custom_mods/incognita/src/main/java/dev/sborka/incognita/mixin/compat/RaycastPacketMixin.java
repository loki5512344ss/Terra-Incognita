package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Create Radar binoculars: "Target: x, y, z" action bar. LastHitPos (turret aim) is untouched. */
@Pseudo
@Mixin(targets = "com.happysg.radar.networking.packets.RaycastPacket", remap = false)
public abstract class RaycastPacketMixin {
    @WrapOperation(
        method = "lambda$handle$0",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;toShortString()Ljava/lang/String;"),
        remap = false,
        require = 0)
    private static String incognita$maskTarget(BlockPos pos, Operation<String> original, @Local(ordinal = 0) ServerPlayer player) {
        return CompatMasking.serverShortPos(original.call(pos), player);
    }
}
