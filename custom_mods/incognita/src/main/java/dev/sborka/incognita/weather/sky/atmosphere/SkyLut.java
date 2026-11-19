package dev.sborka.incognita.weather.sky.atmosphere;

/**
 * The clear sky of {@link Atmosphere} baked into a table over (sun elevation, view elevation, azimuth from the sun),
 * so a frame only pays for trilinear lookups. Baking takes a fraction of a second and runs once, off the render
 * thread. The table also stores how bright the whole sky is for every sun elevation, which drives the eye adaptation
 * in {@link SkyTone}.
 */
public final class SkyLut {
    /** Sun elevation range of the table in degrees; below the lower end the sun no longer lights the sky. */
    static final double SUN_MIN_DEG = -24.0;
    static final double SUN_MAX_DEG = 90.0;
    private static final int SUN_N = 58;
    private static final int VIEW_N = 40;
    private static final int AZ_N = 37;

    private final float[] table = new float[SUN_N * VIEW_N * AZ_N * 3];
    private final float[] average = new float[SUN_N];
    private final Atmosphere atmosphere;

    private SkyLut(Atmosphere atmosphere) {
        this.atmosphere = atmosphere;
    }

    /** Bakes the table; pure computation, safe on any thread. */
    public static SkyLut bake() {
        Atmosphere atmosphere = new Atmosphere();
        SkyLut lut = new SkyLut(atmosphere);
        double[] rgb = new double[3];
        for (int s = 0; s < SUN_N; s++) {
            double sunSin = Math.sin(Math.toRadians(sunDegAt(s)));
            double lum = 0.0;
            double weight = 0.0;
            for (int v = 0; v < VIEW_N; v++) {
                double viewSin = viewSinAt(v);
                for (int a = 0; a < AZ_N; a++) {
                    double cosAz = Math.cos(Math.PI * a / (AZ_N - 1.0));
                    atmosphere.radiance(viewSin, sunSin, cosAz, rgb);
                    int i = ((s * VIEW_N + v) * AZ_N + a) * 3;
                    lut.table[i] = (float) rgb[0];
                    lut.table[i + 1] = (float) rgb[1];
                    lut.table[i + 2] = (float) rgb[2];
                    // Solid angle weight of the cell: rings near the horizon cover more sky than the zenith cap.
                    double w = v + 1 < VIEW_N ? viewSinAt(v + 1) - viewSin : 0.0;
                    lum += luminance(rgb[0], rgb[1], rgb[2]) * w;
                    weight += w;
                }
            }
            lut.average[s] = (float) (lum / Math.max(weight, 1e-9));
        }
        return lut;
    }

    private static double sunDegAt(int index) {
        return SUN_MIN_DEG + (SUN_MAX_DEG - SUN_MIN_DEG) * index / (SUN_N - 1.0);
    }

    /** View samples are dense near the horizon: sin(elevation) = u^2. */
    private static double viewSinAt(int index) {
        double u = index / (VIEW_N - 1.0);
        return u * u;
    }

    static double luminance(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double sunIndex(double sunSin) {
        double deg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sunSin))));
        double f = (deg - SUN_MIN_DEG) / (SUN_MAX_DEG - SUN_MIN_DEG) * (SUN_N - 1.0);
        return Math.max(0.0, Math.min(SUN_N - 1.0, f));
    }

    /**
     * Linear radiance of the clear sky.
     *
     * @param viewSin    sine of the view elevation (below the horizon the horizon value is returned)
     * @param sunSin     sine of the light source elevation
     * @param cosAzimuth cosine of the horizontal angle between view and light source
     * @param out        receives linear RGB
     */
    public void radiance(double viewSin, double sunSin, double cosAzimuth, double[] out) {
        double sf = sunIndex(sunSin);
        double vf = Math.sqrt(Math.max(0.0, Math.min(1.0, viewSin))) * (VIEW_N - 1.0);
        double af = Math.acos(Math.max(-1.0, Math.min(1.0, cosAzimuth))) / Math.PI * (AZ_N - 1.0);
        int s0 = Math.min((int) sf, SUN_N - 2);
        int v0 = Math.min((int) vf, VIEW_N - 2);
        int a0 = Math.min((int) af, AZ_N - 2);
        double fs = sf - s0;
        double fv = vf - v0;
        double fa = af - a0;
        for (int c = 0; c < 3; c++) {
            double s0v = lerp(lerp(at(s0, v0, a0, c), at(s0, v0, a0 + 1, c), fa),
                lerp(at(s0, v0 + 1, a0, c), at(s0, v0 + 1, a0 + 1, c), fa), fv);
            double s1v = lerp(lerp(at(s0 + 1, v0, a0, c), at(s0 + 1, v0, a0 + 1, c), fa),
                lerp(at(s0 + 1, v0 + 1, a0, c), at(s0 + 1, v0 + 1, a0 + 1, c), fa), fv);
            out[c] = lerp(s0v, s1v, fs);
        }
    }

    /** Mean luminance of the upper hemisphere for a light source at this elevation. */
    public double averageLuminance(double sunSin) {
        double sf = sunIndex(sunSin);
        int s0 = Math.min((int) sf, SUN_N - 2);
        return lerp(average[s0], average[s0 + 1], sf - s0);
    }

    /** Transmittance of the atmosphere along a ray from the camera at this elevation (sine). */
    public void transmittance(double elevationSin, double[] out) {
        atmosphere.transmittance(elevationSin, out);
    }

    private double at(int s, int v, int a, int c) {
        return table[((s * VIEW_N + v) * AZ_N + a) * 3 + c];
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
