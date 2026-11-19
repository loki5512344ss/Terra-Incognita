package dev.sborka.incognita.weather.sky.atmosphere;

/**
 * Physically based clear sky: single Rayleigh and Mie scattering in a spherical Earth atmosphere with ozone absorption
 * and a cheap isotropic multiple scattering term. Pure math with no game classes, so it can be baked into a table off
 * the render thread and unit tested. Units are kilometres; colours are linear RGB radiance for a sun of irradiance 1.
 *
 * <p>Constants follow the Earth atmosphere of Bruneton's precomputed scattering and Hillaire's EGSR 2020 paper
 * (Rayleigh and Mie scale heights, scattering coefficients for 680/550/440 nm, the ozone tent profile).
 */
public final class Atmosphere {
    static final double GROUND = 6360.0;
    static final double TOP = 6460.0;
    /** Camera height above the ground in km. */
    static final double VIEW_HEIGHT = 0.3;

    private static final double[] RAYLEIGH = {5.802e-3, 13.558e-3, 33.100e-3};
    private static final double RAYLEIGH_HEIGHT = 8.0;
    private static final double MIE_SCATTER = 3.996e-3;
    private static final double MIE_EXTINCTION = 4.440e-3;
    private static final double MIE_HEIGHT = 1.2;
    /** Anisotropy of the baked Mie lobe; the tight aureole around the sun is drawn by the sun sprite instead. */
    private static final double MIE_G = 0.76;
    private static final double[] OZONE = {1.04e-3, 3.01e-3, 0.136e-3};
    private static final double OZONE_CENTRE = 25.0;
    private static final double OZONE_HALF_WIDTH = 15.0;
    /** Share of the single scattered light that comes back as isotropic multiple scattering. */
    private static final double MULTI_SCATTER = 0.25;
    /** How far (zenith cosine) the multiple scattered light reaches past the shadow line. */
    private static final double MULTI_SHIFT = 0.07;
    /**
     * Multiple scattered light mostly arrives from the blue sky above, not straight from the reddened sun: it is
     * evaluated this many km higher and tinted toward blue. This keeps the Earth's shadow blue-grey after sunset.
     */
    private static final double MULTI_LIFT = 16.0;
    /** Lowest height (km) the multiple scattered light is taken from: the bright upper sky of the twilight. */
    private static final double MULTI_MIN_HEIGHT = 20.0;
    private static final double[] MULTI_TINT = {0.55, 0.78, 1.0};

    /** Longest view path in km; also keeps the horizon from turning into a solid white or orange wall. */
    private static final double MAX_VIEW = 140.0;
    private static final int VIEW_STEPS = 28;
    private static final int DEPTH_STEPS = 40;
    private static final int T_HEIGHTS = 48;
    private static final int T_ANGLES = 256;

    /** Optical depth table over (height, cosine of the zenith angle), three channels. */
    private final float[] depth = new float[T_HEIGHTS * T_ANGLES * 3];

    public Atmosphere() {
        double[] tmp = new double[3];
        for (int h = 0; h < T_HEIGHTS; h++) {
            double r = GROUND + heightAt(h);
            for (int a = 0; a < T_ANGLES; a++) {
                double mu = -1.0 + 2.0 * a / (T_ANGLES - 1.0);
                integrateDepth(r, mu, tmp);
                int i = (h * T_ANGLES + a) * 3;
                depth[i] = (float) tmp[0];
                depth[i + 1] = (float) tmp[1];
                depth[i + 2] = (float) tmp[2];
            }
        }
    }

    private static double heightAt(int index) {
        double u = index / (T_HEIGHTS - 1.0);
        return (TOP - GROUND) * u * u;
    }

    /** Extinction coefficients at a height (km above ground) per channel, written to {@code out}. */
    private static void extinction(double height, double[] out) {
        double rayleigh = Math.exp(-height / RAYLEIGH_HEIGHT);
        double mie = Math.exp(-height / MIE_HEIGHT);
        double ozone = Math.max(0.0, 1.0 - Math.abs(height - OZONE_CENTRE) / OZONE_HALF_WIDTH);
        for (int c = 0; c < 3; c++) {
            out[c] = RAYLEIGH[c] * rayleigh + MIE_EXTINCTION * mie + OZONE[c] * ozone;
        }
    }

    /** Distance along a ray from radius r with zenith cosine mu to the outer sphere of radius {@code radius}. */
    static double toSphere(double r, double mu, double radius) {
        double disc = r * r * (mu * mu - 1.0) + radius * radius;
        return Math.max(0.0, -r * mu + Math.sqrt(Math.max(disc, 0.0)));
    }

    /** True when the ray hits the ground before leaving the atmosphere. */
    static boolean hitsGround(double r, double mu) {
        return mu < 0.0 && r * r * (mu * mu - 1.0) + GROUND * GROUND >= 0.0;
    }

    private static void integrateDepth(double r, double mu, double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        out[2] = 0.0;
        double length = toSphere(r, mu, TOP);
        double[] ext = new double[3];
        double dt = length / DEPTH_STEPS;
        for (int i = 0; i < DEPTH_STEPS; i++) {
            double t = (i + 0.5) * dt;
            double ri = Math.sqrt(r * r + t * t + 2.0 * r * mu * t);
            extinction(Math.max(0.0, ri - GROUND), ext);
            for (int c = 0; c < 3; c++) {
                out[c] += ext[c] * dt;
            }
        }
    }

