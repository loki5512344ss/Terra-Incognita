/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Based on Better Clouds by Qendolin (https://github.com/Qendolin/better-clouds), version 1.7.1.
 * Modified for Incognita (NeoForge 1.21.1, Mojang mappings, OpenGL 3.3 and 4.3 code paths).
 */

package dev.sborka.incognita.weather.clouds.gl;

import dev.sborka.incognita.weather.clouds.config.CloudConfig;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

/**
 * Capability check for the cloud renderer. The modern path (OpenGL 4.3) allocates its targets with immutable texture
 * storage and submits instances with base-instance draws; the legacy path needs OpenGL 3.3 only and re-points the
 * instance attributes per draw. Both share every shader. Anything older draws no clouds.
 */
public final class GlSupport {
    /** The code path the renderer uses. */
    public enum Path {
        /** No usable OpenGL: no clouds. */
        NONE,
        /** OpenGL 3.3: classic texture allocation, attribute offsets per draw. */
        LEGACY,
        /** OpenGL 4.3: immutable texture storage, base-instance draws. */
        MODERN
    }

    private static Boolean modern;
    private static Boolean legacy;

    private GlSupport() {}

    /** Picks the best path the context supports. Must be called on the render thread with a current context. */
    public static Path path() {
        return choose(hasModern(), hasLegacy(), CloudConfig.forceLegacyPath());
    }

    /** Pure selection rule, separate from the GL queries. */
    public static Path choose(boolean gl43, boolean gl33, boolean forceLegacy) {
        if (gl43 && !(forceLegacy && gl33)) {
            return Path.MODERN;
        }
        return gl33 ? Path.LEGACY : Path.NONE;
    }

    public static boolean isSupported() {
        return path() != Path.NONE;
    }

    private static boolean hasModern() {
        ensureQueried();
        return modern != null && modern;
    }

    private static boolean hasLegacy() {
        ensureQueried();
        return legacy != null && legacy;
    }

    private static void ensureQueried() {
        if (modern == null && GLFW.glfwGetCurrentContext() != MemoryUtil.NULL) {
            GLCapabilities caps = GL.getCapabilities();
            modern = caps.OpenGL43;
            legacy = caps.OpenGL33;
        }
    }
}
