package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.ClientMasking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Immersive Engineering (wire coil HUD "Linking from x, y, z") builds HUD strings with I18n.get, which never creates a
 * TranslatableContents. Masks listed keys' numeric arguments here with the same helper. Client only, fails open.
 */
@Mixin(I18n.class)
public abstract class I18nMixin {
    @WrapMethod(method = "get", require = 0)
    private static String incognita$maskArgs(String key, Object[] args, Operation<String> original) {
        Object[] safe = args;
        try {
            safe = ClientMasking.maskArgs(key, args);
        } catch (Throwable ignored) {
            // fail open
        }
        return original.call(key, safe);
    }
}
