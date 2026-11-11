package dev.sborka.incognita.spawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpawnHooksTest {
    // args: enabled, fromEndFight, hasRespawnPos, respawnLevelExists, missingBlock
    @Test
    void bedOrAnchorIsNeverOverridden() {
        assertFalse(SpawnHooks.shouldRandomize(true, false, true, true, false));
    }

    @Test
    void forcedSpawnpointIsNeverOverridden() {
        assertFalse(SpawnHooks.shouldRandomize(true, false, true, true, false));
    }

    @Test
    void noRespawnPositionRandomizes() {
        assertTrue(SpawnHooks.shouldRandomize(true, false, false, true, false));
    }

    @Test
    void missingBlockRandomizes() {
        assertTrue(SpawnHooks.shouldRandomize(true, false, true, true, true));
    }

    @Test
    void goneRespawnLevelRandomizes() {
        assertTrue(SpawnHooks.shouldRandomize(true, false, true, false, false));
    }

    @Test
    void endFightNeverRandomizes() {
        assertFalse(SpawnHooks.shouldRandomize(true, true, false, true, true));
    }

    @Test
    void disabledNeverRandomizes() {
        assertFalse(SpawnHooks.shouldRandomize(false, false, false, true, true));
    }
}
