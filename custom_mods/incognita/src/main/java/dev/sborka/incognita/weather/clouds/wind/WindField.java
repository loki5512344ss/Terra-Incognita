package dev.sborka.incognita.weather.clouds.wind;

import java.util.List;

/**
 * Integrated drift of every band: {@code world = band space + offset}. Offsets are doubles integrated step by step
 * from each band's own velocity, so a change of the wind strength (rain) changes the speed, never the position.
 */
public final class WindField {
    /** Longest step integrated at once; a longer gap (pause, lag spike) is clamped, it only delays the drift. */
    private static final double MAX_STEP = 40.0;

    private final List<BandWind> winds;
    private final double[] offsetX;
    private final double[] offsetZ;
    private final double[] scratch = new double[2];
    private double clock;

    public WindField(List<BandWind> winds) {
        this.winds = List.copyOf(winds);
        offsetX = new double[winds.size()];
        offsetZ = new double[winds.size()];
    }

    /**
     * Starts the clock (and the offsets, as if the wind had always blown at its clear weather speed) at a world
     * time, so a new session does not show the very same sky as the last one.
     */
    public void start(double clockTicks) {
        clock = clockTicks;
        for (int b = 0; b < winds.size(); b++) {
            winds.get(b).velocity(clockTicks, 1.0, scratch);
            offsetX[b] = scratch[0] * clockTicks;
            offsetZ[b] = scratch[1] * clockTicks;
        }
    }

    /**
     * Advances every band.
     *
     * @param ticks  elapsed ticks (fractional)
     * @param factor weather multiplier of the wind speed
     */
    public void advance(double ticks, double factor) {
        double step = Math.max(0.0, Math.min(ticks, MAX_STEP));
        // Midpoint rule: the heading changes so slowly that one evaluation per frame is exact enough.
        double mid = clock + step * 0.5;
        for (int b = 0; b < winds.size(); b++) {
            winds.get(b).velocity(mid, factor, scratch);
            offsetX[b] += scratch[0] * step;
            offsetZ[b] += scratch[1] * step;
        }
        clock += step;
    }

    public double offsetX(int band) {
        return offsetX[band];
    }

    public double offsetZ(int band) {
        return offsetZ[band];
    }

    public double clock() {
        return clock;
    }

    public BandWind wind(int band) {
        return winds.get(band);
    }
}
