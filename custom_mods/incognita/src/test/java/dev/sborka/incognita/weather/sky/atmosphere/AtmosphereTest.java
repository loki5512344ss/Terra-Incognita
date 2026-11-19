package dev.sborka.incognita.weather.sky.atmosphere;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AtmosphereTest {
    private static SkyLut lut;

    @BeforeAll
    static void bake() {
        lut = SkyLut.bake();
    }

    private static double[] sky(double viewSin, double sunDeg, double cosAz) {
        double[] out = new double[3];
        lut.radiance(viewSin, Math.sin(Math.toRadians(sunDeg)), cosAz, out);
        return out;
    }

    @Test
    void noonZenithIsBlue() {
        double[] zenith = sky(1.0, 60.0, 1.0);
        assertTrue(zenith[2] > zenith[1] && zenith[1] > zenith[0], "zenith must be blue");
    }

    @Test
    void sunsetHorizonIsRedderTowardTheSun() {
        double[] toward = sky(0.02, 1.0, 1.0);
        double[] away = sky(0.02, 1.0, -1.0);
        assertTrue(toward[0] / toward[2] > away[0] / away[2], "the sunset side must be warmer");
        assertTrue(toward[0] > away[0], "the sunset side must be brighter in red");
    }

    @Test
    void skyDarkensAsTheSunSinks() {
        double previous = Double.MAX_VALUE;
        for (double deg = 30.0; deg >= -15.0; deg -= 5.0) {
            double lum = lut.averageLuminance(Math.sin(Math.toRadians(deg)));
            assertTrue(lum < previous, "mean sky brightness must fall with the sun at " + deg);
            previous = lum;
        }
    }

    @Test
    void transmittanceFallsTowardTheHorizonAndReddens() {
        double[] zenith = new double[3];
        double[] low = new double[3];
        lut.transmittance(1.0, zenith);
        lut.transmittance(0.05, low);
        for (int c = 0; c < 3; c++) {
            assertTrue(low[c] < zenith[c]);
        }
        assertTrue(low[0] / zenith[0] > low[2] / zenith[2], "a low light source must turn red");
    }

    @Test
    void toneMapStaysInRangeAndIsMonotonic() {
        double previous = -1.0;
        for (double v = 0.0; v < 20.0; v += 0.25) {
            double d = SkyTone.display(v);
            assertTrue(d >= 0.0 && d <= 1.0);
            assertTrue(d >= previous);
            previous = d;
        }
    }
}
