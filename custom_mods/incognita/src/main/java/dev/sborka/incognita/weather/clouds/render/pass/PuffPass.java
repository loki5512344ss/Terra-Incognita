package dev.sborka.incognita.weather.clouds.render.pass;

import dev.sborka.incognita.weather.clouds.gl.Shader;

import java.io.IOException;
import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;

/**
 * The puff program, used by both coverage passes: the nearest surface pass (G-buffer and depth) and the additive
 * thickness pass. Holds the uniform locations and sets the per frame, per pass and per band values.
 */
public final class PuffPass implements AutoCloseable {
    /** Texture unit of the scene depth. */
    public static final int SCENE_DEPTH_UNIT = 0;

    private Shader shader;
    private Shader.Uniform viewProj;
    private Shader.Uniform origin;
    private Shader.Uniform coverShift;
    private Shader.Uniform time;
    private Shader.Uniform curvature;
    private Shader.Uniform depthRange;
    private Shader.Uniform camForward;
    private Shader.Uniform fade;
    private Shader.Uniform pass;
    private Shader.Uniform band;
    private Shader.Uniform resScale;

    public void load(ResourceManager manager) throws IOException {
        close();
        shader = new Shader(manager, "clouds_puff", Map.of());
        shader.bind();
        shader.uniform("u_scene_depth").setInt(SCENE_DEPTH_UNIT);
        viewProj = shader.uniform("u_view_proj");
        origin = shader.uniform("u_origin");
        coverShift = shader.uniform("u_cover_shift");
        time = shader.uniform("u_time");
        curvature = shader.uniform("u_curvature");
        depthRange = shader.uniform("u_depth_range");
        camForward = shader.uniform("u_cam_forward");
        fade = shader.uniform("u_fade");
        pass = shader.uniform("u_pass");
        band = shader.uniform("u_band");
        resScale = shader.uniform("u_res_scale");
    }

    public boolean ready() {
        return shader != null && shader.isComplete();
    }

    /** Binds the program and sets everything that is the same for all bands and both passes. */
    public void beginFrame(Matrix4f cameraViewProj, FrameView view) {
        shader.bind();
        viewProj.setMat4(cameraViewProj);
        time.setFloat(view.seconds());
        curvature.setFloat(view.curvature());
        depthRange.setVec2(view.near(), view.far());
        camForward.setVec3(view.forwardX(), view.forwardY(), view.forwardZ());
        fade.setVec4(view.drawDistance(), view.fogStart(), view.fogEnd(), view.fogHide());
        resScale.setFloat(view.resolutionScale());
    }

    /** 0 for the nearest surface pass, 1 for the thickness pass. */
    public void setPass(int index) {
        pass.setFloat(index);
    }

    public void setBand(int index, float originX, float originY, float originZ, float shift) {
        band.setFloat(index);
        origin.setVec3(originX, originY, originZ);
        coverShift.setFloat(shift);
    }

    @Override
    public void close() {
        if (shader != null) {
            shader.close();
        }
        shader = null;
    }

    /**
     * Per frame view values of the puff passes.
     *
     * @param seconds      animation time
     * @param curvature    vertical drop per squared horizontal block
     * @param near         near plane distance
     * @param far          far plane distance
     * @param drawDistance cloud draw distance
     * @param fogStart     start of the fog distance fade
     * @param fogEnd       end of the fog distance fade
     * @param fogHide      share hidden by the fog regardless of distance
     * @param resolutionScale 1 for full size cloud targets, 2 for half size
     */
    public record FrameView(float seconds, float curvature, float near, float far, float forwardX, float forwardY,
                            float forwardZ, float drawDistance, float fogStart, float fogEnd, float fogHide, float resolutionScale) {}
}
