package dev.sborka.incognita.weather.clouds.field.mesh;

import dev.sborka.incognita.weather.clouds.field.noise.CoverNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Voxelizes the layout boxes of the heaps into small and medium cloud cubes ("puffs"), collects them into one float
 * stream and groups them into culling chunks. Layout per puff, four vec4
 * attributes:
 * <pre>
 *   0: centre x, centre y, centre z, strength (noise minus the cover threshold)
 *   1: half size x, half size y, half size z, heap seed 0..1 (same for every puff of a heap)
 *   2: heap centre x, heap base y, heap centre z, heap height
 *   3: cos yaw, sin yaw, exposure 0..1, growth offset
 * </pre>
 * X and Z are relative to the band window anchor, Y is world height. A heap sets the shared values once with
 * {@link #beginHeap}, then adds its puffs.
 */
public final class PuffWriter {
    public static final int FLOATS_PER_PUFF = 16;
    /** Grid of the box centres and half sizes in blocks: every box edge lands on an even block coordinate. */
    public static final float GRID = 2.0f;
    /** Scales the layout extents down so overlapping boxes do not make a heap too bulky. */
    private static final float BOX_SCALE = 0.86f;
    /** Target edge of one cloud cube, horizontal and vertical, in blocks. */
    private static final float CUBE = 18.0f;
    private static final float CUBE_Y = 12.0f;

    private final int originX;
    private final int originZ;

    private float[] data;
    private int puffs;
    private final List<ChunkBounds> chunks = new ArrayList<>();

    private int chunkStart;
    private float minX;
    private float minY;
    private float minZ;
    private float maxX;
    private float maxY;
    private float maxZ;

    private float heapX;
    private float heapBase;
    private float heapZ;
    private float heapHeight;
    private float heapStrength;
    private float heapSeed;
    private float cubeScale = 1.0f;
    private float baseCubeScale = 1.0f;
    private float lod = 1.0f;
    private float cos = 1.0f;
    private float sin;

    /**
     * @param originX band-space X of the local coordinate origin (whole blocks), for position stable hashing
     * @param originZ band-space Z of the local coordinate origin
     */
    public PuffWriter(int expectedPuffs, int originX, int originZ) {
        this.originX = originX;
        this.originZ = originZ;
        data = new float[Math.max(expectedPuffs, 64) * FLOATS_PER_PUFF];
    }

    /** Starts a new culling chunk. */
    public void beginChunk() {
        chunkStart = puffs;
        minX = Float.POSITIVE_INFINITY;
        minY = Float.POSITIVE_INFINITY;
        minZ = Float.POSITIVE_INFINITY;
        maxX = Float.NEGATIVE_INFINITY;
        maxY = Float.NEGATIVE_INFINITY;
        maxZ = Float.NEGATIVE_INFINITY;
    }

    /** Closes the current chunk; an empty chunk is dropped. */
    public void endChunk() {
        if (puffs > chunkStart) {
            chunks.add(new ChunkBounds(chunkStart, puffs - chunkStart, minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    /**
     * Shared values of the next puffs.
     *
     * @param x        heap centre x
     * @param base     heap base height (world Y)
     * @param z        heap centre z
     * @param height   heap height in blocks
     * @param strength coverage noise minus the threshold the geometry is built for
     * @param yaw      rotation of the heap around Y in radians
     * @param seed     0..1, drives the heap's slow growth cycle in the shader
     */
    public void beginHeap(float x, float base, float z, float height, float strength, float yaw, float seed) {
        heapSeed = seed;
        cubeScale = baseCubeScale;
        // Boxes stay on the world grid: the heap axis is one of the four grid directions.
        double quarter = Math.PI * 0.5;
        double snapped = Math.round(yaw / quarter) * quarter;
        heapX = x;
        heapBase = base;
        heapZ = z;
        heapHeight = height;
        heapStrength = strength;
        cos = (float) Math.rint(Math.cos(snapped));
        sin = (float) Math.rint(Math.sin(snapped));
    }

    /**
     * Uses bigger cubes for the rest of the current heap. The overcast deck is seen from far below as one grey sheet,
     * so coarser cubes there cost a fraction of the instances without a visible difference.
     */
    public void coarse(float scale) {
        cubeScale = baseCubeScale * scale;
    }

    /**
     * Level of detail of the current chunk: cubes grow by this factor sideways and by a quarter of it in height, so a far
     * cloud keeps its thickness and its underside while it needs fewer cubes.
     */
    public void lod(float scale) {
        lod = scale;
    }

    /** Cube size factor of every heap of this window (bigger cubes keep a heavy overcast within the budget). */
    public void baseCubeScale(float scale) {
        baseCubeScale = scale;
    }

    /**
     * Adds a puff. Offsets {@code u} (along the heap axis) and {@code v} (across it) are rotated by the heap yaw.
     *
     * @param u        offset along the heap axis
     * @param y        centre height (world Y)
     * @param v        offset across the heap axis
     * @param ru       radius along the heap axis
     * @param ry       vertical radius
     * @param rv       radius across the heap axis
     * @param exposure 1 for an open crown hump, lower for parts tucked into the body (ambient occlusion)
     * @param grow     strength above which this puff starts to appear
     */
    public void puff(float u, float y, float v, float ru, float ry, float rv, float exposure, float grow) {
        float x = heapX + u * cos - v * sin;
        float z = heapZ + u * sin + v * cos;
        // The heap axis is a quarter turn, so the box extents map straight onto world X and Z.
        boolean alongX = Math.abs(cos) > 0.5f;
        float ex = (alongX ? ru : rv) * BOX_SCALE;
        float ez = (alongX ? rv : ru) * BOX_SCALE;
        float ey = ry * BOX_SCALE;
        float cube = CUBE * cubeScale * lod;
        int nx = Math.max(1, Math.round(2.0f * ex / cube));
        int ny = Math.max(1, Math.round(2.0f * ey / (CUBE_Y * cubeScale * (1.0f + 0.25f * (lod - 1.0f)))));
        int nz = Math.max(1, Math.round(2.0f * ez / cube));
        float sx = 2.0f * ex / nx;
        float sy = 2.0f * ey / ny;
        float sz = 2.0f * ez / nz;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                for (int k = 0; k < nz; k++) {
                    boolean edgeX = i == 0 || i == nx - 1;
                    boolean edgeY = j == 0 || j == ny - 1;
                    boolean edgeZ = k == 0 || k == nz - 1;
                    if (!edgeX && !edgeY && !edgeZ) {
                        // Hidden inside the box; the surface cubes already make it opaque.
                        continue;
                    }
                    cube(x - ex + (i + 0.5f) * sx, y - ey + (j + 0.5f) * sy, z - ez + (k + 0.5f) * sz,
                        sx, sy, sz, edgeX && edgeZ && nx > 1 && nz > 1, j == 0, j == ny - 1, exposure, grow);
                }
            }
        }
    }

    /**
     * One cube of a voxelized box. Its jitter comes from a hash of its band-space position, so it looks the same in
     * every window. Corner columns are thinned out (a rounded silhouette made of cubes), bottom cubes hang down a
     * little (a fuzzy, uneven underside) and top cubes stick up (a bumpy crown).
     */
    private void cube(float x, float y, float z, float sx, float sy, float sz, boolean corner, boolean bottom,
                      boolean top, float exposure, float grow) {
        int h = CoverNoise.hash(Math.round(x) + originX, Math.round(z) + originZ, Math.round(y) * 31 + 0x5EED);
        float r1 = unit(h);
        float r2 = unit(h * 0x2C1B3C6D + 1);
        float r3 = unit(h * 0x297A2D39 + 2);
        float r4 = unit(h * 0x165667B1 + 3);
        if (corner && r1 < 0.45f) {
            return;
        }
        float cx = snap(x + (r2 - 0.5f) * 0.30f * sx);
        float cz = snap(z + (r4 - 0.5f) * 0.30f * sz);
        float cy = y;
        if (bottom) {
            cy -= r3 * 0.45f * sy;
        }
        if (top) {
            cy += r2 * 0.35f * sy;
        }
        cy = snap(cy);
        float grow2 = 1.0f + 0.30f * r3;
        float hx = half(sx * 0.5f * grow2);
        float hy = half(sy * 0.5f * (1.0f + 0.25f * r1));
        float hz = half(sz * 0.5f * grow2);
        ensureCapacity();
        int i = puffs * FLOATS_PER_PUFF;
        data[i] = cx;
        data[i + 1] = cy;
        data[i + 2] = cz;
        data[i + 3] = heapStrength;
        data[i + 4] = hx;
        data[i + 5] = hy;
        data[i + 6] = hz;
        data[i + 7] = heapSeed;
        data[i + 8] = heapX;
        data[i + 9] = heapBase;
        data[i + 10] = heapZ;
        data[i + 11] = heapHeight;
        // Cubes are axis aligned; the yaw slot stays the identity.
        data[i + 12] = 1.0f;
        data[i + 13] = 0.0f;
        data[i + 14] = exposure;
        data[i + 15] = grow + 0.04f * r4;
        puffs++;
        minX = Math.min(minX, cx - hx);
        maxX = Math.max(maxX, cx + hx);
        minZ = Math.min(minZ, cz - hz);
        maxZ = Math.max(maxZ, cz + hz);
        minY = Math.min(minY, cy - hy);
        maxY = Math.max(maxY, cy + hy);
    }

    private static float unit(int hash) {
        int h = hash ^ (hash >>> 16);
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        return (h >>> 8) * (1.0f / (1 << 24));
    }

    private static float snap(float v) {
        return Math.round(v / GRID) * GRID;
    }

    /** Half size of a box on the grid, at least one grid step. */
    private static float half(float radius) {
        return Math.max(GRID, Math.round(radius * BOX_SCALE / GRID) * GRID);
    }

    private void ensureCapacity() {
        if ((puffs + 1) * FLOATS_PER_PUFF > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
    }

    public int puffs() {
        return puffs;
    }

    /** Copies the result into an immutable geometry. */
    public BandGeometry finish(int band, int anchorX, int anchorZ, float chunkSize, float threshold, Object key) {
        return new BandGeometry(band, anchorX, anchorZ, chunkSize, threshold, key,
            Arrays.copyOf(data, puffs * FLOATS_PER_PUFF), puffs, List.copyOf(chunks));
    }
}
