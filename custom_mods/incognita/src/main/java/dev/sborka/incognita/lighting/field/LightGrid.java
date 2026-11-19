package dev.sborka.incognita.lighting.field;

/** Storage and world access needed by {@link FloodFill}. Coordinates are absolute block coordinates. */
public interface LightGrid {
    /** Stored light of a cell, 0 when none. */
    int get(int x, int y, int z);

    /** Writes a cell. Implementations track which cells really changed. */
    void set(int x, int y, int z, int color);

    /** Filter of a cell: -1 blocks light, {@link LightColor#WHITE} is clear, other colors tint what passes. */
    int filter(int x, int y, int z);
}
