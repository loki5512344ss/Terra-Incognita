package dev.sborka.incognita.presence;

import dev.sborka.incognita.core.IncognitaModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/** Discord Rich Presence: shows dimension, weather and the radio frequency, never the coordinates. */
public final class PresenceModule implements IncognitaModule {
    @Override
    public String id() {
        return "presence";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerConfig(ModConfig.Type.CLIENT, PresenceConfig.SPEC, "incognita-presence.toml");
        }
    }
}
