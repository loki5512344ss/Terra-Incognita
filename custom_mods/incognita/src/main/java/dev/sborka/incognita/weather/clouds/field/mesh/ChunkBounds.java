package dev.sborka.incognita.weather.clouds.field.mesh;

/**
 * A run of consecutive puff instances that belong to one chunk of a band, with their bounds. X and Z are relative
 * to the band window anchor, Y is world height. Used for frustum culling.
 *
 * @param start first instance
 * @param count number of instances
 */
public record ChunkBounds(int start, int count, float minX, float minY, float minZ, float maxX, float maxY,
                          float maxZ) {}
