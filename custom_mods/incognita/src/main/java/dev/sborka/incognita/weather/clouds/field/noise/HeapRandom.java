package dev.sborka.incognita.weather.clouds.field.noise;

/**
 * Small deterministic PRNG (xorshift32) seeded from one heap cell, so the shape of a heap is the same every time
 * its cell is generated, whatever the camera position or the generation order.
 */
public final class HeapRandom {
    private int state;

    public HeapRandom(int seed) {
        reset(seed);
    }

    /** Restarts the sequence from a new seed; lets one instance serve many heaps without allocation. */
    public void reset(int seed) {
        state = seed == 0 ? 0x6D2B79F5 : seed;
        // Warm up: the first outputs of xorshift from similar seeds are correlated.
        next();
        next();
    }

    /** Next value in [0, 1). */
    public float next() {
        int x = state;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        state = x;
        return (x >>> 8) * (1.0f / (1 << 24));
    }

    /** Next value in [lo, hi). */
    public float range(float lo, float hi) {
        return lo + (hi - lo) * next();
    }
}
