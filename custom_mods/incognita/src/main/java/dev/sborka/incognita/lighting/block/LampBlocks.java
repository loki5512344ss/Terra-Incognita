package dev.sborka.incognita.lighting.block;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaTab;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Five test lamps. Their colors live in {@code assets/incognita/lighting/emitters.json}, not here. */
public final class LampBlocks {
    public static final List<String> COLORS = List.of("red", "green", "blue", "purple", "orange");

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Incognita.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Incognita.MOD_ID);

    private LampBlocks() {}

    public static void register(IEventBus modBus) {
        for (String color : COLORS) {
            DeferredBlock<Block> block = BLOCKS.registerSimpleBlock("lamp_" + color,
                BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).lightLevel(s -> 14));
            IncognitaTab.add(ITEMS.registerSimpleBlockItem(block));
        }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
