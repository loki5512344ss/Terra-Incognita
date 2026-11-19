package dev.sborka.incognita.weather.precip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrecipitationMathTest {
    @Test
    void countScalesWithWeatherAndDensity() {
        assertEquals(0, PrecipitationMath.particleCount(4000, 1.0, 0.0f, false));
        assertEquals(4000, PrecipitationMath.particleCount(4000, 1.0, 1.0f, false));
        assertTrue(PrecipitationMath.particleCount(4000, 0.5, 1.0f, false)
            < PrecipitationMath.particleCount(4000, 1.0, 1.0f, false));
        assertTrue(PrecipitationMath.particleCount(4000, 1.0, 1.0f, true)
            < PrecipitationMath.particleCount(4000, 1.0, 1.0f, false), "snow needs fewer particles");
    }

    @Test
    void countStaysInsideThePool() {
        assertEquals(4000, PrecipitationMath.particleCount(4000, 3.0, 3.0f, false));
        assertEquals(0, PrecipitationMath.particleCount(4000, 3.0, -1.0f, false));
    }

    @Test
    void lightLevelIsDarkAtNightAndBrightAtNoon() {
        assertEquals(0.0f, PrecipitationMath.lightLevel(-1.0f), 1e-4f);
        assertEquals(1.0f, PrecipitationMath.lightLevel(1.0f), 1e-4f);
        assertTrue(PrecipitationMath.lightLevel(-0.05f) < PrecipitationMath.lightLevel(0.2f));
    }
}
