package dev.sborka.incognita.gear.anvil;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;

/**
 * Scales down the vanilla 12% chance for the anvil to degrade one damage level per repair (see
 * {@code incognita-gear.toml}). The anvil recipe itself moved to brass (kubejs/server_scripts/tools.js).
 */
public final class AnvilDurability {
    private AnvilDurability() {}

    @SubscribeEvent
    public static void onRepair(AnvilRepairEvent event) {
        event.setBreakChance(event.getBreakChance() * (float) AnvilConfig.breakChanceMultiplier());
    }
}
