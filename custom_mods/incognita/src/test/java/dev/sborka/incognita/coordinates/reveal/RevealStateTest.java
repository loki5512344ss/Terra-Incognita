package dev.sborka.incognita.coordinates.reveal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RevealStateTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void reset() {
        RevealState.clearServer(player);
        RevealState.setClientRevealed(false);
    }

    @Test
    void serverFlagDefaultsToHidden() {
        assertFalse(RevealState.isServerRevealed(player));
    }

    @Test
    void serverFlagCanBeSetAndCleared() {
        RevealState.setServerRevealed(player, true);
        assertTrue(RevealState.isServerRevealed(player));
        RevealState.setServerRevealed(player, false);
        assertFalse(RevealState.isServerRevealed(player));
    }

    @Test
    void clearServerRemovesTheFlag() {
        RevealState.setServerRevealed(player, true);
        RevealState.clearServer(player);
        assertFalse(RevealState.isServerRevealed(player));
    }

    @Test
    void clientFlagDefaultsToHiddenAndIsSettable() {
        assertFalse(RevealState.isClientRevealed());
        RevealState.setClientRevealed(true);
        assertTrue(RevealState.isClientRevealed());
    }
}
