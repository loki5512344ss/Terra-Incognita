package dev.sborka.incognita.spawn;

import dev.sborka.incognita.core.IncognitaModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/** Random spawn near the world center on first join and on respawn without a bed. */
public final class SpawnModule implements IncognitaModule {
    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SpawnConfig.SPEC, "incognita-spawn.toml");
    }
}
