package dev.sborka.incognita.weather.clouds.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GlSupportTest {
    @Test
    void prefersModernPath() {
        assertEquals(GlSupport.Path.MODERN, GlSupport.choose(true, true, false));
    }

    @Test
    void forcingLegacyNeedsGl33() {
        assertEquals(GlSupport.Path.LEGACY, GlSupport.choose(true, true, true));
        assertEquals(GlSupport.Path.LEGACY, GlSupport.choose(false, true, false));
    }

    @Test
    void nothingBelowGl33() {
        assertEquals(GlSupport.Path.NONE, GlSupport.choose(false, false, false));
        assertEquals(GlSupport.Path.NONE, GlSupport.choose(false, false, true));
    }
}
