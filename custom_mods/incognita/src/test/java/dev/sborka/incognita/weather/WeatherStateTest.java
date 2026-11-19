package dev.sborka.incognita.weather;

import dev.sborka.incognita.weather.WeatherState;
import dev.sborka.incognita.weather.fog.FogModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeatherStateTest {
    @Test
    void alwaysWithinBounds() {
        for (long t = 0; t < 2_000_000; t += 997) {
            for (double rain = 0.0; rain <= 1.0; rain += 0.5) {
                double c = WeatherState.cloudiness(t, t % 24_000, rain, rain, 1.0, true);
                assertTrue(c >= WeatherState.MIN_CLOUDINESS && c <= WeatherState.MAX_CLOUDINESS, "out of range: " + c);
            }
        }
    }

    @Test
    void deterministic() {
        double a = WeatherState.cloudiness(123_456L, 6_000L, 0.3, 0.0, 1.0, true);
        double b = WeatherState.cloudiness(123_456L, 6_000L, 0.3, 0.0, 1.0, true);
        assertEquals(a, b);
    }

    @Test
    void rainNeverReducesCover() {
        for (long t = 0; t < 500_000; t += 1_313) {
            double dry = WeatherState.cloudiness(t, t % 24_000, 0.0, 0.0, 1.0, false);
            double wet = WeatherState.cloudiness(t, t % 24_000, 1.0, 0.0, 1.0, false);
            assertTrue(wet >= dry, "rain reduced cover at " + t);
        }
    }

    @Test
    void thunderNeverReducesCover() {
        for (long t = 0; t < 500_000; t += 1_777) {
            double calm = WeatherState.cloudiness(t, t % 24_000, 0.0, 0.0, 1.0, false);
            double storm = WeatherState.cloudiness(t, t % 24_000, 0.0, 1.0, 1.0, false);
            assertTrue(storm >= calm, "thunder reduced cover at " + t);
        }
    }

    @Test
    void rainMakesASolidOvercastDeck() {
        for (long t = 0; t < 200_000; t += 1_001) {
            double cover = WeatherState.cloudiness(t, t % 24_000, 1.0, 0.0, 1.0, false);
            assertTrue(cover >= 0.99, "rain cover left gaps: " + cover);
        }
        assertEquals(WeatherState.MAX_CLOUDINESS, WeatherState.cloudiness(0L, 6_000L, 1.0, 1.0, 1.0, false),
            1.0e-9);
    }

    @Test
    void stormIsClearlyHeavierThanDryOnAverage() {
        double dry = 0.0;
        double storm = 0.0;
        int n = 0;
        for (long t = 0; t < 1_000_000; t += 2_003) {
            dry += WeatherState.cloudiness(t, 6_000L, 0.0, 0.0, 1.0, false);
            storm += WeatherState.cloudiness(t, 6_000L, 1.0, 1.0, 1.0, false);
            n++;
        }
        assertTrue(storm / n > dry / n + 0.1);
    }

    @Test
    void denseFogRaisesCover() {
        // Find a tick with a thick fog bank and one with none, same other inputs.
        long fogTick = -1;
        long clearTick = -1;
        for (long t = 0; t < 2_000_000 && (fogTick < 0 || clearTick < 0); t += 500) {
            double fog = FogModel.intensity(t, 6_000L, 0.0, 1.0, 1.0, false);
            if (fog > 0.9 && fogTick < 0) fogTick = t;
            if (fog < 0.01 && clearTick < 0) clearTick = t;
        }
        assertTrue(fogTick >= 0 && clearTick >= 0, "test needs both fog states");
        double fogCover = WeatherState.cloudiness(fogTick, 6_000L, 0.0, 0.0, 1.0, false);
        double slowFog = FogModelProbe.slowBase(fogTick);
        double clearCover = WeatherState.cloudiness(clearTick, 6_000L, 0.0, 0.0, 1.0, false);
        double slowClear = FogModelProbe.slowBase(clearTick);
        // Remove the slow cover variation; the remaining difference is the fog term.
        assertTrue((fogCover - slowFog) > (clearCover - slowClear));
    }

    /** Re-derives the slow cover term so the fog contribution can be isolated. */
    private static final class FogModelProbe {
        static double slowBase(long gameTime) {
            double slow = FogModel.noise(gameTime / 30_000.0 + 7.3);
            return 0.5 + 0.1 * (2.0 * slow - 1.0);
        }
    }
}
