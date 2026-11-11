package dev.sborka.incognita.coordinates.guard;


import dev.sborka.incognita.Incognita;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lodestones stay placeable but can no longer bind compasses (a binding would reveal coordinates). */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class LodestoneHooks {
    private LodestoneHooks() {}

    static boolean blocksBinding(Block block, Item held) {
        return block == Blocks.LODESTONE && held == Items.COMPASS;
    }

    /** Cancelling also forces useBlock/useItem to FALSE, so CompassItem.useOn never runs. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (blocksBinding(block, event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
