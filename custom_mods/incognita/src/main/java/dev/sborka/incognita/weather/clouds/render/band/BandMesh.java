package dev.sborka.incognita.weather.clouds.render.band;

import dev.sborka.incognita.weather.clouds.field.mesh.PuffWriter;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL42;

/**
 * GPU side of one band: a VAO with the shared puff box (attribute 0) and the instance buffer (attributes 1-4, one
 * vec4 each, see {@link PuffWriter}). Draws runs of instances either with base-instance draws (OpenGL 4.2+) or by
 * re-pointing the instance attributes at the first instance of each run (OpenGL 3.3).
 */
public final class BandMesh implements AutoCloseable {
    private static final int STRIDE = PuffWriter.FLOATS_PER_PUFF * Float.BYTES;
    private static final int ATTRIBUTES = 4;

    private final int vao;
    private final int boxVbo;
    private final int instanceVbo;
    private int puffs;

    public BandMesh() {
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        boxVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, boxVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BoxMesh.puffBox(), GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);

        instanceVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, STRIDE, GL15.GL_DYNAMIC_DRAW);
        for (int a = 0; a < ATTRIBUTES; a++) {
            GL20.glEnableVertexAttribArray(1 + a);
            GL33.glVertexAttribDivisor(1 + a, 1);
        }
        pointInstances(0);
        GL30.glBindVertexArray(0);
    }

    /** Replaces the instance data. Render thread. */
    public void upload(float[] data, int count) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        // A fresh store each time: the driver need not wait for frames that still read the old one.
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        puffs = count;
    }

    public int puffs() {
        return puffs;
    }

    public void bind() {
        GL30.glBindVertexArray(vao);
    }

    /**
     * Draws runs of instances; the VAO must be bound.
     *
     * @param baseInstance true to use base-instance draws (OpenGL 4.2+)
     */
    public void draw(int[] starts, int[] counts, int runs, boolean baseInstance) {
        for (int i = 0; i < runs; i++) {
            if (baseInstance) {
                GL42.glDrawArraysInstancedBaseInstance(GL11.GL_TRIANGLE_STRIP, 0, BoxMesh.PUFF_BOX_VERTICES,
                    counts[i], starts[i]);
            } else {
                pointInstances(starts[i]);
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, BoxMesh.PUFF_BOX_VERTICES, counts[i]);
            }
        }
        if (!baseInstance && runs > 0) {
            pointInstances(0);
        }
    }

    private void pointInstances(int first) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
        long base = (long) first * STRIDE;
        for (int a = 0; a < ATTRIBUTES; a++) {
            GL20.glVertexAttribPointer(1 + a, 4, GL11.GL_FLOAT, false, STRIDE, base + (long) a * 4 * Float.BYTES);
        }
    }

    @Override
    public void close() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(boxVbo);
        GL15.glDeleteBuffers(instanceVbo);
    }
}
