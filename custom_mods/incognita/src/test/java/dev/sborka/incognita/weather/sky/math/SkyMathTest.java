package dev.sborka.incognita.weather.sky.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyMathTest {
    @Test
    void sunStartsHighAtNoonAndRisesInTheEast() {
        float[] noon = SkyMath.sunDirection(0.0);
        assertEquals(1.0f, noon[1], 1e-6f);
        float[] sunrise = SkyMath.sunDirection(0.75);
        assertEquals(0.0f, sunrise[1], 1e-6f);
        assertEquals(1.0f, sunrise[0], 1e-6f);
        float[] midnight = SkyMath.sunDirection(0.5);
        assertEquals(-1.0f, midnight[1], 1e-6f);
    }

    @Test
    void sunDirectionIsAUnitVector() {
        for (int i = 0; i < 100; i++) {
            float[] s = SkyMath.sunDirection(i / 100.0);
            assertEquals(1.0, Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]), 1e-5);
        }
    }

    @Test
    void nightWeightRisesAsTheSunSinks() {
        assertEquals(0.0, SkyMath.nightFactor(0.1), 1e-9);
        assertEquals(1.0, SkyMath.nightFactor(-0.6), 1e-9);
        double previous = -1.0;
        for (double e = 1.0; e >= -1.0; e -= 0.05) {
            double night = SkyMath.nightFactor(e);
            assertTrue(night >= previous, "night factor must rise as the sun sinks");
            previous = night;
        }
    }

    @Test
    void airmassIsOneAtTheZenithAndLargeAtTheHorizon() {
        assertEquals(1.0, SkyMath.airmass(1.0), 1e-3);
        assertEquals(2.0, SkyMath.airmass(0.5), 0.01);
        assertTrue(SkyMath.airmass(0.0) > 30.0);
        assertTrue(SkyMath.airmass(-0.2) <= 38.0);
    }

    @Test
    void moonIlluminationFollowsThePhase() {
        assertEquals(1.0, SkyMath.moonIllumination(0), 1e-9);
        assertEquals(0.0, SkyMath.moonIllumination(4), 1e-9);
        assertEquals(0.5, SkyMath.moonIllumination(2), 1e-6);
        assertEquals(SkyMath.moonIllumination(1), SkyMath.moonIllumination(7), 1e-6);
    }

    @Test
    void moonPhaseAngleWrapsAroundEightPhases() {
        assertEquals(0.0f, SkyMath.moonPhaseAngle(0), 1e-6f);
        assertEquals((float) Math.PI, SkyMath.moonPhaseAngle(4), 1e-6f);
        assertEquals(SkyMath.moonPhaseAngle(1), SkyMath.moonPhaseAngle(9), 1e-6f);
    }

    @Test
    void starHashIsDeterministicAndFillsTheUnitRange() {
        assertEquals(SkyMath.cellHash(3, -4, 7), SkyMath.cellHash(3, -4, 7));
        double sum = 0.0;
        int low = 0;
        int count = 0;
        for (int x = -10; x < 10; x++) {
            for (int y = -10; y < 10; y++) {
                for (int z = -10; z < 10; z++) {
                    float u = SkyMath.toUnit(SkyMath.cellHash(x, y, z));
                    assertTrue(u >= 0.0f && u < 1.0f, "hash must map into [0, 1)");
                    sum += u;
                    count++;
                    if (u < 0.25f) {
                        low++;
                    }
                }
            }
        }
        assertEquals(0.5, sum / count, 0.02);
        assertEquals(0.25, (double) low / count, 0.02);
    }
}
