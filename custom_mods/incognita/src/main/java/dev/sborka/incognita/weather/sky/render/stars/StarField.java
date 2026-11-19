package dev.sborka.incognita.weather.sky.render.stars;

import dev.sborka.incognita.weather.sky.atmosphere.SkyLut;
import dev.sborka.incognita.weather.sky.math.SkyMath;
import dev.sborka.incognita.weather.sky.render.dome.SkyDome;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Stars as soft round sprites, rebuilt every frame from a fixed catalogue so each star can follow the sky:
 * <ul>
 *   <li>magnitudes follow the real count law (many faint stars, a handful of bright ones) and colours the stellar
 *       temperature range from blue-white to orange;</li>
 *   <li>light is dimmed and reddened by the air mass toward the horizon, and twinkles more there;</li>
 *   <li>a star only shows once it outshines the sky behind it, so in the dusk the bright stars come out first and the
 *       dark side of the sky fills in before the side of the sunset;</li>
 *   <li>sprites are sized in screen pixels, so stars neither vanish at a wide field of view nor swell when zoomed.</li>
 * </ul>
 */
public final class StarField implements AutoCloseable {
    private static final int UNIFORM_STARS = 9000;
    private static final int BAND_STARS = 7000;
    private static final float DISTANCE = 100.0f;
    /** Faintest and brightest magnitude of the catalogue. */
    private static final double MAG_FAINT = 6.0;
    private static final double MAG_BRIGHT = -1.2;

    private final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_star");
    private final Vector3f dir = new Vector3f();
    private final Vector3f t1 = new Vector3f();
    private final Vector3f t2 = new Vector3f();
    private final double[] trans = new double[3];
    private final double[] zenith = new double[3];
    private float[] dirs = new float[0];
    private float[] colors = new float[0];
    private float[] flux = new float[0];
    private float[] phase = new float[0];
    private int count;
    private double builtDensity = -1.0;
    private boolean ready;

