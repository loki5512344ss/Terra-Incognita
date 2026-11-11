package dev.sborka.incognita.coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SpooferTest {
    private static Spoofer constant() {
        return new Spoofer(() -> new Spoofer.Settings("???", 0.0, Spoofer.Mode.CONSTANT, 3), () -> 0L);
    }

    private static Spoofer rotating(AtomicLong clock) {
        return new Spoofer(() -> new Spoofer.Settings("???", 0.0, Spoofer.Mode.ROTATING, 3), clock::get);
    }

    @Test
    void constantModeReturnsConfiguredValues() {
        assertEquals("???", constant().text());
        assertEquals(0.0, constant().number());
    }

    @Test
    void constantModeIgnoresTheClock() {
        AtomicLong clock = new AtomicLong(0);
        Spoofer spoofer = new Spoofer(() -> new Spoofer.Settings("?", 7.0, Spoofer.Mode.CONSTANT, 3), clock::get);
        clock.set(1_000_000);
        assertEquals("?", spoofer.text());
        assertEquals(7.0, spoofer.number());
    }

    @Test
    void rotatingModeIsStableInsideOneInterval() {
        AtomicLong clock = new AtomicLong(0);
        Spoofer spoofer = rotating(clock);
        double first = spoofer.number();
        clock.set(2_999);
        assertEquals(first, spoofer.number());
        assertEquals(Integer.toString((int) first), spoofer.text());
    }

    @Test
    void rotatingModeChangesBetweenIntervals() {
        AtomicLong clock = new AtomicLong(0);
        Spoofer spoofer = rotating(clock);
        Set<Double> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            clock.set(i * 3_000L);
            seen.add(spoofer.number());
        }
        assertTrue(seen.size() > 1, "values should differ between intervals");
    }

    @Test
    void rotatingValuesStayInRange() {
        AtomicLong clock = new AtomicLong(0);
        Spoofer spoofer = rotating(clock);
        for (int i = 0; i < 200; i++) {
            clock.set(i * 3_000L);
            double v = spoofer.number();
            assertTrue(v >= -30_000 && v <= 30_000, "out of range: " + v);
        }
    }

    @Test
    void rotatingValueIsDeterministicForAnInterval() {
        AtomicLong clock = new AtomicLong(12_345_000L);
        assertEquals(rotating(clock).number(), rotating(clock).number());
    }

    @Test
    void intervalBelowOneSecondIsClamped() {
        Spoofer.Settings settings = new Spoofer.Settings("?", 0, Spoofer.Mode.ROTATING, 0);
        assertEquals(1, settings.rotatingIntervalSeconds());
        assertNotEquals(null, settings.placeholderText());
    }
}
