package dev.sborka.incognita.weather.sky.render.body;

import dev.sborka.incognita.weather.sky.atmosphere.SkyLut;
import dev.sborka.incognita.weather.sky.dome.SkyColors;
import dev.sborka.incognita.weather.sky.math.SkyMath;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the sun, the moon and the moon aureole as textured quads with the vanilla position shaders.
 *
 * <p>The glowing quads are split into a small grid, and every grid vertex carries the colour the atmosphere gives
 * light from its own direction: a body low in the sky turns orange and dim, its lower edge more than its upper one,
 * and it sinks into the horizon haze instead of being cut by a line. The moon disc first paints the sky colour over
 * the stars behind it, then adds its own light, so the dark part of the disc never shows stars or a flat blue.
 */
public final class CelestialBodies implements AutoCloseable {
    private static final float DISTANCE = 100.0f;
    /** Angular radius of the moon disc in radians. */
    private static final float MOON_ANGLE = 0.062f;
    private static final float SUN_HALF = 50.0f;
    private static final float HALO_HALF = 48.0f;
    private static final int GRID = 8;

    private final BodyTextures textures = new BodyTextures();
    private final Vector3f dir = new Vector3f();
    private final double[] trans = new double[3];
    private final double[] zenith = new double[3];
    private final double[] lin = new double[3];
    private final float[] sky = new float[3];

    /** How strongly the atmosphere tints the body being drawn: the sun reddens more than the pale moon reads. */
    private double tintPower;

    /**
     * Draws the sun, the moon aureole and the moon.
     *
     * @param rotation  celestial rotation only (vanilla: Y -90 degrees, X by time of day)
     * @param celestial view times rotation
     */
    public void draw(SkyColors.State s, Matrix4f rotation, Matrix4f celestial, int moonPhase) {
        s.lut().transmittance(1.0, zenith);
        float sunY = s.sun()[1];
        float day = (float) SkyMath.smoothstep(-0.10, 0.20, sunY);
        additive();
        float sunVis = (float) (SkyColors.visibleAt(s, sunY) * s.fogLight() * (1.0 - s.flash()));
        if (sunVis > 0.002f && sunY > -0.12f) {
            tintPower = 0.75;
            glowQuad(s.lut(), celestial, rotation, textures.sun(), SUN_HALF, DISTANCE, 0, 0, 1, 1, false, sunVis);
        }
        float moonY = -sunY;
        double fullness = (1.0 + Math.cos(SkyMath.moonPhaseAngle(moonPhase))) * 0.5;
        float moonVis = (float) (SkyColors.visibleAt(s, moonY) * s.fogLight() * (1.0 - 0.62 * day));
        if (moonVis <= 0.002f || moonY < -0.12f) {
            return;
        }
        tintPower = 0.45;
        float halo = moonVis * (float) (fullness * fullness) * (0.22f + 0.5f * s.overcast()) * (1.0f - day);
        if (halo > 0.002f) {
            glowQuad(s.lut(), celestial, rotation, textures.halo(), HALO_HALF, -DISTANCE, 0, 0, 1, 1, false, halo);
        }
        // The disc first takes the colour of the sky behind it, hiding the stars and the Milky Way.
        dir.set(0, -1, 0);
        rotation.transformDirection(dir);
        SkyColors.sky(s, dir.x, dir.y, dir.z, lin, sky);
        float half = DISTANCE * MOON_ANGLE / BodyTextures.MOON_RADIUS;
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, textures.mask());
        RenderSystem.setShaderColor(sky[0], sky[1], sky[2], 1.0f);
        BufferBuilder mask = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        mask.addVertex(celestial, -half, -DISTANCE, half).setUv(1, 1);
        mask.addVertex(celestial, half, -DISTANCE, half).setUv(0, 1);
        mask.addVertex(celestial, half, -DISTANCE, -half).setUv(0, 0);
        mask.addVertex(celestial, -half, -DISTANCE, -half).setUv(1, 0);
        BufferUploader.drawWithShader(mask.buildOrThrow());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Against a bright dusk or day sky the moon light is scaled down so the disc keeps its detail instead of
        // clipping to a flat colour.
        float skyLum = 0.2126f * sky[0] + 0.7152f * sky[1] + 0.0722f * sky[2];
        float light = moonVis * (1.0f - 0.5f * Math.min(1.0f, skyLum * 1.4f));
        int tile = Math.floorMod(moonPhase, 8);
        float u0 = (tile % 4) / 4.0f;
        float v0 = tile >= 4 ? 0.5f : 0.0f;
        additive();
        glowQuad(s.lut(), celestial, rotation, textures.moon(), half, -DISTANCE, u0, v0, u0 + 0.25f, v0 + 0.5f, true,
            light);
    }

    /**
     * A premultiplied glow texture on a grid facing the camera from the celestial pole at {@code y}; each vertex is
     * tinted by the atmosphere transmittance in its direction and faded below the horizon.
     */
    private void glowQuad(SkyLut lut, Matrix4f celestial, Matrix4f rotation, ResourceLocation texture, float half,
                          float y, float u0, float v0, float u1, float v1, boolean mirrorU, float strength) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < GRID; j++) {
            for (int i = 0; i < GRID; i++) {
                vertex(builder, lut, celestial, rotation, half, y, i, j, u0, v0, u1, v1, mirrorU, strength);
                vertex(builder, lut, celestial, rotation, half, y, i + 1, j, u0, v0, u1, v1, mirrorU, strength);
                vertex(builder, lut, celestial, rotation, half, y, i + 1, j + 1, u0, v0, u1, v1, mirrorU, strength);
                vertex(builder, lut, celestial, rotation, half, y, i, j + 1, u0, v0, u1, v1, mirrorU, strength);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private void vertex(BufferBuilder builder, SkyLut lut, Matrix4f celestial, Matrix4f rotation, float half, float y,
                        int i, int j, float u0, float v0, float u1, float v1, boolean mirrorU, float strength) {
        float fx = i / (float) GRID;
        float fz = j / (float) GRID;
        float x = (fx * 2 - 1) * half;
        float z = (fz * 2 - 1) * half;
        // The moon is seen from below its quad, so its texture is mirrored to keep the phases the right way round.
        float ua = mirrorU ? u1 : u0;
        float ub = mirrorU ? u0 : u1;
        float u = ua + (ub - ua) * fx;
        float v = v0 + (v1 - v0) * fz;
        dir.set(x, y, z).normalize();
        rotation.transformDirection(dir);
        lut.transmittance(Math.max(dir.y, -0.02), trans);
        float horizon = (float) SkyMath.smoothstep(-0.018, 0.012, dir.y);
        float r = (float) Math.pow(trans[0] / zenith[0], tintPower) * strength * horizon;
        float g = (float) Math.pow(trans[1] / zenith[1], tintPower) * strength * horizon;
        float b = (float) Math.pow(trans[2] / zenith[2], tintPower) * strength * horizon;
        builder.addVertex(celestial, x, y, z).setUv(u, v).setColor(r, g, b, 1.0f);
    }

    /** Additive blend for textures whose brightness is stored premultiplied in the colour. */
    public static void additive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }

    @Override
    public void close() {
        textures.close();
    }
}
