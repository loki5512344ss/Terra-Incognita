package dev.sborka.incognita.weather.fog.control;

import dev.sborka.incognita.Incognita;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the current fog override. */
public record FogSyncPayload(FogOverride state) implements CustomPacketPayload {
    public static final Type<FogSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "fog_sync"));
    public static final StreamCodec<ByteBuf, FogSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, payload -> payload.state.mode().ordinal(),
        ByteBufCodecs.FLOAT, payload -> payload.state.density(),
        ByteBufCodecs.VAR_LONG, payload -> payload.state.since(),
        ByteBufCodecs.VAR_LONG, payload -> payload.state.until(),
        ByteBufCodecs.FLOAT, payload -> payload.state.frequency(),
        (mode, density, since, until, frequency) -> new FogSyncPayload(
            new FogOverride(FogOverride.Mode.values()[mode], density, since, until, frequency)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(FogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> FogOverride.setClient(payload.state));
    }
}
