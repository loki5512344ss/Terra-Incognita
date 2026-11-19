package dev.sborka.incognita.lighting.halo;

/** Pure math of the lamp halos (no Minecraft types, unit tested). */
public final class HaloMath {
    /** Halos begin to appear this far from the camera; nearer lamps are lit by their own light pool. */
    static final float NEAR_START = 8.0f;
    static final float NEAR_FULL = 24.0f;
    /** Brightness of a halo in clear air, as a fraction of its brightness in fog. */
    static final float CLEAR_BASE = 0.10f;

    private HaloMath() {}

    /**
     * Peak alpha of a halo.
     *
     * @param dist      distance from the camera in blocks
     * @param range     maximum drawing distance
     * @param fogFar    far distance of the applied terrain fog, negative without fog
     * @param night     0 by day .. 1 at night
     * @param intensity emitter intensity 0..1
     * @param strength  config gain
     */
    public static float alpha(float dist, float range, float fogFar, float night, float intensity, float strength) {
        if (dist <= NEAR_START || dist >= range) {
            return 0.0f;
        }
        float near = smoothstep(NEAR_START, NEAR_FULL, dist);
        float edge = 1.0f - smoothstep(range * 0.55f, range, dist);
        float fogged = fogFar > 0.0f ? smoothstep(0.15f, 1.0f, dist / fogFar) : 0.0f;
        float base = CLEAR_BASE + (1.0f - CLEAR_BASE) * fogged;
        float dayScale = 0.12f + 0.88f * night;
        return near * edge * base * dayScale * intensity * strength;
    }

    /** Radius of the halo billboard in blocks: grows slowly with distance and swells in fog. */
    public static float radius(float dist, float fogFar) {
        float fogged = fogFar > 0.0f ? smoothstep(0.15f, 1.0f, dist / fogFar) : 0.0f;
        return (1.6f + 0.05f * dist) * (1.0f + 0.5f * fogged);
    }

    /** 0 by day, 1 at night, from the 0..1 time of day (0 = noon). */
    public static float night(float timeOfDay) {
        float day = (float) Math.cos(timeOfDay * 2.0 * Math.PI) * 2.0f + 0.5f;
        return 1.0f - Math.max(0.0f, Math.min(1.0f, day));
    }

    /** Brightness of the halo texture at distance {@code r} (0 centre .. 1 edge): a hot core in a wide soft skirt. */
    public static float profile(float r) {
        if (r >= 1.0f) {
            return 0.0f;
        }
        float core = (float) Math.exp(-(r * r) / (0.14f * 0.14f));
        float skirt = (float) Math.exp(-(r * r) / (0.38f * 0.38f));
        float edge = 1.0f - r * r;
        return Math.min(1.0f, 0.6f * core + 0.4f * skirt) * edge * edge;
    }

    static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - e0) / (e1 - e0)));
        return t * t * (3.0f - 2.0f * t);
    }
}
