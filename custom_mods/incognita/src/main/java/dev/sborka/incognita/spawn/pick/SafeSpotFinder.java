package dev.sborka.incognita.spawn.pick;

import dev.sborka.incognita.spawn.SpawnConfig;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Finds a safe surface position near the world center, with retries. */
public final class SafeSpotFinder {
    private SafeSpotFinder() {}

    /** Empty after all attempts fail; callers keep the vanilla behavior then. */
    public static Optional<Vec3> find(ServerLevel level) {
        for (int attempt = 0; attempt < SpawnConfig.attempts(); attempt++) {
            SpawnPicker.Point point = SpawnPicker.pick(
                ThreadLocalRandom.current(), SpawnConfig.minRadius(), SpawnConfig.maxRadius());
            if (isWater(level, point.x(), point.z())) {
                continue;
            }
            BlockPos surface = surfaceAt(level, point.x(), point.z());
            if (isSafe(level, surface)) {
                return Optional.of(Vec3.atBottomCenterOf(surface));
            }
        }
        return Optional.empty();
    }

    private static boolean isWater(ServerLevel level, int x, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, level.getSeaLevel(), z));
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
    }

    private static BlockPos surfaceAt(ServerLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        return new BlockPos(x, y, z);
    }

    private static boolean isHazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.POWDER_SNOW);
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        BlockState ground = level.getBlockState(feet.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }
        if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS) || ground.is(Blocks.CAMPFIRE)
            || ground.is(Blocks.SOUL_CAMPFIRE) || ground.is(Blocks.FIRE) || ground.is(Blocks.SWEET_BERRY_BUSH)
            || ground.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        if (isHazard(feetState) || isHazard(headState)) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && level.getFluidState(feet).isEmpty();
    }
}
