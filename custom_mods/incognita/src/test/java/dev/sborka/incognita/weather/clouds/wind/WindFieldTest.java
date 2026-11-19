package dev.sborka.incognita.weather.clouds.wind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindFieldTest {
    private static double[] displacement(WindField field, int band, double ticks, double factor) {
        double x0 = field.offsetX(band);
        double z0 = field.offsetZ(band);
        for (int i = 0; i < ticks; i++) {
            field.advance(1.0, factor);
        }
        return new double[] {field.offsetX(band) - x0, field.offsetZ(band) - z0};
    }

    private static double angleDegrees(double[] a, double[] b) {
        double cos = (a[0] * b[0] + a[1] * b[1]) / (Math.hypot(a[0], a[1]) * Math.hypot(b[0], b[1]));
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
    }

    @Test
    void bandsMoveInDifferentDirectionsAndSpeeds() {
        double[][] moves = new double[3][];
        for (int b = 0; b < 3; b++) {
            WindField field = new WindField(BandWind.BANDS);
            field.start(12_345.0);
            moves[b] = displacement(field, b, 2400, 1.0);
        }
        assertTrue(angleDegrees(moves[0], moves[1]) > 35.0);
        assertTrue(angleDegrees(moves[1], moves[2]) > 35.0);
        assertTrue(angleDegrees(moves[0], moves[2]) > 70.0);
        double s0 = Math.hypot(moves[0][0], moves[0][1]);
        double s1 = Math.hypot(moves[1][0], moves[1][1]);
        double s2 = Math.hypot(moves[2][0], moves[2][1]);
        assertTrue(s0 < s1 && s1 < s2, "higher bands are faster");
        // Two minutes of clear weather move the low band a visible but calm distance.
        assertTrue(s0 > 150.0 && s0 < 300.0, "low band moved " + s0);
    }

    @Test
    void headingVeersSlowly() {
        BandWind wind = BandWind.BANDS.get(0);
        double a = wind.headingAt(0.0);
        double b = wind.headingAt(20.0);
        assertTrue(Math.abs(a - b) < Math.toRadians(0.5), "no visible jump within a second");
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int t = 0; t < 9_000; t += 50) {
            min = Math.min(min, wind.headingAt(t));
            max = Math.max(max, wind.headingAt(t));
        }
        assertTrue(Math.toDegrees(max - min) > 25.0, "it does veer over a period");
    }

    @Test
    void rainSpeedsUpWithoutJumps() {
        WindField calm = new WindField(BandWind.BANDS);
        WindField storm = new WindField(BandWind.BANDS);
        calm.start(0.0);
        storm.start(0.0);
        double[] slow = displacement(calm, 0, 200, 1.0);
        double[] fast = displacement(storm, 0, 200, 3.0);
        assertEquals(3.0, Math.hypot(fast[0], fast[1]) / Math.hypot(slow[0], slow[1]), 1.0e-6);
        // A long lag spike is clamped, not integrated in one huge step.
        double before = storm.offsetX(0);
        storm.advance(10_000.0, 1.0);
        assertTrue(Math.abs(storm.offsetX(0) - before) < 40.0 * 0.3);
    }
}
