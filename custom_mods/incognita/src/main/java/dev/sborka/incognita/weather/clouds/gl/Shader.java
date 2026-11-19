/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Based on Better Clouds by Qendolin (https://github.com/Qendolin/better-clouds), version 1.7.1.
 * Modified for Incognita (NeoForge 1.21.1, Mojang mappings, OpenGL 3.3 and 4.3 code paths).
 */

package dev.sborka.incognita.weather.clouds.gl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** A linked vertex and fragment program loaded from the resource manager, with textual define substitution. */
public final class Shader implements AutoCloseable {
    private int programId;

    public Shader(ResourceManager manager, String name, Map<String, String> defs) throws IOException {
        ResourceLocation vsh = ResourceLocation.fromNamespaceAndPath("incognita", "shaders/core/" + name + ".vsh");
        ResourceLocation fsh = ResourceLocation.fromNamespaceAndPath("incognita", "shaders/core/" + name + ".fsh");
        int vertex = compile(GL20.GL_VERTEX_SHADER, vsh, manager, defs);
        int fragment;
        try {
            fragment = compile(GL20.GL_FRAGMENT_SHADER, fsh, manager, defs);
        } catch (IOException | RuntimeException e) {
            GL20.glDeleteShader(vertex);
            throw e;
        }
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertex);
        GL20.glAttachShader(programId, fragment);
        GL20.glLinkProgram(programId);
        boolean linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
        String log = linked ? "" : GL20.glGetProgramInfoLog(programId);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (!linked) {
            GL20.glDeleteProgram(programId);
            programId = 0;
            throw new IllegalStateException("Failed to link cloud program " + name + ": " + log);
        }
    }

    private static int compile(int type, ResourceLocation id, ResourceManager manager, Map<String, String> defs)
        throws IOException {
        String source;
        try (InputStream in = manager.getResourceOrThrow(id).open()) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        for (Map.Entry<String, String> def : defs.entrySet()) {
            source = source.replace(def.getKey(), def.getValue());
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 32768).trim();
            GL20.glDeleteShader(shader);
            throw new IOException("Could not compile cloud shader " + id + ":\n" + log);
        }
        return shader;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public boolean isComplete() {
        return programId > 0;
    }

    public Uniform uniform(String name) {
        return new Uniform(GL20.glGetUniformLocation(programId, name));
    }

    @Override
    public void close() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
        programId = 0;
    }

    /** A uniform location; a missing uniform (optimized out by the driver) silently ignores every write. */
    public static final class Uniform {
        private final int location;

        private Uniform(int location) {
            this.location = location;
        }

        public void setInt(int v) {
            if (location >= 0) GL20.glUniform1i(location, v);
        }

        public void setFloat(float v) {
            if (location >= 0) GL20.glUniform1f(location, v);
        }

        public void setVec2(float x, float y) {
            if (location >= 0) GL20.glUniform2f(location, x, y);
        }

        public void setVec3(float x, float y, float z) {
            if (location >= 0) GL20.glUniform3f(location, x, y, z);
        }

        public void setVec4(float x, float y, float z, float w) {
            if (location >= 0) GL20.glUniform4f(location, x, y, z, w);
        }

        public void setMat4(Matrix4f m) {
            if (location < 0) return;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buf = stack.mallocFloat(16);
                m.get(buf);
                GL20.glUniformMatrix4fv(location, false, buf);
            }
        }
    }

}
