package dev.sborka.incognita.mixin.compat;

import dev.sborka.incognita.coordinates.client.CompatMasking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Slice;

/** GraveStone obituary: masks the three X/Y/Z values drawn after the "Location" label. */
@Pseudo
@Mixin(targets = "de.maxhenkel.gravestone.gui.ObituaryScreen", remap = false)
public abstract class GravestoneObituaryMixin {
    @WrapOperation(
        method = "drawFirstPage",
        slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=gui.obituary.location")),
        at = @At(value = "INVOKE", target = "Lde/maxhenkel/gravestone/gui/ObituaryScreen;drawRight("
            + "Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/network/chat/MutableComponent;I)V"),
        remap = false,
        require = 0)
    private void incognita$maskDeathPosition(
        @Coerce Object screen, GuiGraphics graphics, MutableComponent value, int y, Operation<Void> original) {
        MutableComponent shown = CompatMasking.clientMasks()
            ? Component.literal(CompatMasking.clientShortPos(value.getString())).setStyle(value.getStyle())
            : value;
        original.call(screen, graphics, shown, y);
    }
}
