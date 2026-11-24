package dev.sborka.incognita.presence;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config: {@code config/incognita-presence.toml}. */
public final class PresenceConfig {
    /** Discord application "Terra Incognita". Not a secret, safe to ship. */
    public static final String DEFAULT_APPLICATION_ID = "1574849152070848593";

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> APPLICATION_ID;
    private static final ModConfigSpec.ConfigValue<String> LARGE_IMAGE;
    private static final ModConfigSpec.ConfigValue<String> LARGE_TEXT;
    private static final ModConfigSpec.BooleanValue SHOW_MENU;
    private static final ModConfigSpec.IntValue UPDATE_SECONDS;
    private static final ModConfigSpec.ConfigValue<String> BUTTON_LABEL;
    private static final ModConfigSpec.ConfigValue<String> BUTTON_URL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Show what you are doing in Discord (Rich Presence). Needs the Discord desktop client running")
            .define("enabled", true);
        APPLICATION_ID = b.comment("Discord application id from the developer portal (General Information)")
            .define("applicationId", DEFAULT_APPLICATION_ID);
        LARGE_IMAGE = b.comment("Asset key of the large picture, uploaded under Rich Presence -> Art Assets")
            .define("largeImageKey", "logo");
        LARGE_TEXT = b.comment("Tooltip of the large picture")
            .define("largeImageText", "Terra Incognita");
        SHOW_MENU = b.comment("Show the presence in the main menu too")
            .define("showInMenu", true);
        UPDATE_SECONDS = b.comment("How often the presence is refreshed, in seconds")
            .defineInRange("updateSeconds", 15, 5, 120);
        BUTTON_LABEL = b.comment("Optional link button under the presence; leave empty to hide it")
            .define("buttonLabel", "GitHub");
        BUTTON_URL = b.comment("URL of the button")
            .define("buttonUrl", "https://github.com/loki5512344ss/Terra-Incognita");
        SPEC = b.build();
    }

    private PresenceConfig() {}

    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static String applicationId() {
        try {
            return APPLICATION_ID.get().trim();
        } catch (IllegalStateException e) {
            return DEFAULT_APPLICATION_ID;
        }
    }

    public static String largeImageKey() {
        try {
            return LARGE_IMAGE.get().trim();
        } catch (IllegalStateException e) {
            return "logo";
        }
    }

    public static String largeImageText() {
        try {
            return LARGE_TEXT.get().trim();
        } catch (IllegalStateException e) {
            return "Terra Incognita";
        }
    }

    public static boolean showMenu() {
        try {
            return SHOW_MENU.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int updateSeconds() {
        try {
            return UPDATE_SECONDS.get();
        } catch (IllegalStateException e) {
            return 15;
        }
    }

    public static String buttonLabel() {
        try {
            return BUTTON_LABEL.get().trim();
        } catch (IllegalStateException e) {
            return "GitHub";
        }
    }

    public static String buttonUrl() {
        try {
            return BUTTON_URL.get().trim();
        } catch (IllegalStateException e) {
            return "https://github.com/loki5512344ss/Terra-Incognita";
        }
    }
}
