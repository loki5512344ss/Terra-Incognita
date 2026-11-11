package dev.sborka.incognita.coordinates;

import dev.sborka.incognita.coordinates.reveal.RevealSyncPayload;
import dev.sborka.incognita.core.IncognitaModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Hides real coordinates: strips coordinate lines from F3, rewrites tooltips, disables lodestone compass binding, blocks F3+C and F3+I, and feeds spoofed values
 * wherever the game or other mods display XYZ. See docs/ for the design.
 */
public final class CoordinatesModule implements IncognitaModule {
    @Override
    public String id() {
        return "coordinates";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CoordinatesConfig.SPEC, "incognita-coordinates.toml");
        modBus.addListener(CoordinatesModule::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
            RevealSyncPayload.TYPE, RevealSyncPayload.STREAM_CODEC, RevealSyncPayload::handleOnClient);
    }
}