    /**
     * Transmittance from a point toward the sun, with the shadow of the Earth softened over a fraction of a degree so
     * the terminator in the sky has no hard line.
     */
    void sunTransmittance(double r, double mu, double[] out) {
        double h = Math.max(0.0, Math.min(TOP - GROUND, r - GROUND));
        double hf = Math.sqrt(h / (TOP - GROUND)) * (T_HEIGHTS - 1.0);
        double af = (Math.max(-1.0, Math.min(1.0, mu)) + 1.0) * 0.5 * (T_ANGLES - 1.0);
        int h0 = Math.min((int) hf, T_HEIGHTS - 2);
        int a0 = Math.min((int) af, T_ANGLES - 2);
        double fh = hf - h0;
        double fa = af - a0;
        // Cosine of the angle where the ground starts to block the sun from this height.
        double horizonMu = -Math.sqrt(Math.max(0.0, 1.0 - (GROUND / r) * (GROUND / r)));
        double shadow = smooth(horizonMu - 0.004, horizonMu + 0.006, mu);
        for (int c = 0; c < 3; c++) {
            double d00 = depth[(h0 * T_ANGLES + a0) * 3 + c];
            double d01 = depth[(h0 * T_ANGLES + a0 + 1) * 3 + c];
            double d10 = depth[((h0 + 1) * T_ANGLES + a0) * 3 + c];
            double d11 = depth[((h0 + 1) * T_ANGLES + a0 + 1) * 3 + c];
            double d = (d00 + (d01 - d00) * fa) * (1.0 - fh) + (d10 + (d11 - d10) * fa) * fh;
            // Below the horizon the table holds the depth through the ground; the shadow factor zeroes it anyway.
            out[c] = Math.exp(-Math.min(d, 60.0)) * shadow;
        }
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }

    static double rayleighPhase(double cosTheta) {
        return 3.0 / (16.0 * Math.PI) * (1.0 + cosTheta * cosTheta);
    }

    /** Cornette-Shanks form of the Henyey-Greenstein phase function. */
    static double miePhase(double cosTheta) {
        double g2 = MIE_G * MIE_G;
        double k = 3.0 / (8.0 * Math.PI) * (1.0 - g2) / (2.0 + g2);
        return k * (1.0 + cosTheta * cosTheta) / Math.pow(1.0 + g2 - 2.0 * MIE_G * cosTheta, 1.5);
    }

    /**
     * Sky radiance seen from the camera.
     *
     * @param viewSin   sine of the view elevation; rays below the horizon are flattened onto it
     * @param sunSin    sine of the sun elevation
     * @param cosAzimuth cosine of the horizontal angle between the view and the sun
     * @param out       receives linear RGB radiance
     */
    public void radiance(double viewSin, double sunSin, double cosAzimuth, double[] out) {
        double mu = Math.max(viewSin, 0.0);
        double cosView = Math.sqrt(1.0 - mu * mu);
        double cosSun = Math.sqrt(Math.max(0.0, 1.0 - sunSin * sunSin));
        // Local frame: the view lies in the x/y plane, y is up.
        double vx = cosView;
        double vy = mu;
        double sx = cosSun * cosAzimuth;
        double sy = sunSin;
        double sz = cosSun * Math.sqrt(Math.max(0.0, 1.0 - cosAzimuth * cosAzimuth));
        double cosTheta = vx * sx + vy * sy;
        double phaseR = rayleighPhase(cosTheta);
        double phaseM = miePhase(cosTheta);
        double iso = 1.0 / (4.0 * Math.PI);

        double r0 = GROUND + VIEW_HEIGHT;
        // Terrain and fog hide everything beyond a couple of hundred kilometres anyway.
        double length = Math.min(toSphere(r0, mu, TOP), MAX_VIEW);
        double[] ext = new double[3];
        double[] tSun = new double[3];
        double[] tMulti = new double[3];
        double[] optical = new double[3];
        double[] sum = new double[3];
        double previous = 0.0;
        for (int i = 0; i < VIEW_STEPS; i++) {
            // Quadratic step distribution: dense near the camera where the air is thick.
            double u1 = (i + 1.0) / VIEW_STEPS;
            double t1 = length * u1 * u1;
            double dt = t1 - previous;
            double t = previous + dt * 0.5;
            previous = t1;
            double px = vx * t;
            double py = r0 + vy * t;
            double r = Math.sqrt(px * px + py * py);
            double height = Math.max(0.0, r - GROUND);
            double sunMu = (px * sx + py * sy) / r;
            sunTransmittance(r, sunMu, tSun);
            // Multiple scattering reaches into the Earth's shadow: light arrives from lit air higher up.
            sunTransmittance(Math.max(r + MULTI_LIFT, GROUND + MULTI_MIN_HEIGHT), sunMu + MULTI_SHIFT, tMulti);
            double densityR = Math.exp(-height / RAYLEIGH_HEIGHT);
            double densityM = Math.exp(-height / MIE_HEIGHT);
            extinction(height, ext);
            for (int c = 0; c < 3; c++) {
                double half = optical[c] + ext[c] * dt * 0.5;
                double viewT = Math.exp(-half);
                double scatterR = RAYLEIGH[c] * densityR;
                double scatterM = MIE_SCATTER * densityM;
                double single = scatterR * phaseR + scatterM * phaseM;
                double multi = (scatterR + scatterM) * iso * MULTI_SCATTER;
                sum[c] += viewT * (tSun[c] * single + tMulti[c] * MULTI_TINT[c] * multi) * dt;
                optical[c] += ext[c] * dt;
            }
        }
        // sz is only needed to keep the frame orthonormal; the view has no z component.
        assert sz >= 0.0;
        System.arraycopy(sum, 0, out, 0, 3);
    }

    /**
     * Transmittance of the whole atmosphere along a ray from the camera, for sun and moon colour near the horizon.
     *
     * @param elevationSin sine of the elevation of the ray (may be slightly negative)
     */
    public void transmittance(double elevationSin, double[] out) {
        sunTransmittance(GROUND + VIEW_HEIGHT, elevationSin, out);
    }
}
