package dev.sborka.incognita.weather.fog;

import dev.sborka.incognita.Incognita;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client side of the fog: shortens the terrain fog distance and tints the fog color. */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class FogClient {
    /** Fog fades out when the camera is under a roof or underground; smoothed so it never pops. */
    private static double exposure = 0.0;
    private static double lastExposure = 0.0;

    /** Dev tests force a density here (0..1); a negative value means "use the real fog model". */
    private static volatile double testOverride = -1.0;

    /** Far distance of our dense fog as applied to the terrain in the last frame, or a negative value without fog. */
    private static volatile float appliedFar = -1.0f;

    private FogClient() {}

    /**
     * Far distance of the dense fog that was applied to the terrain in the current frame (the near distance is 0), or
     * a negative value when the vanilla fog is in charge. Lets other renderers, like the clouds, fade out with it.
     */
    public static float appliedFarDistance() {
        return appliedFar;
    }

    /** Used only by the client test driver to take reproducible screenshots. */
    public static void setTestOverride(double density) {
        testOverride = density;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        lastExposure = exposure;
        if (level == null || Minecraft.getInstance().player == null) {
            exposure = 0.0;
            return;
        }
        BlockPos eye = BlockPos.containing(Minecraft.getInstance().player.getEyePosition());
        double target = hasOpenSky(level) && level.canSeeSky(eye) ? 1.0 : 0.0;
        exposure += (target - exposure) * 0.05;
    }

    /** Fog banks belong to overworld-like dimensions: the Nether and the End have their own atmosphere. */
    private static boolean hasOpenSky(ClientLevel level) {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
    }

    /** Current fog density 0..1 as the fog itself uses it (test override, exposure and config included). */
    public static double density(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FogConfig.enabled() || !hasOpenSky(level)) {
            return 0.0;
        }
        if (testOverride >= 0.0) {
            return testOverride;
        }
        double raw = FogModel.intensity(level.getGameTime(), level.getDayTime(), level.getRainLevel(partialTick),
            FogConfig.maxIntensity(), FogConfig.frequency(), FogConfig.morningFog());
        double shown = lastExposure + (exposure - lastExposure) * partialTick;
        return raw * shown;
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double density = density((float) event.getPartialTick());
        if (density < 0.001) {
            appliedFar = -1.0f;
            return;
        }
        float far = FogModel.farDistance(event.getFarPlaneDistance(), density, FogConfig.minDistance());
        event.setNearPlaneDistance(0.0f);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
        appliedFar = far;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        double density = density((float) event.getPartialTick());
        if (density < 0.001) {
            return;
        }
        // Pull the color toward a cool gray that keeps the current brightness, so fog still darkens at dusk.
        float luminance = 0.3f * event.getRed() + 0.59f * event.getGreen() + 0.11f * event.getBlue();
        float mix = (float) Math.min(density, 1.0);
        event.setRed(lerp(event.getRed(), luminance * 0.92f + 0.04f, mix));
        event.setGreen(lerp(event.getGreen(), luminance * 0.96f + 0.05f, mix));
        event.setBlue(lerp(event.getBlue(), luminance * 1.00f + 0.07f, mix));
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
