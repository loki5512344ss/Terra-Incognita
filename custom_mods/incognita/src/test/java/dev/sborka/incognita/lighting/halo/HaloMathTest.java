package dev.sborka.incognita.lighting.halo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HaloMathTest {
    @Test
    void nothingNearOrBeyondRange() {
        assertEquals(0.0f, HaloMath.alpha(4, 100, -1, 1, 1, 1));
        assertEquals(0.0f, HaloMath.alpha(100, 100, -1, 1, 1, 1));
    }

    @Test
    void fogMakesHaloStrongerThanClearAir() {
        float clear = HaloMath.alpha(60, 112, -1, 1, 1, 1);
        float fog = HaloMath.alpha(60, 112, 20, 1, 1, 1);
        assertTrue(clear > 0 && fog > clear * 3);
    }

    @Test
    void daylightDimsAndStrengthScales() {
        float night = HaloMath.alpha(40, 112, 20, 1, 1, 1);
        float day = HaloMath.alpha(40, 112, 20, 0, 1, 1);
        assertTrue(day < night * 0.2f);
        assertEquals(night * 2, HaloMath.alpha(40, 112, 20, 1, 1, 2), 1e-5f);
    }

    @Test
    void nightCurve() {
        assertEquals(0.0f, HaloMath.night(0.0f));
        assertEquals(1.0f, HaloMath.night(0.5f));
    }

    @Test
    void profileIsSmoothAndBounded() {
        assertTrue(HaloMath.profile(0) <= 1.0f && HaloMath.profile(0) > 0.9f);
        assertEquals(0.0f, HaloMath.profile(1.0f));
        assertTrue(HaloMath.profile(0.3f) > HaloMath.profile(0.6f));
    }

    @Test
    void radiusGrowsWithDistanceAndFog() {
        assertTrue(HaloMath.radius(60, -1) > HaloMath.radius(20, -1));
        assertTrue(HaloMath.radius(60, 20) > HaloMath.radius(60, -1));
    }
}
