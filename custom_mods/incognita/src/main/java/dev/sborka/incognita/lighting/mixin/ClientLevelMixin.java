package dev.sborka.incognita.lighting.mixin;

import dev.sborka.incognita.lighting.client.LightingClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the light field about every block change the client applies (LambDynamicLights hooks other methods). */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
    private void incognita$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        try {
            LightingClient.onBlockUpdated(pos, oldState, newState);
        } catch (Throwable ignored) {
            // fail open: lighting must never break block updates
        }
    }
}
