package dev.sborka.incognita.projector;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** All behaviour is client side (see ProjectorInput); the item itself only carries the tooltip. */
public final class ProjectorItem extends Item {
    public ProjectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.incognita.projector.hint1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.incognita.projector.hint2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
