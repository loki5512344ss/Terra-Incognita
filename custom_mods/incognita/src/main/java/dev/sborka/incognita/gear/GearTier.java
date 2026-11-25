package dev.sborka.incognita.gear;

import dev.sborka.incognita.Incognita;

import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Gear that may only come from the pack's own recipes. The item lists are tags filled by
 * {@code kubejs/server_scripts/gear.js}; without the script the tags are empty and nothing changes.
 */
enum GearTier {
    IRON("iron", Items.IRON_INGOT, 2),
    DIAMOND("diamond", Items.DIAMOND, 1),
    NETHERITE("netherite", Items.NETHERITE_SCRAP, 1);

    private final transient TagKey<Item> tag;
    private final transient Item refund;
    private final int refundCount;

    GearTier(String name, Item refund, int refundCount) {
        this.tag = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "gated_gear/" + name));
        this.refund = refund;
        this.refundCount = refundCount;
    }

    static Optional<GearTier> of(ItemStack stack) {
        for (GearTier tier : values()) {
            if (stack.is(tier.tag)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /** A little raw material that takes the place of the gear in loot. */
    ItemStack refund() {
        return new ItemStack(refund, refundCount);
    }
}
