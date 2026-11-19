package dev.sborka.incognita.lighting;

import dev.sborka.incognita.core.IncognitaModule;
import dev.sborka.incognita.lighting.block.LampBlocks;
import dev.sborka.incognita.lighting.config.LightingConfig;
import dev.sborka.incognita.lighting.client.LightingReload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * Client side colored block light with a light range beyond vanilla's 15. A client RGB light field is filled from
 * emitter blocks (JSON) and from moving entities; Sodium's chunk mesher tints vertices from it (see the
 * {@code mixin} and {@code sodium} packages). The test lamps below are the only registered content.
 */
public final class LightingModule implements IncognitaModule {
    @Override
    public String id() {
        return "lighting";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LightingConfig.SPEC, "incognita-lighting.toml");
        LampBlocks.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(LightingReload::register);
        }
    }
}
