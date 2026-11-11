package dev.sborka.incognita.coordinates;

import java.util.Objects;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Single source of placeholder values for every hidden coordinate. */
public final class Spoofer {
    public enum Mode { CONSTANT, ROTATING }

    public record Settings(String placeholderText, double numericFallback, Mode mode, int rotatingIntervalSeconds) {
        public Settings {
            Objects.requireNonNull(placeholderText, "placeholderText");
            Objects.requireNonNull(mode, "mode");
            if (rotatingIntervalSeconds < 1) {
                rotatingIntervalSeconds = 1;
            }
        }
    }

    private static final int ROTATING_RANGE = 30_000;
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

    private final Supplier<Settings> settings;
    private final LongSupplier clockMillis;

    public Spoofer(Supplier<Settings> settings, LongSupplier clockMillis) {
        this.settings = settings;
        this.clockMillis = clockMillis;
    }

    /** Placeholder for text contexts. */
    public String text() {
        Settings s = settings.get();
        return s.mode() == Mode.CONSTANT ? s.placeholderText() : Integer.toString(rotating(s));
    }

    /** Placeholder for contexts that need a number. */
    public double number() {
        Settings s = settings.get();
        return s.mode() == Mode.CONSTANT ? s.numericFallback() : rotating(s);
    }

    private int rotating(Settings s) {
        long interval = clockMillis.getAsLong() / (s.rotatingIntervalSeconds() * 1000L);
        return new Random(interval * SEED_MIX).nextInt(2 * ROTATING_RANGE + 1) - ROTATING_RANGE;
    }
}
