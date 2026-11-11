package dev.sborka.incognita.radio;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaModule;
import dev.sborka.incognita.radio.link.RadioState;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Radio item, local chat and (with Simple Voice Chat) frequency voice. */
public final class RadioModule implements IncognitaModule {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Incognita.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Incognita.MOD_ID);

    public static final Supplier<DataComponentType<RadioState>> STATE = register();
    public static final DeferredItem<Item> RADIO =
        ITEMS.registerItem("radio", props -> new RadioItem(props.stacksTo(1)));

    private static DeferredHolder<DataComponentType<?>, DataComponentType<RadioState>> register() {
        return COMPONENTS.registerComponentType("radio_state",
            b -> b.persistent(RadioState.CODEC).networkSynchronized(RadioState.STREAM_CODEC));
    }

    @Override
    public String id() {
        return "radio";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(RadioModule::onCreativeTab);
        container.registerConfig(ModConfig.Type.COMMON, RadioConfig.SPEC, "incognita-radio.toml");
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RADIO.get());
        }
    }
}
