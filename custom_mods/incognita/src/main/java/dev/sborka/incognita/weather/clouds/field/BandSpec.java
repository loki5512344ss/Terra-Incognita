package dev.sborka.incognita.weather.clouds.field;

import java.util.List;

/**
 * One altitude band of the cloud field: which cloud type grows there, at what height, how big and how dense.
 * Heights are relative to the cloud base (dimension cloud height plus the configured offset).
 *
 * @param index        band index, also its position in {@link #BANDS}
 * @param style        cloud type and therefore the heap layout
 * @param base         base height above the cloud base, in blocks
 * @param baseJitter   random spread of the heap bases, in blocks
 * @param thicknessMin height of a weak heap
 * @param thicknessMax height of the strongest heap
 * @param widthMin     width of a weak heap
 * @param widthMax     width of the strongest heap
 * @param spacing      distance between heap cells; one culling chunk is 2 x 2 cells
 * @param noiseScale   wavelength of the coverage noise in blocks
 * @param coverBias    added to the cover threshold: positive values make the band sparser
 * @param streets      stretch of the coverage noise along the wind (cloud streets), 1 = none
 */
public record BandSpec(int index, Style style, float base, float baseJitter, float thicknessMin, float thicknessMax,
                       float widthMin, float widthMax, float spacing, float noiseScale, float coverBias,
                       float streets) {
    /** Cloud type of a band. */
    public enum Style {
        /** Fair weather cumulus heaps; turn into a stratocumulus deck near overcast. */
        CUMULUS,
        /** Patches of small cloudlets. */
        ALTOCUMULUS,
        /** Thin streaks and tiny puffs stretched along the wind. */
        CIRRUS
    }

    /** The bands from low to high. */
    public static final List<BandSpec> BANDS = List.of(
        new BandSpec(0, Style.CUMULUS, 0.0f, 7.0f, 24.0f, 78.0f, 54.0f, 150.0f, 52.0f, 430.0f, 0.0f, 1.7f),
        new BandSpec(1, Style.ALTOCUMULUS, 150.0f, 10.0f, 10.0f, 22.0f, 46.0f, 90.0f, 38.0f, 300.0f, 0.10f, 1.3f),
        new BandSpec(2, Style.CIRRUS, 310.0f, 16.0f, 5.0f, 10.0f, 44.0f, 120.0f, 46.0f, 540.0f, 0.16f, 2.4f));

    public static final int COUNT = 3;

    /** Edge length of one culling chunk in blocks. */
    public float chunkSize() {
        return spacing * 2.0f;
    }

    /** Height of the highest possible cloud top above the cloud base. */
    public float ceiling() {
        return base + baseJitter + thicknessMax * 1.3f;
    }
}
