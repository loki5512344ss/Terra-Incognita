package dev.sborka.incognita.weather.sky.render;

import net.minecraft.client.Camera;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/** Client entry of the sky renderer; the mixin on LevelRenderer#renderSky calls into {@link #render}. */
public final class SkyClient {
    private static SkyRenderer renderer;
    private static final float[] HORIZON = new float[3];

    private SkyClient() {}

    /** Registers the resource reload hook on the mod bus and the fog colour hook on the game bus. Client only. */
    public static void register(IEventBus modBus) {
        modBus.addListener(SkyClient::onRegisterReloadListeners);
        // High priority: the fog bank greying in FogClient then works on top of the sky colour.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SkyClient::onFogColor);
        SkyRenderer.prebake();
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> {
            if (renderer != null) {
                renderer.invalidate();
            }
        });
    }

    /** The fog takes the colour of the sky at the horizon, so distant terrain melts into the sky haze. */
    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        if (renderer != null && renderer.horizonColor(camera, HORIZON)) {
            event.setRed(HORIZON[0]);
            event.setGreen(HORIZON[1]);
            event.setBlue(HORIZON[2]);
        }
    }

    /**
     * Draws our sky if possible.
     *
     * @return true when vanilla must not draw its sky
     */
    public static boolean render(Matrix4f viewMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera,
                                 boolean foggy, Runnable skyFogSetup) {
        RenderSystem.assertOnRenderThread();
        if (renderer == null) {
            renderer = new SkyRenderer();
        }
        return renderer.render(viewMatrix, projectionMatrix, partialTick, camera, foggy, skyFogSetup);
    }
}
