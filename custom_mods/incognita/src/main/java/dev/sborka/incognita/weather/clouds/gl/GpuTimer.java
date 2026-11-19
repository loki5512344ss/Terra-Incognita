package dev.sborka.incognita.weather.clouds.gl;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * GPU time of the cloud pass from {@code GL_TIME_ELAPSED} queries (core since OpenGL 3.3). A small ring of queries
 * is read a few frames late, so the CPU never waits for the GPU. Keeps an exponential average in milliseconds.
 */
public final class GpuTimer implements AutoCloseable {
    private static final int RING = 4;

    private final int[] queries = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int frame;
    private double averageMs = -1.0;
    private boolean active;

    public void begin() {
        if (queries[0] == 0) {
            GL15.glGenQueries(queries);
        }
        int slot = frame % RING;
        if (pending[slot]) {
            collect(slot);
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[slot]);
        active = true;
    }

    public void end() {
        if (!active) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending[frame % RING] = true;
        active = false;
        frame++;
    }

    private void collect(int slot) {
        // The query is RING frames old; if the driver still has not finished it, skip it rather than stall.
        if (GL15.glGetQueryObjecti(queries[slot], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
            pending[slot] = false;
            return;
        }
        long nanos = GL33.glGetQueryObjecti64(queries[slot], GL15.GL_QUERY_RESULT);
        pending[slot] = false;
        double ms = nanos / 1.0e6;
        averageMs = averageMs < 0.0 ? ms : averageMs * 0.95 + ms * 0.05;
    }

    /** Smoothed GPU time of the pass in milliseconds, or a negative value before the first result. */
    public double averageMs() {
        return averageMs;
    }

    @Override
    public void close() {
        if (queries[0] != 0) {
            GL15.glDeleteQueries(queries);
        }
        java.util.Arrays.fill(queries, 0);
        java.util.Arrays.fill(pending, false);
    }
}
