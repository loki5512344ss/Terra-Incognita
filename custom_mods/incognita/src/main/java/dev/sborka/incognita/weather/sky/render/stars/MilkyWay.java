package dev.sborka.incognita.weather.sky.render.stars;

import dev.sborka.incognita.weather.sky.math.SkyMath;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Milky Way: a texture painted once on the CPU (a tilted band of unresolved star light with a bright core and dust
 * lanes) and drawn as a ribbon along a great circle. The ribbon is seamless: the texture wraps around the circle and
 * fades to nothing at both long edges.
 */
public final class MilkyWay implements AutoCloseable {
    /** Pole of the galactic plane in the celestial frame, tilted so the band crosses the sky. */
    static final Vector3f POLE = new Vector3f(0.42f, 0.78f, -0.46f).normalize();

    private static final int TEX_W = 1024;
    private static final int TEX_H = 192;
    /** Half width of the ribbon as a latitude angle in radians. */
    private static final double HALF_LAT = 0.55;
    private static final int SEGMENTS = 72;
    private static final int ROWS = 12;

    private final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_milkyway");
    private final Vector3f e1 = new Vector3f();
    private final Vector3f e2 = new Vector3f();
    private final Vector3f dir = new Vector3f();
    private final Vector3f pos = new Vector3f();
    private final float[] alpha = new float[(SEGMENTS + 1) * (ROWS + 1)];
    private final float[] baseDirs = new float[(SEGMENTS + 1) * (ROWS + 1) * 3];
    private boolean ready;

    public MilkyWay() {
        // Orthonormal galactic frame: e1 points to the galactic centre.
        Vector3f centre = new Vector3f(0.86f, 0.0f, 0.51f).normalize();
        centre.sub(new Vector3f(POLE).mul(centre.dot(POLE))).normalize(e1);
        new Vector3f(POLE).cross(e1, e2).normalize();
        for (int s = 0; s <= SEGMENTS; s++) {
            double lon = s * Math.PI * 2.0 / SEGMENTS - Math.PI;
            for (int r = 0; r <= ROWS; r++) {
                double lat = (r / (double) ROWS - 0.5) * 2.0 * HALF_LAT;
                int i = (s * (ROWS + 1) + r) * 3;
                double c = Math.cos(lat);
                baseDirs[i] = (float) (c * (Math.cos(lon) * e1.x + Math.sin(lon) * e2.x) + Math.sin(lat) * POLE.x);
                baseDirs[i + 1] = (float) (c * (Math.cos(lon) * e1.y + Math.sin(lon) * e2.y) + Math.sin(lat) * POLE.y);
                baseDirs[i + 2] = (float) (c * (Math.cos(lon) * e1.z + Math.sin(lon) * e2.z) + Math.sin(lat) * POLE.z);
            }
        }
    }

