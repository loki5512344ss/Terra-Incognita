package dev.sborka.incognita.weather.fog;

import dev.sborka.incognita.weather.clouds.config.CloudConfig;
import dev.sborka.incognita.weather.precip.PrecipConfig;
import dev.sborka.incognita.weather.sky.config.SkyConfig;


import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: {@code config/incognita-weather.toml}. */
public final class FogConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.DoubleValue MAX_INTENSITY;
    private static final ModConfigSpec.IntValue MIN_DISTANCE;
    private static final ModConfigSpec.DoubleValue FREQUENCY;
    private static final ModConfigSpec.BooleanValue MORNING_FOG;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder.comment("Dense atmospheric fog banks")
            .define("enabled", true);
        MAX_INTENSITY = builder.comment("Strongest fog density, 0 to 1")
            .defineInRange("maxIntensity", 0.9, 0.0, 1.0);
        MIN_DISTANCE = builder.comment("Visibility in blocks at the densest fog")
            .defineInRange("minDistance", 12, 2, 256);
        FREQUENCY = builder.comment("How often fog banks appear (1 = default, higher = more often)")
            .defineInRange("frequency", 1.0, 0.1, 10.0);
        MORNING_FOG = builder.comment("Extra fog around sunrise")
            .define("morningFog", true);
        CloudConfig.define(builder);
        SkyConfig.define(builder);
        PrecipConfig.define(builder);
        SPEC = builder.build();
    }

    private FogConfig() {}

    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static double maxIntensity() {
        try {
            return MAX_INTENSITY.get();
        } catch (IllegalStateException notLoaded) {
            return 0.9;
        }
    }

    public static int minDistance() {
        try {
            return MIN_DISTANCE.get();
        } catch (IllegalStateException notLoaded) {
            return 12;
        }
    }

    public static double frequency() {
        try {
            return FREQUENCY.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    public static boolean morningFog() {
        try {
            return MORNING_FOG.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
