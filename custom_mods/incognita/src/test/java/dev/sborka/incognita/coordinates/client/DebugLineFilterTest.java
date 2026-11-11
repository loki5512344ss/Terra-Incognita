package dev.sborka.incognita.coordinates.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DebugLineFilterTest {
    @Test
    void coordinateLinesAreDetected() {
        assertTrue(DebugLineFilter.isCoordinateLine("XYZ: 41.500 / 97.00000 / -222.500"));
        assertTrue(DebugLineFilter.isCoordinateLine("Block: 41 97 -222 [9 1 2]"));
        assertTrue(DebugLineFilter.isCoordinateLine("Chunk: 2 6 -14 [2 18 in r.0.-1.mca]"));
        assertTrue(DebugLineFilter.isCoordinateLine("Chunk-relative: 9 1 2"));
        assertTrue(DebugLineFilter.isCoordinateLine("Section: 2 6 -14"));
        assertTrue(DebugLineFilter.isCoordinateLine("Targeted Block: 1, 2, 3"));
        assertTrue(DebugLineFilter.isCoordinateLine("Targeted Fluid: 1, 2, 3"));
    }

    @Test
    void flywheelOriginAndHeightmapLinesAreDetected() {
        assertTrue(DebugLineFilter.isCoordinateLine("Origin: 128, 64, -256"));
        assertTrue(DebugLineFilter.isCoordinateLine("CH S: 63 M: 63"));
        assertTrue(DebugLineFilter.isCoordinateLine("SH S: 63 O: 63 M: 63 F: 63"));
        assertTrue(DebugLineFilter.isCoordinateLine("§rSH S: ?? O: ?? M: ?? F: ??"));
        assertFalse(DebugLineFilter.isCoordinateLine("Chunks[C] W: 25 E: 3"));
        assertFalse(DebugLineFilter.isCoordinateLine("Backend: Instancing"));
        assertFalse(DebugLineFilter.isCoordinateLine("Flywheel: 1.0.6"));
        assertFalse(DebugLineFilter.isCoordinateLine("B: 10, E: 5, F: 2"));
        assertFalse(DebugLineFilter.isCoordinateLine("SHADER: on"));
    }

    @Test
    void formattingCodesAndWhitespaceAreIgnored() {
        assertTrue(DebugLineFilter.isCoordinateLine("§nTargeted Block: 1, 2, 3"));
        assertTrue(DebugLineFilter.isCoordinateLine("§nTargeted Fluid: 1, 2, 3"));
        assertTrue(DebugLineFilter.isCoordinateLine("  §r XYZ: 1 / 2 / 3"));
    }

    @Test
    void otherLinesAreKept() {
        assertFalse(DebugLineFilter.isCoordinateLine("Facing: north (Towards negative Z) (45.0 / 0.0)"));
        assertFalse(DebugLineFilter.isCoordinateLine("Biome: minecraft:plains"));
        assertFalse(DebugLineFilter.isCoordinateLine("Local Difficulty: 1.50 // 0.00 (Day 0)"));
        assertFalse(DebugLineFilter.isCoordinateLine("Client Light: 15 (15 sky, 0 block)"));
        assertFalse(DebugLineFilter.isCoordinateLine("minecraft:overworld FC: 0"));
        assertFalse(DebugLineFilter.isCoordinateLine("minecraft:stone"));
        assertFalse(DebugLineFilter.isCoordinateLine("#minecraft:mineable/pickaxe"));
        assertFalse(DebugLineFilter.isCoordinateLine("§nTargeted Entity"));
        assertFalse(DebugLineFilter.isCoordinateLine("60 fps T: 120 vsync fancy"));
        assertFalse(DebugLineFilter.isCoordinateLine(""));
        assertFalse(DebugLineFilter.isCoordinateLine(null));
    }
}
