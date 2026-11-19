package dev.sborka.incognita.weather.clouds.field;

import dev.sborka.incognita.weather.clouds.field.mesh.PuffWriter;
import dev.sborka.incognita.weather.clouds.field.noise.HeapRandom;

/**
 * Layouts of the cloud types. Every heap is a small set of layout boxes, voxelized into cubes by PuffWriter: a broad
 * body with a bumpy crown and an uneven underside, never a single box and never a thin column. Exposure feeds the
 * ambient occlusion of the shading (crown humps 1, parts tucked under or into the body lower); the growth offset
 * orders how a heap builds up when the cover rises (base first, crown last).
 */
public final class HeapShapes {
    private static final float TAU = (float) (Math.PI * 2.0);

    private HeapShapes() {}

    /**
     * Fair weather cumulus: wide flat base puffs at slightly different heights, a body, a cauliflower crown that
     * follows a dome profile and a few small puffs hanging just under the base.
     *
     * @param width  heap width in blocks
     * @param height heap height; capped at 0.6 x width so a heap never turns into a tower
     */
    public static void cumulus(PuffWriter out, HeapRandom rnd, float x, float base, float z, float width,
                               float height, float strength) {
        float h = Math.min(height, width * 0.6f);
        float stretch = rnd.range(1.0f, 1.45f);
        out.beginHeap(x, base, z, h, strength, rnd.next() * TAU, rnd.next());

        int baseCount = width > 105.0f ? 5 : width > 72.0f ? 4 : 3;
        for (int k = 0; k < baseCount; k++) {
            float along = baseCount == 1 ? 0.0f : (k / (float) (baseCount - 1) - 0.5f) * 0.58f * width * stretch;
            along += rnd.range(-0.06f, 0.06f) * width;
            float across = rnd.range(-0.16f, 0.16f) * width / stretch;
            float r = width * rnd.range(0.25f, 0.32f);
            float ry = Math.max(3.0f, h * rnd.range(0.20f, 0.27f));
            // Bottoms between 0.2 ry below and 0.05 ry above the base: one broad underside, gently uneven.
            float y = base + ry * rnd.range(0.80f, 1.05f);
            out.puff(along, y, across, r * (1.0f + 0.22f * (stretch - 1.0f)), ry, r, 0.74f, 0.0f);
        }

        int bodyCount = width > 95.0f ? 3 : 2;
        for (int k = 0; k < bodyCount; k++) {
            float angle = rnd.next() * TAU;
            float rho = rnd.next() * 0.16f * width;
            float r = width * rnd.range(0.22f, 0.29f);
            float ry = h * rnd.range(0.34f, 0.40f);
            float y = base + h * 0.42f + rnd.range(-0.04f, 0.04f) * h;
            out.puff(cos(angle) * rho * stretch, y, sin(angle) * rho, r * (1.0f + 0.15f * (stretch - 1.0f)), ry,
                r, 0.86f, 0.03f);
        }

        // Crown: humps on a dome. Central humps are bigger and grow first, outer ones are smaller.
        int crownCount = Math.min(7, 3 + Math.round(width / 40.0f));
        float dome = 0.30f * width;
        for (int k = 0; k < crownCount; k++) {
            float angle = (k + rnd.range(-0.3f, 0.3f)) * TAU / crownCount;
            float rho = (k == 0 ? rnd.range(0.0f, 0.25f) : rnd.range(0.35f, 0.95f)) * dome;
            float edge = rho / dome;
            float r = width * rnd.range(0.11f, 0.19f) * (1.15f - 0.4f * edge);
            float ry = Math.min(r * rnd.range(0.80f, 0.95f), h * 0.34f);
            float top = base + h * (1.0f - 0.42f * edge * edge);
            float y = top - ry * rnd.range(0.95f, 1.15f);
            out.puff(cos(angle) * rho * stretch, y, sin(angle) * rho, r, ry, r * rnd.range(0.85f, 1.0f), 1.0f,
                0.06f + 0.09f * edge);
        }

        // Underside puffs: small and medium lumps a few blocks under the base, so the belly is not one flat sheet.
        int underCount = width > 90.0f ? 4 : width > 64.0f ? 3 : 2;
        for (int k = 0; k < underCount; k++) {
            float angle = rnd.next() * TAU;
            float rho = (float) Math.sqrt(rnd.next()) * 0.34f * width;
            float r = width * rnd.range(0.07f, 0.14f);
            float ry = r * rnd.range(0.38f, 0.52f);
            float y = base + ry * rnd.range(-0.15f, 0.25f);
            out.puff(cos(angle) * rho * stretch, y, sin(angle) * rho, r * 1.15f, ry, r, 0.62f, 0.02f);
        }
    }

