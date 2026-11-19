package dev.sborka.incognita.weather.clouds.field;

import dev.sborka.incognita.weather.clouds.field.mesh.BandGeometry;
import dev.sborka.incognita.weather.clouds.field.mesh.PuffWriter;
import dev.sborka.incognita.weather.clouds.field.noise.CoverNoise;
import dev.sborka.incognita.weather.clouds.field.noise.HeapRandom;

/**
 * Generates the heaps of one band inside a round window around the camera. Everything is a pure function of the
 * band-space cell coordinates, so a heap looks the same whichever window it is generated in, and the band can move
 * with its own wind simply by shifting the window. Safe to call from any thread.
 */
public final class CloudField {
    private static final int SEED = 0x51C10D;
    /** Distance fractions of the window radius where the cube size starts and stops growing. */
    private static final double LOD_START = 0.40;
    private static final double LOD_END = 0.78;
    /** Extra cube size at the far end, and the number of steps between 1 and 1 + LOD_MAX. */
    private static final float LOD_MAX = 0.8f;
    private static final int LOD_STEPS = 4;

    private CloudField() {}

    /**
     * One generation request.
     *
     * @param spec       the band
     * @param anchorX    window centre chunk along X (band space)
     * @param anchorZ    window centre chunk along Z (band space)
     * @param radius     window radius in blocks
     * @param threshold  cover threshold of {@link CoverModel#threshold} for this band
     * @param cloudBase  world Y of the cloud base
     * @param streetAxis fixed direction of the cloud streets and cirrus streaks in radians (the band's mean wind)
     */
    public record Request(BandSpec spec, int anchorX, int anchorZ, int radius, float threshold, float cloudBase,
                          float streetAxis) {}

    /** Builds the geometry of a request. */
    public static BandGeometry generate(Request request) {
        BandSpec spec = request.spec();
        float chunk = spec.chunkSize();
        int reach = (int) Math.ceil(request.radius() / chunk) + 1;
        double originX = request.anchorX() * (double) chunk;
        double originZ = request.anchorZ() * (double) chunk;
        // Window centre is the middle of the anchor chunk.
        double radiusSq = (double) request.radius() * request.radius();
        PuffWriter out = new PuffWriter(estimate(reach), (int) Math.round(originX), (int) Math.round(originZ));
        HeapRandom rnd = new HeapRandom(1);
        // Near overcast nearly every cell holds a big heap plus a deck lump. Seen from below that is one grey sheet,
        // so bigger cubes keep the window around 100k instances without a visible change.
        float overcast = 1.0f + 0.9f * smoothstep(0.52f, 0.24f, request.threshold());
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                double nearSq = nearestSq(dx, dz, chunk);
                if (nearSq > radiusSq) {
                    continue;
                }
                out.baseCubeScale(overcast);
                out.lod(lodScale(Math.sqrt(nearSq), request.radius()));
                out.beginChunk();
                int cellX = (request.anchorX() + dx) * 2;
                int cellZ = (request.anchorZ() + dz) * 2;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        cell(out, rnd, request, cellX + i, cellZ + j, originX, originZ);
                    }
                }
                out.endChunk();
            }
        }
        return out.finish(spec.index(), request.anchorX(), request.anchorZ(), chunk, request.threshold(), request);
    }

    /**
     * Level of detail of a chunk by its distance from the window centre: near chunks keep the full cube size, far ones
     * (the outer half of the window, which also lies in the far fade of the shader) get up to 1.8 times bigger cubes in
     * five steps. A cube there covers few pixels, so the cloud keeps its look while the instance count of the
     * horizon, where the vertex work dominates, drops by about a third.
     */
    static float lodScale(double distance, int radius) {
        float t = smoothstep((float) (LOD_START * radius), (float) (LOD_END * radius), (float) distance);
        return 1.0f + LOD_MAX * (float) Math.floor(t * LOD_STEPS) / LOD_STEPS;
    }

    /** Squared distance from the window centre to the nearest point of the chunk at offset (dx, dz). */
    private static double nearestSq(int dx, int dz, float chunk) {
        double ex = Math.max(0.0, Math.abs(dx) - 0.5) * chunk;
        double ez = Math.max(0.0, Math.abs(dz) - 0.5) * chunk;
        return ex * ex + ez * ez;
    }

    private static int estimate(int reach) {
        int chunks = (2 * reach + 1) * (2 * reach + 1);
        return chunks * 4 * 40;
    }

    private static void cell(PuffWriter out, HeapRandom rnd, Request request, int i, int j, double originX,
                             double originZ) {
        BandSpec spec = request.spec();
        float spacing = spec.spacing();
        rnd.reset(CoverNoise.hash(i, j, SEED + spec.index() * 7919));
        double bx = (i + 0.5 + rnd.range(-0.4f, 0.4f)) * spacing;
        double bz = (j + 0.5 + rnd.range(-0.4f, 0.4f)) * spacing;
        float noise = coverage(spec, bx, bz, request.streetAxis()) + rnd.range(-0.03f, 0.03f);
        float strength = noise - request.threshold();
        float x = (float) (bx - originX);
        float z = (float) (bz - originZ);
        float base = request.cloudBase() + spec.base() + rnd.range(-1.0f, 1.0f) * spec.baseJitter();
        if (strength >= -CoverModel.MARGIN) {
            float size = smoothstep(-0.05f, 0.35f, strength);
            float width = lerp(spec.widthMin(), spec.widthMax(), size) * rnd.range(0.85f, 1.15f);
            // Tall towers grow in fair weather; under a heavy overcast the heaps flatten into stratocumulus.
            float flatten = 0.45f + 0.55f * smoothstep(0.18f, 0.45f, request.threshold());
            float height = lerp(spec.thicknessMin(), spec.thicknessMax() * flatten, smoothstep(0.0f, 0.40f, strength))
                * rnd.range(0.85f, 1.2f);
            switch (spec.style()) {
                case CUMULUS -> HeapShapes.cumulus(out, rnd, x, base, z, width, height, strength);
                case ALTOCUMULUS -> HeapShapes.altocumulus(out, rnd, x, base, z, width, height, strength);
                case CIRRUS -> HeapShapes.cirrus(out, rnd, x, base, z, width, height, strength,
                    request.streetAxis());
            }
        }
        if (spec.style() == BandSpec.Style.CUMULUS) {
            // Near overcast every cell also grows a deck lump, offset by half a cell so it closes the gaps.
            float deck = CoverModel.DECK_NOISE + rnd.range(-0.03f, 0.03f) - request.threshold();
            // Smaller margin than the heaps: the deck only matters near overcast, so it is not carried around
            // invisibly in fair weather.
            if (deck >= -CoverModel.DECK_MARGIN) {
                float half = spacing * 0.5f;
                HeapShapes.deck(out, rnd, x + half, base - 4.0f, z + half, spacing * rnd.range(1.7f, 2.0f),
                    rnd.range(26.0f, 40.0f), deck);
            }
        }
    }

    /** Coverage noise of a band at a band-space point, stretched along the street axis. */
    static float coverage(BandSpec spec, double x, double z, float streetAxis) {
        double c = Math.cos(streetAxis);
        double s = Math.sin(streetAxis);
        double along = (x * c + z * s) / spec.streets();
        double across = -x * s + z * c;
        return CoverNoise.fbm(along / spec.noiseScale(), across / spec.noiseScale(), SEED + spec.index() * 131);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
