package dev.sborka.incognita.spawn;

import dev.sborka.incognita.spawn.pick.SafeSpotFinder;

import dev.sborka.incognita.Incognita;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class SpawnHooks {
    static final String DONE_KEY = "incognita_spawn_done";

    private SpawnHooks() {}

    /** First join: move a brand-new player to a random spot once. The flag survives death. */
    @SubscribeEvent
    public static void onFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!SpawnConfig.firstJoinRandom() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(DONE_KEY) || root.getBoolean(DONE_KEY)) {
            return;
        }
        persisted.putBoolean(DONE_KEY, true);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        // Veterans of an existing world are not moved when the mod is installed.
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) != 0) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        Optional<Vec3> spot = SafeSpotFinder.find(overworld);
        if (spot.isEmpty()) {
            return;
        }
        Vec3 pos = spot.get();
        player.teleportTo(overworld, pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
        Incognita.LOGGER.info("First join: moved {} to a random spawn point", player.getGameProfile().getName());
    }

    /** Pure decision: should the respawn be replaced by a random spot? */
    static boolean shouldRandomize(boolean enabled, boolean fromEndFight, boolean hasRespawnPos,
                                   boolean respawnLevelExists, boolean missingBlock) {
        return enabled && !fromEndFight && (missingBlock || !hasRespawnPos || !respawnLevelExists);
    }

    /** Death without a usable bed, anchor or forced spawnpoint: respawn at a random spot. */
    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DimensionTransition original = event.getOriginalDimensionTransition();
        if (!shouldRandomize(SpawnConfig.respawnRandom(), event.isFromEndFight(),
            player.getRespawnPosition() != null,
            player.server.getLevel(player.getRespawnDimension()) != null,
            original.missingRespawnBlock())) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        Optional<Vec3> spot = SafeSpotFinder.find(overworld);
        if (spot.isEmpty()) {
            return;
        }
        event.setDimensionTransition(new DimensionTransition(
            overworld, spot.get(), Vec3.ZERO, original.yRot(), original.xRot(), original.postDimensionTransition()));
        event.setCopyOriginalSpawnPosition(false);
    }
}
