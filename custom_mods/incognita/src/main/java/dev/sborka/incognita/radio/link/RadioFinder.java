package dev.sborka.incognita.radio.link;

import dev.sborka.incognita.radio.RadioItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Finds radios on a player. Main thread only; the voice thread uses {@link RadioDirectory}. */
public final class RadioFinder {
    private RadioFinder() {}

    public static boolean isRadio(ItemStack stack) {
        return stack.getItem() instanceof RadioItem;
    }

    public static List<ItemStack> all(ServerPlayer player) {
        Inventory inv = player.getInventory();
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : inv.items) {
            if (isRadio(s)) out.add(s);
        }
        for (ItemStack s : inv.offhand) {
            if (isRadio(s)) out.add(s);
        }
        return out;
    }

    public static boolean hasRadio(ServerPlayer player) {
        return !all(player).isEmpty();
    }

    /** The stack in a hand: only a held radio can transmit. Main hand wins. */
    public static Optional<ItemStack> held(ServerPlayer player) {
        if (isRadio(player.getMainHandItem())) return Optional.of(player.getMainHandItem());
        if (isRadio(player.getOffhandItem())) return Optional.of(player.getOffhandItem());
        return Optional.empty();
    }

    /** Frequency this player transmits on, or -1 (no radio in hand, or it is switched off). */
    public static int transmitFreq(ServerPlayer player) {
        return held(player).map(RadioState::of).filter(RadioState::on).map(RadioState::freq).orElse(-1);
    }

    /** Whether any switched-on radio in the inventory is tuned to {@code freq}. */
    public static boolean receives(ServerPlayer player, int freq) {
        for (ItemStack s : all(player)) {
            RadioState st = RadioState.of(s);
            if (st.on() && st.freq() == freq) return true;
        }
        return false;
    }
}
