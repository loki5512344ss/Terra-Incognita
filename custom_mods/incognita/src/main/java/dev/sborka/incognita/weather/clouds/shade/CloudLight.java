package dev.sborka.incognita.weather.clouds.shade;

/**
 * Pure light model of the weather sky: turns the sun elevation and the weather into the colours the cloud
 * composite uses, and the greyness the sky renderer reads too, so a stormy sky and its clouds always agree.
 * Fair weather keeps every part of a cloud bright (the underside is only a little cooler); only rain and thunder
 * (greyness) make clouds dark. No GL or Minecraft types, so it is unit tested.
 */
public final class CloudLight {
    /** A linear RGB colour, components nominally 0..1 (the sun colour may exceed 1). */
    public record Rgb(float r, float g, float b) {
        /** Relative luminance. */
        public float luma() {
            return 0.2126f * r + 0.7152f * g + 0.0722f * b;
        }

        Rgb mix(Rgb other, float t) {
            return new Rgb(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t);
        }

        Rgb scale(float s) {
            return new Rgb(r * s, g * s, b * s);
        }
    }

    /**
     * Result of the model.
     *
     * @param sunColor    colour of the direct light (sun, or the moon at night)
     * @param skyTop      ambient light that reaches upward facing surfaces
     * @param skyBottom   ambient light of the underside
     * @param opacity     largest opacity of a dense cloud core, 0..1
     * @param sunsetness  0..1, how close the sun is to the horizon
     * @param greyness    0..1, how overcast and rainy the sky is
     */
    public record Result(Rgb sunColor, Rgb skyTop, Rgb skyBottom, float opacity, float sunsetness, float greyness) {}

    private static final Rgb DAY_SUN = new Rgb(1.0f, 0.97f, 0.91f);
    private static final Rgb SUNSET_SUN = new Rgb(1.10f, 0.70f, 0.46f);
    private static final Rgb MOON = new Rgb(0.08f, 0.10f, 0.19f);
    private static final Rgb DAY_TOP = new Rgb(0.84f, 0.89f, 0.97f);
    private static final Rgb DAY_BOTTOM = new Rgb(0.80f, 0.84f, 0.92f);
    private static final Rgb SUNSET_TOP = new Rgb(0.92f, 0.78f, 0.78f);
    private static final Rgb SUNSET_BOTTOM = new Rgb(0.76f, 0.62f, 0.72f);
    private static final Rgb NIGHT_TOP = new Rgb(0.055f, 0.068f, 0.130f);
    private static final Rgb NIGHT_BOTTOM = new Rgb(0.028f, 0.034f, 0.070f);
    private static final Rgb RAIN_TOP = new Rgb(0.58f, 0.61f, 0.66f);
    private static final Rgb RAIN_BOTTOM = new Rgb(0.40f, 0.43f, 0.48f);

    private CloudLight() {}

    /**
     * Computes the light of the clouds.
     *
     * @param sunHeight   sine of the sun elevation: 1 at noon, 0 at the horizon, negative at night
     * @param cloudiness  weather cover in 0..1
     * @param rain        rain level in 0..1
     * @param thunder     thunder level in 0..1
     * @param baseOpacity configured opacity of a dense cloud core, 0..1
     */
    public static Result compute(float sunHeight, float cloudiness, float rain, float thunder, float baseOpacity) {
        float day = smoothstep(-0.10f, 0.25f, sunHeight);
        float horizon = 1.0f - smoothstep(0.0f, 0.38f, Math.abs(sunHeight));
        float sunset = horizon * smoothstep(-0.22f, -0.04f, sunHeight);
        float overcast = Math.max(0.0f, cloudiness - 0.7f) / 0.2f;
        float grey = clamp01(Math.max(rain * 0.78f, thunder) + 0.18f * clamp01(overcast));

        float sunStrength = smoothstep(-0.12f, 0.20f, sunHeight);
        Rgb sunlight = DAY_SUN.mix(SUNSET_SUN, sunset).scale(1.0f - 0.8f * grey);
        // The moon only matters once the sun is gone.
        Rgb sun = MOON.scale(1.0f - 0.5f * grey).mix(sunlight, sunStrength);

        Rgb dayTop = DAY_TOP.mix(SUNSET_TOP, sunset * 0.8f).mix(RAIN_TOP, grey);
        Rgb dayBottom = DAY_BOTTOM.mix(SUNSET_BOTTOM, sunset * 0.85f).mix(RAIN_BOTTOM, grey);
        Rgb top = NIGHT_TOP.scale(1.0f - 0.35f * grey).mix(dayTop, day);
        Rgb bottom = NIGHT_BOTTOM.scale(1.0f - 0.35f * grey).mix(dayBottom, day);

        float opacity = clamp(baseOpacity + 0.06f * (cloudiness - 0.5f) + 0.08f * grey, 0.05f, 0.985f);
        return new Result(sun, top, bottom, opacity, sunset, grey);
    }

    /** Whether the sun (true) or the moon (false) is the direction of the direct light. */
    public static boolean sunLights(float sunHeight) {
        return sunHeight >= -0.12f;
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
