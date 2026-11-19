package dev.sborka.incognita.lighting.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightColorTest {
    @Test
    void packUnpack() {
        int c = LightColor.pack(10, 20, 30);
        assertEquals(10, LightColor.r(c));
        assertEquals(20, LightColor.g(c));
        assertEquals(30, LightColor.b(c));
        assertEquals(255, LightColor.r(LightColor.pack(999, -4, 0)));
        assertEquals(0, LightColor.g(LightColor.pack(999, -4, 0)));
    }

    @Test
    void maxIsPerChannel() {
        assertEquals(LightColor.pack(200, 100, 50), LightColor.max(LightColor.pack(200, 0, 50), LightColor.pack(10, 100, 0)));
    }

    @Test
    void attenuateFadesChannelsIndependentlyAndReachIsColorFree() {
        int decay = 40;
        int c = LightColor.attenuate(LightColor.pack(200, 100, 0), decay);
        assertEquals(LightColor.r(LightColor.attenuate(LightColor.pack(200, 0, 0), decay)), LightColor.r(c));
        assertEquals(LightColor.g(LightColor.attenuate(LightColor.pack(0, 100, 0), decay)), LightColor.g(c));
        assertEquals(0, LightColor.b(c));
        assertEquals(LightColor.rangeOf(200, decay), LightColor.rangeOf(200, decay));
        // Same level, any channel: the same number of blocks until dark.
        int walk = -1;
        for (int ch = 0; ch < 3; ch++) {
            int v = LightColor.pack(ch == 0 ? 255 : 0, ch == 1 ? 255 : 0, ch == 2 ? 255 : 0);
            int n = 0;
            while (v != 0) {
                v = LightColor.attenuate(v, decay);
                n++;
            }
            walk = walk < 0 ? n : walk;
            assertEquals(walk, n);
        }
    }

    @Test
    void fadeReachesZeroGraduallyWithoutAJump() {
        int decay = LightColor.stepForRadius(24);
        int c = LightColor.pack(255, 32, 32);
        int last = LightColor.intensity(c);
        int steps = 0;
        int lastDrop = 0;
        while (c != 0) {
            c = LightColor.attenuate(c, decay);
            int now = LightColor.intensity(c);
            lastDrop = last - now;
            assertTrue(lastDrop >= 0 && lastDrop <= (255 * decay >> 8) + 1, "step " + steps + " drops " + lastDrop);
            last = now;
            steps++;
        }
        assertTrue(lastDrop <= 2, "the final step is tiny: " + lastDrop);
        assertEquals(LightColor.rangeOf(255, decay), steps);
        assertTrue(steps >= 20 && steps <= 24, "steps " + steps);
    }

    @Test
    void withLevelScalesToLevel() {
        assertEquals(255, LightColor.intensity(LightColor.withLevel(0x204080, 15)));
        assertEquals(0, LightColor.withLevel(0x204080, 0));
        assertEquals(17 * 7, LightColor.intensity(LightColor.withLevel(0x204080, 7)));
    }

    @Test
    void filterMultiplies() {
        assertEquals(LightColor.pack(255, 0, 0), LightColor.filter(LightColor.pack(255, 255, 0), LightColor.pack(255, 0, 255)));
        assertEquals(0x123456, LightColor.filter(0x123456, LightColor.WHITE));
    }

    @Test
    void decayMatchesRadius() {
        int decay = LightColor.stepForRadius(24);
        int range = LightColor.rangeFor(15, decay);
        assertTrue(range >= 20 && range <= 40, "range " + range);
        assertEquals(0, LightColor.rangeFor(0, decay));
        assertTrue(LightColor.rangeFor(7, decay) < range);
    }

    @Test
    void parseHex() {
        assertEquals(0xFF8800, LightColor.parseHex("#ff8800"));
        assertEquals(0x00FF00, LightColor.parseHex("00ff00"));
        assertEquals(-1, LightColor.parseHex("nope"));
        assertEquals(-1, LightColor.parseHex(null));
    }
}
