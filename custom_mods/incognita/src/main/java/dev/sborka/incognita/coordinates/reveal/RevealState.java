package dev.sborka.incognita.coordinates.reveal;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks which players have switched to real coordinates (operators only). */
public final class RevealState {
    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
    private static volatile boolean clientRevealed;

    private RevealState() {}

    public static boolean isServerRevealed(UUID player) {
        return SERVER.contains(player);
    }

    public static void setServerRevealed(UUID player, boolean revealed) {
        if (revealed) {
            SERVER.add(player);
        } else {
            SERVER.remove(player);
        }
    }

    public static void clearServer(UUID player) {
        SERVER.remove(player);
    }

    public static boolean isClientRevealed() {
        return clientRevealed;
    }

    public static void setClientRevealed(boolean revealed) {
        clientRevealed = revealed;
    }
}
