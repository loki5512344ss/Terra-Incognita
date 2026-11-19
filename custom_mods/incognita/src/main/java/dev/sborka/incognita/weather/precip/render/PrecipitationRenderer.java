package dev.sborka.incognita.weather.precip.render;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.weather.clouds.gl.Shader;
import dev.sborka.incognita.weather.clouds.render.ShaderPackCheck;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.precip.PrecipConfig;
import dev.sborka.incognita.weather.precip.PrecipitationMath;
import dev.sborka.incognita.weather.sky.math.SkyMath;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

/**
 * Instanced rain streaks, snow flakes and ground splashes in a cylinder that follows the camera. Geometry is a single quad drawn
 * {@code count} times; all motion happens in the vertex shader, so nothing is rebuilt per frame.
 */
public final class PrecipitationRenderer implements AutoCloseable {
    private static final int MAX_PARTICLES = 6144;
    private static final float RADIUS = 16.0f;
    private static final float HEIGHT = 28.0f;
    private static final int COVER_UNIT = 6;
    private static final long SEED = 0x1C0C0FFEEL;

    private final Minecraft client = Minecraft.getInstance();

    private Shader shader;
    private Shader.Uniform uView;
    private Shader.Uniform uProj;
    private Shader.Uniform uCamera;
    private Shader.Uniform uWind;
    private Shader.Uniform uTime;
    private Shader.Uniform uIntensity;
    private Shader.Uniform uMode;
    private Shader.Uniform uDrift;
    private Shader.Uniform uCover;
    private Shader.Uniform uGrid;
    private Shader.Uniform uRadius;
    private Shader.Uniform uHeight;
    private Shader.Uniform uColor;
    private Shader.Uniform uFogColor;
    private Shader.Uniform uFog;

    private int vao;
    private int quadVbo;
    private int instanceVbo;
    private final CoverGrid cover = new CoverGrid();
    private float driftX;
    private float driftZ;
    private float lastSeconds;
    private boolean needsReload = true;
    private boolean failed;

    public boolean failed() {
        return failed;
    }

    public void invalidate() {
        needsReload = true;
        failed = false;
    }

