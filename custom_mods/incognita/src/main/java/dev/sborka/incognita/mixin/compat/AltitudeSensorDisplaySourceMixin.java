package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Simulated altitude sensor display source. In provideLine, String.format ordinal 0 formats air pressure ("%.2f%%") and
 * ordinal 1 formats the world height ("%.2f"); only ordinal 1 is masked (verified with javap, offsets 113 and 136).
 * Always masked: the text lives in a shared block entity.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.display_sources.AltitudeSensorDisplaySource", remap = false)
public abstract class AltitudeSensorDisplaySourceMixin {
    @WrapOperation(
        method = "provideLine",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", ordinal = 1),
        remap = false,
        require = 0)
    private String incognita$maskHeight(String format, Object[] args, Operation<String> original) {
        return CompatMasking.anonymousShortPos(original.call(format, args));
    }
}
