package dev.sborka.incognita.coordinates;

import dev.sborka.incognita.coordinates.rewrite.ComponentRewriter;

/** Shared instances used by every hook. */
public final class Coordinates {
    private static final Spoofer SPOOFER = new Spoofer(CoordinatesConfig::settings, System::currentTimeMillis);
    private static final ComponentRewriter REWRITER = new ComponentRewriter(SPOOFER, CoordinatesConfig::translationKeys);

    private Coordinates() {}

    public static Spoofer spoofer() {
        return SPOOFER;
    }

    public static ComponentRewriter rewriter() {
        return REWRITER;
    }
}
