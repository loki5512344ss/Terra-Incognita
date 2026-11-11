package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Supplementaries "Globe" Create display source writes "X: n, Z: n" of the globe into display boards. The text is stored in
 * the target block entity for every viewer, so there is no player to consult: always masked.
 */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.integration.create.GlobeDisplaySource", remap = false)
public abstract class GlobeDisplaySourceMixin {
    @WrapOperation(
        method = "provideLine",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;", ordinal = 0),
        remap = false,
        require = 0)
    private MutableComponent incognita$maskGlobe(String text, Operation<MutableComponent> original) {
        return original.call(CompatMasking.anonymousShortPos(text));
    }
}
