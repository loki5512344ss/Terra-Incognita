package dev.sborka.incognita.weather.fog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FogModelTest {
    @Test
    void intensityStaysInRange() {
        for (long t = 0; t < 2_000_000; t += 997) {
            double v = FogModel.intensity(t, t, 1.0, 1.0, 1.0, true);
            assertTrue(v >= 0.0 && v <= 1.0, "out of range at " + t + ": " + v);
        }
    }

    @Test
    void deterministicForSameTime() {
        assertEquals(FogModel.intensity(123_456, 5_000, 0.3, 0.9, 1.0, true),
            FogModel.intensity(123_456, 5_000, 0.3, 0.9, 1.0, true));
    }

    @Test
    void zeroMaxMeansNoFog() {
        assertEquals(0.0, FogModel.intensity(50_000, 1_000, 1.0, 0.0, 1.0, true));
    }

    @Test
    void maxCapsIntensity() {
        for (long t = 0; t < 1_000_000; t += 503) {
            assertTrue(FogModel.intensity(t, t, 1.0, 0.4, 1.0, true) <= 0.4 + 1e-9);
        }
    }

    @Test
    void fogComesAndGoes() {
        boolean foggy = false;
        boolean clear = false;
        for (long t = 0; t < 2_000_000; t += 1_000) {
            double v = FogModel.intensity(t, 6_000, 0.0, 1.0, 1.0, false);
            foggy |= v > 0.5;
            clear |= v < 0.01;
        }
        assertTrue(foggy && clear);
    }

    @Test
    void dawnIsFoggierThanNoonAtSameTime() {
        // Same game time, so the same noise value; only the time of day differs.
        for (long t = 0; t < 400_000; t += 7_001) {
            assertTrue(FogModel.intensity(t, 0, 0, 1, 1, true) >= FogModel.intensity(t, 12_000, 0, 1, 1, true));
        }
    }

    @Test
    void rainAddsFog() {
        for (long t = 0; t < 400_000; t += 7_001) {
            assertTrue(FogModel.intensity(t, 6_000, 1.0, 1, 1, false)
                >= FogModel.intensity(t, 6_000, 0.0, 1, 1, false));
        }
    }

    @Test
    void farDistanceShrinksWithDensity() {
        assertEquals(192f, FogModel.farDistance(192f, 0.0, 12f));
        assertEquals(12f, FogModel.farDistance(192f, 1.0, 12f), 1e-3);
        assertTrue(FogModel.farDistance(192f, 0.5, 12f) < FogModel.farDistance(192f, 0.2, 12f));
    }

    @Test
    void farDistanceNeverExceedsVanilla() {
        assertEquals(8f, FogModel.farDistance(8f, 1.0, 12f), 1e-3);
    }

    @Test
    void dawnWeightWrapsAroundMidnight() {
        assertEquals(1.0, FogModel.dawnWeight(0), 1e-9);
        assertEquals(1.0, FogModel.dawnWeight(24_000), 1e-9);
        assertTrue(FogModel.dawnWeight(23_000) > 0.0);
        assertEquals(0.0, FogModel.dawnWeight(12_000), 1e-9);
    }
}
