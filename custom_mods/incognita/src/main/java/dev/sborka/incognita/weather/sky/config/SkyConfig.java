package dev.sborka.incognita.weather.sky.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The {@code [sky]} section of {@code config/incognita-weather.toml}; defined on the builder owned by FogConfig. */
public final class SkyConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.DoubleValue starDensity;
    private static ModConfigSpec.DoubleValue hazeStrength;

    private SkyConfig() {}

    /** Adds the sky section to the given builder. Called once while the weather spec is built. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("sky");
        enabled = builder.comment("Custom procedural sky; false keeps the vanilla sky (for example with a sky mod)")
            .define("enabled", true);
        starDensity = builder.comment("How many stars are drawn at night (1 = default, 0 = none)")
            .defineInRange("starDensity", 1.0, 0.0, 3.0);
        hazeStrength = builder.comment("Thickness of the horizon haze in clear weather (1 = default, 0 = crisp horizon)")
            .defineInRange("hazeStrength", 1.0, 0.0, 2.0);
        builder.pop();
    }

    public static boolean enabled() {
        try {
            return enabled.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static double starDensity() {
        try {
            return starDensity.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    public static double hazeStrength() {
        try {
            return hazeStrength.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }
}
