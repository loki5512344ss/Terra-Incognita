package dev.sborka.incognita.weather.clouds.render;

import dev.sborka.incognita.weather.fog.control.FogOverride;
import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.weather.WeatherState;
import dev.sborka.incognita.weather.clouds.config.CloudConfig;
import dev.sborka.incognita.weather.clouds.config.CloudSettings;
import dev.sborka.incognita.weather.clouds.field.BandSpec;
import dev.sborka.incognita.weather.clouds.field.CoverModel;
import dev.sborka.incognita.weather.clouds.field.mesh.BandGeometry;
import dev.sborka.incognita.weather.clouds.gl.CloudTargets;
import dev.sborka.incognita.weather.clouds.gl.GlSupport;
import dev.sborka.incognita.weather.clouds.gl.GpuTimer;
import dev.sborka.incognita.weather.clouds.render.band.BandCuller;
import dev.sborka.incognita.weather.clouds.render.band.BandStreamer;
import dev.sborka.incognita.weather.clouds.render.pass.CompositePass;
import dev.sborka.incognita.weather.clouds.render.pass.PuffPass;
import dev.sborka.incognita.weather.clouds.shade.CloudFog;
import dev.sborka.incognita.weather.clouds.shade.CloudLight;
import dev.sborka.incognita.weather.clouds.wind.BandWind;
import dev.sborka.incognita.weather.clouds.wind.WindField;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.fog.FogConfig;
import dev.sborka.incognita.weather.sky.math.SkyMath;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * One frame of clouds: advances the band winds, keeps every band's geometry around the camera, culls it, runs the
 * nearest surface pass and the thickness pass into the offscreen G-buffer and composites the result into the scene.
 * Both GL paths share all of this; they differ only in texture allocation and instance submission.
 */
final class CloudRenderer implements AutoCloseable {
    /** Earth curvature of the sky dome: 1 / (2 R) with R = 14000 blocks; distant clouds sink to the horizon. */
    private static final float CURVATURE = 1.0f / 28000.0f;
    /** Extinction per unit of optical depth at the default density. */
    private static final float SIGMA = 0.075f;

    private final Minecraft client = Minecraft.getInstance();
    private final PuffPass puffPass = new PuffPass();
    private final CompositePass composite = new CompositePass();
    private final CloudTargets targets = new CloudTargets();
    private final GpuTimer timer = new GpuTimer();
    private final WindField wind = new WindField(BandWind.BANDS);
    private final List<BandStreamer> streamers = new ArrayList<>();
    private final BandCuller[] cullers = new BandCuller[BandSpec.COUNT];
    private final BandGeometry[] drawn = new BandGeometry[BandSpec.COUNT];
    private final float[] originX = new float[BandSpec.COUNT];
    private final float[] originZ = new float[BandSpec.COUNT];
    private final float[] shift = new float[BandSpec.COUNT];
    /** 0..1 how far a band has melted away in rain; 1 skips its draw calls. */
    private final float[] hidden = new float[BandSpec.COUNT];

    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f rotationViewProj = new Matrix4f();
    private final Matrix4f projectionInverse = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f forward = new Vector3f();
    private final Vector4f planePoint = new Vector4f();
    private final float[] fogRange = new float[2];
    private final float[] lightDir = new float[3];
    private final int[] drawBuffer = new int[1];

    private boolean needsReload = true;
    private boolean failed;
    private boolean windStarted;
    private float lastTicks = -1.0f;
    private GlSupport.Path activePath = GlSupport.Path.NONE;
    private int visiblePuffs;
    private float originY;
    private int resolutionScale = 1;

    CloudRenderer() {
        for (int b = 0; b < BandSpec.COUNT; b++) {
            cullers[b] = new BandCuller();
        }
    }

    /** Called when the resource packs reload; GL objects are rebuilt lazily on the next frame. */
    void invalidate() {
        needsReload = true;
        failed = false;
    }

    double gpuMillis() {
        return timer.averageMs();
    }

    int visiblePuffs() {
        return visiblePuffs;
    }

    /** One line for logs: per band wind offset, heading and puff count. */
    String debugLine() {
        StringBuilder line = new StringBuilder(160);
        for (int b = 0; b < BandSpec.COUNT; b++) {
            BandGeometry g = drawn[b];
            line.append(String.format(java.util.Locale.ROOT, "band%d off=(%.1f, %.1f) heading=%.0f puffs=%d; ", b,
                wind.offsetX(b), wind.offsetZ(b), Math.toDegrees(wind.wind(b).headingAt(wind.clock())),
                g == null ? 0 : g.puffs()));
        }
        return line.append("visible=").append(visiblePuffs).append(" path=").append(activePath).toString();
    }

