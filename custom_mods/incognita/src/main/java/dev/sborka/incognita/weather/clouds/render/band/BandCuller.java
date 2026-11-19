package dev.sborka.incognita.weather.clouds.render.band;

import dev.sborka.incognita.weather.clouds.field.mesh.BandGeometry;
import dev.sborka.incognita.weather.clouds.field.mesh.ChunkBounds;

import java.util.List;
import org.joml.FrustumIntersection;

/**
 * Frustum culls the chunks of one band and merges the visible ones into runs of consecutive instances. Chunk bounds
 * are stored relative to the band anchor; the per band origin (anchor plus wind offset minus camera) moves them to
 * camera space, so the culling follows each band's own drift exactly. The bounds also grow by the earth curvature
 * drop the vertex shader applies.
 */
public final class BandCuller {
    private int[] starts = new int[64];
    private int[] counts = new int[64];
    private int runs;

    /**
     * Culls a band.
     *
     * @param originX    camera-relative X of the band anchor
     * @param originZ    camera-relative Z of the band anchor
     * @param cameraY    camera height
     * @param curvature  vertical drop per squared horizontal block, as in the vertex shader
     * @param distance   draw distance; chunks entirely beyond it are skipped
     */
    public void cull(BandGeometry geometry, FrustumIntersection frustum, float originX, float originZ, float cameraY,
                     float curvature, float distance) {
        List<ChunkBounds> chunks = geometry.chunks();
        if (chunks.size() + 1 > starts.length) {
            starts = new int[chunks.size() + 1];
            counts = new int[chunks.size() + 1];
        }
        runs = 0;
        int runStart = -1;
        int runCount = 0;
        for (ChunkBounds c : chunks) {
            float minX = c.minX() + originX;
            float maxX = c.maxX() + originX;
            float minZ = c.minZ() + originZ;
            float maxZ = c.maxZ() + originZ;
            float nearX = Math.max(0.0f, Math.max(minX, -maxX));
            float nearZ = Math.max(0.0f, Math.max(minZ, -maxZ));
            float nearSq = nearX * nearX + nearZ * nearZ;
            boolean visible = nearSq <= distance * distance;
            if (visible) {
                float farX = Math.max(Math.abs(minX), Math.abs(maxX));
                float farZ = Math.max(Math.abs(minZ), Math.abs(maxZ));
                float lowest = c.minY() - cameraY - (farX * farX + farZ * farZ) * curvature;
                float highest = c.maxY() - cameraY - nearSq * curvature;
                visible = frustum.testAab(minX, lowest, minZ, maxX, highest, maxZ);
            }
            if (visible) {
                if (runCount == 0) {
                    runStart = c.start();
                }
                // Chunks are consecutive in the buffer, so a visible neighbour extends the run.
                if (runStart + runCount == c.start()) {
                    runCount += c.count();
                    continue;
                }
                flush(runStart, runCount);
                runStart = c.start();
                runCount = c.count();
            } else {
                flush(runStart, runCount);
                runCount = 0;
            }
        }
        flush(runStart, runCount);
    }

    private void flush(int start, int count) {
        if (count > 0) {
            starts[runs] = start;
            counts[runs] = count;
            runs++;
        }
    }

    public int[] starts() {
        return starts;
    }

    public int[] counts() {
        return counts;
    }

    public int runs() {
        return runs;
    }
}
