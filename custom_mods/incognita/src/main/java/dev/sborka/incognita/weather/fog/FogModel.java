package dev.sborka.incognita.weather.fog;

/**
 * Pure fog math, free of Minecraft types so it can be unit tested.
 *
 * <p>Fog is a deterministic function of game time: every client computes the same value, so no
 * networking is needed. It is a rare weather event: each game day has a small chance of one fog that
 * rolls in, stays for a few minutes and leaves. Dawn and rain only thicken a fog that is already there.
 */
public final class FogModel {
    /** At most one fog per slot of this length (one game day, 20 minutes). */
    private static final long SLOT_TICKS = 24_000L;
    /** Whole life of one fog, fades included (under 7 minutes). */
    private static final long EVENT_TICKS = 8_000L;
    /** Time to roll in, and again to clear (100 seconds). */
    private static final long FADE_TICKS = 2_000L;
    /** Share of game days that get a fog at frequency 1: on average one per 8 days, about 2 h 40 min. */
    public static final double DAILY_CHANCE = 0.12;
    private static final double WEAKEST = 0.75;
    private static final long CHANCE_SALT = 0x5D1CE4E5B9L;
    private static final long START_SALT = 0x2F0C94D049BL;
    private static final long STRENGTH_SALT = 0x71BB133111EL;
    private static final long DAY_TICKS = 24_000L;

    private FogModel() {}

    /**
     * Fog density in 0..1 (0 = clear, 1 = thickest allowed).
     *
     * @param gameTime  world game time in ticks
     * @param dayTime   time of day in ticks (any value, wrapped to 0..24000)
     * @param rain      rain level in 0..1
     * @param max       upper bound for the result, 0..1
     * @param frequency how often fog happens; 1 is the default, higher is more often
     * @param morning   whether a fog at dawn is thicker
     */
    public static double intensity(long gameTime, long dayTime, double rain, double max, double frequency,
                                   boolean morning) {
        if (max <= 0.0) {
            return 0.0;
        }
        double bank = bank(gameTime, frequency);
        double dawn = morning ? 0.25 * dawnWeight(dayTime) : 0.0;
        double wet = 0.25 * clamp01(rain);
        return clamp01(bank * (1.0 + dawn + wet)) * clamp01(max);
    }

    /** The fog event running at {@code gameTime}, 0 on most of the timeline. */
    private static double bank(long gameTime, double frequency) {
        long slot = Math.floorDiv(gameTime, SLOT_TICKS);
        if (hash(slot ^ CHANCE_SALT) >= DAILY_CHANCE * Math.max(frequency, 0.0)) {
            return 0.0;
        }
        long start = slot * SLOT_TICKS + (long) (hash(slot ^ START_SALT) * (SLOT_TICKS - EVENT_TICKS));
        long age = gameTime - start;
        if (age <= 0 || age >= EVENT_TICKS) {
            return 0.0;
        }
        double envelope = smoothstep(0, FADE_TICKS, age) * (1.0 - smoothstep(EVENT_TICKS - FADE_TICKS, EVENT_TICKS, age));
        return envelope * (WEAKEST + (1.0 - WEAKEST) * hash(slot ^ STRENGTH_SALT));
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
