package dev.sborka.incognita.weather.clouds.wind;

import java.util.List;

/**
 * The wind of one altitude band. Real wind turns clockwise and speeds up with height (Ekman spiral, then the faster
 * winds aloft), so each band blows from its own heading at its own speed and veers slowly on its own period.
 *
 * @param headingDegrees mean direction the band moves towards, degrees from +X towards +Z
 * @param speed          speed in blocks per tick in clear weather
 * @param veerDegrees    amplitude of the slow veer around the mean heading
 * @param veerPeriod     period of the veer in ticks
 * @param phase          phase of the veer in radians
 */
public record BandWind(double headingDegrees, double speed, double veerDegrees, double veerPeriod, double phase) {
    /** One wind per band, low to high (matching the cloud bands). */
    public static final List<BandWind> BANDS = List.of(
        new BandWind(200.0, 0.090, 18.0, 9_000.0, 0.0),
        new BandWind(255.0, 0.145, 16.0, 7_300.0, 1.9),
        new BandWind(325.0, 0.245, 14.0, 11_500.0, 4.1));

    /** Mean heading in radians, used for the fixed orientation of cloud streets. */
    public double headingRadians() {
        return Math.toRadians(headingDegrees);
    }

    /** Heading at a moment in radians, including the slow veer. */
    public double headingAt(double clockTicks) {
        double veer = veerDegrees * Math.sin(clockTicks * (2.0 * Math.PI) / veerPeriod + phase);
        return Math.toRadians(headingDegrees + veer);
    }

    /**
     * Velocity in blocks per tick.
     *
     * @param factor weather multiplier, 1 in clear weather
     * @param out    receives x, z
     */
    public void velocity(double clockTicks, double factor, double[] out) {
        double heading = headingAt(clockTicks);
        double v = speed * factor;
        out[0] = Math.cos(heading) * v;
        out[1] = Math.sin(heading) * v;
    }
}
