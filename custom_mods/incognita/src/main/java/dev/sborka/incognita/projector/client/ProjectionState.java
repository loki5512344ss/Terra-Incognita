package dev.sborka.incognita.projector.client;

import dev.sborka.incognita.projector.ProjectorModule;
import dev.sborka.incognita.projector.source.IeSources;
import dev.sborka.incognita.projector.source.NbtSources;
import dev.sborka.incognita.projector.source.ProjectionRegistry;
import dev.sborka.incognita.projector.source.ProjectionSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** What the local player is projecting right now. Client only, lives until the game closes. */
public final class ProjectionState {
    private static List<ProjectionSource> sources = List.of();
    private static int index;
    private static BlockPos anchor;
    private static int turns;
    private static boolean mirror;
    private static ProjectionTransform.Result cached;

    private ProjectionState() {}

    public static boolean holdingProjector(Player player) {
        return isProjector(player.getMainHandItem()) || isProjector(player.getOffhandItem());
    }

    private static boolean isProjector(ItemStack stack) {
        return stack.is(ProjectorModule.PROJECTOR.get());
    }

    /** Rebuilds the list of selectable structures (called when the projector is first used). */
    public static void reload() {
        String keep = current() == null ? null : current().id().toString();
        List<ProjectionSource> all = new ArrayList<>(ProjectionRegistry.registered());
        if (ModList.get().isLoaded("immersiveengineering")) {
            all.addAll(IeSources.all());
        }
        all.addAll(NbtSources.all());
        sources = ProjectionRegistry.sorted(all);
        index = 0;
        if (keep != null) {
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).id().toString().equals(keep)) index = i;
            }
        }
        cached = null;
    }

    public static boolean loaded() {
        return !sources.isEmpty();
    }

    public static ProjectionSource current() {
        return sources.isEmpty() ? null : sources.get(Math.floorMod(index, sources.size()));
    }

    public static int count() {
        return sources.size();
    }

    public static int position() {
        return sources.isEmpty() ? 0 : Math.floorMod(index, sources.size()) + 1;
    }

    public static void cycle(int delta) {
        if (!sources.isEmpty()) {
            index = Math.floorMod(index + delta, sources.size());
            cached = null;
        }
    }

    public static void setAnchor(BlockPos pos) {
        anchor = pos;
    }

    public static BlockPos anchor() {
        return anchor;
    }

    public static void clear() {
        anchor = null;
    }

    public static void rotate() {
        turns = (turns + 1) % 4;
        cached = null;
    }

    public static void flip() {
        mirror = !mirror;
        cached = null;
    }

    public static int turns() {
        return turns;
    }

    public static boolean mirrored() {
        return mirror;
    }

    /** Transformed blocks of the selected structure, computed once per change of structure/rotation/mirror. */
    public static ProjectionTransform.Result result() {
        if (cached == null && current() != null && Minecraft.getInstance().level != null) {
            cached = ProjectionTransform.transform(current().blocks(Minecraft.getInstance().level), turns, mirror);
        }
        return cached;
    }
}
