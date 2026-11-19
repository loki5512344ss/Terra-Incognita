package dev.sborka.incognita.weather.clouds.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix4f;

/**
 * Client entry of the clouds. The mixin on {@code LevelRenderer#renderClouds} always cancels the vanilla clouds and
 * calls {@link #render} instead; there is no vanilla fallback.
 */
public final class CloudsClient {
    private static CloudRenderer renderer;

    private CloudsClient() {}

    /** Registers the resource reload hook on the mod bus. Client only. */
    public static void register(IEventBus modBus) {
        modBus.addListener(CloudsClient::onRegisterReloadListeners);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> {
            if (renderer != null) {
                renderer.invalidate();
            }
        });
    }

    /**
     * Draws the clouds in place of the vanilla ones. A dimension with its own cloud renderer
     * ({@code DimensionSpecialEffects#renderClouds}) keeps it.
     */
    public static void render(PoseStack poseStack, Matrix4f viewMatrix, Matrix4f projectionMatrix, float partialTick,
                              double camX, double camY, double camZ) {
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.effects().renderClouds(level, mc.levelRenderer.getTicks(), partialTick, poseStack,
            camX, camY, camZ, viewMatrix, projectionMatrix)) {
            return;
        }
        if (renderer == null) {
            renderer = new CloudRenderer();
        }
        renderer.render(viewMatrix, projectionMatrix, partialTick, camX, camY, camZ);
    }

    /** Smoothed GPU time of the cloud pass in milliseconds, negative before the first measurement. */
    public static double gpuMillis() {
        return renderer == null ? -1.0 : renderer.gpuMillis();
    }

    /** Per band wind offsets, headings and puff counts, for the dev test log. */
    public static String debugLine() {
        return renderer == null ? "no renderer" : renderer.debugLine();
    }

    /** Number of puffs that passed the frustum culling in the last frame. */
    public static int visiblePuffs() {
        return renderer == null ? 0 : renderer.visiblePuffs();
    }
}
