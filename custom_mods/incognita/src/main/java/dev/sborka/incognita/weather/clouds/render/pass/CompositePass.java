package dev.sborka.incognita.weather.clouds.render.pass;

import dev.sborka.incognita.weather.clouds.gl.CloudTargets;
import dev.sborka.incognita.weather.clouds.gl.Shader;
import dev.sborka.incognita.weather.clouds.render.band.BoxMesh;
import dev.sborka.incognita.weather.clouds.shade.CloudLight;

import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The composite: lights every cloud pixel once from the G-buffer and blends it into the scene target, writing the
 * cloud depth so later passes (rain, particles) sort against the clouds.
 */
public final class CompositePass implements AutoCloseable {
    private Shader shader;
    private Shader.Uniform viewProj;
    private Shader.Uniform lightDir;
    private Shader.Uniform sun;
    private Shader.Uniform skyTop;
    private Shader.Uniform skyBottom;
    private Shader.Uniform haze;
    private Shader.Uniform params;
    private Shader.Uniform sunHeight;
    private Shader.Uniform resScale;
    private Shader.Uniform fogHide;
    private int vao;
    private int vbo;

    public void load(ResourceManager manager) throws IOException {
        close();
        shader = new Shader(manager, "clouds_shading", Map.of());
        shader.bind();
        shader.uniform("u_surface").setInt(1);
        shader.uniform("u_detail").setInt(2);
        shader.uniform("u_thickness").setInt(3);
        shader.uniform("u_depth").setInt(4);
        viewProj = shader.uniform("u_view_proj");
        lightDir = shader.uniform("u_light_dir");
        sun = shader.uniform("u_sun");
        skyTop = shader.uniform("u_sky_top");
        skyBottom = shader.uniform("u_sky_bottom");
        haze = shader.uniform("u_haze");
        params = shader.uniform("u_params");
        sunHeight = shader.uniform("u_sun_height");
        resScale = shader.uniform("u_res_scale");
        fogHide = shader.uniform("u_fog_hide");
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BoxMesh.skyCube(), GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        GL30.glBindVertexArray(0);
    }

    public boolean ready() {
        return shader != null && shader.isComplete() && vao != 0;
    }

    /**
     * Draws the composite into the bound target.
     *
     * @param light    colours of this frame
     * @param light3   direction of the direct light (x, y, z)
     * @param fog      haze colour (r, g, b)
     * @param sigma    extinction per unit of optical depth
     * @param sunSin   sine of the sun elevation
     * @param scale    screen size relative to the cloud targets
     * @param hide     share hidden by the dense fog
     */
    public void draw(Matrix4f rotationViewProj, CloudTargets targets, CloudLight.Result light, float[] light3,
                     float[] fog, float sigma, float sunSin, float scale, float hide) {
        bindTexture(1, targets.surface());
        bindTexture(2, targets.detail());
        bindTexture(3, targets.thickness());
        bindTexture(4, targets.depth());
        shader.bind();
        viewProj.setMat4(rotationViewProj);
        lightDir.setVec3(light3[0], light3[1], light3[2]);
        set(sun, light.sunColor());
        set(skyTop, light.skyTop());
        set(skyBottom, light.skyBottom());
        haze.setVec3(fog[0], fog[1], fog[2]);
        params.setVec4(sigma, light.opacity(), light.greyness(), light.sunsetness());
        sunHeight.setFloat(sunSin);
        resScale.setFloat(scale);
        fogHide.setFloat(hide);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, BoxMesh.SKY_CUBE_VERTICES);
    }

    private static void set(Shader.Uniform uniform, CloudLight.Rgb c) {
        uniform.setVec3(c.r(), c.g(), c.b());
    }

    private static void bindTexture(int unit, int id) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        RenderSystem.bindTexture(id);
    }

    @Override
    public void close() {
        if (shader != null) {
            shader.close();
        }
        shader = null;
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
        }
        vbo = 0;
        vao = 0;
    }
}
