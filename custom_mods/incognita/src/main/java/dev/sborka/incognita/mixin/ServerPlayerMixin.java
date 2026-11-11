package dev.sborka.incognita.mixin;

import dev.sborka.incognita.coordinates.Coordinates;
import dev.sborka.incognita.coordinates.reveal.RevealCommand;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @ModifyVariable(
        method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
        at = @At("HEAD"),
        argsOnly = true)
    private Component incognita$hideCoordinates(Component message) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (RevealCommand.canSeeRealCoordinates(self)) {
            return message;
        }
        return Coordinates.rewriter().rewriteSafely(message);
    }
}
