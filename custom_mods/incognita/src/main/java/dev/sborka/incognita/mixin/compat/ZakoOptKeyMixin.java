package dev.sborka.incognita.mixin.compat;

import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes the ZakoOpt monitor key from the controls screen after options are loaded (the key does nothing now). */
@Mixin(Options.class)
public abstract class ZakoOptKeyMixin {
    @Mutable
    @Final
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("TAIL"), require = 0)
    private void incognita$dropZakoKey(CallbackInfo ci) {
        keyMappings = Arrays.stream(keyMappings)
            .filter(key -> !"key.zakoopt.monitor".equals(key.getName()))
            .toArray(KeyMapping[]::new);
    }
}
