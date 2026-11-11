package dev.sborka.incognita.radio.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RadioStateTest {
    @Test
    void formatsTenthsAsMegahertz() {
        assertEquals("100.0", RadioState.format(1000));
        assertEquals("145.5", RadioState.format(1455));
        assertEquals("30.0", RadioState.format(300));
    }

    @Test
    void convertsMegahertzToTenths() {
        assertEquals(1455, RadioState.fromMegahertz(145.5));
        assertEquals(1000, RadioState.fromMegahertz(100.0));
    }

    @Test
    void clampsToRange() {
        assertEquals(300, RadioState.clamp(10, 300, 1080));
        assertEquals(1080, RadioState.clamp(5000, 300, 1080));
        assertEquals(700, RadioState.clamp(700, 300, 1080));
    }

    @Test
    void withersKeepOtherField() {
        RadioState s = RadioState.DEFAULT.withOn(true).withFreq(900);
        assertTrue(s.on());
        assertEquals(900, s.freq());
        assertFalse(s.withOn(false).on());
        assertEquals(900, s.withOn(false).freq());
    }
}
