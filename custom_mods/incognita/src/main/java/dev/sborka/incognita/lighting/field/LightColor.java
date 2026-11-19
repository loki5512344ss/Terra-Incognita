package dev.sborka.incognita.lighting.field;

/**
 * Packed RGB light value: {@code 0xRRGGBB}, each channel 0..255. The brightest channel is the light intensity
 * (255 corresponds to vanilla level 15); the ratio of the channels is the hue.
 */
public final class LightColor {
    public static final int WHITE = 0xFFFFFF;

    private LightColor() {}

    public static int pack(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    /** Intensity of a light value, the largest channel. */
    public static int intensity(int c) {
        return Math.max(r(c), Math.max(g(c), b(c)));
    }

    /** Per-channel maximum, how two lights combine. */
    public static int max(int a, int b) {
        return pack(Math.max(r(a), r(b)), Math.max(g(a), g(b)), Math.max(b(a), b(b)));
    }

    /** Scales the color so its intensity becomes {@code level / 15 * 255}, keeping the hue. */
    public static int withLevel(int color, int level) {
        int m = intensity(color);
        int target = Math.max(0, Math.min(15, level)) * 255 / 15;
        if (m == 0 || target == 0) {
            return 0;
        }
        return pack(r(color) * target / m, g(color) * target / m, b(color) * target / m);
    }

    /** A cell below this intensity is not spread any further (it can only be 0 or faint residue). */
    public static final int CUTOFF = 1;

    /**
     * Fades a light by one block. Every channel loses the fraction {@code decay / 256} of its own value, rounded up, so
     * the loss is at least 1 per block: the light shrinks geometrically and then runs out one step at a time instead
     * of being cut off at a threshold. Channels never influence each other, so the reach depends only on the level
     * (the brightest channel) and not on the color, several sources superpose exactly by maximum, and removing one
     * source cannot leave residue of another.
     */
    public static int attenuate(int c, int decay) {
        return pack(fade(r(c), decay), fade(g(c), decay), fade(b(c), decay));
    }

    private static int fade(int v, int decay) {
        return v <= 0 ? 0 : Math.max(0, v - ((v * decay + 255) >> 8));
    }

    /** Multiplies a light by a filter color (0xFFFFFF = clear), used for stained glass. */
    public static int filter(int c, int filter) {
        return pack(r(c) * r(filter) / 255, g(c) * g(filter) / 255, b(c) * b(filter) / 255);
    }

    /** Decay (1/256 per block) so that a full-intensity light runs out after at most {@code radius} blocks. */
    public static int stepForRadius(int radius) {
        int want = Math.max(1, radius);
        for (int decay = 1; decay < 256; decay++) {
            if (rangeOf(255, decay) <= want) {
                return decay;
            }
        }
        return 255;
    }

    /** How many blocks a channel of the given value travels before it goes dark. */
    public static int rangeOf(int value, int decay) {
        int n = 0;
        int v = value;
        while (v > 0) {
            v = fade(v, decay);
            n++;
        }
        return n;
    }

    /** How far, in blocks, a light of the given level travels. */
    public static int rangeFor(int level, int decay) {
        return rangeOf(Math.max(0, Math.min(15, level)) * 255 / 15, decay);
    }

    /** Parses {@code #RRGGBB} or {@code RRGGBB}; returns -1 when invalid. */
    public static int parseHex(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.startsWith("#") ? s.substring(1) : s;
        if (t.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
