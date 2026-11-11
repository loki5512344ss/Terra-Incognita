package dev.sborka.incognita.coordinates.client;

import java.util.List;
import java.util.regex.Pattern;

/** Decides which F3 debug lines expose the player's position (vanilla 1.21.1 prefixes). */
public final class DebugLineFilter {
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final List<String> PREFIXES = List.of(
        "XYZ:", "Block:", "Chunk:", "Chunk-relative:", "Section:", "Targeted Block:", "Targeted Fluid:",
        // Flywheel F3 render origin (a real block position near the camera)
        "Origin:",
        // Vanilla heightmap lines: "CH S: 63 M: 63" and "SH S: 63 O: 63 M: 63 F: 63", absolute surface Y at the player's column
        "CH ", "SH ");

    private DebugLineFilter() {}

    public static boolean isCoordinateLine(String line) {
        if (line == null) {
            return false;
        }
        String plain = FORMATTING.matcher(line).replaceAll("").trim();
        for (String prefix : PREFIXES) {
            if (plain.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
