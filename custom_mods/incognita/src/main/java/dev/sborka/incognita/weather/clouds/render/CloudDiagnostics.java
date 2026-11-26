package dev.sborka.incognita.weather.clouds.render;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.weather.clouds.gl.GlSupport;

import java.util.HashSet;
import java.util.Set;
import org.lwjgl.opengl.GL11;

/**
 * Writes down why clouds may be missing. OpenGL does not report errors by itself and several reasons not to draw are
 * silent, so a player's log said nothing about an empty sky. Every distinct message is logged once.
 */
final class CloudDiagnostics {
    private static final int MAX_ERRORS_PER_STAGE = 8;
    private final Set<String> seen = new HashSet<>();

    /** A reason the renderer draws nothing this frame. */
    void skipped(String reason) {
        if (seen.add(reason)) {
            Incognita.LOGGER.info("Clouds are not drawn: {}", reason);
        }
    }

    /** Throws away errors left over from earlier rendering, so they are not blamed on the clouds. */
    void clearErrors() {
        int left = MAX_ERRORS_PER_STAGE;
        while (left > 0 && GL11.glGetError() != GL11.GL_NO_ERROR) {
            left--;
        }
    }

    void checkStage(String stage) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR && seen.add(stage + error)) {
            Incognita.LOGGER.warn("Cloud rendering: OpenGL error 0x{} after {}", Integer.toHexString(error), stage);
        }
    }

    /** One line with everything needed to understand a report from another machine. */
    void firstClouds(GlSupport.Path path, int visiblePuffs, int width, int height) {
        if (visiblePuffs > 0 && seen.add("first")) {
            Incognita.LOGGER.info("Clouds drawn: path {}, visible puffs {}, targets {}x{}, GPU '{}', OpenGL '{}'",
                path, visiblePuffs, width, height, GL11.glGetString(GL11.GL_RENDERER),
                GL11.glGetString(GL11.GL_VERSION));
        }
    }
}
