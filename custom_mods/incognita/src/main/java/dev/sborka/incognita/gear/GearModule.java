package dev.sborka.incognita.gear;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaModule;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Closes the shortcuts around the pack's harder gear recipes: villager trades and chest loot. */
public final class GearModule implements IncognitaModule {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Incognita.MOD_ID);

    static {
        LOOT_MODIFIERS.register("gated_gear", () -> GearLootModifier.CODEC);
    }

    @Override
    public String id() {
        return "gear";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        LOOT_MODIFIERS.register(modBus);
        NeoForge.EVENT_BUS.register(GearTrades.class);
    }
}
