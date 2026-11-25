package dev.sborka.incognita.gear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LootRulesTest {
    @Test
    void coversStructureChestsAndBartering() {
        assertTrue(GearLootModifier.covers("chests/village/village_weaponsmith"));
        assertTrue(GearLootModifier.covers("chests/trial_chambers/reward"));
        assertTrue(GearLootModifier.covers("structures/tower/treasure_chest"));
        assertTrue(GearLootModifier.covers("gameplay/piglin_bartering"));
    }

    @Test
    void leavesBlocksMobsAndFishingAlone() {
        assertFalse(GearLootModifier.covers("blocks/iron_ore"));
        assertFalse(GearLootModifier.covers("entities/zombie"));
        assertFalse(GearLootModifier.covers("gameplay/fishing"));
        // a chest block broken by a player must drop itself
        assertFalse(GearLootModifier.covers("blocks/chest"));
    }
}
