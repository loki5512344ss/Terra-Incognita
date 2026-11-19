package dev.sborka.incognita.weather.sky.render.dome;

import dev.sborka.incognita.weather.sky.dome.SkyColors;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The sky gradient as a coloured dome mesh drawn with the vanilla position-colour shader. The colours are computed on
 * the CPU for a grid of rings and meridians (dense near the horizon, where the twilight colours change fastest) and
 * the GPU interpolates them. The grid of this frame also answers how bright the sky is in a direction, which the
 * stars use to decide whether they can be seen.
 */
public final class SkyDome {
    /** Elevation angles of the rings in degrees, nadir to zenith (a full sphere, so the sky has no edge); dense around the horizon. */
    private static final float[] RING_DEGREES = {
        -90, -65, -40, -20, -10, -5, -2.5f, -1, 0, 0.7f, 1.5f, 2.4f, 3.5f, 4.8f, 6.3f, 8, 10, 12.5f, 15.5f, 19, 23, 28, 34, 41,
        49, 58, 68, 79, 90
    };
    private static final int MERIDIANS = 96;
    private static final float RADIUS = 100.0f;
    private static final int RINGS = RING_DEGREES.length;

    private final float[] colors = new float[RINGS * MERIDIANS * 3];
    private final float[] luminance = new float[RINGS * MERIDIANS];
    private final float[] dirs = new float[RINGS * MERIDIANS * 3];
    private final float[] ringSin = new float[RINGS];
    private final float[] tmp = new float[3];
    private final double[] lin = new double[3];
    private final Vector3f pos = new Vector3f();

    public SkyDome() {
        for (int r = 0; r < RINGS; r++) {
            double el = Math.toRadians(RING_DEGREES[r]);
            ringSin[r] = (float) Math.sin(el);
            for (int m = 0; m < MERIDIANS; m++) {
                double az = m * Math.PI * 2.0 / MERIDIANS;
                int i = (r * MERIDIANS + m) * 3;
                dirs[i] = (float) (Math.cos(el) * Math.cos(az));
                dirs[i + 1] = (float) Math.sin(el);
                dirs[i + 2] = (float) (Math.cos(el) * Math.sin(az));
            }
        }
    }

    /** Evaluates the colours of this frame; call before {@link #draw} and {@link #luminanceAt}. */
    public void update(SkyColors.State state) {
        for (int i = 0; i < RINGS * MERIDIANS; i++) {
            SkyColors.sky(state, dirs[i * 3], dirs[i * 3 + 1], dirs[i * 3 + 2], lin, tmp);
            colors[i * 3] = tmp[0];
            colors[i * 3 + 1] = tmp[1];
            colors[i * 3 + 2] = tmp[2];
            luminance[i] = 0.2126f * tmp[0] + 0.7152f * tmp[1] + 0.0722f * tmp[2];
        }
    }

    /** Display luminance (0..1) of the sky of this frame in a world direction, interpolated from the grid. */
    public double luminanceAt(double x, double y, double z) {
        int r = 0;
        while (r < RINGS - 2 && ringSin[r + 1] < y) {
            r++;
        }
        double fr = Math.max(0.0, Math.min(1.0, (y - ringSin[r]) / (ringSin[r + 1] - ringSin[r])));
        double az = Math.atan2(z, x);
        if (az < 0) {
            az += Math.PI * 2.0;
        }
        double fm = az / (Math.PI * 2.0) * MERIDIANS;
        int m0 = Math.min((int) fm, MERIDIANS - 1);
        int m1 = (m0 + 1) % MERIDIANS;
        double t = fm - m0;
        double low = luminance[r * MERIDIANS + m0] * (1 - t) + luminance[r * MERIDIANS + m1] * t;
        double high = luminance[(r + 1) * MERIDIANS + m0] * (1 - t) + luminance[(r + 1) * MERIDIANS + m1] * t;
        return low + (high - low) * fr;
    }

    /**
     * Draws the dome. The depth test is expected to be off and blending off; the view matrix is the rotation only
     * matrix the vanilla sky uses.
     */
    public void draw(Matrix4f view) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int r = 0; r < RINGS - 1; r++) {
            for (int m = 0; m < MERIDIANS; m++) {
                int m2 = (m + 1) % MERIDIANS;
                vertex(builder, view, r, m);
                vertex(builder, view, r, m2);
                vertex(builder, view, r + 1, m2);
                vertex(builder, view, r + 1, m);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private void vertex(BufferBuilder builder, Matrix4f view, int ring, int meridian) {
        int i = (ring * MERIDIANS + meridian) * 3;
        pos.set(dirs[i] * RADIUS, dirs[i + 1] * RADIUS, dirs[i + 2] * RADIUS);
        view.transformPosition(pos);
        builder.addVertex(pos.x, pos.y, pos.z).setColor(colors[i], colors[i + 1], colors[i + 2], 1.0f);
    }
}
