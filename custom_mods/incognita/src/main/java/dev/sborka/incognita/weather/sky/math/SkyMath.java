package dev.sborka.incognita.weather.sky.math;

/**
 * Small pure helpers of the sky: sun direction, the night weight, moon phase geometry, air mass and the integer hash
 * that places stars and paints the textures.
 */
public final class SkyMath {
    private static final double NIGHT_START = -0.30;
    private static final double NIGHT_END = -0.06;
    private static final float UNIT_SCALE = 1.0f / 16_777_216.0f;
    /** Air mass at the horizon; the Kasten-Young formula gives about 38 there. */
    private static final double MAX_AIRMASS = 38.0;

    private SkyMath() {}

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    /**
     * World space direction of the sun, matching the vanilla sun path: it travels in the XY plane, rises in the east
     * (+X) and stands at the zenith when the time of day fraction is 0.
     *
     * @param timeOfDay vanilla time of day fraction (0 at noon, 0.5 at midnight)
     * @return unit vector {x, y, z}
     */
    public static float[] sunDirection(double timeOfDay) {
        double angle = timeOfDay * 2.0 * Math.PI;
        return new float[] {(float) -Math.sin(angle), (float) Math.cos(angle), 0.0f};
    }

    /** 1 under a dark sky, fading to 0 as dusk begins; the sun elevation is the Y of {@link #sunDirection}. */
    public static double nightFactor(double elevation) {
        return 1.0 - smoothstep(NIGHT_START, NIGHT_END, elevation);
    }

    /** Light direction angle of the moon phase: 0 is a full moon, pi a new moon (vanilla phase 0..7). */
    public static float moonPhaseAngle(int phase) {
        return (float) (Math.floorMod(phase, 8) * Math.PI / 4.0);
    }

    /** Illuminated fraction of the moon disc for a vanilla phase: 1 at full moon, 0 at new moon. */
    public static double moonIllumination(int phase) {
        return (1.0 + Math.cos(moonPhaseAngle(phase))) * 0.5;
    }

    /**
     * Relative air mass toward an elevation (Kasten and Young 1989): 1 at the zenith, about 38 at the horizon.
     *
     * @param elevationSin sine of the elevation
     */
    public static double airmass(double elevationSin) {
        double deg = Math.toDegrees(Math.asin(Math.max(0.0, Math.min(1.0, elevationSin))));
        double am = 1.0 / (Math.sin(Math.toRadians(deg)) + 0.50572 * Math.pow(deg + 6.07995, -1.6364));
        return Math.min(am, MAX_AIRMASS);
    }

    /** PCG output hash of one 32 bit value. */
    public static int pcg(int value) {
        int state = value * 747_796_405 + 0xAC564B05;
        int word = ((state >>> ((state >>> 28) + 4)) ^ state) * 277_803_737;
        return (word >>> 22) ^ word;
    }

    /** Hash of a 3D integer cell; further random values of the same cell come from repeated {@link #pcg}. */
    public static int cellHash(int x, int y, int z) {
        return pcg(x + pcg(y + pcg(z)));
    }

    /** Maps a hash to [0, 1) using its top 24 bits. */
    public static float toUnit(int hash) {
        return (hash >>> 8) * UNIT_SCALE;
    }
}
