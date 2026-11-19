package dev.sborka.incognita.lighting.client;

import dev.sborka.incognita.lighting.config.LightingConfig;
import dev.sborka.incognita.lighting.emitter.DynamicSources;
import dev.sborka.incognita.lighting.emitter.EmitterDefs;
import dev.sborka.incognita.lighting.field.ColorLightField;
import dev.sborka.incognita.lighting.field.LightColor;

import dev.sborka.incognita.Incognita;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Owns the client light field and feeds it: static block emitters from loaded chunks and block updates, dynamic
 * emitters (held items, dropped items, burning entities) every tick. Dirty sections are handed to the renderer with
 * a per-tick budget, because every change to the field costs chunk rebuilds with vertex-tint delivery.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class LightingClient {
    /** Key of a moving emitter. */
    private record DynKey(int entityId) {}

    private static volatile ColorLightField field;
    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();
    private static final LongLinkedOpenHashSet PENDING_CHUNKS = new LongLinkedOpenHashSet();
    private static final LongLinkedOpenHashSet PENDING_SECTIONS = new LongLinkedOpenHashSet();
    private static Set<DynKey> lastDynamic = new HashSet<>();

    // Cost counters, reset by the debug log.
    private static long dynNanos;
    private static long dynCells;
    private static int dynUpdates;
    private static int rebuilt;
    private static int rebuiltTotal;
    private static int ticks;

    private LightingClient() {}

    /** Chunk sections handed to the renderer since start, for tests and the debug log. */
    public static int rebuiltTotal() {
        return rebuiltTotal;
    }

    /** The current field, null outside a level or when disabled. Read from mesher threads. */
    public static ColorLightField field() {
        return field;
    }

    private static ColorLightField create() {
        return new ColorLightField(LightColor.stepForRadius(LightingConfig.radius()), LightingClient::worldFilter);
    }

    // ---- world access (main thread only) ----

    private static int worldFilter(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.isOutsideBuildHeight(y)) {
            return -1;
        }
        ChunkAccess chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            return -1;
        }
        BlockState state = chunk.getBlockState(SCRATCH.set(x, y, z));
        if (state.isAir()) {
            return LightColor.WHITE;
        }
        if (state.isSolidRender(level, SCRATCH)) {
            return -1;
        }
        if (state.getBlock() instanceof BeaconBeamBlock glass) {
            int dye = glass.getColor().getTextureDiffuseColor() & 0xFFFFFF;
            // Mixed 60/40 with white: stained glass tints the light without swallowing the other channels.
            return LightColor.pack(
                (LightColor.r(dye) * 6 + 255 * 4) / 10,
                (LightColor.g(dye) * 6 + 255 * 4) / 10,
                (LightColor.b(dye) * 6 + 255 * 4) / 10);
        }
        return LightColor.WHITE;
    }

    // ---- lifecycle ----

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
        field = create();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        field = null;
        PENDING_CHUNKS.clear();
        PENDING_SECTIONS.clear();
        lastDynamic = new HashSet<>();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (field != null && event.getLevel().isClientSide()) {
            PENDING_CHUNKS.add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        ColorLightField f = field;
        if (f != null && event.getLevel().isClientSide()) {
            ChunkPos pos = event.getChunk().getPos();
            PENDING_CHUNKS.remove(pos.toLong());
            f.dropColumn(pos.x, pos.z);
        }
    }

    /** Called by the ClientLevel mixin for every block change the client applies. */
    public static void onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState) {
        ColorLightField f = field;
        if (f == null || !LightingConfig.enabled() || oldState == newState) {
            return;
        }
        try {
            EmitterDefs.Def def = EmitterDefs.blockDef(newState.getBlock());
            if (def == null && f.sectionCount() == 0) {
                return; // nothing lit anywhere, and this block is not a light: nothing to update
            }
            Long key = pos.asLong();
            if (def != null) {
                f.put(key, pos.getX(), pos.getY(), pos.getZ(), blockColor(def, newState));
            } else if (f.emitter(key) != null) {
                f.remove(key);
            }
            f.onBlockChanged(pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException e) {
            Incognita.LOGGER.warn("Colored light block update failed at {}: {}", pos, e.toString());
        }
    }

    private static int blockColor(EmitterDefs.Def def, BlockState state) {
        int level = def.level() >= 0 ? def.level() : state.getLightEmission();
        return LightColor.withLevel(def.color(), level);
    }

    // ---- per tick ----

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        ColorLightField f = field;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (f == null || level == null || mc.player == null) {
            return;
        }
        if (!LightingConfig.enabled()) {
            f.clear();
            return;
        }
        scanPendingChunks(f, level);
        updateDynamic(f, level, mc);
        flushDirty(f, level, mc);
        if (LightingConfig.debugStats() && ++ticks >= 100) {
            Incognita.LOGGER.info("Colored light: {} emitters, {} sections, dynamic: {} updates, {} cells, {} us/tick avg, {} sections rebuilt",
                f.emitterCount(), f.sectionCount(), dynUpdates, dynCells, dynNanos / 1000 / 100, rebuilt);
            dynNanos = 0;
            dynCells = 0;
            dynUpdates = 0;
            rebuilt = 0;
            ticks = 0;
        }
    }

    private static void scanPendingChunks(ColorLightField f, ClientLevel level) {
        if (PENDING_CHUNKS.isEmpty()) {
            return;
        }
        LongIterator it = PENDING_CHUNKS.iterator();
        int budget = 4;
        while (it.hasNext() && budget-- > 0) {
            long packed = it.nextLong();
            it.remove();
            int cx = ChunkPos.getX(packed);
            int cz = ChunkPos.getZ(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                continue;
            }
            scanChunk(f, chunk);
            f.refreshColumn(cx, cz); // runs the single batched propagation for every lamp found in the chunk
        }
    }

    private static void scanChunk(ColorLightField f, LevelChunk chunk) {
        if (!EmitterDefs.anyBlocks()) {
            return;
        }
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(s -> EmitterDefs.blockDef(s.getBlock()) != null)) {
                continue;
            }
            int baseY = (minSection + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState s = section.getBlockState(x, y, z);
                        EmitterDefs.Def def = EmitterDefs.blockDef(s.getBlock());
                        if (def != null) {
                            p.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                            f.putDeferred(p.asLong(), p.getX(), p.getY(), p.getZ(), blockColor(def, s));
                        }
                    }
                }
            }
        }
    }

    // ---- dynamic emitters ----

    private static void updateDynamic(ColorLightField f, ClientLevel level, Minecraft mc) {
        long start = System.nanoTime();
        Set<DynKey> seen = new HashSet<>();
        if (LightingConfig.dynamic()) {
            int cap = LightingConfig.dynamicMaxLevel();
            double maxDistSq = 48.0 * 48.0;
            for (Entity e : level.entitiesForRendering()) {
                if (e.isRemoved() || e.distanceToSqr(mc.player) > maxDistSq) {
                    continue;
                }
                int color = DynamicSources.colorOf(e, cap);
                if (color == 0) {
                    continue;
                }
                DynKey key = new DynKey(e.getId());
                seen.add(key);
                BlockPos cell = DynamicSources.cellOf(e);
                dynCells += f.put(key, cell.getX(), cell.getY(), cell.getZ(), color);
                dynUpdates++;
            }
        }
        for (DynKey old : lastDynamic) {
            if (!seen.contains(old)) {
                dynCells += f.remove(old);
            }
        }
        lastDynamic = seen;
        dynNanos += System.nanoTime() - start;
    }

    // ---- handing sections to the renderer ----

    private static void flushDirty(ColorLightField f, ClientLevel level, Minecraft mc) {
        LongArrayList fresh = f.drainDirty();
        for (int i = 0; i < fresh.size(); i++) {
            PENDING_SECTIONS.add(fresh.getLong(i));
        }
        int budget = LightingConfig.maxRebuildsPerTick();
        LongIterator it = PENDING_SECTIONS.iterator();
        while (it.hasNext() && budget-- > 0) {
            long k = it.nextLong();
            it.remove();
            int sy = ColorLightField.sectionY(k);
            if (sy >= level.getMinSection() && sy < level.getMaxSection()) {
                mc.levelRenderer.setSectionDirty(ColorLightField.sectionX(k), sy, ColorLightField.sectionZ(k));
                rebuilt++;
                rebuiltTotal++;
            }
        }
    }
}
