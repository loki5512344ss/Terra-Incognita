package dev.sborka.incognita.weather.clouds.shade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CloudLightTest {
    private static CloudLight.Result at(float sun, float rain) {
        return CloudLight.compute(sun, 0.6f, rain, 0.0f, 0.9f);
    }

    @Test
    void noonHasBrightTopAndSlightlyDimmerUnderside() {
        CloudLight.Result noon = at(1.0f, 0.0f);
        assertTrue(noon.skyTop().luma() > 0.75f);
        // The underside is still the darker side, but only by a gentle amount: a dark grey belly reads as a
        // greasy mass from below, which is what the brighter bottom ambient fixes.
        assertTrue(noon.skyBottom().luma() < noon.skyTop().luma());
        assertTrue(noon.skyTop().luma() - noon.skyBottom().luma() > 0.015f);
        assertTrue(noon.sunColor().luma() > 0.9f);
    }

    @Test
    void sunsetIsWarm() {
        CloudLight.Result sunset = at(0.02f, 0.0f);
        assertTrue(sunset.sunColor().r() > sunset.sunColor().b() * 1.5f);
        assertTrue(sunset.sunsetness() > 0.9f);
        CloudLight.Result noon = at(1.0f, 0.0f);
        // The underside leans warmer than at noon (relative to its blue), without being a flat orange.
        assertTrue(sunset.skyBottom().r() / sunset.skyBottom().b() > noon.skyBottom().r() / noon.skyBottom().b());
    }

    @Test
    void nightIsNearlyBlackAndBluish() {
        CloudLight.Result night = at(-0.8f, 0.0f);
        assertTrue(night.skyTop().luma() < 0.1f);
        assertTrue(night.skyTop().b() > night.skyTop().r());
        assertTrue(night.sunColor().luma() < 0.15f);
    }

    @Test
    void rainIsDarkerAndDenser() {
        CloudLight.Result clear = at(1.0f, 0.0f);
        CloudLight.Result rain = at(1.0f, 1.0f);
        assertTrue(rain.skyTop().luma() < clear.skyTop().luma());
        assertTrue(rain.skyBottom().luma() < clear.skyBottom().luma());
        assertTrue(rain.sunColor().luma() < clear.sunColor().luma() * 0.5f);
        assertTrue(rain.opacity() > clear.opacity());
    }

    @Test
    void brightnessFallsMonotonicallyIntoNight() {
        float previous = Float.MAX_VALUE;
        for (float sun = 1.0f; sun >= -1.0f; sun -= 0.05f) {
            float luma = at(sun, 0.0f).skyTop().luma();
            assertTrue(luma <= previous + 1.0e-4f, "not monotonic at " + sun);
            previous = luma;
        }
    }

    @Test
    void opacityStaysInRange() {
        for (float base : new float[] {0.05f, 0.5f, 0.9f, 1.0f}) {
            float opacity = CloudLight.compute(1.0f, 1.0f, 1.0f, 1.0f, base).opacity();
            assertTrue(opacity >= 0.05f && opacity <= 0.985f);
        }
    }

    @Test
    void lightDirectionSwitchesToMoonAtNight() {
        assertTrue(CloudLight.sunLights(0.5f));
        assertTrue(CloudLight.sunLights(-0.05f));
        assertFalse(CloudLight.sunLights(-0.5f));
    }
}
