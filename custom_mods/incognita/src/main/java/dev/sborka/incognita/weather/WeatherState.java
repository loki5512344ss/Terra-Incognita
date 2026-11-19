package dev.sborka.incognita.weather;

import dev.sborka.incognita.weather.fog.FogModel;


/**
 * Pure weather model that decides how overcast the sky is. It builds on the same deterministic inputs as
 * {@link FogModel} (game time, time of day, rain) so clouds, fog and rain always agree, and it never reads the
 * vanilla thunder or rain flags on its own.
 */
public final class WeatherState {
    /** Lowest cover: a few scattered clouds. */
    public static final double MIN_CLOUDINESS = 0.35;
    /** Highest cover: a solid overcast sky without a single hole. */
    public static final double MAX_CLOUDINESS = 1.0;

    private static final double BASE = 0.5;
    private static final double BASE_SWING = 0.1;
    private static final double WET_WEIGHT = 0.35;
    private static final double FOG_WEIGHT = 0.15;
    /** Rain is an overcast deck, not scattered clouds: once it rains the cover goes to the top of the range. */
    /** Ticks per noise cell of the slow cover variation, longer than the fog cell so they drift apart. */
    private static final double COVER_CELL_TICKS = 30_000.0;
    private static final double COVER_NOISE_OFFSET = 7.3;

    private WeatherState() {}

    /**
     * Cloud cover in {@link #MIN_CLOUDINESS}..{@link #MAX_CLOUDINESS}.
     *
     * @param gameTime  world game time in ticks
     * @param dayTime   time of day in ticks
     * @param rain      rain level in 0..1
     * @param thunder   thunder level in 0..1
     * @param fogFrequency  fog bank frequency, as in {@link FogModel#intensity}
     * @param morningFog    whether dawn fog is enabled
     */
    public static double cloudiness(long gameTime, long dayTime, double rain, double thunder, double fogFrequency,
                                    boolean morningFog) {
        double slow = FogModel.noise(gameTime / COVER_CELL_TICKS + COVER_NOISE_OFFSET);
        double base = BASE + BASE_SWING * (2.0 * slow - 1.0);
        double wet = Math.max(0.6 * clamp01(rain), clamp01(thunder));
        // Fog banks are low stratus: thick fog comes with a heavier sky.
        double fog = FogModel.intensity(gameTime, dayTime, rain, 1.0, fogFrequency, morningFog);
        double value = base + WET_WEIGHT * wet + FOG_WEIGHT * fog;
        // A rainy sky is solid from horizon to horizon; leave no gaps for the sun to shine through.
        double strength = clamp01(Math.max(rain, thunder));
        if (strength > 0.0) {
            value = Math.max(value, MIN_CLOUDINESS + (MAX_CLOUDINESS - MIN_CLOUDINESS) * strength);
        }
        return Math.max(MIN_CLOUDINESS, Math.min(MAX_CLOUDINESS, value));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}
