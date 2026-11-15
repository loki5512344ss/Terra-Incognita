package dev.sborka.incognita.radio.voice;

import dev.sborka.incognita.Incognita;

/**
 * Switches Simple Voice Chat groups off for the running server, the same as {@code enable_groups=false} in
 * voicechat-server.properties. With the option off the voice chat server refuses create/join requests and tells every
 * client to hide the group button, so there is nothing left to click. The setting is not part of the public API, so it
 * is reached by reflection; if a future voice chat version moves it, {@link RadioRouter} still cancels the group events.
 */
final class GroupLock {
    private GroupLock() {}

    static boolean apply() {
        try {
            Object config = Class.forName("de.maxhenkel.voicechat.Voicechat").getField("SERVER_CONFIG").get(null);
            Object entry = config.getClass().getField("groupsEnabled").get(config);
            Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry")
                .getMethod("set", Object.class).invoke(entry, Boolean.FALSE);
            Incognita.LOGGER.info("Voice chat groups are switched off, long-distance voice goes through the radio");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Incognita.LOGGER.warn("Could not switch voice chat groups off in its config; group requests are still refused, "
                + "but the group button stays visible. Set enable_groups=false in config/voicechat/voicechat-server.properties", e);
            return false;
        }
    }
}
