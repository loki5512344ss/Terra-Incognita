package dev.sborka.incognita.weather.clouds.field.noise;

/**
 * Deterministic 2D noise for the cloud cover: integer hashes and a gradient (Perlin style) fbm. Pure and thread
 * safe, so every client builds the same sky from the same band-space coordinates.
 */
public final class CoverNoise {
    private static final int OCTAVES = 4;
    /** Sum of the octave amplitudes 1 + 1/2 + 1/4 + 1/8, used to normalise the fbm. */
    private static final float AMPLITUDE_SUM = 1.875f;

    private CoverNoise() {}

    /** Well mixed 32 bit hash of a cell and a seed. */
    public static int hash(int x, int z, int seed) {
        int h = seed * 0x27D4EB2D;
        h ^= x * 0x165667B1;
        h = Integer.rotateLeft(h, 13) * 0x85EBCA6B;
        h ^= z * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    /** Hash of a cell mapped to [0, 1). */
    public static float unit(int x, int z, int seed) {
        return (hash(x, z, seed) >>> 8) * (1.0f / (1 << 24));
    }

    /**
     * Fractal gradient noise with {@value #OCTAVES} octaves, remapped to roughly 0..1 with its mean at 0.5.
     * Coordinates are in noise cells (one cell is one wavelength of the first octave).
     */
    public static float fbm(double x, double z, int seed) {
        float sum = 0.0f;
        float amplitude = 1.0f;
        double fx = x;
        double fz = z;
        for (int octave = 0; octave < OCTAVES; octave++) {
            sum += amplitude * gradient(fx, fz, seed + octave * 101);
            amplitude *= 0.5f;
            // Rotate a little between octaves so the lattice of one octave never lines up with the next.
            double rx = fx * 1.6 - fz * 1.2;
            double rz = fx * 1.2 + fz * 1.6;
            fx = rx + 17.3;
            fz = rz - 9.1;
        }
        // A single gradient octave stays within about +-0.7; the sum is scaled so the result spreads over 0..1.
        float value = 0.5f + sum / AMPLITUDE_SUM * 1.15f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** One octave of 2D gradient noise, roughly -0.7..0.7. */
    static float gradient(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        float fx = (float) (x - x0);
        float fz = (float) (z - z0);
        float n00 = corner(x0, z0, seed, fx, fz);
        float n10 = corner(x0 + 1, z0, seed, fx - 1.0f, fz);
        float n01 = corner(x0, z0 + 1, seed, fx, fz - 1.0f);
        float n11 = corner(x0 + 1, z0 + 1, seed, fx - 1.0f, fz - 1.0f);
        float u = fade(fx);
        float v = fade(fz);
        float a = n00 + (n10 - n00) * u;
        float b = n01 + (n11 - n01) * u;
        return a + (b - a) * v;
    }

    private static float corner(int cx, int cz, int seed, float dx, float dz) {
        // One of 16 unit gradient directions.
        double angle = (hash(cx, cz, seed) >>> 28) * (Math.PI / 8.0);
        return (float) (Math.cos(angle) * dx + Math.sin(angle) * dz);
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }
}
