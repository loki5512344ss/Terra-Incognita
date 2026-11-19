package dev.sborka.incognita.weather.fog;

/**
 * Pure fog math, free of Minecraft types so it can be unit tested.
 *
 * <p>Fog is a deterministic function of game time: every client computes the same value, so no
 * networking is needed. Dense banks drift in on a slow noise curve, are thicker around dawn and
 * get an extra push from rain.
 */
public final class FogModel {
    /** Ticks per noise cell: about 0.75 of a Minecraft day between fog peaks. */
    static final double CELL_TICKS = 18_000.0;
    private static final long DAY_TICKS = 24_000L;

    private FogModel() {}

    /**
     * Fog density in 0..1 (0 = clear, 1 = thickest allowed).
     *
     * @param gameTime  world game time in ticks
     * @param dayTime   time of day in ticks (any value, wrapped to 0..24000)
     * @param rain      rain level in 0..1
     * @param max       upper bound for the result, 0..1
     * @param frequency how often banks appear; 1 is the default, higher is more often
     * @param morning   whether dawn adds extra fog
     */
    public static double intensity(long gameTime, long dayTime, double rain, double max, double frequency,
                                   boolean morning) {
        if (max <= 0.0) {
            return 0.0;
        }
        double base = noise(gameTime / CELL_TICKS * Math.max(frequency, 0.01));
        double bank = smoothstep(0.55, 0.85, base);
        double dawn = morning ? dawnWeight(dayTime) * base : 0.0;
        double wet = clamp01(rain) * 0.25 * base;
        return clamp01(bank + 0.5 * dawn + wet) * clamp01(max);
    }

    /**
     * Far fog distance for a given density.
     *
     * @param vanillaFar vanilla far plane distance in blocks
     * @param intensity  density from {@link #intensity}
     * @param minFar     fog distance at full density, in blocks
     */
    public static float farDistance(float vanillaFar, double intensity, float minFar) {
        double t = Math.pow(clamp01(intensity), 0.7);
        float target = Math.min(minFar, vanillaFar);
        return (float) (vanillaFar + (target - vanillaFar) * t);
    }

    /** 1 at sunrise, fading to 0 about 2500 ticks either side. */
    static double dawnWeight(long dayTime) {
        long t = Math.floorMod(dayTime, DAY_TICKS);
        double distance = Math.min(Math.abs(t - 0L), Math.abs(t - DAY_TICKS));
        return 1.0 - smoothstep(0.0, 2_500.0, distance);
    }

    /** Smooth 1D value noise in 0..1; integer cells hashed, cubic interpolation between them. */
    public static double noise(double x) {
        long cell = (long) Math.floor(x);
        double f = x - cell;
        double a = hash(cell);
        double b = hash(cell + 1);
        double u = f * f * (3.0 - 2.0 * f);
        return a + (b - a) * u;
    }

    private static double hash(long n) {
        long h = n * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) / (double) (1L << 53);
    }

    static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
