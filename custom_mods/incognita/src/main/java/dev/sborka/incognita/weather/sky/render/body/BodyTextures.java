package dev.sborka.incognita.weather.sky.render.body;

import dev.sborka.incognita.weather.sky.math.SkyMath;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Textures of the sun, the moon and its aureole, painted on the CPU once. Nothing is loaded from resource packs and no
 * shader is involved: the sky draws them as plain textured quads.
 *
 * <p>The moon comes as two sheets. The light sheet (4 x 2 phase tiles) stores the light of the surface premultiplied
 * by the disc coverage and is drawn additively; the mask is the disc coverage in alpha and is drawn first in the colour
 * of the sky behind the moon, so stars and the Milky Way never shine through the disc. Both use the same disc, so the
 * edge carries no ring. The tiles are sized so a moon on screen is drawn at about one texel per pixel: a smaller
 * texture would blur, a larger one would sparkle along the rim without mipmaps.
 */
public final class BodyTextures implements AutoCloseable {
    /** Size of one moon phase tile in pixels; the sheet holds 4 x 2 tiles like the vanilla one. */
    private static final int TILE = 160;
    private static final int SUN_SIZE = 256;
    private static final int HALO_SIZE = 128;
    /** Radius of the moon disc inside its tile (0..1), the rest is empty margin for the antialiased rim. */
    public static final float MOON_RADIUS = 0.90f;
    /** Radius of the sun disc inside the sun sprite (0..1). */
    public static final float SUN_DISC = 0.085f;

    private final ResourceLocation moonId = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_moon");
    private final ResourceLocation maskId = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_moon_mask");
    private final ResourceLocation sunId = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_sun");
    private final ResourceLocation haloId = ResourceLocation.fromNamespaceAndPath("incognita", "dynamic/sky_halo");
    private boolean ready;

    public ResourceLocation moon() {
        ensure();
        return moonId;
    }

    public ResourceLocation mask() {
        ensure();
        return maskId;
    }

    public ResourceLocation sun() {
        ensure();
        return sunId;
    }

    public ResourceLocation halo() {
        ensure();
        return haloId;
    }

    private void ensure() {
        if (ready) {
            return;
        }
        ready = true;
        register(moonId, paintMoon());
        register(maskId, paintMask());
        register(sunId, paintSun());
        register(haloId, paintHalo());
    }

    private static void register(ResourceLocation id, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.setFilter(true, false);
    }

    @Override
    public void close() {
        if (!ready) {
            return;
        }
        for (ResourceLocation id : new ResourceLocation[] {moonId, maskId, sunId, haloId}) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
        ready = false;
    }

    private static int pack(float r, float g, float b, float a) {
        return (to8(a) << 24) | (to8(b) << 16) | (to8(g) << 8) | to8(r);
    }

