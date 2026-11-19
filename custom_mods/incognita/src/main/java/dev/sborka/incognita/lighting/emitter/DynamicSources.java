package dev.sborka.incognita.lighting.emitter;

import dev.sborka.incognita.lighting.field.LightColor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;

/**
 * Decides which entities emit colored light and where. Living entities: the brighter of both hands. Item entities and
 * item frames: their stack. Also primed TNT, creepers (charged or about to blow), fireballs, glow squid, blazes,
 * magma cubes and anything burning. Stacks glow by their explicit entry or by the light level of their block.
 */
public final class DynamicSources {
    private static final int FIRE = 0xFF8A28;
    private static final int FIRE_LEVEL = 11;
    private static final int BLAZE_LEVEL = 12;
    private static final int FIREBALL_LEVEL = 12;
    private static final int TNT = 0xFFE6B8;
    private static final int TNT_LEVEL = 9;
    private static final int CREEPER_CHARGED = 0x6C9CFF;
    private static final int CREEPER_CHARGED_LEVEL = 8;
    private static final int CREEPER_FUSE = 0xFFFFFF;
    private static final int GLOW_SQUID = 0x3CF0C8;
    private static final int GLOW_SQUID_LEVEL = 8;

    private DynamicSources() {}

    /** Light color (with intensity) of an entity, 0 when it does not glow. */
    public static int colorOf(Entity e, int levelCap) {
        int best = 0;
        if (e instanceof LivingEntity living) {
            best = LightColor.max(best, stackColor(living.getMainHandItem(), levelCap));
            best = LightColor.max(best, stackColor(living.getOffhandItem(), levelCap));
        } else if (e instanceof ItemEntity item) {
            best = stackColor(item.getItem(), levelCap);
        } else if (e instanceof ItemFrame frame) {
            best = stackColor(frame.getItem(), levelCap);
        } else if (e instanceof PrimedTnt) {
            best = capped(TNT, TNT_LEVEL, levelCap);
        } else if (e instanceof Fireball) {
            best = capped(FIRE, FIREBALL_LEVEL, levelCap);
        } else if (e instanceof Creeper creeper) {
            best = creeperColor(creeper.isPowered(), creeper.getSwelling(1.0f), levelCap);
        }
        EntityType<?> type = e.getType();
        if (type == EntityType.BLAZE || type == EntityType.MAGMA_CUBE) {
            best = LightColor.max(best, capped(FIRE, BLAZE_LEVEL, levelCap));
        } else if (type == EntityType.GLOW_SQUID) {
            best = LightColor.max(best, capped(GLOW_SQUID, GLOW_SQUID_LEVEL, levelCap));
        } else if (e.isOnFire()) {
            best = LightColor.max(best, capped(FIRE, FIRE_LEVEL, levelCap));
        }
        return best;
    }

    /** A creeper glows blue while charged and white while its fuse burns (swelling above zero). */
    static int creeperColor(boolean charged, float swelling, int levelCap) {
        int c = 0;
        if (charged) {
            c = capped(CREEPER_CHARGED, CREEPER_CHARGED_LEVEL, levelCap);
        }
        if (swelling > 0.0f) {
            // Brighter the closer to the blast, in three steps so the field is not rebuilt every tick.
            int level = swelling > 0.66f ? 11 : swelling > 0.33f ? 9 : 7;
            c = LightColor.max(c, capped(CREEPER_FUSE, level, levelCap));
        }
        return c;
    }

    /** Color scaled to a level, with the level limited by the dynamic light cap. */
    static int capped(int color, int level, int levelCap) {
        return LightColor.withLevel(color, Math.min(level, levelCap));
    }

    private static int stackColor(ItemStack stack, int levelCap) {
        if (stack.isEmpty()) {
            return 0;
        }
        EmitterDefs.Def def = EmitterDefs.itemLight(stack.getItem());
        return def == null ? 0 : capped(def.color(), def.level(), levelCap);
    }

    /** The cell an entity lights: the middle of its body, at least one block above the feet for items. */
    public static BlockPos cellOf(Entity e) {
        return BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
    }
}
