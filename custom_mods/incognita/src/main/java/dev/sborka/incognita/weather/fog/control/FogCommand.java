package dev.sborka.incognita.weather.fog.control;

import dev.sborka.incognita.weather.fog.FogModel;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Operator commands: {@code /incognita fog status|on|off|set|chance} and {@code /weather fog}. The vanilla
 * {@code /weather clear} also ends the current fog.
 */
public final class FogCommand {
    private static final int DEFAULT_SECONDS = 360;
    private static final int MAX_SECONDS = 86_400;
    private static final float DEFAULT_DENSITY = 0.9f;
    private static final int TICKS_PER_SECOND = 20;

    private FogCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("incognita")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("fog")
                .then(Commands.literal("status").executes(FogCommand::status))
                .then(Commands.literal("on").executes(ctx -> change(ctx, FogOverride::natural, "on")))
                .then(Commands.literal("off").executes(ctx -> change(ctx, FogOverride::off, "off")))
                .then(Commands.literal("set")
                    .then(Commands.argument("density", FloatArgumentType.floatArg(0.05f, 1.0f))
                        .executes(ctx -> force(ctx, FloatArgumentType.getFloat(ctx, "density"), DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(20, MAX_SECONDS))
                            .executes(ctx -> force(ctx, FloatArgumentType.getFloat(ctx, "density"),
                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("chance")
                    .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                        .executes(ctx -> {
                            double percent = DoubleArgumentType.getDouble(ctx, "percent");
                            return change(ctx, state -> state.withDailyChance(percent), "chance", percent);
                        })))));
        // Merged into the vanilla /weather command, which keeps its own permission check
        event.getDispatcher().register(Commands.literal("weather")
            .then(Commands.literal("fog")
                .executes(ctx -> force(ctx, DEFAULT_DENSITY, DEFAULT_SECONDS))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(20, MAX_SECONDS))
                    .executes(ctx -> force(ctx, DEFAULT_DENSITY, IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var parsed = event.getParseResults();
        String line = parsed.getReader().getString();
        String command = line.startsWith("/") ? line.substring(1) : line;
        CommandSourceStack source = parsed.getContext().getSource();
        if (("weather clear".equals(command) || command.startsWith("weather clear "))
            && parsed.getExceptions().isEmpty() && source.hasPermission(2)) {
            MinecraftServer server = source.getServer();
            FogSavedData data = FogSavedData.get(server);
            data.update(server, data.state().cleared(server.overworld().getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FogSavedData.get(player.server).send(player);
        }
    }

    private static int force(CommandContext<CommandSourceStack> ctx, float density, int seconds) {
        long now = ctx.getSource().getServer().overworld().getGameTime();
        return change(ctx, state -> state.forced(density, now, (long) seconds * TICKS_PER_SECOND),
            "set", density, seconds);
    }

    private static int change(CommandContext<CommandSourceStack> ctx, UnaryOperator<FogOverride> change,
                              String message, Object... args) {
        MinecraftServer server = ctx.getSource().getServer();
        FogSavedData data = FogSavedData.get(server);
        data.update(server, change.apply(data.state()));
        ctx.getSource().sendSuccess(() -> Component.translatable("incognita.fog." + message, args), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        FogOverride state = FogSavedData.get(ctx.getSource().getServer()).state();
        String chance = state.frequency() < 0.0f ? "config"
            : Math.round(state.frequency() * 1000.0 * FogModel.DAILY_CHANCE) / 10.0 + "%";
        ctx.getSource().sendSuccess(() -> Component.translatable("incognita.fog.status",
            state.mode().name(), chance), false);
        return 1;
    }
}
