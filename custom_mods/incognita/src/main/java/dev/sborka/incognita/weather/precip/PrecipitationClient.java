package dev.sborka.incognita.weather.precip;

import dev.sborka.incognita.weather.clouds.render.ShaderPackCheck;
import dev.sborka.incognita.weather.precip.render.PrecipitationRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry of the precipitation renderer. It draws at {@code AFTER_WEATHER}, and the mixin on
 * {@code LevelRenderer#renderSnowAndRain} skips the vanilla quads whenever {@link #replacesVanilla()} is true.
 */
public final class PrecipitationClient {
    private static PrecipitationRenderer renderer;

    private PrecipitationClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PrecipitationClient::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(PrecipitationClient::onRenderLevel);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> {
            if (renderer != null) {
                renderer.invalidate();
            }
        });
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !replacesVanilla()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        if (renderer == null) {
            renderer = new PrecipitationRenderer();
        }
        renderer.render(event.getModelViewMatrix(), event.getProjectionMatrix(),
            event.getPartialTick().getGameTimeDeltaPartialTick(false), event.getCamera());
    }

    /** Whether the vanilla rain and snow renderer must be skipped because ours takes over this frame. */
    public static boolean replacesVanilla() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null || !PrecipConfig.enabled()) {
            return false;
        }
        if (ShaderPackCheck.isShaderPackActive()) {
            return false;
        }
        if (level.getRainLevel(1.0f) <= 0.01f) {
            return false;
        }
        if (client.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
            return false;
        }
        return renderer == null || !renderer.failed();
    }

    /** Dev test hook: forces snow (true), rain (false), or the real biome precipitation (null). */
    public static void setTestSnow(Boolean snow) {
        PrecipConfig.setTestSnow(snow);
    }
}
