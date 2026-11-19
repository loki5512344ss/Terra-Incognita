package dev.sborka.incognita.lighting.field;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client side RGB light field, one {@code int[4096]} per 16x16x16 section (0xRRGGBB per cell).
 *
 * <p>Writes happen only on the main thread ({@link #get} is the main thread view); chunk mesh builder threads read
 * through {@link #sample}, which uses an immutable snapshot of the section table that is republished after every
 * structural change (a section added or dropped). Both views share the same {@code int[]} cells, plain ints, so a
 * racing read sees an old or a new value and never a torn one; the section is re-queued after every change, so a
 * stale read is repaired by the next rebuild. Keys are primitive longs, nothing is boxed on the hot paths.
 *
 * <p>Emitters are the abstraction behind both static block lights and moving entity lights: each has an arbitrary
 * key, a cell and a color (intensity encodes the level). {@link #put} and {@link #remove} update the field
 * incrementally, only the neighborhood of the emitter is touched.
 */
public final class ColorLightField implements LightGrid {
    /** One light source. {@code color} already carries the intensity. */
    public record Emitter(int x, int y, int z, int color) {}

    /** Supplies the world filter for {@link #filter}. */
    public interface WorldFilter {
        int filter(int x, int y, int z);
    }

    private final Long2ObjectOpenHashMap<int[]> sections = new Long2ObjectOpenHashMap<>();
    private volatile Long2ObjectOpenHashMap<int[]> snapshot = new Long2ObjectOpenHashMap<>();
    private boolean structureChanged;
    private long lastKey = Long.MIN_VALUE;
    private int[] lastSection;
    private final Map<Object, Emitter> emitters = new HashMap<>();
    private int blockVersion;
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final FloodFill fill;
    private final WorldFilter world;
    private final int step;

    public ColorLightField(int step, WorldFilter world) {
        this.step = step;
        this.fill = new FloodFill(step);
        this.world = world;
    }

    public int decay() {
        return step;
    }

    public int emitterCount() {
        return emitters.size();
    }

    public int sectionCount() {
        return sections.size();
    }

    /** Changes whenever a block emitter (long key) is added, moved, recolored or removed. Main thread. */
    public int blockEmitterVersion() {
        return blockVersion;
    }

    /** Appends every block emitter (not the moving ones) to {@code out}. Main thread. */
    public void collectBlockEmitters(java.util.List<Emitter> out) {
        for (Map.Entry<Object, Emitter> e : emitters.entrySet()) {
            if (e.getKey() instanceof Long) {
                out.add(e.getValue());
            }
        }
    }

    public Emitter emitter(Object key) {
        return emitters.get(key);
    }

    // ---- LightGrid ----

    /** Main thread read. Remembers the last section, flood fill reads neighboring cells most of the time. */
    @Override
    public int get(int x, int y, int z) {
        int[] s = section(sectionKey(x >> 4, y >> 4, z >> 4));
        return s == null ? 0 : s[index(x, y, z)];
    }

    /** Mesher thread read: same value as {@link #get} but from the published snapshot, safe from any thread. */
    public int sample(int x, int y, int z) {
        int[] s = snapshot.get(sectionKey(x >> 4, y >> 4, z >> 4));
        return s == null ? 0 : s[index(x, y, z)];
    }

    private int[] section(long key) {
        if (key == lastKey) {
            return lastSection;
        }
        int[] s = sections.get(key);
        if (s != null) {
            lastKey = key;
            lastSection = s;
        }
        return s;
    }

    @Override
    public void set(int x, int y, int z, int color) {
        long key = sectionKey(x >> 4, y >> 4, z >> 4);
        int[] s = section(key);
        if (s == null) {
            if (color == 0) {
                return;
            }
            s = new int[4096];
            sections.put(key, s);
            structureChanged = true;
            lastKey = key;
            lastSection = s;
        }
        int i = index(x, y, z);
        if (s[i] == color) {
            return;
        }
        s[i] = color;
        markDirty(x, y, z);
    }

    /** Publishes the section table to mesher threads if sections were added or dropped since the last call. */
    private void publish() {
        if (structureChanged) {
            structureChanged = false;
            snapshot = new Long2ObjectOpenHashMap<>(sections);
        }
    }

    @Override
    public int filter(int x, int y, int z) {
        return world.filter(x, y, z);
    }

    /**
     * True when the section containing the block, or a neighbor reachable through smoothing, has any light data.
     * Reads the snapshot, so it is safe from mesher threads.
     */
    public boolean mayHaveLight(int x, int y, int z) {
        return mayHaveLight(snapshot, x, y, z);
    }

    private static boolean mayHaveLight(Long2ObjectOpenHashMap<int[]> sections, int x, int y, int z) {
        if (sections.isEmpty()) {
            return false;
        }
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        for (int dy = ly == 0 ? -1 : 0; dy <= (ly == 15 ? 1 : 0); dy++) {
            for (int dz = lz == 0 ? -1 : 0; dz <= (lz == 15 ? 1 : 0); dz++) {
                for (int dx = lx == 0 ? -1 : 0; dx <= (lx == 15 ? 1 : 0); dx++) {
                    if (sections.containsKey(sectionKey(sx + dx, sy + dy, sz + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- emitters ----

    /**
     * Adds an emitter, or moves/recolors it when the key exists. Does nothing when nothing changed.
     * Returns the number of cells visited by the propagation (cost metric).
     */
    public int put(Object key, int x, int y, int z, int color) {
        int cost = putDeferred(key, x, y, z, color);
        cost += flush();
        publish();
        return cost;
    }

    /**
     * Like {@link #put} but only registers the emitter and queues it; the light spreads on the next {@link #flush}
     * (or any later operation that runs the fill). Lets a chunk scan with many lamps run one propagation.
     */
    public int putDeferred(Object key, int x, int y, int z, int color) {
        if (color == 0) {
            return remove(key);
        }
        Emitter old = emitters.get(key);
        Emitter neu = new Emitter(x, y, z, color);
        if (neu.equals(old)) {
            return 0;
        }
        int cost = 0;
        if (old != null) {
            cost += remove(key);
        }
        emitters.put(key, neu);
        if (key instanceof Long) {
            blockVersion++;
        }
        set(x, y, z, LightColor.max(get(x, y, z), color));
        fill.push(x, y, z);
        return cost;
    }

    /** Runs the queued propagation and publishes new sections to the mesher threads. Returns cells visited. */
    public int flush() {
        int cost = fill.run(this);
        publish();
        return cost;
    }

    /** Removes an emitter and refills the hole it leaves from the other lights. Returns cells visited. */
    public int remove(Object key) {
        Emitter e = emitters.remove(key);
        if (e == null) {
            return 0;
        }
        if (key instanceof Long) {
            blockVersion++;
        }
        int range = LightColor.rangeOf(LightColor.intensity(e.color), step) + 1;
        int cost = recompute(e.x, e.y, e.z, range);
        publish();
        return cost;
    }

    /** A block next to lit space changed its filter (placed or removed an opaque block, glass...). */
    public int onBlockChanged(int x, int y, int z) {
        if (!mayHaveLight(sections, x, y, z)) {
            return 0;
        }
        int v = Math.max(
            intensityAt(x, y, z),
            Math.max(Math.max(intensityAt(x + 1, y, z), intensityAt(x - 1, y, z)),
                Math.max(Math.max(intensityAt(x, y + 1, z), intensityAt(x, y - 1, z)),
                    Math.max(intensityAt(x, y, z + 1), intensityAt(x, y, z - 1)))));
        if (v == 0) {
            return 0;
        }
        int cost = recompute(x, y, z, LightColor.rangeOf(v, step) + 1);
        publish();
        return cost;
    }

    /** Re-spreads every emitter whose light can reach a chunk column; used after a chunk (and its blocks) loaded. */
    public int refreshColumn(int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int cost = 0;
        for (Emitter e : emitters.values()) {
            int range = LightColor.rangeOf(LightColor.intensity(e.color), step) + 1;
            if (e.x + range >= minX && e.x - range <= minX + 15 && e.z + range >= minZ && e.z - range <= minZ + 15) {
                fill.push(e.x, e.y, e.z);
            }
        }
        cost += flush();
        return cost;
    }

    private int recompute(int x, int y, int z, int range) {
        fill.clearRegion(this, x, y, z, range);
        for (Emitter e : emitters.values()) {
            if (Math.abs(e.x - x) + Math.abs(e.y - y) + Math.abs(e.z - z) <= range) {
                set(e.x, e.y, e.z, LightColor.max(get(e.x, e.y, e.z), e.color));
                fill.push(e.x, e.y, e.z);
            }
        }
        return fill.run(this);
    }

    private int intensityAt(int x, int y, int z) {
        return LightColor.intensity(get(x, y, z));
    }

    /** Forgets everything in one chunk column (chunk unloaded). */
    public void dropColumn(int chunkX, int chunkZ) {
        var sectionIt = sections.keySet().iterator();
        while (sectionIt.hasNext()) {
            long k = sectionIt.nextLong();
            if (sectionX(k) == chunkX && sectionZ(k) == chunkZ) {
                sectionIt.remove();
                structureChanged = true;
            }
        }
        lastKey = Long.MIN_VALUE;
        lastSection = null;
        publish();
        Iterator<Map.Entry<Object, Emitter>> it = emitters.entrySet().iterator();
        while (it.hasNext()) {
            Emitter e = it.next().getValue();
            if ((e.x >> 4) == chunkX && (e.z >> 4) == chunkZ) {
                it.remove();
                blockVersion++;
            }
        }
    }

    public void clear() {
        sections.clear();
        emitters.clear();
        blockVersion++;
        dirty.clear();
        lastKey = Long.MIN_VALUE;
        lastSection = null;
        structureChanged = true;
        publish();
    }

    // ---- dirty sections ----

    private void markDirty(int x, int y, int z) {
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        int lx = x & 15;
        int ly = y & 15;
        int lz = z & 15;
        // Smooth sampling reads across section borders, so border cells dirty the neighbors as well.
        for (int dy = ly == 0 ? -1 : 0; dy <= (ly == 15 ? 1 : 0); dy++) {
            for (int dz = lz == 0 ? -1 : 0; dz <= (lz == 15 ? 1 : 0); dz++) {
                for (int dx = lx == 0 ? -1 : 0; dx <= (lx == 15 ? 1 : 0); dx++) {
                    dirty.add(sectionKey(sx + dx, sy + dy, sz + dz));
                }
            }
        }
    }

    public int dirtyCount() {
        return dirty.size();
    }

    /** Takes and clears the sections whose cells changed since the last call. */
    public LongArrayList drainDirty() {
        LongArrayList out = new LongArrayList(dirty);
        dirty.clear();
        return out;
    }

    // ---- keys ----

    public static int index(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    public static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sz & 0x3FFFFF) << 20) | (sy & 0xFFFFF);
    }

    public static int sectionX(long k) {
        return (int) (k >> 42) << 10 >> 10;
    }

    public static int sectionZ(long k) {
        return (int) (k >> 20 & 0x3FFFFF) << 10 >> 10;
    }

    public static int sectionY(long k) {
        return (int) (k & 0xFFFFF) << 12 >> 12;
    }
}
