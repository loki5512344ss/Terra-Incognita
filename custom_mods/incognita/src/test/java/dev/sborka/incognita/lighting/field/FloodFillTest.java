package dev.sborka.incognita.lighting.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloodFillTest {
    private static final int RED = LightColor.pack(255, 0, 0);

    private static ColorLightField field(Set<Long> opaque) {
        return new ColorLightField(LightColor.stepForRadius(24), (x, y, z) -> opaque.contains(key(x, y, z)) ? -1 : LightColor.WHITE);
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    @Test
    void lightFadesWithDistanceAndStaysRed() {
        ColorLightField f = field(Set.of());
        f.put("a", 0, 0, 0, RED);
        assertEquals(RED, f.get(0, 0, 0));
        int near = LightColor.r(f.get(1, 0, 0));
        int mid = LightColor.r(f.get(5, 0, 0));
        assertTrue(near < 255 && mid < near && mid > 0);
        assertEquals(0, LightColor.g(f.get(5, 0, 0)));
        assertEquals(LightColor.r(f.get(5, 0, 0)), LightColor.r(f.get(2, 3, 0)), "same Manhattan distance, same value");
        assertEquals(0, f.get(40, 0, 0), "beyond the radius there is no light");
        assertEquals(0, f.get(5, 0, 0) & 0xFFFF, "no green or blue from a pure red light");
    }

    @Test
    void everyColorReachesTheSameDistanceWithTheSameIntensity() {
        int[] colors = {0xFF2020, 0x20FF30, 0x2050FF, 0xB030FF, 0xFF8A10, 0xFFFFFF};
        int reach = -1;
        for (int color : colors) {
            ColorLightField f = field(Set.of());
            f.put("a", 0, 0, 0, LightColor.withLevel(color, 14));
            int last = 0;
            int prev = Integer.MAX_VALUE;
            for (int x = 0; x < 60; x++) {
                int i = LightColor.intensity(f.get(x, 0, 0));
                assertTrue(i <= prev, "monotone fall at " + x);
                prev = i;
                if (i > 0) {
                    last = x;
                }
            }
            if (reach < 0) {
                reach = last;
            }
            assertEquals(reach, last, "same reach for color " + Integer.toHexString(color));
        }
        assertTrue(reach >= 19 && reach <= 24, "reach " + reach);
    }

    @Test
    void rimIsDimNotAHardEdge() {
        ColorLightField f = field(Set.of());
        f.put("a", 0, 0, 0, RED);
        int step = f.decay();
        int lastLit = 0;
        while (f.get(lastLit + 1, 0, 0) != 0) {
            lastLit++;
        }
        assertTrue(LightColor.intensity(f.get(lastLit, 0, 0)) <= 3, "last lit cell is nearly dark");
        assertTrue(step > 0);
    }

    @Test
    void deferredBatchEqualsIndividualPuts() {
        ColorLightField batch = field(Set.of());
        ColorLightField single = field(Set.of());
        for (int i = 0; i < 6; i++) {
            single.put("e" + i, i * 5, i % 2, -i * 3, LightColor.withLevel(0x20FF30, 10 + i % 4));
            batch.putDeferred("e" + i, i * 5, i % 2, -i * 3, LightColor.withLevel(0x20FF30, 10 + i % 4));
        }
        batch.flush();
        for (int x = -30; x <= 60; x += 1) {
            for (int z = -30; z <= 30; z += 3) {
                assertEquals(single.get(x, 0, z), batch.get(x, 0, z), "at " + x + "," + z);
                assertEquals(single.get(x, 0, z), batch.sample(x, 0, z), "snapshot at " + x + "," + z);
            }
        }
    }

    @Test
    void snapshotSeesNewSectionsAfterThePutReturns() {
        ColorLightField f = field(Set.of());
        assertEquals(0, f.sample(5, 5, 5));
        f.put("a", 5, 5, 5, RED);
        assertEquals(RED, f.sample(5, 5, 5));
        assertTrue(f.mayHaveLight(5, 5, 5));
        f.remove("a");
        f.dropColumn(0, 0);
        assertTrue(!f.mayHaveLight(5, 5, 5));
    }

    @Test
    void reachesFartherThanVanillaFifteen() {
        ColorLightField f = field(Set.of());
        f.put("a", 0, 0, 0, RED);
        assertTrue(LightColor.intensity(f.get(20, 0, 0)) > 0);
    }

    @Test
    void opaqueBlockCastsShadow() {
        Set<Long> wall = new HashSet<>();
        for (int y = -30; y <= 30; y++) {
            for (int z = -30; z <= 30; z++) {
                wall.add(key(2, y, z));
            }
        }
        ColorLightField f = field(wall);
        f.put("a", 0, 0, 0, RED);
        assertEquals(0, f.get(3, 0, 0));
        assertNotEquals(0, f.get(1, 0, 0));
    }

    @Test
    void removalClearsTheLight() {
        ColorLightField f = field(Set.of());
        f.put("a", 0, 0, 0, RED);
        f.remove("a");
        for (int x = -30; x <= 30; x++) {
            assertEquals(0, f.get(x, 0, 0));
        }
    }

    @Test
    void removalKeepsTheOtherEmitterAndMatchesAFreshFill() {
        ColorLightField both = field(Set.of());
        int blue = LightColor.pack(0, 0, 255);
        both.put("a", 0, 0, 0, RED);
        both.put("b", 10, 0, 0, blue);
        both.remove("a");
        ColorLightField only = field(Set.of());
        only.put("b", 10, 0, 0, blue);
        for (int x = -30; x <= 40; x++) {
            for (int y = -3; y <= 3; y++) {
                assertEquals(only.get(x, y, 0), both.get(x, y, 0), "at " + x + "," + y);
            }
        }
    }

    @Test
    void movingAnEmitterEqualsAFreshFillAtTheNewCell() {
        ColorLightField moved = field(Set.of());
        moved.put("m", 0, 0, 0, RED);
        moved.put("m", 3, 1, -2, RED);
        ColorLightField fresh = field(Set.of());
        fresh.put("m", 3, 1, -2, RED);
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                assertEquals(fresh.get(x, 1, z), moved.get(x, 1, z));
            }
        }
    }

    @Test
    void sameStateIsFree() {
        ColorLightField f = field(Set.of());
        f.put("m", 0, 0, 0, RED);
        f.drainDirty();
        assertEquals(0, f.put("m", 0, 0, 0, RED));
        assertEquals(0, f.dirtyCount());
    }

    @Test
    void blockedThenOpenedLightFloodsIn() {
        Set<Long> opaque = new HashSet<>();
        for (int y = -40; y <= 40; y++) {
            for (int z = -40; z <= 40; z++) {
                opaque.add(key(1, y, z));
            }
        }
        ColorLightField f = field(opaque);
        f.put("a", 0, 0, 0, RED);
        assertEquals(0, f.get(4, 0, 0));
        opaque.remove(key(1, 0, 0));
        f.onBlockChanged(1, 0, 0);
        assertNotEquals(0, f.get(4, 0, 0), "light passes through the new hole");
    }

    @Test
    void dirtySectionsAreTrackedWithBorderNeighbors() {
        ColorLightField f = field(Set.of());
        f.put("a", 15, 8, 8, RED);
        var dirty = f.drainDirty();
        assertTrue(dirty.contains(ColorLightField.sectionKey(0, 0, 0)));
        assertTrue(dirty.contains(ColorLightField.sectionKey(1, 0, 0)));
        assertEquals(0, f.dirtyCount());
    }

    @Test
    void sectionKeyRoundTripsNegatives() {
        long k = ColorLightField.sectionKey(-5, -4, 70000);
        assertEquals(-5, ColorLightField.sectionX(k));
        assertEquals(-4, ColorLightField.sectionY(k));
        assertEquals(70000, ColorLightField.sectionZ(k));
    }

    @Test
    void movingLightCostIsBounded() {
        ColorLightField f = field(Set.of());
        f.put("m", 0, 0, 0, RED);
        long start = System.nanoTime();
        int cells = 0;
        for (int i = 1; i <= 200; i++) {
            cells += f.put("m", i, 0, 0, RED);
        }
        long micros = (System.nanoTime() - start) / 1000 / 200;
        System.out.println("[lighting] moving level-15 light: " + cells / 200 + " cells visited per block step, "
            + micros + " us per step, " + f.drainDirty().size() + " sections dirty in total");
        assertTrue(cells / 200 < 200_000);
    }

    @Test
    void throughputBenchmarkLog() {
        ColorLightField f = field(Set.of());
        for (int round = 0; round < 3; round++) { // warm up the JIT, only the last round is reported
            f.clear();
            long start = System.nanoTime();
            long cells = 0;
            for (int i = 0; i < 40; i++) {
                f.putDeferred("l" + i, (i % 8) * 6, 0, (i / 8) * 6, LightColor.withLevel(0xFF8A10, 14));
            }
            cells += f.flush();
            double ms = (System.nanoTime() - start) / 1e6;
            if (round == 2) {
                System.out.println("[lighting] batched fill of 40 lamps: " + cells + " cells visited in "
                    + String.format("%.1f", ms) + " ms (" + String.format("%.1f", cells / ms / 1000) + " M cells/s)");
            }
        }
        assertTrue(f.sectionCount() > 0);
    }
}
