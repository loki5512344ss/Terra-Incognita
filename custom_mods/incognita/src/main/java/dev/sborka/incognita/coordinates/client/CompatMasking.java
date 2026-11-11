package dev.sborka.incognita.coordinates.client;

import dev.sborka.incognita.coordinates.Coordinates;
import dev.sborka.incognita.coordinates.reveal.RevealCommand;
import dev.sborka.incognita.coordinates.reveal.RevealState;

import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;

/** Helpers for the optional third-party mixins (incognita.compat.mixins.json). Every method fails open. */
public final class CompatMasking {
    private CompatMasking() {}

    /** Result for a wrapped {@code toShortString()} call on the client: the placeholder unless revealed. */
    public static String clientShortPos(String original) {
        try {
            return RevealState.isClientRevealed() ? original : Coordinates.spoofer().text();
        } catch (Throwable t) {
            return original;
        }
    }

    /** Server call site that knows the player: honour that player's reveal state. */
    public static String serverShortPos(String original, ServerPlayer player) {
        try {
            return RevealCommand.canSeeRealCoordinates(player) ? original : Coordinates.spoofer().text();
        } catch (Throwable t) {
            return original;
        }
    }

    /** Server call site without a player (shared block displays, train status): always the placeholder. */
    public static String anonymousShortPos(String original) {
        try {
            return Coordinates.spoofer().text();
        } catch (Throwable t) {
            return original;
        }
    }

    /** True when displays should show a masked version of a real value (client, not revealed). */
    public static boolean clientMasks() {
        return !RevealState.isClientRevealed();
    }

    /** One '?' per character, so the EditBox keeps its layout while the real digits stay in getValue(). */
    public static String maskText(String text) {
        return "?".repeat(text.length());
    }

    /** EditBox formatter: display-only mask while not revealed. */
    public static FormattedCharSequence formatEditBox(String text, int displayPos) {
        try {
            return FormattedCharSequence.forward(clientMasks() ? maskText(text) : text, Style.EMPTY);
        } catch (Throwable t) {
            return FormattedCharSequence.forward(text, Style.EMPTY);
        }
    }
}
