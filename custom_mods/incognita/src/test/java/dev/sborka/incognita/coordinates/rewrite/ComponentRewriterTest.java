package dev.sborka.incognita.coordinates.rewrite;

import dev.sborka.incognita.coordinates.Spoofer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ComponentRewriterTest {
    private static final Set<String> KEYS = Set.of("commands.teleport.success.location.*", "commands.locate.*", "exact.key");

    private static ComponentRewriter rewriter() {
        Spoofer spoofer = new Spoofer(() -> new Spoofer.Settings("???", 0.0, Spoofer.Mode.CONSTANT, 3), () -> 0L);
        return new ComponentRewriter(spoofer, () -> KEYS);
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }

    @Test
    void chatCoordinatesBecomesPlaceholder() {
        Component in = Component.translatable("chat.coordinates", 10, 64, -20);
        assertEquals("???", rewriter().rewrite(in).getString());
    }

    @Test
    void numberArgsOfListedKeyBecomeTextPlaceholder() {
        Component in = Component.translatable("commands.teleport.success.location.single", "Steve", 10.5, 64, -20);
        Component out = rewriter().rewrite(in);
        assertArrayEquals(new Object[] {"Steve", "???", "???", "???"}, args(out));
    }

    @Test
    void numericStringArgsOfListedKeyBecomeTextPlaceholder() {
        Component in = Component.translatable("exact.key", "Steve", "10.50", "64", "-20");
        Component out = rewriter().rewrite(in);
        assertArrayEquals(new Object[] {"Steve", "???", "???", "???"}, args(out));
    }

    @Test
    void prefixKeyMatches() {
        Component in = Component.translatable("commands.locate.structure.success", "village", "x", 120);
        assertArrayEquals(new Object[] {"village", "x", "???"}, args(rewriter().rewrite(in)));
    }

    @Test
    void unlistedKeysKeepTheirArguments() {
        Component in = Component.translatable("some.other.key", 5, "12");
        assertArrayEquals(new Object[] {5, "12"}, args(rewriter().rewrite(in)));
    }

    @Test
    void nestedComponentArgsAreRewritten() {
        Component nested = Component.translatable("chat.coordinates", 1, 2, 3);
        Component in = Component.translatable("commands.locate.structure.success", "village", nested, 5);
        Component out = rewriter().rewrite(in);
        assertEquals("???", ((Component) args(out)[1]).getString());
    }

    @Test
    void literalTriplesAreReplaced() {
        assertEquals("Position: ???", rewriter().rewrite(Component.literal("Position: 1.00 2.00 3.00")).getString());
        assertEquals("???", rewriter().rewrite(Component.literal("[10, 64, -20]")).getString());
        assertEquals("Cannon at ??? ok", rewriter().rewrite(Component.literal("Cannon at [1, 2, 3] ok")).getString());
    }

    @Test
    void ordinaryNumbersAreKept() {
        assertEquals("I have 3 apples", rewriter().rewrite(Component.literal("I have 3 apples")).getString());
        assertEquals("1 2", rewriter().rewrite(Component.literal("1 2")).getString());
    }

    @Test
    void siblingsAreRewritten() {
        MutableComponent in = Component.literal("at ").append(Component.literal("[1, 2, 3]"));
        assertEquals("at ???", rewriter().rewrite(in).getString());
    }

    @Test
    void teleportClickEventAndHoverAreRemoved() {
        Component in = Component.literal("[x]").withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/execute in minecraft:overworld run tp @s 1 ~ 2"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to teleport"))));
        Component out = rewriter().rewrite(in);
        assertNull(out.getStyle().getClickEvent());
        assertNull(out.getStyle().getHoverEvent());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN), out.getStyle().getColor());
    }

    @Test
    void otherClickEventsAreKept() {
        Component in = Component.literal("hi").withStyle(style -> style
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/say hi"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("say"))));
        Component out = rewriter().rewrite(in);
        assertNotNull(out.getStyle().getClickEvent());
        assertNotNull(out.getStyle().getHoverEvent());
    }

    @Test
    void rewriteIsExactAndLeavesInputUntouched() {
        Component in = Component.literal("at [1, 2, 3]");
        Component out = rewriter().rewrite(in);
        assertEquals("at ???", out.getString());
        assertEquals("at [1, 2, 3]", in.getString());
    }

    @Test
    void coordinateShapesAreReplaced() {
        for (String text : new String[] {
            "1 2 3 4", "[1, 2, 3, 4]", "90 10 64 -20", "10,50 64,00 -20,00", "x: 1 y: 2 z: 3", "(1.0, 2.0, 3.0)",
            "X=1, Y=2, Z=3"}) {
            assertEquals("???", rewriter().rewrite(Component.literal(text)).getString(), text);
        }
        assertEquals("BlockPos{???}", rewriter().rewrite(Component.literal("BlockPos{x=10, y=64, z=-20}")).getString());
    }

    @Test
    void nonCoordinateTextIsKept() {
        for (String text : new String[] {"1 2", "I have 3 apples", "Minecraft 1.21.1", "12:30:45"}) {
            assertEquals(text, rewriter().rewrite(Component.literal(text)).getString(), text);
        }
    }

    @Test
    void commandsKeyWithThreeNumberArgsIsScrubbed() {
        Component in = Component.translatable("commands.setblock.success", 10, 64, -20);
        assertArrayEquals(new Object[] {"???", "???", "???"}, args(rewriter().rewrite(in)));
    }

    @Test
    void commandsKeyRunOfMixedNumericArgsIsScrubbed() {
        Component in = Component.translatable("commands.some.key", "Steve", 10, "64", -20.5, "end");
        assertArrayEquals(new Object[] {"Steve", "???", "???", "???", "end"}, args(rewriter().rewrite(in)));
    }

    @Test
    void nonCommandsKeyWithThreeNumberArgsIsKept() {
        Component in = Component.translatable("toms_storage.inventory_connector.free", 184, 216, 4);
        assertArrayEquals(new Object[] {184, 216, 4}, args(rewriter().rewrite(in)));
    }

    @Test
    void hoverTextIsRewrittenRecursively() {
        Component in = Component.literal("hi").withStyle(style -> style
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("at 1 2 3"))));
        Component out = rewriter().rewrite(in);
        assertEquals("at ???", out.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT).getString());
    }

    @Test
    void setblockClickWithCoordinatesIsRemoved() {
        Component in = Component.literal("[x]").withStyle(style -> style
            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/setblock 1 2 3 stone"))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("x"))));
        Component out = rewriter().rewrite(in);
        assertNull(out.getStyle().getClickEvent());
        assertNull(out.getStyle().getHoverEvent());
    }

    @Test
    void failureReturnsTheOriginalMessage() {
        Spoofer broken = new Spoofer(() -> {
            throw new IllegalStateException("boom");
        }, () -> 0L);
        ComponentRewriter rewriter = new ComponentRewriter(broken, () -> KEYS);
        Component in = Component.literal("[1, 2, 3]");
        assertSame(in, rewriter.rewriteSafely(in));
    }

    @Test
    void brokenSpooferNeverThrowsAcrossRepeatedCalls() {
        Spoofer broken = new Spoofer(() -> {
            throw new IllegalStateException("boom");
        }, () -> 0L);
        ComponentRewriter rewriter = new ComponentRewriter(broken, () -> KEYS);
        Component in = Component.literal("[1, 2, 3]");
        assertSame(in, rewriter.rewriteSafely(in));
        assertSame(in, rewriter.rewriteSafely(in));
        assertTrue(rewriter.hasWarned());
    }
}