    /**
     * Draws the stars with ONE/ONE additive blending (set by the caller); the sprite stores brightness in its colour.
     *
     * @param celestial  view matrix rotated to the celestial frame
     * @param rotation   celestial rotation only
     * @param projection projection matrix, for the size of a pixel
     * @param dome       the sky dome of this frame, for the brightness of the sky behind each star
     * @param strength   overall visibility, 0..1 (weather, flash)
     * @param seconds    animation time for the twinkle
     */
    public void draw(Matrix4f celestial, Matrix4f rotation, Matrix4f projection, SkyLut lut, SkyDome dome,
                     float strength, float seconds, double density) {
        if (strength <= 0.004f || density <= 0.0) {
            return;
        }
        ensure(density);
        lut.transmittance(1.0, zenith);
        int height = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        // Angle covered by one pixel at the screen centre.
        float pixel = 2.0f / (Math.abs(projection.m11()) * height);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, id);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            // Faint stars are skipped before any trigonometry: most of the catalogue never reaches the screen.
            if (flux[i] * strength < 0.012f) {
                continue;
            }
            dir.set(dirs[i * 3], dirs[i * 3 + 1], dirs[i * 3 + 2]);
            rotation.transformDirection(dir);
            // The sphere is complete: stars below the horizon mirror the upper sky (terrain hides them in play).
            double el = Math.abs(dir.y);
            double airmass = SkyMath.airmass(el);
            // Extinction of about 0.2 magnitudes per air mass.
            double ext = Math.pow(10.0, -0.08 * (airmass - 1.0));
            double twinkleAmp = Math.min(0.55, 0.06 + 0.018 * airmass);
            float p = phase[i];
            double twinkle = 1.0 + twinkleAmp * Math.sin(seconds * (3.1 + 2.3 * p) + p * 40.0)
                * Math.sin(seconds * (1.7 + 1.1 * p) + p * 17.0);
            double light = flux[i] * ext * twinkle * strength;
            double sky = dome.luminanceAt(dir.x, dir.y, dir.z);
            double threshold = Math.max(0.0, sky - 0.075) * 8.0;
            light *= SkyMath.smoothstep(threshold * 0.5, threshold * 1.5 + 0.015, light);
            if (light < 0.01) {
                continue;
            }
            lut.transmittance(el, trans);
            float k = (float) light;
            float r = colors[i * 3] * k * (float) Math.pow(trans[0] / zenith[0], 0.35);
            float g = colors[i * 3 + 1] * k * (float) Math.pow(trans[1] / zenith[1], 0.35);
            float b = colors[i * 3 + 2] * k * (float) Math.pow(trans[2] / zenith[2], 0.35);
            // Half size of the sprite: about 2 px for faint stars, a little more for the brightest.
            float half = pixel * (2.2f + 1.6f * (float) Math.min(1.0, Math.sqrt(flux[i]))) * DISTANCE;
            sprite(builder, celestial, i, half, r, g, b);
            drawn++;
        }
        MeshData mesh = builder.build();
        if (mesh != null && drawn > 0) {
            BufferUploader.drawWithShader(mesh);
        } else if (mesh != null) {
            mesh.close();
        }
    }

    private void sprite(BufferBuilder builder, Matrix4f celestial, int i, float half, float r, float g, float b) {
        float x = dirs[i * 3];
        float y = dirs[i * 3 + 1];
        float z = dirs[i * 3 + 2];
        dir.set(x, y, z);
        dir.cross(Math.abs(y) < 0.9f ? 0 : 1, Math.abs(y) < 0.9f ? 1 : 0, 0, t1).normalize();
        dir.cross(t1, t2).normalize();
        float cx = x * DISTANCE;
        float cy = y * DISTANCE;
        float cz = z * DISTANCE;
        corner(builder, celestial, cx, cy, cz, -half, -half, 0, 0, r, g, b);
        corner(builder, celestial, cx, cy, cz, half, -half, 1, 0, r, g, b);
        corner(builder, celestial, cx, cy, cz, half, half, 1, 1, r, g, b);
        corner(builder, celestial, cx, cy, cz, -half, half, 0, 1, r, g, b);
    }

    private void corner(BufferBuilder builder, Matrix4f m, float cx, float cy, float cz, float a, float c, float u,
                        float v, float r, float g, float b) {
        builder.addVertex(m, cx + t1.x * a + t2.x * c, cy + t1.y * a + t2.y * c, cz + t1.z * a + t2.z * c)
            .setUv(u, v).setColor(r, g, b, 1.0f);
    }

    private void ensure(double density) {
        if (!ready) {
            paintSprite();
            ready = true;
        }
        if (Math.abs(density - builtDensity) < 1e-6) {
            return;
        }
        builtDensity = density;
        int uniform = (int) (UNIFORM_STARS * density);
        int band = (int) (BAND_STARS * density);
        count = uniform + band;
        dirs = new float[count * 3];
        colors = new float[count * 3];
        flux = new float[count];
        phase = new float[count];
        for (int i = 0; i < count; i++) {
            catalogue(i, i >= uniform);
        }
    }

    private static float rnd(int seed, int salt) {
        return SkyMath.toUnit(SkyMath.pcg(seed * 31 + SkyMath.pcg(salt)));
    }

    private void catalogue(int i, boolean onBand) {
        int seed = i * 7 + 13;
        Vector3f d = new Vector3f();
        if (onBand) {
            // Concentrated along the galactic plane: latitude is a sum of uniforms, longitude is free.
            double lat = (rnd(seed, 1) + rnd(seed, 2) + rnd(seed, 3) - 1.5) * 0.42;
            double lon = rnd(seed, 4) * Math.PI * 2.0;
            Vector3f e1 = new Vector3f(MilkyWay.POLE).cross(new Vector3f(0, 1, 0)).normalize();
            Vector3f e2 = new Vector3f(MilkyWay.POLE).cross(e1).normalize();
            d.set(e1).mul((float) (Math.cos(lat) * Math.cos(lon)));
            d.add(new Vector3f(e2).mul((float) (Math.cos(lat) * Math.sin(lon))));
            d.add(new Vector3f(MilkyWay.POLE).mul((float) Math.sin(lat)));
        } else {
            double z = rnd(seed, 1) * 2.0 - 1.0;
            double phi = rnd(seed, 2) * Math.PI * 2.0;
            double r = Math.sqrt(1.0 - z * z);
            d.set((float) (r * Math.cos(phi)), (float) z, (float) (r * Math.sin(phi)));
        }
        d.normalize();
        dirs[i * 3] = d.x;
        dirs[i * 3 + 1] = d.y;
        dirs[i * 3 + 2] = d.z;
        // Star counts grow about 2.5 times per magnitude: sample the magnitude from that law.
        double u = Math.max(rnd(seed, 5), 1e-6);
        double mag = Math.max(MAG_BRIGHT, MAG_FAINT + Math.log10(u) / 0.4);
        // Display brightness: a gentle curve keeps the faint stars visible in 8 bits and the bright ones unclipped.
        double rel = Math.pow(10.0, -0.4 * (mag - MAG_FAINT));
        flux[i] = (float) Math.min(1.6, 0.16 * Math.pow(rel, 0.42));
        float[] tint = temperatureTint(rnd(seed, 6));
        colors[i * 3] = tint[0];
        colors[i * 3 + 1] = tint[1];
        colors[i * 3 + 2] = tint[2];
        phase[i] = rnd(seed, 7);
    }

    /** Colour of a random star: mostly white and blue-white, some yellow, a few orange and red giants. */
    static float[] temperatureTint(float t) {
        if (t < 0.18f) {
            return new float[] {0.72f, 0.82f, 1.0f};
        } else if (t < 0.62f) {
            return new float[] {0.90f, 0.94f, 1.0f};
        } else if (t < 0.84f) {
            return new float[] {1.0f, 0.96f, 0.86f};
        } else if (t < 0.96f) {
            return new float[] {1.0f, 0.84f, 0.64f};
        }
        return new float[] {1.0f, 0.72f, 0.52f};
    }

    /** A soft round dot: a gaussian core that reaches zero at the edge of the square. */
    private void paintSprite() {
        int n = 32;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, n, n, false);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float px = (i + 0.5f) / n * 2 - 1;
                float py = (j + 0.5f) / n * 2 - 1;
                float rr = (float) Math.sqrt(px * px + py * py);
                float a = (float) Math.exp(-rr * rr * 6.0) * (1.0f - (float) SkyMath.smoothstep(0.75, 1.0, rr));
                int v = Math.round(Math.max(0, Math.min(1, a)) * 255);
                image.setPixelRGBA(i, j, (255 << 24) | (v << 16) | (v << 8) | v);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(true, false);
    }

    @Override
    public void close() {
        if (ready) {
            Minecraft.getInstance().getTextureManager().release(id);
            ready = false;
        }
        builtDensity = -1.0;
    }
}
