package dev.sborka.incognita.lighting.halo.render;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.lighting.config.LightingConfig;
import dev.sborka.incognita.lighting.field.ColorLightField;
import dev.sborka.incognita.lighting.halo.HaloMath;
import dev.sborka.incognita.lighting.field.LightColor;
import dev.sborka.incognita.weather.fog.FogClient;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Additive soft sprites over strong colored lamps, drawn after the terrain with the fog switched off, so the lamp
 * color bleeds through dense fog. Depth tested against the terrain (hills still hide lamps), no shader needed, so
 * it works with Sodium. The per-frame work is one pass over a cached array of the strong emitters.
 */
public final class HaloRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "lamp_halo");
    private static final int TEX_SIZE = 64;
    /** Emitters below this intensity (about level 10) get no halo. */
    private static final int MIN_INTENSITY = 170;
    private static final int MAX_QUADS = 192;
    /** The halo floats this far toward the camera so it is not swallowed by the lamp's own cube. */
    private static final float LIFT = 0.9f;

    private final List<ColorLightField.Emitter> scratch = new ArrayList<>();
    private ColorLightField cachedField;
    private int cachedVersion = -1;
    private int count;
    private float[] px = new float[64];
    private float[] py = new float[64];
    private float[] pz = new float[64];
    private float[] cr = new float[64];
    private float[] cg = new float[64];
    private float[] cb = new float[64];
    private float[] ci = new float[64];
    private boolean textureReady;

    /** Draws all halos for this frame. */
    public void render(Matrix4f view, Camera camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        ColorLightField field = dev.sborka.incognita.lighting.client.LightingClient.field();
        if (level == null || field == null) {
            return;
        }
        refresh(field);
        if (count == 0) {
            return;
        }
        ensureTexture();
        float range = LightingConfig.haloRange();
        float strength = LightingConfig.haloStrength();
        float fogFar = FogClient.appliedFarDistance();
        float night = HaloMath.night(level.getTimeOfDay(partialTick));
        double cx = camera.getPosition().x;
        double cy = camera.getPosition().y;
        double cz = camera.getPosition().z;
        Vector3f look = camera.getLookVector();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int i = 0; i < count && quads < MAX_QUADS; i++) {
            float dx = (float) (px[i] - cx);
            float dy = (float) (py[i] - cy);
            float dz = (float) (pz[i] - cz);
            float forward = dx * look.x + dy * look.y + dz * look.z;
            if (forward <= 1.0f) {
                continue;
            }
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float alpha = HaloMath.alpha(dist, range, fogFar, night, ci[i], strength);
            if (alpha < 0.01f) {
                continue;
            }
            float inv = 1.0f / dist;
            float lift = Math.min(LIFT, dist * 0.5f);
            float x = dx - dx * inv * lift;
            float y = dy - dy * inv * lift;
            float z = dz - dz * inv * lift;
            float r = HaloMath.radius(dist, fogFar);
            int red = (int) (cr[i] * 255.0f);
            int green = (int) (cg[i] * 255.0f);
            int blue = (int) (cb[i] * 255.0f);
            int a = (int) (Math.min(alpha, 1.0f) * 255.0f);
            float lx = left.x * r;
            float ly = left.y * r;
            float lz = left.z * r;
            float ux = up.x * r;
            float uy = up.y * r;
            float uz = up.z * r;
            buf.addVertex(x + lx - ux, y + ly - uy, z + lz - uz).setUv(0f, 1f).setColor(red, green, blue, a);
            buf.addVertex(x - lx - ux, y - ly - uy, z - lz - uz).setUv(1f, 1f).setColor(red, green, blue, a);
            buf.addVertex(x - lx + ux, y - ly + uy, z - lz + uz).setUv(1f, 0f).setColor(red, green, blue, a);
            buf.addVertex(x + lx + ux, y + ly + uy, z + lz + uz).setUv(0f, 0f).setColor(red, green, blue, a);
            quads++;
        }
        MeshData mesh = buf.build();
        if (mesh == null) {
            return;
        }
        float oldStart = RenderSystem.getShaderFogStart();
        float oldEnd = RenderSystem.getShaderFogEnd();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
            com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.setShaderFogStart(oldStart);
        RenderSystem.setShaderFogEnd(oldEnd);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        stack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** Rebuilds the cached arrays when the set of block emitters changed. */
    private void refresh(ColorLightField field) {
        int version = field.blockEmitterVersion();
        if (field == cachedField && version == cachedVersion) {
            return;
        }
        cachedField = field;
        cachedVersion = version;
        scratch.clear();
        field.collectBlockEmitters(scratch);
        count = 0;
        for (ColorLightField.Emitter e : scratch) {
            int intensity = LightColor.intensity(e.color());
            if (intensity < MIN_INTENSITY) {
                continue;
            }
            if (count == px.length) {
                grow();
            }
            px[count] = e.x() + 0.5f;
            py[count] = e.y() + 1.1f;
            pz[count] = e.z() + 0.5f;
            // Saturate toward the full-brightness hue; the intensity goes into alpha instead.
            float m = 1.0f / intensity;
            cr[count] = LightColor.r(e.color()) * m;
            cg[count] = LightColor.g(e.color()) * m;
            cb[count] = LightColor.b(e.color()) * m;
            ci[count] = intensity / 255.0f;
            count++;
        }
        scratch.clear();
    }

    private void grow() {
        int n = px.length * 2;
        px = java.util.Arrays.copyOf(px, n);
        py = java.util.Arrays.copyOf(py, n);
        pz = java.util.Arrays.copyOf(pz, n);
        cr = java.util.Arrays.copyOf(cr, n);
        cg = java.util.Arrays.copyOf(cg, n);
        cb = java.util.Arrays.copyOf(cb, n);
        ci = java.util.Arrays.copyOf(ci, n);
    }

    private void ensureTexture() {
        if (textureReady) {
            return;
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                float u = (x + 0.5f) / TEX_SIZE * 2.0f - 1.0f;
                float v = (y + 0.5f) / TEX_SIZE * 2.0f - 1.0f;
                int a = Math.round(HaloMath.profile((float) Math.sqrt(u * u + v * v)) * 255.0f);
                image.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        DynamicTexture tex = new DynamicTexture(image);
        tex.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, tex);
        textureReady = true;
    }
}
