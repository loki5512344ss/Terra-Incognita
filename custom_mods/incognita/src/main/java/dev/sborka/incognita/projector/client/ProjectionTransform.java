package dev.sborka.incognita.projector.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Turns template blocks into positions relative to the anchor: optionally mirrored (east-west), then rotated
 * clockwise in 90 degree steps. The footprint is centred on the anchor in x/z and starts at the anchor's y.
 */
public final class ProjectionTransform {
    public record Placed(BlockPos offset, BlockState state) {}

    public record Result(List<Placed> blocks, int width, int depth, int height) {}

    private ProjectionTransform() {}

    public static Result transform(List<StructureBlockInfo> blocks, int quarterTurns, boolean mirror) {
        if (blocks.isEmpty()) {
            return new Result(List.of(), 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureBlockInfo b : blocks) {
            BlockPos p = b.pos();
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        int sx0 = maxX - minX + 1, sz0 = maxZ - minZ + 1, height = maxY - minY + 1;
        int turns = Math.floorMod(quarterTurns, 4);
        boolean swap = turns % 2 == 1;
        int width = swap ? sz0 : sx0, depth = swap ? sx0 : sz0;

        List<Placed> out = new ArrayList<>(blocks.size());
        for (StructureBlockInfo b : blocks) {
            int x = b.pos().getX() - minX, z = b.pos().getZ() - minZ, y = b.pos().getY() - minY;
            BlockState state = b.state();
            int sx = sx0, sz = sz0;
            if (mirror) {
                x = sx - 1 - x;
                state = state.mirror(Mirror.FRONT_BACK);
            }
            for (int i = 0; i < turns; i++) {
                int nx = sz - 1 - z, nz = x;
                x = nx;
                z = nz;
                state = state.rotate(Rotation.CLOCKWISE_90);
                int t = sx;
                sx = sz;
                sz = t;
            }
            out.add(new Placed(new BlockPos(x - width / 2, y, z - depth / 2), state));
        }
        return new Result(out, width, depth, height);
    }
}
