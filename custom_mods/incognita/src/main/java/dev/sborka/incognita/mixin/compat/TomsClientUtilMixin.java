package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tom's Storage inventory configurator HUD: forces the "reduced info" variant (block name only) while coordinates are
 * hidden. The bound position itself is untouched because the overlay also uses it for distance checks.
 */
@Pseudo
@Mixin(targets = "com.tom.storagemod.client.ClientUtil", remap = false)
public abstract class TomsClientUtilMixin {
    @WrapOperation(
        method = "drawConfiguratorOverlay",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;showOnlyReducedInfo()Z"),
        remap = false,
        require = 0)
    private static boolean incognita$reducedInfo(Minecraft minecraft, Operation<Boolean> original) {
        return CompatMasking.clientMasks() || original.call(minecraft);
    }
}
