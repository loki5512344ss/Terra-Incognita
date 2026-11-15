package dev.sborka.incognita.compass;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaModule;
import dev.sborka.incognita.core.IncognitaTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Two compasses. The vanilla one points to the holder's own spawn point (bed, or the spot they last appeared at);
 * the origin compass points to the world center, block 0 0.
 */
public final class CompassModule implements IncognitaModule {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Incognita.MOD_ID);

    public static final DeferredItem<Item> ORIGIN_COMPASS =
        ITEMS.registerItem("origin_compass", props -> new OriginCompassItem(props.stacksTo(1)));

    @Override
    public String id() {
        return "compass";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        IncognitaTab.add(ORIGIN_COMPASS);
        modBus.addListener(CompassModule::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(HomePayload.TYPE, HomePayload.STREAM_CODEC, HomePayload::handleOnClient);
    }
}
