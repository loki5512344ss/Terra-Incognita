package dev.sborka.incognita.lighting.sodium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sborka.incognita.lighting.field.LightColor;
import org.junit.jupiter.api.Test;

class VertexLightTest {
    private static final int RED = LightColor.pack(255, 0, 0);

    @Test
    void coloredLightRaisesBlockLightBeyondVanilla() {
        assertEquals(240, VertexLight.blockLm(RED, 0));
        assertEquals(240, VertexLight.blockLm(RED, 240));
        assertEquals(64, VertexLight.blockLm(LightColor.pack(60, 0, 0), 64));
        assertTrue(VertexLight.blockLm(LightColor.pack(100, 0, 0), 0) > 0);
        assertEquals(30, VertexLight.blockLm(LightColor.pack(3, 0, 0), 30));
    }

    @Test
    void brighterWhiteLightDilutesTheTint() {
        int dim = LightColor.pack(60, 0, 0);
        assertEquals(1f, VertexLight.weight(RED, 0), 1e-6);
        assertTrue(VertexLight.weight(dim, 240) < 0.3f);
        assertEquals(0f, VertexLight.weight(0, 0), 1e-6);
    }

    @Test
    void redTintKeepsRedAndDarkensTheRest() {
        int out = VertexLight.tintArgb(0xFFFFFFFF, RED, 0, 1f);
        assertEquals(255, (out >> 16) & 0xFF);
        assertTrue(((out >> 8) & 0xFF) < 50);
        assertEquals(0xFF, out >>> 24);
    }

    @Test
    void tintFadesInTowardTheRim() {
        float prev = 0f;
        for (int i = 4; i <= 120; i += 4) {
            float w = VertexLight.weight(LightColor.pack(i, 0, 0), 0);
            assertTrue(w >= prev, "monotone at " + i);
            prev = w;
        }
        assertTrue(VertexLight.weight(LightColor.pack(10, 0, 0), 0) < 0.15f);
    }

    @Test
    void noLightMeansNoChange() {
        assertEquals(0xFF336699, VertexLight.tintArgb(0xFF336699, 0, 100, 1f));
        assertEquals(0xFF336699, VertexLight.tintArgb(0xFF336699, RED, 100, 0f));
    }

    @Test
    void average() {
        assertEquals(LightColor.pack(63, 0, 0), VertexLight.average4(LightColor.pack(255, 0, 0), 0, 0, 0));
    }
}
