package dev.sborka.incognita.spawn;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: {@code config/incognita-spawn.toml}. */
public final class SpawnConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue FIRST_JOIN_RANDOM;
    private static final ModConfigSpec.BooleanValue RESPAWN_RANDOM;
    private static final ModConfigSpec.IntValue MIN_RADIUS;
    private static final ModConfigSpec.IntValue MAX_RADIUS;
    private static final ModConfigSpec.IntValue ATTEMPTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FIRST_JOIN_RANDOM = builder.comment("Random spawn point when a player joins the world for the first time")
            .define("firstJoinRandom", true);
        RESPAWN_RANDOM = builder.comment("Random respawn point after death when the player has no bed or respawn anchor")
            .define("respawnRandom", true);
        MIN_RADIUS = builder.comment("Minimum distance from the world center (0, 0)")
            .defineInRange("minRadius", 0, 0, 29_999_000);
        MAX_RADIUS = builder.comment("Maximum distance from the world center (0, 0)")
            .defineInRange("maxRadius", 1000, 0, 29_999_000);
        ATTEMPTS = builder.comment("How many random points to try before falling back to the world spawn")
            .defineInRange("attempts", 32, 1, 512);
        SPEC = builder.build();
    }

    private SpawnConfig() {}

    public static boolean firstJoinRandom() {
        try {
            return FIRST_JOIN_RANDOM.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean respawnRandom() {
        try {
            return RESPAWN_RANDOM.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static int minRadius() {
        try {
            return MIN_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return 0;
        }
    }

    public static int maxRadius() {
        try {
            return MAX_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return 1000;
        }
    }

    public static int attempts() {
        try {
            return ATTEMPTS.get();
        } catch (IllegalStateException notLoaded) {
            return 32;
        }
    }
}
