package dev.sborka.incognita;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModIdTest {
    @Test
    void modIdIsStable() {
        assertEquals("incognita", Incognita.MOD_ID);
    }
}
