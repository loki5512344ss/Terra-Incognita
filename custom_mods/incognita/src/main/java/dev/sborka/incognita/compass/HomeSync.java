package dev.sborka.incognita.compass;

import dev.sborka.incognita.Incognita;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps each client's compass target up to date: checked once a second, sent only when it changes. */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class HomeSync {
    private static final Map<UUID, Optional<GlobalPos>> SENT = new ConcurrentHashMap<>();

    private HomeSync() {}

    /** Runs after the spawn module has moved the player, so the remembered spot is the random one. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()
            && player.getRespawnPosition() == null) {
            HomeTarget.rememberArrival(player);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 1) {
            return;
        }
        // First sight of this player (new, or from before this feature): where they stand now is where they appeared.
        if (player.getRespawnPosition() == null && !HomeTarget.hasArrival(player)) {
            HomeTarget.rememberArrival(player);
        }
        Optional<GlobalPos> now = Optional.ofNullable(HomeTarget.of(player));
        if (!Objects.equals(SENT.put(player.getUUID(), now), now)) {
            PacketDistributor.sendToPlayer(player, new HomePayload(now));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
