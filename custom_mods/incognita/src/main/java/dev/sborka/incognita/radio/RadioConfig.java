package dev.sborka.incognita.radio;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: {@code config/incognita-radio.toml}. */
public final class RadioConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue LOCAL_CHAT;
    private static final ModConfigSpec.IntValue CHAT_RADIUS;
    private static final ModConfigSpec.BooleanValue DISABLE_GROUPS;
    private static final ModConfigSpec.BooleanValue SELF_ECHO;
    private static final ModConfigSpec.IntValue MIN_FREQ;
    private static final ModConfigSpec.IntValue MAX_FREQ;
    private static final ModConfigSpec.BooleanValue EFFECT;
    private static final ModConfigSpec.DoubleValue EFFECT_NOISE;
    private static final ModConfigSpec.DoubleValue EFFECT_DRIVE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        LOCAL_CHAT = b.comment("Normal chat is only delivered to players within chatRadius blocks (same dimension)")
            .define("localChat", true);
        CHAT_RADIUS = b.comment("Local chat radius in blocks")
            .defineInRange("chatRadius", 60, 1, 10_000);
        DISABLE_GROUPS = b.comment("Simple Voice Chat: groups are switched off completely (nobody can create or join one,",
                "the group button disappears). Long-distance voice only works through the radio. Applied when the server starts")
            .define("disableGroups", true);
        SELF_ECHO = b.comment("Testing aid: the speaker also hears their own radio transmission, so the radio can be checked alone")
            .define("selfEcho", false);
        MIN_FREQ = b.comment("Lowest frequency, in tenths of MHz (300 = 30.0)")
            .defineInRange("minFrequency", 300, 1, 99_999);
        MAX_FREQ = b.comment("Highest frequency, in tenths of MHz (1080 = 108.0)")
            .defineInRange("maxFrequency", 1080, 1, 99_999);
        EFFECT = b.comment("Apply the radio voice effect (band-pass, distortion, static) to transmitted voice")
            .define("voiceEffect", true);
        EFFECT_NOISE = b.comment("Static level, 0 = none")
            .defineInRange("effectNoise", 0.02, 0.0, 0.5);
        EFFECT_DRIVE = b.comment("Distortion amount, 1 = almost clean")
            .defineInRange("effectDrive", 2.5, 1.0, 10.0);
        SPEC = b.build();
    }

    private RadioConfig() {}

    public static boolean localChat() {
        try { return LOCAL_CHAT.get(); } catch (IllegalStateException e) { return true; }
    }

    public static int chatRadius() {
        try { return CHAT_RADIUS.get(); } catch (IllegalStateException e) { return 60; }
    }

    public static boolean disableGroups() {
        try { return DISABLE_GROUPS.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean selfEcho() {
        try { return SELF_ECHO.get(); } catch (IllegalStateException e) { return false; }
    }

    public static int minFreq() {
        try { return Math.min(MIN_FREQ.get(), MAX_FREQ.get()); } catch (IllegalStateException e) { return 300; }
    }

    public static int maxFreq() {
        try { return Math.max(MIN_FREQ.get(), MAX_FREQ.get()); } catch (IllegalStateException e) { return 1080; }
    }

    public static boolean effect() {
        try { return EFFECT.get(); } catch (IllegalStateException e) { return true; }
    }

    public static double effectNoise() {
        try { return EFFECT_NOISE.get(); } catch (IllegalStateException e) { return 0.02; }
    }

    public static double effectDrive() {
        try { return EFFECT_DRIVE.get(); } catch (IllegalStateException e) { return 2.5; }
    }
}
