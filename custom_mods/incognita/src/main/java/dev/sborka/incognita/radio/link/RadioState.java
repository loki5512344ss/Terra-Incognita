package dev.sborka.incognita.radio.link;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sborka.incognita.radio.RadioModule;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Per-stack radio data. {@code freq} is in tenths of MHz (1000 = 100.0 MHz). */
public record RadioState(int freq, boolean on) {
    public static final int DEFAULT_FREQ = 1000;
    public static final RadioState DEFAULT = new RadioState(DEFAULT_FREQ, false);

    public static final Codec<RadioState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("freq").forGetter(RadioState::freq),
        Codec.BOOL.fieldOf("on").forGetter(RadioState::on)
    ).apply(i, RadioState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, RadioState::freq,
        ByteBufCodecs.BOOL, RadioState::on,
        RadioState::new
    );

    public static RadioState of(ItemStack stack) {
        return stack.getOrDefault(RadioModule.STATE.get(), DEFAULT);
    }

    public RadioState withOn(boolean value) {
        return new RadioState(freq, value);
    }

    public RadioState withFreq(int value) {
        return new RadioState(value, on);
    }

    /** 1000 -> "100.0". */
    public static String format(int freq) {
        return String.format(Locale.ROOT, "%d.%d", freq / 10, Math.abs(freq % 10));
    }

    /** MHz as typed by a player (145.5) to tenths (1455). */
    public static int fromMegahertz(double mhz) {
        return (int) Math.round(mhz * 10.0);
    }

    public static int clamp(int freq, int min, int max) {
        return Math.max(min, Math.min(max, freq));
    }
}
