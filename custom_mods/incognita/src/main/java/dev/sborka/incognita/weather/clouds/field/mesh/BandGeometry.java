package dev.sborka.incognita.weather.clouds.field.mesh;

import java.util.List;

/**
 * Finished, immutable geometry of one band window, ready for upload.
 *
 * @param band       band index
 * @param anchorX    window anchor in band-space chunks along X; the puff X coordinates are relative to
 *                   {@code anchorX * chunkSize}
 * @param anchorZ    window anchor in band-space chunks along Z
 * @param chunkSize  edge length of one chunk in blocks
 * @param threshold  cover threshold the strengths in the data are relative to
 * @param key        identity of the request this geometry answers
 * @param data       {@link PuffWriter#FLOATS_PER_PUFF} floats per puff
 * @param puffs      number of puffs
 * @param chunks     culling runs, in instance order
 */
public record BandGeometry(int band, int anchorX, int anchorZ, float chunkSize, float threshold, Object key,
                           float[] data, int puffs, List<ChunkBounds> chunks) {
    /** Band-space X of the coordinate origin of this geometry. */
    public double originX() {
        return anchorX * (double) chunkSize;
    }

    /** Band-space Z of the coordinate origin of this geometry. */
    public double originZ() {
        return anchorZ * (double) chunkSize;
    }
}
