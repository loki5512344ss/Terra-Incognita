package dev.sborka.incognita.weather.clouds.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The {@code [clouds]} section of {@code config/incognita-weather.toml}; defined on the builder owned by FogConfig. */
public final class CloudConfig {
    public static final double DEFAULT_BASE_OFFSET = 60.0;
    public static final int DEFAULT_DRAW_DISTANCE = 1024;
    public static final double DEFAULT_DENSITY = 0.9;

    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.DoubleValue baseOffset;
    private static ModConfigSpec.DoubleValue density;
    private static ModConfigSpec.IntValue drawDistance;
    private static ModConfigSpec.BooleanValue forceLegacyPath;
    private static ModConfigSpec.BooleanValue halfResolution;
    private static volatile Boolean testHalfResolution;
    private static volatile boolean testForceLegacy;
    private static volatile boolean testDisabled;

    private CloudConfig() {}

    /** Adds the clouds section to the given builder. Called once while the weather spec is built. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("clouds");
        enabled = builder.comment("Volumetric clouds; false shows no clouds at all (the vanilla clouds are always off)")
            .define("enabled", true);
        baseOffset = builder.comment("Blocks added to the dimension cloud height (192); 60 puts the cumulus base at "
                + "Y 252, altocumulus at ~402 and cirrus at ~562")
            .defineInRange("cloudBaseOffset", DEFAULT_BASE_OFFSET, -128.0, 512.0);
        density = builder.comment("Opacity of a dense cloud core, 0 to 1 (edges stay soft, rain makes clouds denser)")
            .defineInRange("density", DEFAULT_DENSITY, 0.2, 1.0);
        drawDistance = builder.comment("How far clouds are generated and drawn, in blocks")
            .defineInRange("drawDistance", DEFAULT_DRAW_DISTANCE, 256, 2048);
        halfResolution = builder.comment("Render the cloud passes at half resolution (much cheaper, edges stay smooth)")
            .define("halfResolution", true);
        forceLegacyPath = builder.comment("Use the OpenGL 3.3 code path even when OpenGL 4.3 is available (for testing)")
            .define("forceLegacyPath", false);
        builder.pop();
    }

    public static boolean enabled() {
        return !testDisabled && read(enabled, true);
    }

    public static double baseOffset() {
        return read(baseOffset, DEFAULT_BASE_OFFSET);
    }

    public static double density() {
        return read(density, DEFAULT_DENSITY);
    }

    public static int drawDistance() {
        return read(drawDistance, DEFAULT_DRAW_DISTANCE);
    }

    /** Whether the OpenGL 3.3 path is forced, by the config or by the dev test driver. */
    public static boolean forceLegacyPath() {
        return testForceLegacy || read(forceLegacyPath, false);
    }

    /** Whether the cloud passes run at half resolution, by the config or by the dev test driver. */
    public static boolean halfResolution() {
        Boolean test = testHalfResolution;
        return test != null ? test : read(halfResolution, true);
    }

    /** Dev test hook: forces half (true) or full (false) resolution; null follows the config. */
    public static void setTestHalfResolution(Boolean half) {
        testHalfResolution = half;
    }

    /** Dev test hook: forces the legacy path regardless of the config file. */
    public static void setTestForceLegacy(boolean force) {
        testForceLegacy = force;
    }

    /** Dev test hook: hides the clouds entirely, for screenshots of the sky alone and for the cost of the pass. */
    public static void setTestDisabled(boolean disabled) {
        testDisabled = disabled;
    }

    /** Value of a config entry, or the fallback before the config is loaded (or when it is not defined). */
    private static <T> T read(ModConfigSpec.ConfigValue<T> value, T fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }
}
