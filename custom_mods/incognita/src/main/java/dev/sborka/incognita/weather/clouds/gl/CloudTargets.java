package dev.sborka.incognita.weather.clouds.gl;

import dev.sborka.incognita.Incognita;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

/**
 * The offscreen G-buffer of the cloud passes, screen sized or half size:
 * <ul>
 *   <li>attachment 0, RGBA8: normal of the nearest cloud surface (xyz) and its height inside the heap (a)</li>
 *   <li>attachment 1, RGBA8: ambient exposure, distance and fog fade, per puff variance, band</li>
 *   <li>attachment 2, R16F: optical depth summed over every puff along the pixel's ray</li>
 *   <li>depth texture: depth of the nearest cloud surface</li>
 * </ul>
 * On the OpenGL 4.3 path the textures use immutable storage; the 3.3 path allocates them the classic way.
 */
public final class CloudTargets implements AutoCloseable {
    public static final int SURFACE = GL30.GL_COLOR_ATTACHMENT0;
    public static final int DETAIL = GL30.GL_COLOR_ATTACHMENT1;
    public static final int THICKNESS = GL30.GL_COLOR_ATTACHMENT2;

    private int fbo;
    private int surface;
    private int detail;
    private int thickness;
    private int depth;
    private int width;
    private int height;
    private GlSupport.Path path = GlSupport.Path.NONE;

    /** Whether the targets match the screen size and the GL path. */
    public boolean matches(int w, int h, GlSupport.Path p) {
        return fbo != 0 && width == w && height == h && path == p;
    }

    /**
     * (Re)creates the targets.
     *
     * @return false when the driver reports the framebuffer as incomplete
     */
    public boolean create(int w, int h, GlSupport.Path p) {
        close();
        if (w <= 0 || h <= 0 || p == GlSupport.Path.NONE) {
            return false;
        }
        width = w;
        height = h;
        path = p;
        fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        surface = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SURFACE);
        detail = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, DETAIL);
        thickness = texture(GL30.GL_R16F, GL11.GL_RED, GL11.GL_FLOAT, THICKNESS);
        // The composite filters the optical depth, which keeps edges smooth when the targets are half size.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        depth = texture(GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT,
            GL30.GL_DEPTH_ATTACHMENT);
        RenderSystem.bindTexture(0);
        GL20.glDrawBuffers(new int[] {SURFACE, DETAIL, THICKNESS});
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            Incognita.LOGGER.warn("Cloud framebuffer incomplete (status {}), clouds disabled", status);
            close();
            return false;
        }
        return true;
    }

    private int texture(int internalFormat, int format, int type, int attachment) {
        int id = GL11.glGenTextures();
        RenderSystem.bindTexture(id);
        if (path == GlSupport.Path.MODERN) {
            GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, internalFormat, width, height);
        } else {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0L);
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, id, 0);
        return id;
    }

    public void bind() {
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        RenderSystem.viewport(0, 0, width, height);
    }

    public int surface() {
        return surface;
    }

    public int detail() {
        return detail;
    }

    public int thickness() {
        return thickness;
    }

    public int depth() {
        return depth;
    }

    @Override
    public void close() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
        }
        for (int id : new int[] {surface, detail, thickness, depth}) {
            if (id != 0) {
                GlStateManager._deleteTexture(id);
            }
        }
        fbo = 0;
        surface = 0;
        detail = 0;
        thickness = 0;
        depth = 0;
        width = 0;
        height = 0;
        path = GlSupport.Path.NONE;
    }
}
