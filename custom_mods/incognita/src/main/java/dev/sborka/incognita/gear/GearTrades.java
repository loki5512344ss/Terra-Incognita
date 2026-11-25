package dev.sborka.incognita.gear;

import java.util.List;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/** Stops villagers from selling gated gear. Villagers that already have offers keep them. */
final class GearTrades {
    private GearTrades() {}

    // LOWEST: trades added by other mods are already in the lists
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onVillagerTrades(VillagerTradesEvent event) {
        for (List<VillagerTrades.ItemListing> level : event.getTrades().values()) {
            level.replaceAll(GearTrades::withoutGear);
        }
    }

    /** The result is only known when the offer is rolled; a null offer is skipped by vanilla. */
    private static VillagerTrades.ItemListing withoutGear(VillagerTrades.ItemListing listing) {
        return (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            return offer == null || GearTier.of(offer.getResult()).isPresent() ? null : offer;
        };
    }
}
