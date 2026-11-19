package dev.sborka.incognita.lighting.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sborka.incognita.lighting.field.LightColor;
import org.junit.jupiter.api.Test;

class DynamicSourcesTest {
    @Test
    void cappedLimitsIntensity() {
        int c = DynamicSources.capped(0xFF8000, 15, 6);
        assertEquals(6 * 255 / 15, LightColor.intensity(c));
        assertEquals(0, DynamicSources.capped(0xFF8000, 0, 6));
    }

    @Test
    void idleCreeperIsDark() {
        assertEquals(0, DynamicSources.creeperColor(false, 0.0f, 12));
    }

    @Test
    void chargedCreeperIsBlueish() {
        int c = DynamicSources.creeperColor(true, 0.0f, 12);
        assertTrue(LightColor.b(c) > LightColor.r(c));
    }

    @Test
    void swellingCreeperBrightensInSteps() {
        int a = LightColor.intensity(DynamicSources.creeperColor(false, 0.2f, 15));
        int b = LightColor.intensity(DynamicSources.creeperColor(false, 0.5f, 15));
        int d = LightColor.intensity(DynamicSources.creeperColor(false, 0.9f, 15));
        assertTrue(a > 0 && a < b && b < d);
        assertEquals(6 * 255 / 15, LightColor.intensity(DynamicSources.creeperColor(false, 0.9f, 6)));
    }
}
