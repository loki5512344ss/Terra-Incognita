package dev.sborka.incognita.radio.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.radio.RadioConfig;
import dev.sborka.incognita.radio.RadioModule;
import dev.sborka.incognita.radio.link.RadioFinder;
import dev.sborka.incognita.radio.link.RadioState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /radio freq <MHz>} tunes the held radio, {@code /r <text>} sends text over it. */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class RadioCommand {
    private RadioCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("radio").then(Commands.literal("freq").then(
            Commands.argument("mhz", DoubleArgumentType.doubleArg(RadioConfig.minFreq() / 10.0, RadioConfig.maxFreq() / 10.0))
                .executes(c -> setFreq(c.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(c, "mhz"))))));
        d.register(Commands.literal("r").then(Commands.argument("message", StringArgumentType.greedyString())
            .executes(c -> send(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")))));
    }

    private static int setFreq(ServerPlayer player, double mhz) {
        ItemStack stack = RadioFinder.held(player).orElse(null);
        if (stack == null) {
            player.displayClientMessage(Component.translatable("incognita.radio.need_hold"), false);
            return 0;
        }
        int freq = RadioState.clamp(RadioState.fromMegahertz(mhz), RadioConfig.minFreq(), RadioConfig.maxFreq());
        stack.set(RadioModule.STATE.get(), RadioState.of(stack).withFreq(freq));
        player.displayClientMessage(Component.translatable("incognita.radio.tuned", RadioState.format(freq)), true);
        return 1;
    }

    private static int send(ServerPlayer sender, String text) {
        int freq = RadioFinder.transmitFreq(sender);
        if (freq < 0) {
            sender.displayClientMessage(Component.translatable("incognita.radio.need_on"), false);
            return 0;
        }
        MutableComponent line = Component.literal("[radio] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("<").append(sender.getDisplayName()).append("> ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (p == sender || RadioFinder.receives(p, freq)) {
                p.sendSystemMessage(line);
            }
        }
        sender.server.sendSystemMessage(line);
        return 1;
    }
}
