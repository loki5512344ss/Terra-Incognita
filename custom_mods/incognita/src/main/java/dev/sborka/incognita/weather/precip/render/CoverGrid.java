package dev.sborka.incognita.weather.precip.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Height of the first rain-blocking surface above every column around the camera, as a small float texture. The
 * shader compares each drop with it, so rain stops at roofs, trees and terrain and nothing falls indoors. The grid is
 * rebuilt every few ticks into preallocated buffers, so there is no per-frame allocation.
 */
final class CoverGrid implements AutoCloseable {
    static final int SIZE = 48;
    private static final int REFRESH_TICKS = 3;

    private final FloatBuffer buffer = MemoryUtil.memAllocFloat(SIZE * SIZE);
    private int texture;
    private int originX;
    private int originZ;
    private long lastTick = Long.MIN_VALUE;

    int originX() {
        return originX;
    }

    int originZ() {
        return originZ;
    }

    /** Refreshes the grid when it is stale. Must run on the render thread with a current GL context. */
    void update(ClientLevel level, Vec3 camera) {
        long tick = level.getGameTime();
        int cx = (int) Math.floor(camera.x) - SIZE / 2;
        int cz = (int) Math.floor(camera.z) - SIZE / 2;
        boolean moved = Math.abs(cx - originX) > 3 || Math.abs(cz - originZ) > 3;
        if (texture != 0 && !moved && tick - lastTick < REFRESH_TICKS && tick >= lastTick) {
            return;
        }
        lastTick = tick;
        originX = cx;
        originZ = cz;
        buffer.clear();
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                buffer.put(level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx + x, cz + z));
            }
        }
        buffer.flip();
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, SIZE, SIZE, 0, GL11.GL_RED, GL11.GL_FLOAT, buffer);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11.GL_RED, GL11.GL_FLOAT, buffer);
        }
    }

    /** Binds the texture to the currently active unit. */
    void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    @Override
    public void close() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        lastTick = Long.MIN_VALUE;
    }
}
