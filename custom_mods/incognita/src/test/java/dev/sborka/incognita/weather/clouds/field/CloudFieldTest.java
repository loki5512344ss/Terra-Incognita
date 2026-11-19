package dev.sborka.incognita.weather.clouds.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sborka.incognita.weather.clouds.field.mesh.BandGeometry;
import dev.sborka.incognita.weather.clouds.field.mesh.ChunkBounds;
import dev.sborka.incognita.weather.clouds.field.mesh.PuffWriter;
import dev.sborka.incognita.weather.clouds.field.noise.CoverNoise;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CloudFieldTest {
    private static final BandSpec CUMULUS = BandSpec.BANDS.get(0);

    private static BandGeometry generate(BandSpec spec, int anchorX, int anchorZ, float cover) {
        return CloudField.generate(new CloudField.Request(spec, anchorX, anchorZ, 600,
            CoverModel.threshold(cover, spec.coverBias()), 252.0f, 3.5f));
    }

    /** Share of heap cells whose noise exceeds the threshold, over a large area. */
    private static double heapShare(BandSpec spec, float cover) {
        float threshold = CoverModel.threshold(cover, spec.coverBias());
        int hits = 0;
        int total = 0;
        for (int i = -150; i < 150; i++) {
            for (int j = -150; j < 150; j++) {
                float n = CloudField.coverage(spec, i * spec.spacing(), j * spec.spacing(), 3.5f);
                hits += n > threshold ? 1 : 0;
                total++;
            }
        }
        return hits / (double) total;
    }

    @Test
    void coverControlsHowManyHeapsExist() {
        double scattered = heapShare(CUMULUS, 0.35f);
        double fair = heapShare(CUMULUS, 0.5f);
        double heavy = heapShare(CUMULUS, 0.85f);
        assertTrue(scattered > 0.08 && scattered < 0.35, "scattered " + scattered);
        assertTrue(fair > 0.28 && fair < 0.60, "fair " + fair);
        assertTrue(heavy > 0.75, "heavy " + heavy);
        assertTrue(heapShare(BandSpec.BANDS.get(2), 0.5f) < fair, "the cirrus band is sparser");
    }

    @Test
    void sameBandSpaceHeapLooksTheSameFromAnyWindow() {
        BandGeometry a = generate(CUMULUS, 0, 0, 0.55f);
        BandGeometry b = generate(CUMULUS, 3, -2, 0.55f);
        Set<String> inA = puffKeys(a);
        Set<String> inB = puffKeys(b);
        int common = 0;
        for (String key : inB) {
            common += inA.contains(key) ? 1 : 0;
        }
        // The windows overlap by most of their area, so most puffs are shared, at identical band-space positions.
        assertTrue(common > inB.size() / 2, "common " + common + " of " + inB.size());
    }

    private static Set<String> puffKeys(BandGeometry g) {
        Set<String> keys = new HashSet<>();
        float[] d = g.data();
        for (int p = 0; p < g.puffs(); p++) {
            int i = p * PuffWriter.FLOATS_PER_PUFF;
            double x = d[i] + g.originX();
            double z = d[i + 2] + g.originZ();
            keys.add(Math.round(x * 10) + ":" + Math.round(d[i + 1] * 10) + ":" + Math.round(z * 10));
        }
        return keys;
    }

    @Test
    void heapsAreWideNotTowers() {
        BandGeometry g = generate(CUMULUS, 0, 0, 0.7f);
        float[] d = g.data();
        assertTrue(g.puffs() > 200, "puffs " + g.puffs());
        for (int p = 0; p < g.puffs(); p++) {
            int i = p * PuffWriter.FLOATS_PER_PUFF;
            float rx = d[i + 4];
            float ry = d[i + 5];
            float rz = d[i + 6];
            // Heaps are built from small and medium cubes, never from tall columns.
            assertTrue(ry <= 24.0f && Math.max(rx, rz) <= 40.0f, "oversized cube at " + p);
            assertTrue(ry <= Math.max(rx, rz) * 2.6f, "column at puff " + p);
            // Every puff stays inside its heap's height band (a little below the base for the underside lumps; far chunks use bigger cubes).
            float top = d[i + 9] + d[i + 11];
            assertTrue(d[i + 1] - ry > d[i + 9] - 20.0f, "puff below the heap base");
            assertTrue(d[i + 1] + ry < top + 14.0f, "puff above the heap top");
            assertTrue(d[i + 11] <= 0.6f * 150.0f * 1.15f + 1.0f, "heap too tall");
        }
    }

    @Test
    void chunkBoundsContainTheirPuffs() {
        BandGeometry g = generate(BandSpec.BANDS.get(1), 1, 1, 0.6f);
        float[] d = g.data();
        int seen = 0;
        for (ChunkBounds c : g.chunks()) {
            assertEquals(seen, c.start(), "chunks are consecutive");
            for (int p = c.start(); p < c.start() + c.count(); p++) {
                int i = p * PuffWriter.FLOATS_PER_PUFF;
                assertTrue(d[i] >= c.minX() && d[i] <= c.maxX());
                assertTrue(d[i + 1] >= c.minY() && d[i + 1] <= c.maxY());
                assertTrue(d[i + 2] >= c.minZ() && d[i + 2] <= c.maxZ());
            }
            seen += c.count();
        }
        assertEquals(g.puffs(), seen);
    }

    @Test
    void overcastAddsAWatertightDeck() {
        BandGeometry fair = generate(CUMULUS, 0, 0, 0.5f);
        BandGeometry rain = generate(CUMULUS, 0, 0, 1.0f);
        assertTrue(rain.puffs() > fair.puffs() * 1.3, fair.puffs() + " vs " + rain.puffs());
    }

    @Test
    void lowBandStaysUnderTheCubeBudgetEvenOvercast() {
        // Default draw distance 1024 gives a 1312 block window for the low band.
        for (float cover : new float[] {0.5f, 0.85f, 1.0f}) {
            BandGeometry g = CloudField.generate(new CloudField.Request(CUMULUS, 0, 0, 1312,
                CoverModel.threshold(cover, CUMULUS.coverBias()), 252.0f, 3.5f));
            System.out.println("BUDGET cover " + cover + " cubes " + g.puffs());
            assertTrue(g.puffs() < 100_000, "cover " + cover + ": " + g.puffs() + " cubes");
        }
    }

    @Test
    void farChunksUseBiggerCubesInSteps() {
        assertEquals(1.0f, CloudField.lodScale(0.0, 1312));
        assertEquals(1.0f, CloudField.lodScale(0.40 * 1312, 1312));
        assertEquals(1.8f, CloudField.lodScale(1312, 1312), 1.0e-4f);
        float last = 1.0f;
        for (double d = 0.0; d <= 1312.0; d += 8.0) {
            float scale = CloudField.lodScale(d, 1312);
            assertTrue(scale >= last, "monotonic at " + d);
            last = scale;
        }
    }

    @Test
    void noiseIsDeterministicAndBounded() {
        for (int k = 0; k < 2000; k++) {
            float v = CoverNoise.fbm(k * 0.37, k * -0.21, 7);
            assertTrue(v >= 0.0f && v <= 1.0f);
            assertEquals(v, CoverNoise.fbm(k * 0.37, k * -0.21, 7));
        }
        assertTrue(CoverNoise.unit(3, 4, 5) != CoverNoise.unit(4, 3, 5));
    }
}