    void render(Matrix4f view, Matrix4f projection, float partialTick, double camX, double camY, double camZ) {
        ClientLevel level = client.level;
        if (failed || level == null || !CloudConfig.enabled() || ShaderPackCheck.isShaderPackActive()
            || Float.isNaN(level.effects().getCloudHeight())
            || client.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        GlSupport.Path path = GlSupport.path();
        if (path == GlSupport.Path.NONE) {
            return;
        }
        CloudSettings settings = CloudSettings.fromConfig();
        try {
            if (!ensureResources(path, settings.halfResolution() ? 2 : 1)) {
                failed = true;
                return;
            }
            timer.begin();
            prepare(level, settings, view, projection, partialTick, camX, camY, camZ);
            drawPasses();
            drawComposite(level, settings, partialTick);
        } catch (RuntimeException | LinkageError e) {
            Incognita.LOGGER.error("Cloud renderer failed, clouds disabled until the next resource reload", e);
            failed = true;
        } finally {
            timer.end();
            restoreState();
        }
    }

    private boolean ensureResources(GlSupport.Path path, int scale) {
        if (needsReload || path != activePath) {
            close();
            try {
                puffPass.load(client.getResourceManager());
                composite.load(client.getResourceManager());
            } catch (Exception e) {
                Incognita.LOGGER.error("Could not load the cloud shaders", e);
                return false;
            }
            for (BandSpec spec : BandSpec.BANDS) {
                streamers.add(new BandStreamer(spec));
            }
            needsReload = false;
            activePath = path;
            Incognita.LOGGER.info("Cloud renderer initialized, path {}", path);
        }
        RenderTarget main = client.getMainRenderTarget();
        resolutionScale = scale;
        int width = (main.width + scale - 1) / scale;
        int height = (main.height + scale - 1) / scale;
        if (!targets.matches(width, height, path) && !targets.create(width, height, path)) {
            return false;
        }
        return puffPass.ready() && composite.ready();
    }

    private void prepare(ClientLevel level, CloudSettings settings, Matrix4f view, Matrix4f projection,
                         float partialTick, double camX, double camY, double camZ) {
        float ticks = client.levelRenderer.getTicks() + partialTick;
        if (!windStarted) {
            wind.start(Math.floorMod(level.getGameTime(), 1_000_000L));
            windStarted = true;
            lastTicks = ticks;
        }
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        wind.advance(ticks - lastTicks, CoverModel.windFactor(rain, thunder));
        lastTicks = ticks;

        rotation.set(view).m30(0.0f).m31(0.0f).m32(0.0f);
        rotationViewProj.set(projection).mul(rotation);
        frustum.set(rotationViewProj);
        rotation.positiveZ(forward).negate();

        float cover = (float) WeatherState.cloudiness(level.getGameTime(), level.getDayTime(), rain, thunder,
            FogOverride.client().frequency(FogConfig.frequency()), FogConfig.morningFog());
        float cloudBase = level.effects().getCloudHeight() + settings.baseOffset();
        visiblePuffs = 0;
        for (int b = 0; b < BandSpec.COUNT; b++) {
            BandStreamer streamer = streamers.get(b);
            BandSpec spec = streamer.spec();
            float threshold = CoverModel.threshold(cover, spec.coverBias());
            double offX = wind.offsetX(b);
            double offZ = wind.offsetZ(b);
            BandGeometry geometry = streamer.update(camX - offX, camZ - offZ,
                settings.generationRadius(spec.chunkSize()), threshold, cloudBase,
                (float) wind.wind(b).headingRadians());
            drawn[b] = geometry;
            if (geometry == null) {
                continue;
            }
            // Exact per band origin in double precision: anchor plus this band's own wind offset, camera relative.
            originX[b] = (float) (geometry.originX() + offX - camX);
            originZ[b] = (float) (geometry.originZ() + offZ - camZ);
            // In rain only the lowest deck matters: the upper bands melt away (their puffs shrink to nothing) and
            // are not drawn at all once gone.
            hidden[b] = b == 0 ? 0.0f : (float) SkyMath.smoothstep(0.15, 0.65, rain);
            shift[b] = geometry.threshold() - threshold - hidden[b] * 2.5f;
            cullers[b].cull(geometry, frustum, originX[b], originZ[b], (float) camY, CURVATURE,
                settings.drawDistance());
            for (int r = 0; r < cullers[b].runs(); r++) {
                visiblePuffs += cullers[b].counts()[r];
            }
        }

        projectionInverse.set(projection).invert();
        float near = planeDistance(-1.0f);
        float far = planeDistance(1.0f);
        float density = (float) SkyMath.clamp01(FogClient.density(partialTick));
        CloudFog.distanceRange(density, FogClient.appliedFarDistance(), fogRange);
        puffPass.beginFrame(rotationViewProj, new PuffPass.FrameView(ticks / 20.0f, CURVATURE, near, far,
            forward.x, forward.y, forward.z, settings.drawDistance(), fogRange[0], fogRange[1],
            CloudFog.hideAll(density), resolutionScale));
        // The y origin is the same for all bands: puff heights are world heights.
        originY = (float) -camY;
    }

    /** View distance of the near (-1) or far (1) plane, from the inverse projection; robust to view bobbing. */
    private float planeDistance(float ndcZ) {
        planePoint.set(0.0f, 0.0f, ndcZ, 1.0f);
        projectionInverse.transform(planePoint);
        return -planePoint.z / planePoint.w;
    }

    private void drawPasses() {
        targets.bind();
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
        RenderSystem.clearDepth(1.0);
        GL20.glDrawBuffers(new int[] {CloudTargets.SURFACE, CloudTargets.DETAIL, CloudTargets.THICKNESS});
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + PuffPass.SCENE_DEPTH_UNIT);
        RenderSystem.bindTexture(client.getMainRenderTarget().getDepthTextureId());
        // Only the far faces of each box: every ray through a box rasterizes it exactly once.
        RenderSystem.enableCull();
        boolean baseInstance = activePath == GlSupport.Path.MODERN;

        // Pass 0: nearest surface, depth tested.
        GL20.glDrawBuffers(new int[] {CloudTargets.SURFACE, CloudTargets.DETAIL});
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.disableBlend();
        puffPass.setPass(0);
        drawBands(baseInstance);

        // Pass 1: optical depth of every puff, added up.
        setDrawBuffer(CloudTargets.THICKNESS);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        puffPass.setPass(1);
        drawBands(baseInstance);
    }

