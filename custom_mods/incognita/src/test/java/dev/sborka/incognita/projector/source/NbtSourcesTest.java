package dev.sborka.incognita.projector.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NbtSourcesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag paletteEntry(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", name);
        return t;
    }

    private static CompoundTag block(int x, int y, int z, int state) {
        CompoundTag t = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        t.put("pos", pos);
        t.putInt("state", state);
        return t;
    }

    @Test
    void readsBlocksAndSkipsAirAndStructureVoid() {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:stone"));
        palette.add(paletteEntry("minecraft:air"));
        palette.add(paletteEntry("minecraft:structure_void"));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(block(0, 0, 0, 0));
        blocks.add(block(1, 0, 0, 1));
        blocks.add(block(2, 0, 0, 2));
        blocks.add(block(3, 1, 2, 0));
        root.put("blocks", blocks);

        List<StructureBlockInfo> out = NbtSources.parse(root);
        assertEquals(2, out.size());
        assertEquals(new BlockPos(0, 0, 0), out.get(0).pos());
        assertEquals(new BlockPos(3, 1, 2), out.get(1).pos());
        assertTrue(out.get(0).state().is(Blocks.STONE));
    }

    @Test
    void ignoresOutOfRangePaletteIndexes() {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(paletteEntry("minecraft:stone"));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(block(0, 0, 0, 5));
        blocks.add(block(0, 0, 1, -1));
        root.put("blocks", blocks);
        assertTrue(NbtSources.parse(root).isEmpty());
    }

    @Test
    void emptyFileGivesNoBlocks() {
        assertTrue(NbtSources.parse(new CompoundTag()).isEmpty());
    }
}
