package dev.sborka.incognita.gear;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Swaps gated gear found in chests and piglin bartering for a little of its material. */
public final class GearLootModifier extends LootModifier {
    static final MapCodec<GearLootModifier> CODEC = RecordCodecBuilder.mapCodec(
        instance -> codecStart(instance).apply(instance, GearLootModifier::new));

    private GearLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    /** Whether a loot table is a container reward. Block and mob drops must stay untouched. */
    static boolean covers(String tablePath) {
        if (tablePath.startsWith("blocks/") || tablePath.startsWith("entities/")) {
            return false;
        }
        return tablePath.startsWith("chests/") || tablePath.contains("chest")
            || "gameplay/piglin_bartering".equals(tablePath);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (covers(context.getQueriedLootTableId().getPath())) {
            loot.replaceAll(stack -> GearTier.of(stack).map(GearTier::refund).orElse(stack));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
