package dev.sborka.incognita.weather;

import dev.sborka.incognita.weather.clouds.render.CloudsClient;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.fog.FogConfig;
import dev.sborka.incognita.weather.fog.control.FogCommand;
import dev.sborka.incognita.weather.fog.control.FogSyncPayload;
import dev.sborka.incognita.weather.precip.PrecipitationClient;
import dev.sborka.incognita.weather.sky.render.SkyClient;

import dev.sborka.incognita.core.IncognitaModule;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Atmospheric weather: dense fog banks that cut visibility and volumetric clouds. The rendering hooks live in
 * {@link FogClient} and {@link CloudsClient}. */
public final class WeatherModule implements IncognitaModule {
    @Override
    public String id() {
        return "weather";
    }

    @Override
    public void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, FogConfig.SPEC, "incognita-weather.toml");
        modBus.addListener(WeatherModule::registerPayloads);
        NeoForge.EVENT_BUS.register(FogCommand.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CloudsClient.register(modBus);
            SkyClient.register(modBus);
            PrecipitationClient.register(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: players with an older build can still join, they only miss the operators' fog commands
        event.registrar("1").optional()
            .playToClient(FogSyncPayload.TYPE, FogSyncPayload.STREAM_CODEC, FogSyncPayload::handleOnClient);
    }
}
