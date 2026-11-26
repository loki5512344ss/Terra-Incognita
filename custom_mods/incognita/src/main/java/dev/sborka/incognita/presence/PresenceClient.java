package dev.sborka.incognita.presence;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.presence.ipc.DiscordActivity;
import dev.sborka.incognita.presence.ipc.DiscordIpc;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Builds the Discord card from the client state. Only the world phase and the session timer are sent: no
 * dimension, biome, coordinates, weather, radio or player names.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class PresenceClient {
    private static final int TICKS_PER_SECOND = 20;
    private static final long MIN_INTERVAL_MILLIS = 5_000L;

    private static volatile DiscordIpc ipc;
    private static String ipcClientId = "";
    private static long sessionStart;
    private static long lastSend;
    private static String lastSignature = "";
    private static volatile boolean announced;
    /** Talking to Discord blocks until it answers, so it never happens on the game thread. */
    private static final ExecutorService DISCORD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "incognita-discord-rpc");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static int ticks;

    private PresenceClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % TICKS_PER_SECOND != 0) {
            return;
        }
        if (!PresenceConfig.enabled()) {
            shutdown();
            return;
        }
        DiscordActivity activity = build();
        if (activity == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String signature = activity.details() + '\u0000' + activity.state();
        boolean changed = !signature.equals(lastSignature);
        boolean due = now - lastSend >= PresenceConfig.updateSeconds() * 1000L;
        if (!changed && !due) {
            return;
        }
        if (changed && now - lastSend < MIN_INTERVAL_MILLIS) {
            return;
        }
        if (send(activity)) {
            lastSignature = signature;
            lastSend = now;
        }
    }

    @SubscribeEvent
    public static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        sessionStart = System.currentTimeMillis();
        lastSignature = "";
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        sessionStart = 0L;
        lastSignature = "";
    }

    private static DiscordActivity build() {
        Minecraft client = Minecraft.getInstance();
        boolean inWorld = client.level != null && client.player != null;
        if (!inWorld) {
            return menuActivity(client.level != null);
        }
        return card(translate("incognita.presence.exploring", "Ищет путь без координат"), sessionStart);
    }

    private static DiscordActivity menuActivity(boolean loading) {
        if (!PresenceConfig.showMenu()) {
            return null;
        }
        String state = loading
            ? translate("incognita.presence.loading", "Загружает мир")
            : translate("incognita.presence.menu", "В главном меню");
        return card(state, 0L);
    }

    private static DiscordActivity card(String state, long start) {
        return new DiscordActivity("", state, start,
            PresenceConfig.largeImageKey(), PresenceConfig.largeImageText(),
            PresenceConfig.buttonLabel(), PresenceConfig.buttonUrl());
    }

    private static String translate(String key, String fallback) {
        String value = Component.translatable(key).getString();
        return value.equals(key) ? fallback : value;
    }

    /** Hands the card to the Discord thread. Returns {@code false} while the previous update is still in flight. */
    private static boolean send(DiscordActivity activity) {
        String clientId = PresenceConfig.applicationId();
        if (clientId.isBlank() || !BUSY.compareAndSet(false, true)) {
            return false;
        }
        DISCORD.execute(() -> {
            try {
                if (clientFor(clientId).setActivity(activity) && !announced) {
                    announced = true;
                    Incognita.LOGGER.info("Discord Rich Presence is connected");
                }
            } finally {
                BUSY.set(false);
            }
        });
        return true;
    }

    /** Runs on the Discord thread, like every other use of {@link #ipc}. */
    private static DiscordIpc clientFor(String clientId) {
        if (ipc == null || !clientId.equals(ipcClientId)) {
            close();
            ipc = new DiscordIpc(clientId);
            ipcClientId = clientId;
        }
        return ipc;
    }

    private static void close() {
        if (ipc != null) {
            ipc.close();
            ipc = null;
            ipcClientId = "";
        }
    }

    private static void shutdown() {
        DISCORD.execute(PresenceClient::close);
    }
}
