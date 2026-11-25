package dev.sborka.incognita.weather.fog.control;

import dev.sborka.incognita.weather.fog.FogModel;

/**
 * What the server's operators decided about the fog, on top of the natural events of {@link FogModel}. Immutable and
 * free of Minecraft types; the client keeps the copy the server sent.
 *
 * @param mode        which rule applies
 * @param density     density of a forced fog, 0..1
 * @param since       game time a forced fog started
 * @param until       game time a forced or cleared state ends
 * @param frequency   event frequency set by command, or a negative value to use the config
 */
public record FogOverride(Mode mode, float density, long since, long until, float frequency) {
    public enum Mode { NATURAL, OFF, CLEARED, FORCED }

    public static final FogOverride NONE = new FogOverride(Mode.NATURAL, 0.0f, 0L, 0L, -1.0f);
    /** A forced fog takes this long to roll in, and again to roll out. */
    private static final double RAMP_TICKS = 200.0;
    private static final long DAY_TICKS = 24_000L;

    private static volatile FogOverride client = NONE;

    /** The state last received from the server. */
    public static FogOverride client() {
        return client;
    }

    public static void setClient(FogOverride state) {
        client = state;
    }

    public FogOverride natural() {
        return new FogOverride(Mode.NATURAL, 0.0f, 0L, 0L, frequency);
    }

    public FogOverride off() {
        return new FogOverride(Mode.OFF, 0.0f, 0L, 0L, frequency);
    }

    /** Hides the current fog until the game day ends; there is at most one natural fog per game day. */
    public FogOverride cleared(long now) {
        if (mode == Mode.OFF) {
            return this;
        }
        long dayEnd = (Math.floorDiv(now, DAY_TICKS) + 1) * DAY_TICKS;
        return new FogOverride(Mode.CLEARED, 0.0f, now, dayEnd, frequency);
    }

    public FogOverride forced(float strength, long now, long duration) {
        return new FogOverride(Mode.FORCED, strength, now, now + duration, frequency);
    }

    /** @param percent chance of a natural fog per game day, 0..100 */
    public FogOverride withDailyChance(double percent) {
        float value = (float) (percent / 100.0 / FogModel.DAILY_CHANCE);
        return new FogOverride(mode, density, since, until, value);
    }

    public double frequency(double configured) {
        return frequency < 0.0f ? configured : frequency;
    }

    /** The density to show, given what the natural weather would show. */
    public double apply(double natural, long now) {
        return switch (mode) {
            case NATURAL -> natural;
            case OFF -> 0.0;
            case CLEARED -> now < until ? 0.0 : natural;
            case FORCED -> now < until ? blend(natural, now) : natural;
        };
    }

    private double blend(double natural, long now) {
        double ramp = Math.min((now - since) / RAMP_TICKS, (until - now) / RAMP_TICKS);
        double weight = Math.max(0.0, Math.min(1.0, ramp));
        return density * weight + natural * (1.0 - weight);
    }
}
