package dev.sborka.incognita.coordinates.reveal;


import dev.sborka.incognita.Incognita;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: tells the client whether the operator revealed real coordinates. */
public record RevealSyncPayload(boolean revealed) implements CustomPacketPayload {
    public static final Type<RevealSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "reveal_sync"));
    public static final StreamCodec<ByteBuf, RevealSyncPayload> STREAM_CODEC =
        ByteBufCodecs.BOOL.map(RevealSyncPayload::new, RevealSyncPayload::revealed);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(RevealSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RevealState.setClientRevealed(payload.revealed()));
    }
}