    /**
     * Stratocumulus deck cell for an overcast sky: wide flat lumps that overlap their neighbours, so a rainy deck is
     * watertight, with a lumpy top and underside.
     */
    public static void deck(PuffWriter out, HeapRandom rnd, float x, float base, float z, float width, float height,
                            float strength) {
        out.beginHeap(x, base, z, height, strength, rnd.next() * TAU, rnd.next());
        out.coarse(1.8f);
        for (int k = 0; k < 3; k++) {
            float along = (k - 1) * 0.30f * width + rnd.range(-0.05f, 0.05f) * width;
            float across = rnd.range(-0.12f, 0.12f) * width;
            float r = width * rnd.range(0.32f, 0.38f);
            float ry = height * rnd.range(0.30f, 0.36f);
            out.puff(along, base + ry * rnd.range(0.85f, 1.05f), across, r * 1.1f, ry, r, 0.72f, 0.0f);
        }
        for (int k = 0; k < 2; k++) {
            float angle = rnd.next() * TAU;
            float rho = rnd.range(0.05f, 0.25f) * width;
            float r = width * rnd.range(0.18f, 0.24f);
            float ry = height * rnd.range(0.26f, 0.32f);
            out.puff(cos(angle) * rho, base + height * 0.66f, sin(angle) * rho, r, ry, r, 0.95f, 0.05f);
        }
        for (int k = 0; k < 3; k++) {
            float angle = rnd.next() * TAU;
            float rho = (float) Math.sqrt(rnd.next()) * 0.38f * width;
            float r = width * rnd.range(0.09f, 0.15f);
            float ry = r * rnd.range(0.35f, 0.50f);
            out.puff(cos(angle) * rho, base + ry * rnd.range(-0.2f, 0.2f), sin(angle) * rho, r * 1.2f, ry, r, 0.6f,
                0.02f);
        }
    }

    /** Altocumulus patch: a handful of small, slightly flattened cloudlets. */
    public static void altocumulus(PuffWriter out, HeapRandom rnd, float x, float base, float z, float width,
                                   float height, float strength) {
        out.beginHeap(x, base, z, height, strength, rnd.next() * TAU, rnd.next());
        int count = Math.min(8, 4 + Math.round(width / 22.0f));
        for (int k = 0; k < count; k++) {
            float angle = rnd.next() * TAU;
            float rho = (float) Math.sqrt(rnd.next()) * 0.28f * width;
            float r = width * rnd.range(0.18f, 0.28f);
            float ry = Math.min(height * rnd.range(0.38f, 0.55f), r * 0.75f);
            float y = base + ry * rnd.range(0.9f, 1.3f);
            out.puff(cos(angle) * rho, y, sin(angle) * rho, r * rnd.range(1.0f, 1.3f), ry, r, 0.92f,
                rnd.range(0.0f, 0.10f));
        }
    }

    /**
     * Cirrus streak: thin puffs strung along the wind.
     *
     * @param axis direction of the streak in radians
     */
    public static void cirrus(PuffWriter out, HeapRandom rnd, float x, float base, float z, float width,
                              float height, float strength, float axis) {
        out.beginHeap(x, base, z, height, strength, axis + rnd.range(-0.25f, 0.25f), rnd.next());
        int count = 3 + (int) (rnd.next() * 3.0f);
        for (int k = 0; k < count; k++) {
            float along = (k / (float) (count - 1) - 0.5f) * 0.8f * width + rnd.range(-0.05f, 0.05f) * width;
            float across = rnd.range(-0.08f, 0.08f) * width;
            float ru = width * rnd.range(0.16f, 0.26f);
            float rv = width * rnd.range(0.05f, 0.09f);
            float ry = height * rnd.range(0.35f, 0.55f);
            out.puff(along, base + ry + rnd.range(-1.0f, 1.0f), across, ru, ry, rv, 1.0f, 0.03f * k);
        }
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }
}
