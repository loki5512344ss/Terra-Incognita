package dev.sborka.incognita.presence.ipc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** One Rich Presence payload. Empty lines are simply omitted from the card. */
public record DiscordActivity(
    String details,
    String state,
    long startMillis,
    String largeImage,
    String largeText,
    String buttonLabel,
    String buttonUrl
) {
    private static final int MAX_TEXT = 128;
    private static final int MAX_BUTTON_LABEL = 32;
    private static final int MAX_BUTTON_URL = 512;

    /** Discord rejects an activity without any text, so an empty payload means "nothing to show". */
    public boolean hasContent() {
        return !blank(details) || !blank(state);
    }

    JsonObject toJson() {
        JsonObject activity = new JsonObject();
        if (!blank(details)) {
            activity.addProperty("details", cut(details, MAX_TEXT));
        }
        if (!blank(state)) {
            activity.addProperty("state", cut(state, MAX_TEXT));
        }
        if (startMillis > 0L) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", startMillis);
            activity.add("timestamps", timestamps);
        }
        if (!blank(largeImage)) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", largeImage);
            if (!blank(largeText)) {
                assets.addProperty("large_text", cut(largeText, MAX_TEXT));
            }
            activity.add("assets", assets);
        }
        if (!blank(buttonLabel) && !blank(buttonUrl)) {
            JsonObject button = new JsonObject();
            button.addProperty("label", cut(buttonLabel, MAX_BUTTON_LABEL));
            button.addProperty("url", cut(buttonUrl, MAX_BUTTON_URL));
            JsonArray buttons = new JsonArray();
            buttons.add(button);
            activity.add("buttons", buttons);
        }
        return activity;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
