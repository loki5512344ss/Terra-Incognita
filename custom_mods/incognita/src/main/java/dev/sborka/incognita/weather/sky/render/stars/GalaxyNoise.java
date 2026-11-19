package dev.sborka.incognita.weather.sky.render.stars;

import dev.sborka.incognita.weather.sky.math.SkyMath;

/** Value noise on the integer hash of {@link SkyMath}; the 3D variant is continuous over the whole sphere. */
final class GalaxyNoise {
    private GalaxyNoise() {}

    private static float hash3(int x, int y, int z) {
        return SkyMath.toUnit(SkyMath.cellHash(x, y, z));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float value3(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        float fx = (float) (x - ix);
        float fy = (float) (y - iy);
        float fz = (float) (z - iz);
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float x00 = lerp(hash3(ix, iy, iz), hash3(ix + 1, iy, iz), fx);
        float x10 = lerp(hash3(ix, iy + 1, iz), hash3(ix + 1, iy + 1, iz), fx);
        float x01 = lerp(hash3(ix, iy, iz + 1), hash3(ix + 1, iy, iz + 1), fx);
        float x11 = lerp(hash3(ix, iy + 1, iz + 1), hash3(ix + 1, iy + 1, iz + 1), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    /** Noise along the galactic band: longitude is mapped onto a circle so the pattern wraps without a seam. */
    static float band(double lon, double lat, double lonScale, double latScale) {
        return value3(Math.cos(lon) * lonScale, Math.sin(lon) * lonScale, lat * latScale);
    }
}
