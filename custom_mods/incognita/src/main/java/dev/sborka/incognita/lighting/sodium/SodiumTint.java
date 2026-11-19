package dev.sborka.incognita.lighting.sodium;

import dev.sborka.incognita.lighting.client.LightingClient;
import dev.sborka.incognita.lighting.config.LightingConfig;
import dev.sborka.incognita.lighting.field.ColorLightField;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Applies the light field to one Sodium quad while the chunk mesh is built (runs on mesher worker threads, reads only).
 * Per vertex: average the four field cells in the layer in front of the face (so smooth lighting looks smooth),
 * raise the vertex block light to the colored light if it is brighter, and tint the vertex color.
 * Only loaded when Sodium is present, because it references Sodium classes.
 */
public final class SodiumTint {
    private SodiumTint() {}

    public static void apply(MutableQuadViewImpl quad, BlockPos pos) {
        ColorLightField field = LightingClient.field();
        if (field == null || !LightingConfig.enabled() || !field.mayHaveLight(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        Direction face = quad.lightFace();
        float strength = LightingConfig.strength();
        for (int i = 0; i < 4; i++) {
            int rgb = sample(field, pos.getX() + quad.x(i), pos.getY() + quad.y(i), pos.getZ() + quad.z(i), face, pos);
            if (rgb == 0) {
                continue;
            }
            int lm = quad.lightmap(i);
            int vanillaBlock = lm & 0xFF;
            quad.color(i, VertexLight.tintArgb(quad.color(i), rgb, vanillaBlock, strength));
            quad.lightmap(i, (lm & ~0xFF) | VertexLight.blockLm(rgb, vanillaBlock));
        }
    }

    /** Field value at a vertex: mean of the 4 cells touching the vertex in the layer the face looks into. */
    static int sample(ColorLightField f, double vx, double vy, double vz, Direction face, BlockPos pos) {
        int rx = (int) Math.floor(vx + 0.5);
        int ry = (int) Math.floor(vy + 0.5);
        int rz = (int) Math.floor(vz + 0.5);
        if (face == null) {
            return f.sample(pos.getX(), pos.getY(), pos.getZ());
        }
        int s = face.getAxisDirection().getStep();
        return switch (face.getAxis()) {
            case X -> {
                int x = s > 0 ? rx : rx - 1;
                yield VertexLight.average4(f.sample(x, ry - 1, rz - 1), f.sample(x, ry, rz - 1), f.sample(x, ry - 1, rz), f.sample(x, ry, rz));
            }
            case Y -> {
                int y = s > 0 ? ry : ry - 1;
                yield VertexLight.average4(f.sample(rx - 1, y, rz - 1), f.sample(rx, y, rz - 1), f.sample(rx - 1, y, rz), f.sample(rx, y, rz));
            }
            case Z -> {
                int z = s > 0 ? rz : rz - 1;
                yield VertexLight.average4(f.sample(rx - 1, ry - 1, z), f.sample(rx, ry - 1, z), f.sample(rx - 1, ry, z), f.sample(rx, ry, z));
            }
        };
    }
}
