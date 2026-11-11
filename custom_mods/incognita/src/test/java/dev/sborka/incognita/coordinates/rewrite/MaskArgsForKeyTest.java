package dev.sborka.incognita.coordinates.rewrite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Set;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class MaskArgsForKeyTest {
    private static final Set<String> KEYS = Set.of("exact.key", "prefix.*");

    private static Object[] mask(String key, Object... args) {
        return ComponentRewriter.maskArgsForKey(key, args, KEYS, () -> "???");
    }

    @Test
    void masksNumbersAndNumericStringsOfListedKeys() {
        assertArrayEquals(new Object[] {"Steve", "???", "???", "???"}, mask("exact.key", "Steve", 1, "2.5", -3L));
        assertArrayEquals(new Object[] {"???"}, mask("prefix.deep.key", 7));
    }

    @Test
    void doesNotMutateInputAndReturnsSameArrayWhenNothingChanges() {
        Object[] in = {1, 2};
        Object[] out = ComponentRewriter.maskArgsForKey("exact.key", in, KEYS, () -> "???");
        assertArrayEquals(new Object[] {1, 2}, in);
        assertArrayEquals(new Object[] {"???", "???"}, out);
        Object[] words = {"a", "b"};
        assertSame(words, ComponentRewriter.maskArgsForKey("exact.key", words, KEYS, () -> "???"));
    }

    @Test
    void unlistedKeysAreUntouched() {
        Object[] in = {1, 2, 3};
        assertSame(in, ComponentRewriter.maskArgsForKey("other.key", in, KEYS, () -> "???"));
    }

    @Test
    void literalNumberComponentsAreMaskedButNamesKept() {
        Object[] out = mask("exact.key", Component.literal("12.50"), Component.literal("Stone"));
        assertEquals("???", ((Component) out[0]).getString());
        assertEquals("Stone", ((Component) out[1]).getString());
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(ComponentRewriter.maskArgsForKey("exact.key", null, KEYS, () -> "???"));
        Object[] empty = {};
        assertSame(empty, ComponentRewriter.maskArgsForKey("exact.key", empty, KEYS, () -> "???"));
        Object[] in = {1};
        assertSame(in, ComponentRewriter.maskArgsForKey(null, in, KEYS, () -> "???"));
    }

    @Test
    void preformattedPositionStringsOfListedKeysAreScrubbed() {
        Object[] out = mask("exact.key", "minecraft:overworld / [10,64,-20]", "Steve");
        assertArrayEquals(new Object[] {"minecraft:overworld / ???", "Steve"}, out);
        assertArrayEquals(new Object[] {"x:???"}, new Object[] {"x:" + mask("exact.key", 5)[0]});
        assertArrayEquals(new Object[] {"???"}, mask("exact.key", "[1,2,3]"));
    }

    @Test
    void chunkPairLiteralsAndListsAreMaskedOnlyForListedKeys() {
        Object[] out = mask("exact.key", Component.literal("[12, -34]"), "[0, 0], [1, 0]", Component.literal("[1, 2] stays"));
        assertEquals("???", ((Component) out[0]).getString());
        assertEquals("???", out[1]);
        assertEquals("[1, 2] stays", ((Component) out[2]).getString());
        Object[] unlisted = {Component.literal("[12, -34]")};
        assertSame(unlisted, ComponentRewriter.maskArgsForKey("other.key", unlisted, KEYS, () -> "???"));
    }
}