    /** Draws the precipitation for this frame. Safe to call with no rain or no level. */
    public void render(Matrix4f view, Matrix4f projection, float partialTick, Camera camera) {
        ClientLevel level = client.level;
        if (failed || level == null || !PrecipConfig.enabled()) {
            return;
        }
        float rain = level.getRainLevel(partialTick);
        if (rain <= 0.01f || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        if (ShaderPackCheck.isShaderPackActive()) {
            return;
        }
        boolean snow = isSnow(level, camera);
        float thunder = level.getThunderLevel(partialTick);
        float intensity = Mth.clamp(rain * 0.92f + thunder * 0.35f, 0.0f, 1.0f);
        int count = PrecipitationMath.particleCount(MAX_PARTICLES, PrecipConfig.density(), intensity, snow);
        if (count <= 0) {
            return;
        }
        try {
            if (!ensureResources()) {
                failed = true;
                return;
            }
            draw(level, view, projection, partialTick, camera, snow, intensity, count);
        } catch (RuntimeException | LinkageError e) {
            Incognita.LOGGER.error("Precipitation renderer failed, switching to the vanilla weather", e);
            failed = true;
        }
    }

    private boolean isSnow(ClientLevel level, Camera camera) {
        Boolean forced = PrecipConfig.testSnow();
        if (forced != null) {
            return forced;
        }
        BlockPos pos = BlockPos.containing(camera.getPosition());
        return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW;
    }

    private void draw(ClientLevel level, Matrix4f view, Matrix4f projection, float partialTick, Camera camera,
                      boolean snow, float intensity, int count) {
        float seconds = ((level.getGameTime() % 480000L) + partialTick) / 20.0f;
        float thunder = level.getThunderLevel(partialTick);
        // Slow gusts: two incommensurate waves multiplied, so the wind swells and drops back unpredictably.
        float gustWave = 0.5f + 0.5f * Mth.sin(seconds * 0.41f) * Mth.sin(seconds * 0.17f + 1.3f);
        float gust = (0.55f + 0.9f * gustWave) * (1.0f + 1.4f * thunder) * (snow ? 1.4f : 1.0f);
        float windX = (2.1f + 0.9f * Mth.sin(seconds * 0.13f)) * gust;
        float windZ = (0.8f + 0.5f * Mth.cos(seconds * 0.09f)) * gust;
        // The drift is the integral of the wind, so a change of gust bends the rain instead of teleporting it.
        float dt = Mth.clamp(seconds - lastSeconds, 0.0f, 0.25f);
        lastSeconds = seconds;
        driftX += windX * dt;
        driftZ += windZ * dt;

        float sunHeight = SkyMath.sunDirection(level.getTimeOfDay(partialTick))[1];
        float light = PrecipitationMath.lightLevel(sunHeight);
        float fog = (float) Mth.clamp(FogClient.density(partialTick), 0.0, 1.0);
        float[] fogColor = RenderSystem.getShaderFogColor();
        // Drops take the sky/fog colour (so dusk is pink-tinted, storms grey) scaled by daylight; night is dim blue.
        float dim = (0.16f + 0.74f * light) * (1.0f - 0.3f * thunder);
        float red = Mth.lerp(0.45f, snow ? 0.92f : 0.80f, fogColor[0]) * dim;
        float green = Mth.lerp(0.45f, snow ? 0.95f : 0.86f, fogColor[1]) * dim;
        float blue = Mth.lerp(0.45f, snow ? 1.00f : 0.95f, fogColor[2]) * dim;
        if (light < 0.5f) {
            blue += 0.04f * (1.0f - light * 2.0f);
        }

        cover.update(level, camera.getPosition());

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            shader.bind();
            uView.setMat4(view);
            uProj.setMat4(projection);
            uCamera.setVec3((float) camera.getPosition().x, (float) camera.getPosition().y, (float) camera.getPosition().z);
            uWind.setVec2(windX, windZ);
            uDrift.setVec2(driftX, driftZ);
            uTime.setFloat(seconds);
            uIntensity.setFloat(intensity);
            uRadius.setFloat(RADIUS);
            uHeight.setFloat(HEIGHT);
            uColor.setVec3(red, green, blue);
            uFogColor.setVec3(fogColor[0], fogColor[1], fogColor[2]);
            uFog.setFloat(fog);
            uGrid.setVec3(cover.originX(), cover.originZ(), CoverGrid.SIZE);
            uCover.setInt(COVER_UNIT);

            GL13.glActiveTexture(GL13.GL_TEXTURE0 + COVER_UNIT);
            cover.bind();

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            GL30.glBindVertexArray(vao);
            uMode.setFloat(snow ? 1.0f : 0.0f);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, count);
            if (!snow) {
                uMode.setFloat(2.0f);
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, Math.min(count / 3, MAX_PARTICLES));
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL13.glActiveTexture(previousUnit);
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            RenderSystem.depthMask(true);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullFace) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
        }
    }

    private boolean ensureResources() {
        if (!needsReload && shader != null) {
            return true;
        }
        close();
        try {
            shader = new Shader(client.getResourceManager(), "precipitation", Map.of());
        } catch (IOException | RuntimeException e) {
            Incognita.LOGGER.error("Could not load the precipitation shader", e);
            close();
            return false;
        }
        uView = shader.uniform("u_view");
        uProj = shader.uniform("u_proj");
        uCamera = shader.uniform("u_camera");
        uWind = shader.uniform("u_wind");
        uTime = shader.uniform("u_time");
        uIntensity = shader.uniform("u_intensity");
        uMode = shader.uniform("u_mode");
        uDrift = shader.uniform("u_drift");
        uCover = shader.uniform("u_cover");
        uGrid = shader.uniform("u_grid");
        uRadius = shader.uniform("u_radius");
        uHeight = shader.uniform("u_height");
        uColor = shader.uniform("u_color");
        uFogColor = shader.uniform("u_fog_color");
        uFog = shader.uniform("u_fog");

        quadVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        FloatBuffer quad = MemoryUtil.memAllocFloat(8);
        quad.put(new float[] {
            -0.5f, -0.5f,
             0.5f, -0.5f,
            -0.5f,  0.5f,
             0.5f,  0.5f
        }).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(quad);

        instanceVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        FloatBuffer seeds = MemoryUtil.memAllocFloat(MAX_PARTICLES * 4);
        RandomSource random = RandomSource.create(SEED);
        for (int i = 0; i < MAX_PARTICLES; i++) {
            seeds.put(random.nextFloat()).put(random.nextFloat()).put(random.nextFloat()).put(random.nextFloat());
        }
        seeds.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, seeds, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(seeds);

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL15.GL_FLOAT, false, 0, 0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL15.GL_FLOAT, false, 0, 0);
        GL33.glVertexAttribDivisor(1, 1);
        GL30.glBindVertexArray(0);

        needsReload = false;
        Incognita.LOGGER.info("Precipitation renderer initialized");
        return true;
    }

    @Override
    public void close() {
        cover.close();
        if (shader != null) {
            shader.close();
            shader = null;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (quadVbo != 0) {
            GL15.glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }
        if (instanceVbo != 0) {
            GL15.glDeleteBuffers(instanceVbo);
            instanceVbo = 0;
        }
    }
}
