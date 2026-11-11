package dev.sborka.incognita.projector.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProjectionTransformTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StructureBlockInfo stone(int x, int y, int z) {
        return new StructureBlockInfo(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), null);
    }

    private static boolean has(ProjectionTransform.Result r, int x, int y, int z) {
        return r.blocks().stream().anyMatch(p -> p.offset().equals(new BlockPos(x, y, z)));
    }

    @Test
    void emptyStructureGivesEmptyResult() {
        ProjectionTransform.Result r = ProjectionTransform.transform(List.of(), 0, false);
        assertTrue(r.blocks().isEmpty());
        assertEquals(0, r.width());
    }

    @Test
    void identityKeepsShapeAndCentresFootprint() {
        // 3 wide (x), 2 deep (z), 1 tall
        List<StructureBlockInfo> in = List.of(stone(0, 0, 0), stone(2, 0, 1));
        ProjectionTransform.Result r = ProjectionTransform.transform(in, 0, false);
        assertEquals(3, r.width());
        assertEquals(2, r.depth());
        assertTrue(has(r, -1, 0, -1));
        assertTrue(has(r, 1, 0, 0));
    }

    @Test
    void quarterTurnSwapsWidthAndDepth() {
        List<StructureBlockInfo> in = List.of(stone(0, 0, 0), stone(2, 0, 1));
        ProjectionTransform.Result r = ProjectionTransform.transform(in, 1, false);
        assertEquals(2, r.width());
        assertEquals(3, r.depth());
        // (0,0) -> (sz-1-0, 0) = (1,0); (2,1) -> (0,2); then centred by (1,1)
        assertTrue(has(r, 0, 0, -1));
        assertTrue(has(r, -1, 0, 1));
    }

    @Test
    void fourTurnsAreTheIdentity() {
        List<StructureBlockInfo> in = List.of(stone(0, 0, 0), stone(2, 1, 1), stone(1, 0, 1));
        ProjectionTransform.Result a = ProjectionTransform.transform(in, 0, false);
        ProjectionTransform.Result b = ProjectionTransform.transform(in, 4, false);
        assertEquals(a.blocks().stream().map(ProjectionTransform.Placed::offset).toList(),
            b.blocks().stream().map(ProjectionTransform.Placed::offset).toList());
    }

    @Test
    void mirrorFlipsEastWest() {
        List<StructureBlockInfo> in = List.of(stone(0, 0, 0), stone(2, 0, 0));
        ProjectionTransform.Result r = ProjectionTransform.transform(in, 0, true);
        // x: 0 -> 2, 2 -> 0, centred by width/2 = 1
        assertTrue(has(r, 1, 0, 0));
        assertTrue(has(r, -1, 0, 0));
    }

    @Test
    void structureNotStartingAtOriginIsNormalised() {
        List<StructureBlockInfo> in = List.of(stone(10, 5, 20), stone(11, 6, 20));
        ProjectionTransform.Result r = ProjectionTransform.transform(in, 0, false);
        assertEquals(2, r.width());
        assertEquals(2, r.height());
        assertTrue(has(r, -1, 0, 0));
        assertTrue(has(r, 0, 1, 0));
    }
}
