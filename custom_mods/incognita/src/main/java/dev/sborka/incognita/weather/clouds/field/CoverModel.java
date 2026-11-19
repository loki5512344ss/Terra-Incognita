package dev.sborka.incognita.weather.clouds.field;

/**
 * Maps the weather to the cloud field. A heap exists where the coverage noise (0..1) exceeds the threshold of the
 * current cover; its strength is how far it exceeds it. Geometry is built with a margin below the threshold, and the
 * vertex shader grows or shrinks every puff from the live threshold, so a change of the weather never pops clouds
 * in or out; only a change larger than {@link #REBUILD_STEP} rebuilds the geometry.
 */
public final class CoverModel {
    /** Heaps this far below the threshold are still generated (invisible until the cover rises). */
    public static final float MARGIN = 0.14f;
    /** Threshold change that triggers a rebuild; well inside the margin. */
    public static final float REBUILD_STEP = 0.07f;
    /** Strength of the deck cells that make the low band watertight; they show from a cover of about 0.8. */
    public static final float DECK_NOISE = 0.31f;
    /** Generation margin of the deck cells; larger than the rebuild step plus the shader's growth band. */
    public static final float DECK_MARGIN = 0.12f;

    private CoverModel() {}

    /**
     * Noise threshold for a weather cover.
     *
     * @param cover cloud cover of the weather model, 0.35 (scattered) .. 1 (overcast)
     * @param bias  band specific bias, positive = sparser
     */
    public static float threshold(float cover, float bias) {
        float c = clamp01(cover);
        // Calibrated against the coverage noise: 0.35 cover leaves roughly a fifth of the cells with a heap,
        // 0.5 about 45 %, 0.85 about 90 %; the deck cells (DECK_NOISE) appear above ~0.83.
        return 0.844f - 0.64f * c + bias;
    }

    /**
     * Drift multiplier of all bands: the wind picks up with rain and storms so the deck visibly races.
     */
    public static float windFactor(float rain, float thunder) {
        return 1.0f + 1.6f * clamp01(rain) + 1.2f * clamp01(thunder);
    }

    /** Whether geometry built for {@code built} must be rebuilt for {@code current}. */
    public static boolean needsRebuild(float current, float built) {
        return Math.abs(current - built) >= REBUILD_STEP;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }
}
