package dev.sborka.incognita.lighting.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: {@code config/incognita-lighting.toml}. */
public final class LightingConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue RADIUS;
    private static final ModConfigSpec.DoubleValue STRENGTH;
    private static final ModConfigSpec.BooleanValue DYNAMIC;
    private static final ModConfigSpec.IntValue DYNAMIC_MAX_LEVEL;
    private static final ModConfigSpec.IntValue MAX_REBUILDS_PER_TICK;
    private static final ModConfigSpec.BooleanValue HALO;
    private static final ModConfigSpec.IntValue HALO_RANGE;
    private static final ModConfigSpec.DoubleValue HALO_STRENGTH;
    private static final ModConfigSpec.BooleanValue DEBUG_STATS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Colored block light (needs Sodium)").define("enabled", true);
        RADIUS = b.comment("Blocks a level 15 colored light travels (vanilla: 15)")
            .defineInRange("radius", 24, 4, 48);
        STRENGTH = b.comment("How strongly the color tints surfaces, 0 to 1")
            .defineInRange("strength", 1.0, 0.0, 1.0);
        DYNAMIC = b.comment("Colored light from held items, dropped items and burning entities")
            .define("dynamic", true);
        DYNAMIC_MAX_LEVEL = b.comment("Cap for moving lights, lower is cheaper (fewer chunk rebuilds)")
            .defineInRange("dynamicMaxLevel", 12, 1, 15);
        MAX_REBUILDS_PER_TICK = b.comment("Chunk sections queued for rebuild per tick at most, the rest waits")
            .defineInRange("maxRebuildsPerTick", 24, 1, 512);
        HALO = b.comment("Soft glow sprites around strong colored lamps, so they stay readable in dense fog")
            .define("halo", true);
        HALO_RANGE = b.comment("How far away (blocks) lamp halos are drawn")
            .defineInRange("haloRange", 112, 16, 256);
        HALO_STRENGTH = b.comment("Brightness of the halos, 0 to 2")
            .defineInRange("haloStrength", 1.0, 0.0, 2.0);
        DEBUG_STATS = b.comment("Log field update cost every few seconds").define("debugStats", false);
        SPEC = b.build();
    }

    private LightingConfig() {}

    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static int radius() {
        try {
            return RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return 24;
        }
    }

    public static float strength() {
        try {
            return STRENGTH.get().floatValue();
        } catch (IllegalStateException notLoaded) {
            return 1f;
        }
    }

    public static boolean dynamic() {
        try {
            return DYNAMIC.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static int dynamicMaxLevel() {
        try {
            return DYNAMIC_MAX_LEVEL.get();
        } catch (IllegalStateException notLoaded) {
            return 12;
        }
    }

    public static int maxRebuildsPerTick() {
        try {
            return MAX_REBUILDS_PER_TICK.get();
        } catch (IllegalStateException notLoaded) {
            return 24;
        }
    }

    public static boolean debugStats() {
        try {
            return DEBUG_STATS.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    public static boolean halo() {
        try {
            return HALO.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static int haloRange() {
        try {
            return HALO_RANGE.get();
        } catch (IllegalStateException notLoaded) {
            return 112;
        }
    }

    public static float haloStrength() {
        try {
            return HALO_STRENGTH.get().floatValue();
        } catch (IllegalStateException notLoaded) {
            return 1f;
        }
    }
}