    private void drawBands(boolean baseInstance) {
        for (int b = 0; b < BandSpec.COUNT; b++) {
            if (drawn[b] == null || cullers[b].runs() == 0 || hidden[b] >= 0.995f) {
                continue;
            }
            puffPass.setBand(b, originX[b], originY, originZ[b], shift[b]);
            BandStreamer streamer = streamers.get(b);
            streamer.mesh().bind();
            streamer.mesh().draw(cullers[b].starts(), cullers[b].counts(), cullers[b].runs(), baseInstance);
        }
    }

    private void drawComposite(ClientLevel level, CloudSettings settings, float partialTick) {
        RenderTarget target = client.levelRenderer.getCloudsTarget();
        if (target == null) {
            target = client.getMainRenderTarget();
        }
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        float[] sun = SkyMath.sunDirection(level.getTimeOfDay(partialTick));
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        float cover = (float) WeatherState.cloudiness(level.getGameTime(), level.getDayTime(), rain, thunder,
            FogOverride.client().frequency(FogConfig.frequency()), FogConfig.morningFog());
        CloudLight.Result light = CloudLight.compute(sun[1], cover, rain, thunder, settings.density());
        float sign = CloudLight.sunLights(sun[1]) ? 1.0f : -1.0f;
        lightDir[0] = sun[0] * sign;
        lightDir[1] = sun[1] * sign;
        lightDir[2] = sun[2] * sign;
        float sigma = SIGMA * (settings.density() / (float) CloudConfig.DEFAULT_DENSITY)
            * (1.0f + 0.3f * light.greyness());
        composite.draw(rotationViewProj, targets, light, lightDir, RenderSystem.getShaderFogColor(), sigma, sun[1],
            resolutionScale, CloudFog.hideAll((float) SkyMath.clamp01(FogClient.density(partialTick))));
    }

    private void setDrawBuffer(int attachment) {
        drawBuffer[0] = attachment;
        GL20.glDrawBuffers(drawBuffer);
    }

    /**
     * Leaves GL the way vanilla expects it outside a draw call: no program, VAO or array buffer bound, default
     * blending, depth and culling, texture units cleared, the main target bound.
     */
    private void restoreState() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.colorMask(true, true, true, true);
        for (int unit = 4; unit >= 0; unit--) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(0);
        }
        client.getMainRenderTarget().bindWrite(true);
    }

    @Override
    public void close() {
        for (BandStreamer streamer : streamers) {
            streamer.close();
        }
        streamers.clear();
        java.util.Arrays.fill(drawn, null);
        puffPass.close();
        composite.close();
        targets.close();
        timer.close();
        activePath = GlSupport.Path.NONE;
    }
}
