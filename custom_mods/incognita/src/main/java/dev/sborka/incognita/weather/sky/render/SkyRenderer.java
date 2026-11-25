package dev.sborka.incognita.weather.sky.render;

import dev.sborka.incognita.weather.fog.control.FogOverride;
import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.weather.WeatherState;
import dev.sborka.incognita.weather.clouds.shade.CloudLight;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.fog.FogConfig;
import dev.sborka.incognita.weather.sky.atmosphere.SkyLut;
import dev.sborka.incognita.weather.sky.atmosphere.SkyTone;
import dev.sborka.incognita.weather.sky.config.SkyConfig;
import dev.sborka.incognita.weather.sky.dome.SkyColors;
import dev.sborka.incognita.weather.sky.math.SkyMath;
import dev.sborka.incognita.weather.sky.render.body.CelestialBodies;
import dev.sborka.incognita.weather.sky.render.dome.SkyDome;
import dev.sborka.incognita.weather.sky.render.stars.MilkyWay;
import dev.sborka.incognita.weather.sky.render.stars.StarField;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

/**
 * Draws the sky with plain meshes and the vanilla position shaders: a coloured dome from the baked physical
 * atmosphere, a Milky Way ribbon, star sprites, the sun and the moon as textured quads. All colour work happens on
 * the CPU, so the sky needs no custom shader program.
 */
final class SkyRenderer implements AutoCloseable {
    /** The clear sky table is baked once per game session on a background thread. */
    private static final CompletableFuture<SkyLut> LUT = CompletableFuture.supplyAsync(SkyLut::bake);

    private final Minecraft client = Minecraft.getInstance();
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f celestialRotation = new Matrix4f();
    private final Matrix4f celestial = new Matrix4f();
    private final SkyDome dome = new SkyDome();
    private final MilkyWay milkyWay = new MilkyWay();
    private final StarField stars = new StarField();
    private final CelestialBodies bodies = new CelestialBodies();
    private final double[] lin = new double[3];
    private SkyColors.State lastState;
    private boolean failed;

    /** Starts baking the clear sky table early, so it is ready before the first world is shown. */
    static void prebake() {
        LUT.isDone();
    }

    /** Called when the resource packs reload: the painted textures are dropped and rebuilt on the next frame. */
    void invalidate() {
        close();
        failed = false;
    }

