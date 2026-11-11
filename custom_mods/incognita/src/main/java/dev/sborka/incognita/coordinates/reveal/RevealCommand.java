package dev.sborka.incognita.coordinates.reveal;

import dev.sborka.incognita.coordinates.CoordinatesConfig;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RevealCommand {
    private RevealCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("incognita")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reveal")
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))));
    }

    /** Pure rule: revealed AND still an operator AND reveal still enabled in config. */
    static boolean canSee(boolean revealed, boolean isOp, boolean opCanReveal) {
        return revealed && isOp && opCanReveal;
    }

    public static boolean canSeeRealCoordinates(ServerPlayer player) {
        return canSee(RevealState.isServerRevealed(player.getUUID()), player.hasPermissions(2),
            CoordinatesConfig.opCanReveal());
    }

    private static int set(CommandSourceStack source, boolean reveal) throws CommandSyntaxException {
        if (reveal && !CoordinatesConfig.opCanReveal()) {
            source.sendFailure(Component.translatable("incognita.reveal.disabled"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        RevealState.setServerRevealed(player.getUUID(), reveal);
        PacketDistributor.sendToPlayer(player, new RevealSyncPayload(reveal));
        source.sendSuccess(() -> Component.translatable(reveal ? "incognita.reveal.on" : "incognita.reveal.off"), false);
        return 1;
    }
}
