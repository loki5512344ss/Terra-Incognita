package dev.sborka.incognita.coordinates.reveal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RevealCommandTest {
    @Test
    void seesRealCoordinatesOnlyWhenRevealedOpAndEnabled() {
        assertTrue(RevealCommand.canSee(true, true, true));
        assertFalse(RevealCommand.canSee(false, true, true));
        assertFalse(RevealCommand.canSee(true, false, true));
        assertFalse(RevealCommand.canSee(true, true, false));
    }
}
