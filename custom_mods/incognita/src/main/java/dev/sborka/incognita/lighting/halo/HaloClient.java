package dev.sborka.incognita.lighting.halo;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.lighting.config.LightingConfig;
import dev.sborka.incognita.lighting.halo.render.HaloRenderer;
import dev.sborka.incognita.weather.clouds.render.ShaderPackCheck;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Hooks the lamp halo renderer into the level render. */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class HaloClient {
    private static HaloRenderer renderer;
    private static boolean failed;

    private HaloClient() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || failed
            || !LightingConfig.enabled() || !LightingConfig.halo()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getCamera().getFluidInCamera() != FogType.NONE
            || ShaderPackCheck.isShaderPackActive()) {
            return;
        }
        try {
            if (renderer == null) {
                renderer = new HaloRenderer();
            }
            renderer.render(event.getModelViewMatrix(), event.getCamera(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        } catch (RuntimeException | LinkageError e) {
            Incognita.LOGGER.error("Lamp halo renderer failed, disabling it", e);
            failed = true;
        }
    }
}
