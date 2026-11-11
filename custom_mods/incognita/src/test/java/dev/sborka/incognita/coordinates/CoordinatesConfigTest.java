package dev.sborka.incognita.coordinates;

import dev.sborka.incognita.coordinates.rewrite.ComponentRewriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class CoordinatesConfigTest {
    @Test
    void configFallsBackToDefaultsBeforeLoad() {
        assertFalse(CoordinatesConfig.translationKeys().contains("chat.coordinates"));
        assertTrue(CoordinatesConfig.translationKeys().contains("commands.locate.*"));
        assertTrue(CoordinatesConfig.opCanReveal());
        assertEquals("???", CoordinatesConfig.settings().placeholderText());
    }

    private static ComponentRewriter rewriterWith(java.util.Set<String> keys) {
        Spoofer spoofer = new Spoofer(() -> new Spoofer.Settings("???", 0.0, Spoofer.Mode.CONSTANT, 3), () -> 0L);
        return new ComponentRewriter(spoofer, () -> keys);
    }

    @Test
    void effectiveKeysAreConfiguredUnionDefaults() {
        java.util.Set<String> keys = CoordinatesConfig.effectiveKeys(java.util.List.of("old.key"));
        assertTrue(keys.contains("old.key"));
        assertTrue(keys.containsAll(CoordinatesConfig.DEFAULT_KEYS));
        assertTrue(CoordinatesConfig.effectiveKeys(java.util.List.of()).containsAll(CoordinatesConfig.DEFAULT_KEYS));
    }

    @Test
    void everyDefaultKeyMasksNumericArguments() {
        ComponentRewriter rewriter = rewriterWith(CoordinatesConfig.effectiveKeys(java.util.List.of()));
        for (String pattern : CoordinatesConfig.DEFAULT_KEYS) {
            String key = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) + "sample" : pattern;
            Component out = rewriter.rewrite(Component.translatable(key, 11, 22, 33));
            assertEquals("???", ((TranslatableContents) out.getContents()).getArgs()[0], key);
            assertEquals("???", ((TranslatableContents) out.getContents()).getArgs()[2], key);
        }
    }

    @Test
    void numberLiteralComponentArgIsMaskedForListedKeys() {
        ComponentRewriter rewriter = rewriterWith(CoordinatesConfig.effectiveKeys(java.util.List.of()));
        Component out = rewriter.rewrite(Component.translatable("simulated.altitude_sensor.height", Component.literal("123.45")));
        assertEquals("???", ((Component) ((TranslatableContents) out.getContents()).getArgs()[0]).getString());
        Component other = rewriter.rewrite(Component.translatable("some.other", Component.literal("123.45")));
        assertEquals("123.45", ((Component) ((TranslatableContents) other.getContents()).getArgs()[0]).getString());
    }

    @Test
    void maskedNumberLiteralLosesHoverAndClickEvents() {
        ComponentRewriter rewriter = rewriterWith(CoordinatesConfig.effectiveKeys(java.util.List.of()));
        net.minecraft.network.chat.Style styled = net.minecraft.network.chat.Style.EMPTY
            .withColor(net.minecraft.ChatFormatting.GOLD)
            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tp 1 2 3"))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("123")));
        Component out = rewriter.rewrite(Component.translatable("simulated.altitude_sensor.height", Component.literal("123").setStyle(styled)));
        Component arg = (Component) ((TranslatableContents) out.getContents()).getArgs()[0];
        org.junit.jupiter.api.Assertions.assertEquals("???", arg.getString());
        org.junit.jupiter.api.Assertions.assertNull(arg.getStyle().getClickEvent());
        org.junit.jupiter.api.Assertions.assertNull(arg.getStyle().getHoverEvent());
        assertEquals(net.minecraft.ChatFormatting.GOLD.getColor(), arg.getStyle().getColor().getValue());
    }

    @Test
    void maskedNumberLiteralStripsAnyClickAndHoverUnconditionally() {
        ComponentRewriter rewriter = rewriterWith(CoordinatesConfig.effectiveKeys(java.util.List.of()));
        net.minecraft.network.chat.Style styled = net.minecraft.network.chat.Style.EMPTY
            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, "12"))
            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("value 12")));
        Component out = rewriter.rewrite(Component.translatable("create.portal_track.blocked", Component.literal("12").setStyle(styled)));
        Component arg = (Component) ((TranslatableContents) out.getContents()).getArgs()[0];
        assertEquals("???", arg.getString());
        org.junit.jupiter.api.Assertions.assertNull(arg.getStyle().getClickEvent());
        org.junit.jupiter.api.Assertions.assertNull(arg.getStyle().getHoverEvent());
    }
}
