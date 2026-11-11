package dev.sborka.incognita.coordinates.reveal;


import dev.sborka.incognita.Incognita;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class ServerHooks {
    private ServerHooks() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RevealCommand.register(event.getDispatcher());
    }

    /** A new session always starts hidden. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RevealState.clearServer(event.getEntity().getUUID());
    }

    /** A de-opped player must not keep seeing real coordinates. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.getNewLevel() >= 2 || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (RevealState.isServerRevealed(player.getUUID())) {
            RevealState.clearServer(player.getUUID());
            PacketDistributor.sendToPlayer(player, new RevealSyncPayload(false));
        }
    }
}
