package dev.sborka.incognita.lighting.sodium;

import dev.sborka.incognita.lighting.field.LightColor;

/**
 * The per-vertex math of the Sodium delivery: how a sampled RGB light changes the vertex lightmap and color.
 * Block light in a packed lightmap is {@code level << 4} (0..240) in the low byte.
 */
public final class VertexLight {
    /** Smallest tint channel, so a pure red light never turns a surface fully black. */
    public static final float TINT_FLOOR = 0.12f;

    /** Lights fainter than this are ignored (residue of mixed cells, invisible anyway). */
    public static final int MIN_INTENSITY = 4;

    /** Below this intensity the tint fades in linearly, so the outer rim of a light has no visible edge. */
    public static final int TINT_RAMP = 96;

    private VertexLight() {}

    /** Block light (0..240, lightmap scale) that the colored light alone would give. */
    public static int coloredBlockLm(int rgb) {
        return LightColor.intensity(rgb) * 240 / 255;
    }

    /** New block light for the vertex: the brighter of vanilla and colored, so the radius can exceed vanilla's 15. */
    public static int blockLm(int rgb, int vanillaBlockLm) {
        if (LightColor.intensity(rgb) < MIN_INTENSITY) {
            return vanillaBlockLm;
        }
        return Math.max(vanillaBlockLm, coloredBlockLm(rgb));
    }

    /** Share of the vertex block light that comes from the colored source, 0..1, faded out toward the light's rim. */
    public static float weight(int rgb, int vanillaBlockLm) {
        int ours = coloredBlockLm(rgb);
        int total = Math.max(ours, vanillaBlockLm);
        if (total == 0) {
            return 0f;
        }
        float ramp = Math.min(1f, (float) LightColor.intensity(rgb) / TINT_RAMP);
        return (float) ours / total * ramp;
    }

    /** Multiplier for one color channel (0..255 of the light) given the weight and the light's peak channel. */
    public static float channelTint(int channel, int peak, float weight, float strength) {
        if (peak == 0) {
            return 1f;
        }
        float hue = Math.max(TINT_FLOOR, (float) channel / peak);
        return 1f - weight * strength * (1f - hue);
    }

    /** Applies the tint to an ARGB color and returns the new ARGB color (alpha untouched). */
    public static int tintArgb(int argb, int rgb, int vanillaBlockLm, float strength) {
        int peak = LightColor.intensity(rgb);
        if (peak < MIN_INTENSITY) {
            return argb;
        }
        float w = weight(rgb, vanillaBlockLm);
        int a = argb >>> 24;
        int r = Math.round(((argb >> 16) & 0xFF) * channelTint(LightColor.r(rgb), peak, w, strength));
        int g = Math.round(((argb >> 8) & 0xFF) * channelTint(LightColor.g(rgb), peak, w, strength));
        int b = Math.round((argb & 0xFF) * channelTint(LightColor.b(rgb), peak, w, strength));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Average of up to four light samples, in 0xRRGGBB. */
    public static int average4(int a, int b, int c, int d) {
        return LightColor.pack(
            (LightColor.r(a) + LightColor.r(b) + LightColor.r(c) + LightColor.r(d)) / 4,
            (LightColor.g(a) + LightColor.g(b) + LightColor.g(c) + LightColor.g(d)) / 4,
            (LightColor.b(a) + LightColor.b(b) + LightColor.b(c) + LightColor.b(d)) / 4);
    }
}
