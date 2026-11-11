package dev.sborka.incognita.mixin;

import dev.sborka.incognita.coordinates.client.ClientMasking;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Masks numeric arguments of listed translation keys at construction, on the client only. This covers components built by
 * Create/Tom's for goggle and HUD tooltips (which never fire ItemTooltipEvent) and components decoded from NBT or packets.
 */
@Mixin(TranslatableContents.class)
public abstract class TranslatableContentsMixin {
    @Shadow
    @Final
    private String key;

    @Shadow
    @Final
    @Mutable
    private Object[] args;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void incognita$maskArgs(String key, String fallback, Object[] args, CallbackInfo ci) {
        try {
            this.args = ClientMasking.maskArgs(this.key, this.args);
        } catch (Throwable ignored) {
            // fail open: a masking problem must never break component construction
        }
    }
}
