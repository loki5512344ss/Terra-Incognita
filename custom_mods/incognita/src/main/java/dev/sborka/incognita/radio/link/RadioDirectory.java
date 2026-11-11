package dev.sborka.incognita.radio.link;

import dev.sborka.incognita.Incognita;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Immutable snapshot of who has which radio, refreshed on the server thread every few ticks.
 * The Simple Voice Chat events run on the voice thread, which must not touch inventories.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class RadioDirectory {
    private static final int REFRESH_TICKS = 4;

    public record Entry(UUID id, String dimension, double x, double y, double z,
                        boolean hasRadio, int transmitFreq, Set<Integer> receiveFreqs) {
        public double distanceSqr(Entry other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static volatile Map<UUID, Entry> snapshot = Map.of();

    private RadioDirectory() {}

    public static Map<UUID, Entry> snapshot() {
        return snapshot;
    }

    public static Entry get(UUID id) {
        return snapshot.get(id);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_TICKS != 0) {
            return;
        }
        Map<UUID, Entry> next = new HashMap<>();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            Set<Integer> rx = new HashSet<>();
            boolean has = false;
            for (ItemStack s : RadioFinder.all(p)) {
                has = true;
                RadioState st = RadioState.of(s);
                if (st.on()) rx.add(st.freq());
            }
            next.put(p.getUUID(), new Entry(p.getUUID(), p.level().dimension().location().toString(),
                p.getX(), p.getY(), p.getZ(), has, RadioFinder.transmitFreq(p), Set.copyOf(rx)));
        }
        snapshot = Map.copyOf(next);
    }
}
