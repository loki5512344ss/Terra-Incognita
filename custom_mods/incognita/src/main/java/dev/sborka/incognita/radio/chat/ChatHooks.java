package dev.sborka.incognita.radio.chat;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.radio.RadioConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Normal chat only reaches players within {@code chatRadius} blocks, in the same dimension. */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class ChatHooks {
    private ChatHooks() {}

    public static boolean within(Vec3 a, Vec3 b, double radius) {
        return a.distanceToSqr(b) <= radius * radius;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        if (!RadioConfig.localChat()) {
            return;
        }
        ServerPlayer sender = event.getPlayer();
        event.setCanceled(true);
        Component line = Component.translatable("chat.type.text", sender.getDisplayName(), event.getMessage());
        double radius = RadioConfig.chatRadius();
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (p == sender || (p.level() == sender.level() && within(p.position(), sender.position(), radius))) {
                p.sendSystemMessage(line);
            }
        }
        sender.server.sendSystemMessage(line);
    }
}
