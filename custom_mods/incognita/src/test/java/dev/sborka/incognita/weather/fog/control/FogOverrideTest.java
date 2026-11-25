package dev.sborka.incognita.weather.fog.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FogOverrideTest {
    private static final double EPS = 1.0e-6;

    @Test
    void naturalKeepsTheModelValue() {
        assertEquals(0.4, FogOverride.NONE.apply(0.4, 1_000), EPS);
    }

    @Test
    void offRemovesFogForGood() {
        assertEquals(0.0, FogOverride.NONE.off().apply(0.9, 99_000_000L), EPS);
    }

    @Test
    void clearedHidesFogOnlyUntilTheEndOfTheGameDay() {
        FogOverride cleared = FogOverride.NONE.cleared(30_000);
        assertEquals(0.0, cleared.apply(0.9, 47_999), EPS);
        assertEquals(0.9, cleared.apply(0.9, 48_000), EPS);
    }

    @Test
    void clearingDoesNotSwitchDisabledFogBackOn() {
        assertEquals(0.0, FogOverride.NONE.off().cleared(30_000).apply(0.9, 100_000), EPS);
    }

    @Test
    void forcedFogRollsInHoldsAndRollsOut() {
        FogOverride forced = FogOverride.NONE.forced(0.8f, 1_000, 2_000);
        assertEquals(0.0, forced.apply(0.0, 1_000), EPS);
        assertEquals(0.4, forced.apply(0.0, 1_100), EPS);
        assertEquals(0.8, forced.apply(0.0, 2_000), EPS);
        assertEquals(0.4, forced.apply(0.0, 2_900), EPS);
        assertEquals(0.0, forced.apply(0.0, 3_000), EPS);
        assertEquals(0.3, forced.apply(0.3, 3_001), EPS);
    }

    @Test
    void chanceIsGivenPerGameDay() {
        assertEquals(1.0, FogOverride.NONE.frequency(1.0), EPS);
        assertEquals(0.5, FogOverride.NONE.withDailyChance(6).frequency(1.0), 1.0e-4);
        assertEquals(0.0, FogOverride.NONE.withDailyChance(0).frequency(1.0), EPS);
    }

    @Test
    void switchingModeKeepsTheChance() {
        assertEquals(0.5, FogOverride.NONE.withDailyChance(6).off().natural().frequency(1.0), 1.0e-4);
    }
}
