package dev.sborka.incognita.coordinates.client;

import dev.sborka.incognita.coordinates.Coordinates;
import dev.sborka.incognita.coordinates.reveal.RevealState;

import dev.sborka.incognita.Incognita;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    /** Rewrites system messages (command feedback, mod errors). Player chat is left alone. */
    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (RevealState.isClientRevealed()) {
            return;
        }
        event.setMessage(Coordinates.rewriter().rewriteSafely(event.getMessage()));
    }

    /** F3 stays available, but lines that expose the position are dropped. */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (RevealState.isClientRevealed()) {
            return;
        }
        event.getLeft().removeIf(DebugLineFilter::isCoordinateLine);
        event.getRight().removeIf(DebugLineFilter::isCoordinateLine);
    }

    /** Item tooltips (e.g. bound terminals) can contain coordinates. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (RevealState.isClientRevealed()) {
            return;
        }
        List<Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, Coordinates.rewriter().rewriteSafely(lines.get(i)));
        }
    }

    /** Advanced item tooltips can dump raw data components (bound positions); keep them off while masking. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options != null && minecraft.options.advancedItemTooltips && !RevealState.isClientRevealed()) {
                minecraft.options.advancedItemTooltips = false;
            }
        } catch (Throwable ignored) {
            // fail open
        }
    }

    /** Reveal is per session: leaving a world switches the hiding back on. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RevealState.setClientRevealed(false);
    }
}
