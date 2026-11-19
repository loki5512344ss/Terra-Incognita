package dev.sborka.incognita.lighting.field;

/**
 * Breadth-first colored light propagation. A cell only ever takes the per-channel maximum of what arrives, so the
 * fill is monotone and terminates, and several lights mix by maximum. Removing light is done by clearing a region and
 * re-propagating from what is left (see {@link #clearRegion}).
 *
 * <p>The queue is a growable int triple array to avoid boxing.
 */
public final class FloodFill {
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private int[] queue = new int[3 * 4096];
    private int head;
    private int tail;
    private final int step;

    public FloodFill(int decay) {
        this.step = decay;
    }

    public int decay() {
        return step;
    }

    /** Queues a cell whose stored value should be spread to its neighbors. */
    public void push(int x, int y, int z) {
        if (tail + 3 > queue.length) {
            if (head > 0) {
                System.arraycopy(queue, head, queue, 0, tail - head);
                tail -= head;
                head = 0;
            }
            if (tail + 3 > queue.length) {
                queue = java.util.Arrays.copyOf(queue, queue.length * 2);
            }
        }
        queue[tail++] = x;
        queue[tail++] = y;
        queue[tail++] = z;
    }

    /** Runs the queued propagation to completion. Returns the number of cells visited. */
    public int run(LightGrid grid) {
        int visited = 0;
        while (head < tail) {
            int x = queue[head++];
            int y = queue[head++];
            int z = queue[head++];
            visited++;
            int c = grid.get(x, y, z);
            if (LightColor.intensity(c) < LightColor.CUTOFF) {
                continue;
            }
            for (int i = 0; i < 6; i++) {
                int nx = x + DX[i];
                int ny = y + DY[i];
                int nz = z + DZ[i];
                int f = grid.filter(nx, ny, nz);
                if (f < 0) {
                    continue;
                }
                int incoming = LightColor.attenuate(f == LightColor.WHITE ? c : LightColor.filter(c, f), step);
                if (incoming == 0) {
                    continue;
                }
                int old = grid.get(nx, ny, nz);
                int merged = LightColor.max(old, incoming);
                if (merged != old) {
                    grid.set(nx, ny, nz, merged);
                    push(nx, ny, nz);
                }
            }
        }
        head = 0;
        tail = 0;
        return visited;
    }

    /**
     * Zeroes every cell within Manhattan distance {@code range} of the center and queues the lit cells just outside
     * the region, so a following {@link #run} refills the hole from the lights that survive. The caller re-seeds
     * emitters inside the region before running.
     */
    public void clearRegion(LightGrid grid, int cx, int cy, int cz, int range) {
        for (int dy = -range; dy <= range; dy++) {
            int rem = range - Math.abs(dy);
            for (int dz = -rem; dz <= rem; dz++) {
                int rem2 = rem - Math.abs(dz);
                for (int dx = -rem2; dx <= rem2; dx++) {
                    int x = cx + dx;
                    int y = cy + dy;
                    int z = cz + dz;
                    if (grid.get(x, y, z) != 0) {
                        grid.set(x, y, z, 0);
                    }
                }
            }
        }
        // Shell: cells at distance range + 1 hold valid light and re-seed the hole.
        int outer = range + 1;
        for (int dy = -outer; dy <= outer; dy++) {
            int rem = outer - Math.abs(dy);
            for (int dz = -rem; dz <= rem; dz++) {
                int rem2 = rem - Math.abs(dz);
                pushIfLit(grid, cx + rem2, cy + dy, cz + dz);
                if (rem2 != 0) {
                    pushIfLit(grid, cx - rem2, cy + dy, cz + dz);
                }
            }
        }
    }

    private void pushIfLit(LightGrid grid, int x, int y, int z) {
        if (grid.get(x, y, z) != 0) {
            push(x, y, z);
        }
    }
}
