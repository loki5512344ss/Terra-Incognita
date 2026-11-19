package dev.sborka.incognita.weather.precip;

/** Pure arithmetic of the precipitation renderer: particle budget and the daylight factor of the drop color. */
public final class PrecipitationMath {
    private PrecipitationMath() {}

    /**
     * Number of particles to draw for the current weather.
     *
     * @param max       size of the particle pool
     * @param density   config multiplier, 1 = default
     * @param intensity rain (or thunder) level, 0..1
     * @param snow      snow gets slightly fewer particles because flakes are larger
     */
    public static int particleCount(int max, double density, float intensity, boolean snow) {
        float scale = (float) (density * (snow ? 0.92 : 1.0));
        return Math.round(max * clamp01(scale * clamp01(intensity)));
    }

    /** 0 at night, 1 in full daylight, with a soft twilight ramp. */
    public static float lightLevel(float sunHeight) {
        float t = clamp01((sunHeight + 0.10f) / 0.35f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
