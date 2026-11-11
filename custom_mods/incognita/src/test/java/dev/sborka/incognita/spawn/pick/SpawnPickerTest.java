package dev.sborka.incognita.spawn.pick;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SpawnPickerTest {
    private static double distance(SpawnPicker.Point p) {
        return Math.sqrt((double) p.x() * p.x() + (double) p.z() * p.z());
    }

    @Test
    void pointsStayInsideTheRing() {
        Random rng = new Random(1);
        for (int i = 0; i < 10_000; i++) {
            double d = distance(SpawnPicker.pick(rng, 100, 500));
            assertTrue(d >= 99 && d <= 501, "distance out of range: " + d);
        }
    }

    @Test
    void zeroMinimumAllowsPointsNearTheCenter() {
        Random rng = new Random(2);
        boolean near = false;
        for (int i = 0; i < 10_000; i++) {
            if (distance(SpawnPicker.pick(rng, 0, 500)) < 50) {
                near = true;
            }
        }
        assertTrue(near, "expected at least one point within 50 blocks of the center");
    }

    @Test
    void pointsCoverAllFourQuadrants() {
        Random rng = new Random(3);
        boolean[] seen = new boolean[4];
        for (int i = 0; i < 1_000; i++) {
            SpawnPicker.Point p = SpawnPicker.pick(rng, 0, 500);
            seen[(p.x() >= 0 ? 1 : 0) + (p.z() >= 0 ? 2 : 0)] = true;
        }
        for (boolean quadrant : seen) {
            assertTrue(quadrant);
        }
    }

    @Test
    void reversedBoundsAreSwapped() {
        Random rng = new Random(4);
        for (int i = 0; i < 1_000; i++) {
            double d = distance(SpawnPicker.pick(rng, 500, 100));
            assertTrue(d >= 99 && d <= 501, "distance out of range: " + d);
        }
    }

    @Test
    void zeroRadiusGivesTheCenter() {
        SpawnPicker.Point p = SpawnPicker.pick(new Random(5), 0, 0);
        assertTrue(p.x() == 0 && p.z() == 0);
    }

    @Test
    void negativeBoundsClampToZero() {
        double d = distance(SpawnPicker.pick(new Random(6), -50, 10));
        assertTrue(d <= 11);
    }
}
