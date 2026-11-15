package dev.sborka.incognita.core;

import dev.sborka.incognita.Incognita;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own creative tab. Modules add their items with {@link #add} while they register them. */
public final class IncognitaTab {
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Incognita.MOD_ID);
    private static final List<Supplier<? extends ItemLike>> ITEMS = new ArrayList<>();

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Incognita.MOD_ID))
            .icon(() -> ITEMS.isEmpty() ? ItemStack.EMPTY : new ItemStack(ITEMS.get(0).get()))
            .displayItems((params, output) -> ITEMS.forEach(item -> output.accept(item.get())))
            .build());
    }

    private IncognitaTab() {}

    /** Lists an item in the tab, in call order. The first one added is the tab icon. */
    public static void add(Supplier<? extends ItemLike> item) {
        ITEMS.add(item);
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
