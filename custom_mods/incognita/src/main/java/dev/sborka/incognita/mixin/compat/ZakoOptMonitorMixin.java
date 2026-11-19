package dev.sborka.incognita.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the ZakoOpt in-game monitor panel and its Shift+key "turn the mod off" shortcut. The pack tunes the mod
 * through its config file, so the panel is only a distraction (and a way to switch optimisations off by accident).
 */
@Pseudo
@Mixin(targets = "zako.opt.bench.MonitorScreen", remap = false)
public abstract class ZakoOptMonitorMixin {
    @Inject(method = "pollKey", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void incognita$noMonitor(CallbackInfo ci) {
        ci.cancel();
    }
}
