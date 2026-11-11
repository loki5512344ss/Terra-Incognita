package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Create Radar guided fuze tooltip: masks the linked block position text on the client. */
@Pseudo
@Mixin(targets = "com.happysg.radar.item.GuidedFuzeItem", remap = false)
public abstract class GuidedFuzeItemMixin {
    @WrapOperation(
        method = "appendHoverText",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;toShortString()Ljava/lang/String;"),
        remap = false,
        require = 0)
    private String incognita$maskPosition(BlockPos pos, Operation<String> original) {
        return CompatMasking.clientShortPos(original.call(pos));
    }
}
