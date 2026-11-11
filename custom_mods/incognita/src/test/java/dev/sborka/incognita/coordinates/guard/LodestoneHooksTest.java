package dev.sborka.incognita.coordinates.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class LodestoneHooksTest {
    @Test
    void compassOnLodestoneIsBlocked() {
        assertTrue(LodestoneHooks.blocksBinding(Blocks.LODESTONE, Items.COMPASS));
    }

    @Test
    void otherCombinationsAreAllowed() {
        assertFalse(LodestoneHooks.blocksBinding(Blocks.STONE, Items.COMPASS));
        assertFalse(LodestoneHooks.blocksBinding(Blocks.LODESTONE, Items.STICK));
        assertFalse(LodestoneHooks.blocksBinding(Blocks.LODESTONE, Items.RECOVERY_COMPASS));
    }
}
