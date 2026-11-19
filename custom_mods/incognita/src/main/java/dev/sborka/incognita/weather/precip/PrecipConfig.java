package dev.sborka.incognita.weather.precip;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The {@code [precipitation]} section of {@code config/incognita-weather.toml}; defined on the builder owned by
 * {@link dev.sborka.incognita.weather.fog.FogConfig}. */
public final class PrecipConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.DoubleValue density;

    /** Dev test hook: forces snow (true), rain (false), or the real biome precipitation (null). */
    private static volatile Boolean testSnow;

    private PrecipConfig() {}

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("precipitation");
        enabled = builder.comment("Wind blown rain and snow with splashes and roof occlusion; false falls back to the vanilla weather")
            .define("enabled", false);
        density = builder.comment("Particle density, 1 = default")
            .defineInRange("density", 1.0, 0.1, 3.0);
        builder.pop();
    }

    /** Dev test hook: forces the renderer on (true), off (false), or follows the config (null). */
    private static volatile Boolean testEnabled;

    public static void setTestEnabled(Boolean value) {
        testEnabled = value;
    }

    public static boolean enabled() {
        Boolean forced = testEnabled;
        if (forced != null) {
            return forced;
        }
        try {
            return enabled.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static double density() {
        try {
            return density.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    public static Boolean testSnow() {
        return testSnow;
    }

    /** Dev test hook: forces snow (true), rain (false), or the real biome precipitation (null). */
    public static void setTestSnow(Boolean snow) {
        testSnow = snow;
    }
}
