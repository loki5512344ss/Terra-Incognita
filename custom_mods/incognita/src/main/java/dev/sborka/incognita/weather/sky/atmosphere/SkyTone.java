package dev.sborka.incognita.weather.sky.atmosphere;

/**
 * Turns the linear sky radiance into display colours: the sunlit sky, the much fainter moonlit sky and the airglow
 * of a moonless night are summed in one linear scale, an eye adaptation exposure follows the overall brightness of
 * the sky, and an exponential shoulder plus a 2.2 gamma map the result to the 8 bit framebuffer. Because the
 * adaptation is partial, dusk reads darker than noon and night stays dark, yet nothing ever clips to a flat colour.
 */
public final class SkyTone {
    /** Moon irradiance relative to the sun at full moon (real ratio is ~1/400000; the game moon is brighter). */
    public static final double MOON_SCALE = 1.0 / 24000.0;
    /** Linear radiance of the moonless night sky at the zenith, bluish (airglow plus scattered starlight). */
    private static final double[] AIRGLOW = {0.9e-6, 1.25e-6, 2.6e-6};
    /** Brightness floor of the adaptation: the eye stops opening further below this sky luminance. */
    private static final double ADAPT_FLOOR = 6.0e-5;
    private static final double ADAPT_POWER = 0.82;
    private static final double KEY = 0.54;

    private SkyTone() {}

    /**
     * Exposure for this frame.
     *
     * @param sunSin    sine of the sun elevation
     * @param moonSin   sine of the moon elevation
     * @param moonLight illuminated fraction of the moon, 0..1
     */
    public static double exposure(SkyLut lut, double sunSin, double moonSin, double moonLight) {
        double sky = lut.averageLuminance(sunSin) + MOON_SCALE * moonLight * lut.averageLuminance(moonSin);
        return KEY / Math.pow(sky + ADAPT_FLOOR, ADAPT_POWER);
    }

    /**
     * Linear radiance of the clear sky in a direction, sun, moon and airglow together.
     *
     * @param viewSin       sine of the view elevation
     * @param sunSin        sine of the sun elevation
     * @param sunCosAz      cosine of the horizontal angle between view and sun
     * @param moonSin       sine of the moon elevation
     * @param moonCosAz     cosine of the horizontal angle between view and moon
     * @param moonLight     illuminated fraction of the moon
     * @param out           receives linear RGB
     */
    public static void clearSky(SkyLut lut, double viewSin, double sunSin, double sunCosAz, double moonSin,
                                double moonCosAz, double moonLight, double[] out) {
        lut.radiance(viewSin, sunSin, sunCosAz, out);
        double r = out[0];
        double g = out[1];
        double b = out[2];
        if (moonLight > 0.0 && moonSin > -0.3) {
            lut.radiance(viewSin, moonSin, moonCosAz, out);
            double k = MOON_SCALE * moonLight;
            r += out[0] * k;
            g += out[1] * k;
            b += out[2] * k;
        }
        // Ozone tints the twilight zenith toward violet in the model; real eyes and cameras see it deep blue.
        double twilight = smooth(0.12, 0.0, sunSin) * smooth(-0.22, -0.02, sunSin);
        double up = Math.max(viewSin, 0.0);
        double cool = twilight * Math.sqrt(up);
        r *= 1.0 - 0.50 * cool;
        g *= 1.0 + 0.08 * cool;
        // Purkinje shift: in the deep twilight the eye loses colour and sees the faint remaining light blue-grey,
        // so the last red remnant of the sunset does not glow all around the horizon.
        double deep = smooth(-0.08, -0.17, sunSin);
        if (deep > 0.0) {
            double lum = SkyLut.luminance(r, g, b);
            r += (lum * 0.62 - r) * deep * 0.85;
            g += (lum * 0.86 - g) * deep * 0.85;
            b += (lum * 1.45 - b) * deep * 0.85;
        }
        // Airglow grows toward the horizon (a longer path through the glowing layer, van Rhijn effect).
        double glow = 1.0 + 1.6 * Math.exp(-up * 9.0);
        out[0] = r + AIRGLOW[0] * glow;
        out[1] = g + AIRGLOW[1] * glow;
        out[2] = b + AIRGLOW[2] * glow;
    }

    /**
     * Restores the colour the shoulder and gamma wash out (a photographic look rather than a flat grey-blue):
     * pushes the three display channels away from their luminance, in place.
     */
    public static void saturate(double[] rgb, double amount) {
        double lum = SkyLut.luminance(rgb[0], rgb[1], rgb[2]);
        for (int c = 0; c < 3; c++) {
            rgb[c] = Math.max(0.0, Math.min(1.0, lum + (rgb[c] - lum) * amount));
        }
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Maps one linear channel value multiplied by the exposure to a display value in 0..1. */
    public static double display(double value) {
        double shoulder = 1.0 - Math.exp(-Math.max(value, 0.0));
        return Math.pow(shoulder, 1.0 / 2.2);
    }
}
