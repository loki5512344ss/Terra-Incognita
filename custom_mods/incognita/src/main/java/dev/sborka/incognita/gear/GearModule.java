package dev.sborka.incognita.gear;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.core.IncognitaModule;
import dev.sborka.incognita.gear.anvil.AnvilConfig;
import dev.sborka.incognita.gear.anvil.AnvilDurability;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Closes the shortcuts around the pack's harder gear recipes: villager trades, chest loot and the anvil. */
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
        container.registerConfig(ModConfig.Type.COMMON, AnvilConfig.SPEC, "incognita-gear.toml");
        NeoForge.EVENT_BUS.register(AnvilDurability.class);
    }
}
