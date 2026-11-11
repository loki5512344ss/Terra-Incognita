package dev.sborka.incognita.spawn.pick;

import java.util.random.RandomGenerator;

/** Picks a random point in a ring around (0, 0), uniform by area. Pure logic, no game state. */
public final class SpawnPicker {
    public record Point(int x, int z) {}

    private SpawnPicker() {}

    public static Point pick(RandomGenerator rng, int minRadius, int maxRadius) {
        int lo = Math.max(0, Math.min(minRadius, maxRadius));
        int hi = Math.max(0, Math.max(minRadius, maxRadius));
        double angle = rng.nextDouble() * 2 * Math.PI;
        double radius = Math.sqrt((double) lo * lo + rng.nextDouble() * ((double) hi * hi - (double) lo * lo));
        return new Point((int) Math.round(radius * Math.cos(angle)), (int) Math.round(radius * Math.sin(angle)));
    }
}
