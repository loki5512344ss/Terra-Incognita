package dev.sborka.incognita.coordinates.guard;


import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import dev.sborka.incognita.Incognita;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * {@code /kubejs hand|offhand|inventory|hotbar} (and the {@code /kjs} alias) print full item strings, including data
 * components with bound positions, for any player. Restricted to operators. Also catches nested use such as
 * {@code /execute run kubejs hand}. Fails open: any error lets the command run unchanged.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID)
public final class KubeJsCommandGuard {
    private static final Set<String> ROOTS = Set.of("kubejs", "kjs");
    private static final Set<String> DUMPS = Set.of("hand", "offhand", "inventory", "hotbar");

    private KubeJsCommandGuard() {}

    /** True when a root literal ("kubejs"/"kjs") is directly followed by one of the item-dump literals in any chained context. */
    static boolean isItemDump(ParseResults<?> parse) {
        // Redirects (/kjs alias, /execute run ...) split the parsed nodes across chained child contexts: flatten them.
        List<String> names = new java.util.ArrayList<>();
        CommandContextBuilder<?> context = parse.getContext();
        while (context != null) {
            for (ParsedCommandNode<?> node : context.getNodes()) {
                names.add(node.getNode().getName());
            }
            context = context.getChild();
        }
        for (int i = 0; i + 1 < names.size(); i++) {
            if (ROOTS.contains(names.get(i)) && DUMPS.contains(names.get(i + 1))) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            if (!parse.getContext().getSource().hasPermission(2) && isItemDump(parse)) {
                event.setCanceled(true);
                parse.getContext().getSource().sendFailure(Component.literal("This command is restricted to operators."));
            }
        } catch (Throwable ignored) {
            // fail open
        }
    }
}
