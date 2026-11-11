package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.reveal.RevealState;

import java.util.Collection;
import java.util.Collections;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The chat box suggests the real looked-at block or hit position for every position argument (/tp, /setblock, /fill ...).
 * While masking, offer only "~ ~ ~", which is what vanilla does when nothing is targeted.
 */
@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {
    @Inject(method = "getRelevantCoordinates", at = @At("HEAD"), cancellable = true, require = 0)
    private void incognita$hideRelevant(CallbackInfoReturnable<Collection<SharedSuggestionProvider.TextCoordinates>> cir) {
        incognita$replace(cir);
    }

    @Inject(method = "getAbsoluteCoordinates", at = @At("HEAD"), cancellable = true, require = 0)
    private void incognita$hideAbsolute(CallbackInfoReturnable<Collection<SharedSuggestionProvider.TextCoordinates>> cir) {
        incognita$replace(cir);
    }

    private static void incognita$replace(CallbackInfoReturnable<Collection<SharedSuggestionProvider.TextCoordinates>> cir) {
        try {
            if (!RevealState.isClientRevealed()) {
                cir.setReturnValue(Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL));
            }
        } catch (Throwable ignored) {
            // fail open
        }
    }
}
