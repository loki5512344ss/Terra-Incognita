package dev.sborka.incognita.compass;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Compass that points to the world center (block 0 0 of the overworld). The needle is driven on the client. */
public final class OriginCompassItem extends Item {
    public OriginCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.incognita.origin_compass.hint").withStyle(ChatFormatting.GRAY));
    }
}