    private void ensure() {
        if (ready) {
            return;
        }
        ready = true;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TEX_W, TEX_H, true);
        for (int j = 0; j < TEX_H; j++) {
            double lat = ((j + 0.5) / TEX_H - 0.5) * 2.0 * HALF_LAT;
            for (int i = 0; i < TEX_W; i++) {
                double lon = (i + 0.5) / TEX_W * Math.PI * 2.0 - Math.PI;
                float[] c = paint(lon, lat);
                image.setPixelRGBA(i, j, pack(c[0], c[1], c[2]));
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(true, false);
    }

    private static int pack(float r, float g, float b) {
        return (255 << 24) | (to8(b) << 16) | (to8(g) << 8) | to8(r);
    }

    private static int to8(float v) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, v)) * 255.0f);
    }

    /**
     * Light of the band at a galactic longitude and latitude (radians): cool blue-white star clouds that thicken
     * toward a faintly warmer core, split by dark dust lanes along the plane, fading smoothly to nothing at the edges.
     */
    private static float[] paint(double lon, double lat) {
        double sinLat = Math.sin(lat);
        // The band widens toward the galactic centre (lon = 0).
        double width = 0.16 + 0.10 * Math.exp(-lon * lon * 0.9);
        double across = sinLat / width;
        double band = Math.exp(-across * across);
        double core = Math.exp(-lon * lon * 1.1) * Math.exp(-sinLat * sinLat * 14.0);
        double clumps = GalaxyNoise.band(lon, sinLat, 3.6, 6.0) * 0.45 + GalaxyNoise.band(lon, sinLat, 8.5, 14.0) * 0.33
            + GalaxyNoise.band(lon, sinLat, 21.0, 34.0) * 0.22;
        double cloud = SkyMath.smoothstep(0.30, 0.78, clumps);
        // Dust: a long wavy lane along the plane plus patches; it only darkens, never adds light.
        double laneCentre = 0.035 * Math.sin(lon * 3.0 + 1.3) + 0.02 * Math.sin(lon * 7.0);
        double lane = Math.exp(-Math.pow((sinLat - laneCentre) / (0.035 + 0.02 * core), 2.0))
            * SkyMath.smoothstep(0.35, 0.65, GalaxyNoise.band(lon, sinLat, 3.7, 9.0));
        double patches = SkyMath.smoothstep(0.55, 0.85, GalaxyNoise.band(lon, sinLat, 6.1, 18.0)) * band;
        double dust = Math.min(1.0, lane * 0.85 + patches * 0.45);
        double warm = Math.min(0.35, core * 0.6);
        double r = 0.66 + (0.96 - 0.66) * warm;
        double g = 0.76 + (0.90 - 0.76) * warm;
        double b = 1.00 + (0.84 - 1.00) * warm;
        double halo = Math.exp(-across * across * 0.18) * 0.05;
        double bright = band * (0.16 + 0.62 * cloud + 0.55 * core) + halo * (0.6 + 0.4 * clumps);
        // The long edges fade to exactly zero so the ribbon has no visible boundary.
        double edge = 1.0 - SkyMath.smoothstep(HALF_LAT * 0.70, HALF_LAT, Math.abs(lat));
        double k = bright * (1.0 - 0.75 * dust) * 0.40 * edge;
        return new float[] {(float) (r * k), (float) (g * k), (float) (b * k)};
    }

    /**
     * Draws the ribbon with ONE/ONE additive blending (set by the caller); brightness lives in the vertex colour.
     *
     * @param rotation celestial rotation only (no view)
     * @param view     the view rotation matrix the vanilla sky uses
     * @param strength overall alpha, 0..1 (night, clearness)
     */
    public void draw(Matrix4f rotation, Matrix4f view, float strength) {
        if (strength <= 0.004f) {
            return;
        }
        ensure();
        for (int i = 0; i < alpha.length; i++) {
            dir.set(baseDirs[i * 3], baseDirs[i * 3 + 1], baseDirs[i * 3 + 2]);
            rotation.transformDirection(dir);
            // Air mass extinction: the band dims steadily toward the horizon instead of being cut at a fixed height.
            alpha[i] = strength * (float) (Math.pow(10.0, -0.10 * (SkyMath.airmass(Math.abs(dir.y)) - 1.0))
                * SkyMath.smoothstep(0.0, 0.25, Math.abs(dir.y)));
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, id);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int s = 0; s < SEGMENTS; s++) {
            for (int r = 0; r < ROWS; r++) {
                vertex(builder, rotation, view, s, r);
                vertex(builder, rotation, view, s + 1, r);
                vertex(builder, rotation, view, s + 1, r + 1);
                vertex(builder, rotation, view, s, r + 1);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private void vertex(BufferBuilder builder, Matrix4f rotation, Matrix4f view, int s, int r) {
        int n = s * (ROWS + 1) + r;
        dir.set(baseDirs[n * 3], baseDirs[n * 3 + 1], baseDirs[n * 3 + 2]);
        rotation.transformDirection(dir);
        pos.set(dir).mul(100.0f);
        view.transformPosition(pos);
        float u = s / (float) SEGMENTS;
        float v = r / (float) ROWS;
        builder.addVertex(pos.x, pos.y, pos.z).setUv(u, v).setColor(alpha[n], alpha[n], alpha[n], 1.0f);
    }

    @Override
    public void close() {
        if (ready) {
            Minecraft.getInstance().getTextureManager().release(id);
            ready = false;
        }
    }
}
