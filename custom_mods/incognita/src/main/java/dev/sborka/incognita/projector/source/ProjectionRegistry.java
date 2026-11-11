package dev.sborka.incognita.projector.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sources registered from code. Public API: {@code ProjectionRegistry.register(source)}.
 * The client adds Immersive Engineering multiblocks and NBT files on top of these when it builds its list.
 */
public final class ProjectionRegistry {
    private static final List<ProjectionSource> REGISTERED = new CopyOnWriteArrayList<>();

    private ProjectionRegistry() {}

    public static void register(ProjectionSource source) {
        REGISTERED.removeIf(s -> s.id().equals(source.id()));
        REGISTERED.add(source);
    }

    public static List<ProjectionSource> registered() {
        return List.copyOf(REGISTERED);
    }

    /** Stable order for the selection list: by display name. */
    public static List<ProjectionSource> sorted(List<ProjectionSource> sources) {
        List<ProjectionSource> out = new ArrayList<>(sources);
        out.sort(Comparator.comparing((ProjectionSource s) -> s.name().getString()).thenComparing(s -> s.id().toString()));
        return out;
    }
}