    private static int to8(float v) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, v)) * 255.0f);
    }

    private static float hash(int x, int y) {
        return SkyMath.toUnit(SkyMath.pcg(x + SkyMath.pcg(y)));
    }

    private static float noise(double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float fx = (float) (x - ix);
        float fy = (float) (y - iy);
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy);
        float b = hash(ix + 1, iy);
        float c = hash(ix, iy + 1);
        float d = hash(ix + 1, iy + 1);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static float smooth(float e0, float e1, float x) {
        return (float) SkyMath.smoothstep(e0, e1, x);
    }

    /** Disc coordinate of a tile pixel, 1 at the rim. */
    private static float discCoord(int i) {
        return ((i + 0.5f) / TILE * 2 - 1) / MOON_RADIUS;
    }

    /** Antialiased coverage of the disc: a rim about 1.2 texels wide, the same in the light sheet and the mask. */
    private static float coverage(float r) {
        float aa = 1.2f / (TILE * 0.5f * MOON_RADIUS);
        return 1 - smooth(1 - aa, 1 + aa, r);
    }

    /**
     * Maria of the near side as soft ellipses: centre x, centre y (disc coordinates, y down), radii and darkness.
     * Positions follow a photograph of the full moon: Imbrium and Serenitatis in the north, Procellarum on the left,
     * Tranquillitatis and Fecunditatis on the right, Crisium near the right limb, Nubium and Humorum in the south.
     */
    private static final float[][] MARIA = {
        {-0.18f, -0.52f, 0.34f, 0.24f, 0.74f}, {0.30f, -0.48f, 0.20f, 0.17f, 0.70f},
        {0.42f, -0.18f, 0.27f, 0.20f, 0.52f}, {0.80f, -0.26f, 0.10f, 0.08f, 0.58f},
        {-0.60f, -0.12f, 0.30f, 0.46f, 0.52f}, {-0.36f, 0.28f, 0.19f, 0.14f, 0.40f},
        {0.42f, 0.08f, 0.10f, 0.09f, 0.36f}, {0.62f, 0.14f, 0.12f, 0.18f, 0.40f},
        {-0.05f, -0.05f, 0.18f, 0.12f, 0.30f}
    };

    /** Albedo of the lunar surface seen from the Earth: bright highlands, dark maria, craters and ray systems. */
    private static float[] albedoMap() {
        float[] albedo = new float[TILE * TILE];
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                float px = discCoord(i);
                float py = discCoord(j);
                // Wobble the sample point at two scales so the shores are ragged and the seas fray into the highlands.
                float wx = px + (noise(px * 3 + 1.3, py * 3 + 9.1) - 0.5f) * 0.30f
                    + (noise(px * 9 + 4.1, py * 9 + 1.7) - 0.5f) * 0.10f;
                float wy = py + (noise(px * 3 + 5.7, py * 3 + 2.2) - 0.5f) * 0.30f
                    + (noise(px * 9 + 8.3, py * 9 + 6.4) - 0.5f) * 0.10f;
                float dark = 0;
                for (float[] sea : MARIA) {
                    float dx = (wx - sea[0]) / sea[2];
                    float dy = (wy - sea[1]) / sea[3];
                    float cover = 1 - smooth(0.15f, 1.05f, (float) Math.sqrt(dx * dx + dy * dy));
                    dark = Math.max(dark, cover * sea[4]);
                }
                // Seas are not flat: slightly lighter streaks inside them.
                dark *= 0.80f + 0.20f * noise(px * 6 + 12, py * 6 + 3);
                float mottling = 0.88f + 0.12f * noise(px * 9 + 3, py * 9 + 8);
                float detail = noise(px * 17 + 11, py * 17 + 5) * 0.6f + noise(px * 34 + 2, py * 34 + 7) * 0.4f;
                albedo[j * TILE + i] = (1 - dark) * mottling * (0.92f + 0.08f * detail);
            }
        }
        addCraters(albedo);
        return albedo;
    }

    /**
     * The 4 x 2 moon light sheet, premultiplied by the disc coverage. Every tile shows the same surface lit from a
     * different side with Lommel-Seeliger reflectance (the flat looking full moon and the gradual darkening toward
     * the terminator of the real one), a terminator softened over the lunar relief, and earthshine on the night side:
     * strongest at the crescent, when the Earth seen from the moon is nearly full.
     */
    private static NativeImage paintMoon() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TILE * 4, TILE * 2, false);
        float[] albedo = albedoMap();
        for (int phase = 0; phase < 8; phase++) {
            double phi = SkyMath.moonPhaseAngle(phase);
            // Light direction in the disc frame: x to the right, z toward the viewer. Phases 1..3 (waning) are lit
            // on the left, 5..7 (waxing) on the right; phase 0 is full, 4 is new.
            float lx = (float) Math.sin(phi);
            float lz = (float) Math.cos(phi);
            float fullness = (float) ((1 + Math.cos(phi)) * 0.5);
            float earthshine = 0.010f + 0.026f * (1 - fullness) * (1 - fullness);
            int ox = (phase % 4) * TILE;
            int oy = (phase / 4) * TILE;
            for (int j = 0; j < TILE; j++) {
                for (int i = 0; i < TILE; i++) {
                    float px = discCoord(i);
                    float py = discCoord(j);
                    float r = (float) Math.sqrt(px * px + py * py);
                    float cover = coverage(r);
                    if (cover <= 0) {
                        image.setPixelRGBA(ox + i, oy + j, pack(0, 0, 0, 1));
                        continue;
                    }
                    float z = (float) Math.sqrt(Math.max(1 - r * r, 0));
                    float a = albedo[j * TILE + i];
                    float incidence = px * lx + z * lz;
                    // Relief makes the terminator ragged and soft rather than a drawn line; it fades out toward the
                    // limb, so the rim of a new moon never lights up.
                    float relief = (a - 0.85f) * 0.35f * z;
                    float lit = smooth(-0.05f, 0.10f, incidence + relief);
                    float mu0 = Math.max(incidence + relief, 0.0f);
                    float lommel = 2 * mu0 / (mu0 + Math.max(z, 0.05f));
                    float day = lit * Math.min(1.0f, lommel) * a;
                    float night = (1 - lit) * earthshine * a;
                    float cr = (day * 1.00f + night * 0.80f) * cover;
                    float cg = (day * 0.97f + night * 0.90f) * cover;
                    float cb = (day * 0.92f + night * 1.05f) * cover;
                    image.setPixelRGBA(ox + i, oy + j, pack(cr, cg, cb, 1));
                }
            }
        }
        return image;
    }

    /** Disc coverage in alpha (white colour), one tile: the sky colour is painted through it behind the moon. */
    private static NativeImage paintMask() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, TILE, TILE, false);
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                float px = discCoord(i);
                float py = discCoord(j);
                // A hair wider than the light disc, so no sliver of star field survives at the rim.
                float cover = coverage((float) Math.sqrt(px * px + py * py) * 0.992f);
                image.setPixelRGBA(i, j, pack(1, 1, 1, cover));
            }
        }
        return image;
    }

    /** Subtle craters: a shadowed floor with a slightly brighter rim, fixed positions so every phase matches. */
    private static void addCraters(float[] albedo) {
        for (int c = 0; c < 40; c++) {
            float cx = hash(c, 101) * 1.7f - 0.85f;
            float cy = hash(c, 202) * 1.7f - 0.85f;
            float radius = 0.025f + 0.07f * hash(c, 303) * hash(c, 404);
            float strength = 0.04f + 0.05f * hash(c, 505);
            for (int j = 0; j < TILE; j++) {
                for (int i = 0; i < TILE; i++) {
                    float px = discCoord(i);
                    float py = discCoord(j);
                    float d = (float) Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy)) / radius;
                    if (d > 1.4f) {
                        continue;
                    }
                    float floor = (1 - smooth(0.6f, 1.0f, d)) * strength;
                    float rim = smooth(0.85f, 1.0f, d) * (1 - smooth(1.0f, 1.4f, d)) * strength * 0.7f;
                    albedo[j * TILE + i] += rim - floor;
                }
            }
        }
        // Two bright rayed craters like Tycho (low left) and Copernicus (upper left): a bright core and long rays.
        rayedCrater(albedo, -0.26f, 0.54f, 1.0f);
        rayedCrater(albedo, -0.49f, -0.64f, 0.55f);
    }

    private static void rayedCrater(float[] albedo, float cx, float cy, float strength) {
        for (int j = 0; j < TILE; j++) {
            for (int i = 0; i < TILE; i++) {
                float dx = discCoord(i) - cx;
                float dy = discCoord(j) - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                double angle = Math.atan2(dy, dx);
                float rays = noise(angle * 6.0 + 20 * cx, 3.0 + cy);
                rays = rays * rays * rays;
                float reach = (float) Math.exp(-d * 2.4);
                float core = 0.22f * (float) Math.exp(-d * d * 1400.0) + 0.05f * (float) Math.exp(-d * 9.0);
                albedo[j * TILE + i] += strength * (core + 0.34f * rays * reach * Math.min(1.0f, d * 8.0f));
            }
        }
    }

    /**
     * White sun sprite, premultiplied for additive drawing: a solid disc with a slight limb darkening, a tight glow
     * and a wide soft aureole, all fading to zero well inside the square.
     */
    private static NativeImage paintSun() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SUN_SIZE, SUN_SIZE, false);
        for (int j = 0; j < SUN_SIZE; j++) {
            for (int i = 0; i < SUN_SIZE; i++) {
                float px = (i + 0.5f) / SUN_SIZE * 2 - 1;
                float py = (j + 0.5f) / SUN_SIZE * 2 - 1;
                float r = (float) Math.sqrt(px * px + py * py);
                float aa = 1.5f / (SUN_SIZE * 0.5f);
                float disc = 1 - smooth(SUN_DISC - aa, SUN_DISC + aa, r);
                float mu = (float) Math.sqrt(Math.max(0, 1 - (r / SUN_DISC) * (r / SUN_DISC)));
                float limb = 0.82f + 0.18f * mu;
                float glow = 0.42f * (float) Math.exp(-(r / 0.075) * (r / 0.075))
                    + 0.16f * (float) Math.exp(-(r / 0.20) * (r / 0.20))
                    + 0.05f * (float) Math.exp(-(r / 0.48) * (r / 0.48));
                float edge = 1 - smooth(0.80f, 1.0f, r);
                float k = Math.max(disc * limb * 1.1f, glow) * edge;
                image.setPixelRGBA(i, j, pack(k, k * 0.98f, k * 0.94f, 1));
            }
        }
        return image;
    }

    /** Soft aureole of the moon in thin haze, premultiplied: a smooth radial falloff to zero, no edge and no ring. */
    private static NativeImage paintHalo() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, HALO_SIZE, HALO_SIZE, false);
        for (int j = 0; j < HALO_SIZE; j++) {
            for (int i = 0; i < HALO_SIZE; i++) {
                float px = (i + 0.5f) / HALO_SIZE * 2 - 1;
                float py = (j + 0.5f) / HALO_SIZE * 2 - 1;
                float r = (float) Math.sqrt(px * px + py * py);
                float k = (0.30f * (float) Math.exp(-(r / 0.14) * (r / 0.14))
                    + 0.08f * (float) Math.exp(-(r / 0.40) * (r / 0.40))) * (1 - smooth(0.65f, 1.0f, r));
                image.setPixelRGBA(i, j, pack(k * 0.80f, k * 0.88f, k, 1));
            }
        }
        return image;
    }
}
