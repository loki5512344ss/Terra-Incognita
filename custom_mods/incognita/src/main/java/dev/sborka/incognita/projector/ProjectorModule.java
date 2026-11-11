package dev.sborka.incognita.projector;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaModule;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Multiblock projector: a translucent ghost of Immersive Engineering multiblocks, NBT structures and mod-provided layouts. */
public final class ProjectorModule implements IncognitaModule {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Incognita.MOD_ID);

    public static final DeferredItem<Item> PROJECTOR =
        ITEMS.registerItem("projector", props -> new ProjectorItem(props.stacksTo(1)));

    @Override
    public String id() {
        return "projector";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(ProjectorModule::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PROJECTOR.get());
        }
    }
}
