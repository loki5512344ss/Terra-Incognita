package dev.sborka.incognita.weather.clouds.shade;

/**
 * How fog hides the clouds. Clouds are sky objects, so the vanilla distance fog does not touch them; a dense fog bank
 * does, the same way it swallows the sky dome: they thin out as the bank thickens and are gone from a density of
 * about 0.7. On top of that, clouds further away than a few fog distances melt into the haze first.
 */
public final class CloudFog {
    /** Fog density from which nothing of the clouds is left. */
    public static final float HIDDEN_DENSITY = 0.68f;

    private CloudFog() {}

    /**
     * Share of the cloud that is hidden whatever its distance, 0..1.
     *
     * @param density fog bank density 0..1 (0 without a bank)
     */
    public static float hideAll(float density) {
        return CloudLight.smoothstep(0.12f, HIDDEN_DENSITY, density);
    }

    /**
     * Start and end distance of the distance fade of the clouds, in blocks, or an infinite range without a bank.
     *
     * @param density  fog bank density 0..1
     * @param fogFar   far distance of the bank applied to the terrain, negative without a bank
     * @param out      receives start, end
     */
    public static void distanceRange(float density, float fogFar, float[] out) {
        if (density <= 0.001f || fogFar <= 0.0f) {
            out[0] = Float.POSITIVE_INFINITY;
            out[1] = Float.POSITIVE_INFINITY;
            return;
        }
        // The bank hugs the ground while the clouds sit hundreds of blocks up, so the range is stretched.
        out[0] = fogFar * 1.5f;
        out[1] = fogFar * 4.5f;
    }
}