    boolean render(Matrix4f viewMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean foggy,
                   Runnable skyFogSetup) {
        ClientLevel level = client.level;
        if (!canDraw(level, camera) || foggy) {
            lastState = null;
            return false;
        }
        if (!LUT.isDone()) {
            return false;
        }
        SkyLut lut = LUT.join();
        try {
            skyFogSetup.run();
            draw(level, lut, viewMatrix, projectionMatrix, partialTick);
            return true;
        } catch (RuntimeException | LinkageError e) {
            Incognita.LOGGER.error("Sky renderer failed, switching to the vanilla sky", e);
            failed = true;
            lastState = null;
            return false;
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private boolean canDraw(ClientLevel level, Camera camera) {
        if (failed || level == null || !SkyConfig.enabled() || !isOurs(level)) {
            return false;
        }
        // Fabulous graphics routes the sky through its own targets; a shader pack replaces the sky entirely.
        if (Minecraft.useShaderTransparency() || SkyShaderPackCheck.isShaderPackActive()) {
            return false;
        }
        // Vanilla draws no sky in these cases either, so it is left to run.
        return camera.getFluidInCamera() == FogType.NONE && !blocksSky(camera);
    }

    /**
     * Colour of the sky at the horizon in the direction the camera looks, before fog: the fog colour, so terrain fog
     * and the sky haze are one colour. False when the custom sky is not active.
     */
    boolean horizonColor(Camera camera, float[] out) {
        SkyColors.State state = lastState;
        if (state == null || !canDraw(client.level, camera)) {
            return false;
        }
        org.joml.Vector3f look = camera.getLookVector();
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        double x = flat > 1e-4 ? look.x / flat : 1.0;
        double z = flat > 1e-4 ? look.z / flat : 0.0;
        // Averaged over a 120 degree arc a little above the horizon, so the sun's aureole does not flood the fog.
        double el = 0.05;
        double c = Math.cos(el);
        float r = 0;
        float g = 0;
        float b = 0;
        for (int i = -2; i <= 2; i++) {
            double a = Math.toRadians(30.0 * i);
            double dx = x * Math.cos(a) - z * Math.sin(a);
            double dz = x * Math.sin(a) + z * Math.cos(a);
            SkyColors.skyNoFog(state, dx * c, Math.sin(el), dz * c, lin, out);
            r += out[0] * 0.2f;
            g += out[1] * 0.2f;
            b += out[2] * 0.2f;
        }
        out[0] = r;
        out[1] = g;
        out[2] = b;
        return true;
    }

    /** Only the plain overworld style sky is replaced; the End sky and modded dimension effects stay vanilla. */
    private static boolean isOurs(ClientLevel level) {
        DimensionSpecialEffects effects = level.effects();
        return effects.skyType() == DimensionSpecialEffects.SkyType.NORMAL
            && effects.getClass() == DimensionSpecialEffects.OverworldEffects.class;
    }

    private static boolean blocksSky(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    private void draw(ClientLevel level, SkyLut lut, Matrix4f view, Matrix4f projection, float partialTick) {
        // Rotation only: the sky is infinitely far away.
        rotation.set(view).m30(0).m31(0).m32(0);
        float timeOfDay = level.getTimeOfDay(partialTick);
        float[] sun = SkyMath.sunDirection(timeOfDay);
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        double cloudiness = WeatherState.cloudiness(level.getGameTime(), level.getDayTime(), rain, thunder,
            FogOverride.client().frequency(FogConfig.frequency()), FogConfig.morningFog());
        // The same greyness as the clouds, so a stormy sky and its clouds agree.
        float overcast = CloudLight.compute(sun[1], (float) cloudiness, rain, thunder, 0.5f).greyness();
        float fog = (float) SkyMath.clamp01(FogClient.density(partialTick));
        float seconds = (client.levelRenderer.getTicks() + partialTick) / 20.0f;
        int phase = level.getMoonPhase();
        double moonLight = SkyMath.moonIllumination(phase);
        double exposure = SkyTone.exposure(lut, sun[1], -sun[1], moonLight);
        float night = (float) SkyMath.nightFactor(sun[1]);
        SkyColors.State state = new SkyColors.State(lut, sun, moonLight, exposure, night, overcast, fog,
            (float) SkyConfig.hazeStrength(), flash(level, partialTick), RenderSystem.getShaderFogColor().clone());
        lastState = state;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        dome.update(state);
        dome.draw(rotation);

        // The celestial sphere turns once a day around the axis the vanilla sun uses.
        celestialRotation.identity().rotateY((float) Math.toRadians(-90.0)).rotateX(timeOfDay * (float) (Math.PI * 2.0));
        celestial.set(rotation).mul(celestialRotation);
        CelestialBodies.additive();
        float clear = (float) ((1.0 - overcast * 0.95) * (1.0 - SkyMath.smoothstep(0.2, 0.75, fog)))
            * (1.0f - state.flash());
        milkyWay.draw(celestialRotation, rotation, night * clear);
        stars.draw(celestial, celestialRotation, projection, lut, dome, clear, seconds, SkyConfig.starDensity());
        bodies.draw(state, celestialRotation, celestial, phase);
    }

    /** Lightning flash strength in 0..1, shaped like the vanilla sky flash. */
    private static float flash(ClientLevel level, float partialTick) {
        int time = level.getSkyFlashTime();
        if (time <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, time - partialTick) * 0.45f;
    }

    @Override
    public void close() {
        milkyWay.close();
        stars.close();
        bodies.close();
    }
}
