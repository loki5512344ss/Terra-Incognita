package dev.sborka.incognita.gear.anvil;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The {@code [anvil]} section of {@code incognita-gear.toml}. */
public final class AnvilConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.DoubleValue breakChanceMultiplier;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("anvil");
        breakChanceMultiplier = builder
            .comment("Multiplies the vanilla 12% per-repair break chance; 0.2 makes the anvil break about 5 times "
                + "less often (roughly 125 repairs on average instead of 25)")
            .defineInRange("breakChanceMultiplier", 0.2, 0.0, 1.0);
        builder.pop();
        SPEC = builder.build();
    }

    private AnvilConfig() {}

    static double breakChanceMultiplier() {
        return breakChanceMultiplier.get();
    }
}
