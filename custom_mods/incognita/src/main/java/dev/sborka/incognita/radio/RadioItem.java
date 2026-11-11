package dev.sborka.incognita.radio;

import dev.sborka.incognita.radio.link.RadioState;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

/** Right click toggles power. Frequency is set with {@code /radio freq <MHz>}. */
public final class RadioItem extends Item {
    public RadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            RadioState next = RadioState.of(stack).withOn(!RadioState.of(stack).on());
            stack.set(RadioModule.STATE.get(), next);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.PLAYERS, 0.6f, next.on() ? 1.4f : 0.9f);
            player.displayClientMessage(Component.translatable(
                next.on() ? "item.incognita.radio.on" : "item.incognita.radio.off",
                RadioState.format(next.freq())), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioState state = RadioState.of(stack);
        tooltip.add(Component.translatable("item.incognita.radio.tooltip.freq", RadioState.format(state.freq()))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(state.on() ? "item.incognita.radio.tooltip.on" : "item.incognita.radio.tooltip.off")
            .withStyle(state.on() ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable("item.incognita.radio.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
