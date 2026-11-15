package dev.sborka.incognita.compass;

import dev.sborka.incognita.Incognita;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: where the player's own compass should point. Empty means "nowhere", the needle spins. */
public record HomePayload(Optional<GlobalPos> home) implements CustomPacketPayload {
    public static final Type<HomePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "compass_home"));
    public static final StreamCodec<ByteBuf, HomePayload> STREAM_CODEC =
        ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).map(HomePayload::new, HomePayload::home);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(HomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HomeTarget.setClient(payload.home().orElse(null)));
    }
}
