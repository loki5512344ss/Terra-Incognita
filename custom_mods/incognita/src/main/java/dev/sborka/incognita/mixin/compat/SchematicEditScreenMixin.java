package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create's schematic deploy screen pre-fills X/Y/Z with the player's or the anchor's real position. The boxes keep
 * their real value (removed() parses it back to place the schematic); only the rendering is masked.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.schematics.client.SchematicEditScreen", remap = false)
public abstract class SchematicEditScreenMixin {
    @Shadow(remap = false)
    private EditBox xInput;

    @Shadow(remap = false)
    private EditBox yInput;

    @Shadow(remap = false)
    private EditBox zInput;

    @Inject(method = "init", at = @At("TAIL"), remap = false, require = 0)
    private void incognita$maskCoordinateBoxes(CallbackInfo ci) {
        try {
            for (EditBox box : new EditBox[] {xInput, yInput, zInput}) {
                if (box != null) {
                    box.setFormatter(CompatMasking::formatEditBox);
                }
            }
        } catch (Throwable ignored) {
            // fail open
        }
    }
}
