package dev.sborka.incognita.weather.sky.dome;

import dev.sborka.incognita.weather.sky.atmosphere.SkyLut;
import dev.sborka.incognita.weather.sky.atmosphere.SkyTone;
import dev.sborka.incognita.weather.sky.math.SkyMath;

/**
 * The colour of the sky in a direction: the baked physical clear sky (sun, moon and airglow), the eye adaptation and
 * tone curve of {@link SkyTone}, then the weather on top: rain and thunder grey it out, horizon haze and fog banks melt
 * it into the fog colour, a lightning flash lifts it. The dome mesh evaluates this per vertex on the CPU.
 */
public final class SkyColors {
    /**
     * Everything one frame of sky colours depends on.
     *
     * @param lut       baked clear sky
     * @param sun       unit direction of the sun (x, y, z)
     * @param moonLight illuminated fraction of the moon, 0..1
     * @param exposure  eye adaptation exposure from {@link SkyTone#exposure}
     * @param night     0..1 darkness weight (stars out)
     * @param overcast  0..1 rain/storm greying
     * @param fog       0..1 fog bank density
     * @param haze      horizon haze strength from the config
     * @param flash     lightning flash strength, 0..1
     * @param fogColor  fog colour (r, g, b)
     */
    public record State(SkyLut lut, float[] sun, double moonLight, double exposure, float night, float overcast, float fog,
                        float haze, float flash, float[] fogColor) {
        /** 1 in clear air, 0 once a dense fog bank hides the sun and the moon. */
        public float fogLight() {
            return 1.0f - (float) SkyMath.smoothstep(0.30, 0.80, fog);
        }
    }

    private static final double[] OVERCAST_TINT = {0.93, 0.96, 1.02};
    private static final double SATURATION = 1.6;

    private SkyColors() {}

    private static double mix(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Share of a direction's light that survives rain and fog: fades stars, the Milky Way and the moon. */
    public static double visibleAt(State s, double v) {
        return (1.0 - s.overcast() * 0.92) * (1.0 - fogMix(s, v));
    }

    /**
     * Fog mix of the horizon haze for a view direction with elevation {@code v}: the horizon becomes the fog colour
     * first, then the fog climbs up the sky; a dense bank swallows the whole dome.
     */
    public static double fogMix(State s, double v) {
        double up = Math.max(v, 0.0);
        // Clear weather still has a thin band where the far terrain fog and the sky meet.
        double band = 0.85 * s.haze() * Math.exp(-up * 28.0);
        double fogHeight = 0.12 + 0.95 * s.fog();
        double rise = s.fog() * (1.0 - SkyMath.smoothstep(0.0, fogHeight, up)) * (0.55 + 0.45 * s.haze()) * 1.15;
        double dome = Math.pow(s.fog(), 1.6) * 1.15 * (0.86 + 0.14 * s.haze());
        return SkyMath.clamp01(Math.max(Math.max(Math.max(band, rise), dome), s.fog() * 0.18));
    }

    /**
     * Colour of the sky in a direction before the fog is applied: physical clear sky, tone mapping and rain greying.
     * The fog colour itself is taken from this at the horizon, so terrain fog and sky meet without a seam.
     *
     * @param lin scratch array of 3
     * @param out receives r, g, b in 0..1
     */
    public static void skyNoFog(State s, double x, double y, double z, double[] lin, float[] out) {
        double sx = s.sun()[0];
        double sy = s.sun()[1];
        double sz = s.sun()[2];
        double flat = Math.sqrt(x * x + z * z);
        double flatSun = Math.sqrt(sx * sx + sz * sz);
        double cosAz = flat > 1e-5 && flatSun > 1e-5 ? (x * sx + z * sz) / (flat * flatSun) : 0.0;
        // The moon stands opposite the sun.
        // Below the horizon the sky keeps the horizon colour and sinks smoothly toward a darker nadir: a mirrored sky
        // would draw a bright ridge exactly on the horizon line. The dome is a full sphere, so a flying player or a
        // void world sees a skybox and not a cut-off cap.
        double above = Math.max(y, 0.0);
        SkyTone.clearSky(s.lut(), above, sy, cosAz, -sy, -cosAz, s.moonLight(), lin);
        double sink = y < 0.0 ? mix(1.0, 0.45, SkyMath.smoothstep(0.0, 0.7, -y)) : 1.0;
        double k = s.exposure() * sink;
        lin[0] = SkyTone.display(lin[0] * k);
        lin[1] = SkyTone.display(lin[1] * k);
        lin[2] = SkyTone.display(lin[2] * k);
        SkyTone.saturate(lin, SATURATION);
        double r = lin[0];
        double g = lin[1];
        double b = lin[2];

        // Rain and thunder: a flat grey sky, a little brighter toward the zenith like a real overcast.
        double over = s.overcast();
        if (over > 0.0) {
            double mean = s.lut().averageLuminance(sy) + SkyTone.MOON_SCALE * s.moonLight() * s.lut().averageLuminance(-sy);
            double grey = SkyTone.display(mean * s.exposure() * (0.80 - 0.36 * over)) * (0.88 + 0.16 * Math.max(y, 0.0));
            double t = over * 0.92;
            r = mix(r, grey * OVERCAST_TINT[0], t);
            g = mix(g, grey * OVERCAST_TINT[1], t);
            b = mix(b, grey * OVERCAST_TINT[2], t);
        }
        out[0] = (float) r;
        out[1] = (float) g;
        out[2] = (float) b;
    }

    /**
     * Final colour of the sky in a direction.
     *
     * @param lin scratch array of 3
     * @param out receives r, g, b in 0..1
     */
    public static void sky(State s, double x, double y, double z, double[] lin, float[] out) {
        skyNoFog(s, x, y, z, lin, out);
        double fogMix = fogMix(s, y);
        double flash = s.flash();
        float[] fog = s.fogColor();
        for (int i = 0; i < 3; i++) {
            double c = mix(out[i], fog[i], fogMix);
            // Lightning lifts the whole sky toward a pale blue-white.
            out[i] = (float) mix(c, i == 2 ? 1.0 : 0.82, flash);
        }
    }
}
