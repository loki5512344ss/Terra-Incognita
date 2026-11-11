package dev.sborka.incognita.coordinates.rewrite;

import dev.sborka.incognita.coordinates.Spoofer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class NbtPrettyMaskTest {
    private static ComponentRewriter rewriter() {
        Spoofer spoofer = new Spoofer(() -> new Spoofer.Settings("???", 0.0, Spoofer.Mode.CONSTANT, 3), () -> 0L);
        return new ComponentRewriter(spoofer, () -> java.util.Set.of());
    }

    @Test
    void posDoubleListInPrettyNbtIsMasked() {
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(1.5));
        pos.add(DoubleTag.valueOf(64.0));
        pos.add(DoubleTag.valueOf(-3.25));
        tag.put("Pos", pos);
        tag.put("CustomName", StringTag.valueOf("Bob"));
        Component pretty = NbtUtils.toPrettyComponent(tag);
        assertTrue(pretty.getString().contains("1.5"), "precondition: unmasked output holds the number");
        String out = rewriter().rewrite(Component.translatable("commands.data.entity.query", "Bob", pretty)).getString();
        assertFalse(out.contains("64.0"), out);
        assertFalse(out.contains("1.5"), out);
        assertTrue(out.contains("Pos: [???]") || out.contains("???"), out);
        assertTrue(out.contains("Bob"), out);
    }

    @Test
    void intArrayAndBlockXyzCompoundAreMasked() {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", new IntArrayTag(new int[] {12, 70, -300}));
        String out = rewriter().rewrite(NbtUtils.toPrettyComponent(tag)).getString();
        assertFalse(out.contains("70"), out);
        assertFalse(out.contains("300"), out);
    }

    @Test
    void nbtWithoutTripleKeepsColouringAndText() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Count", 5);
        tag.putString("id", "minecraft:stone");
        Component pretty = NbtUtils.toPrettyComponent(tag);
        Component out = rewriter().rewrite(pretty);
        assertEquals(pretty.getString(), out.getString());
        assertEquals(pretty.getSiblings().size(), out.getSiblings().size());
    }

    @Test
    void twoNumberListIsNotTouched() {
        CompoundTag tag = new CompoundTag();
        ListTag rot = new ListTag();
        rot.add(net.minecraft.nbt.FloatTag.valueOf(90.0f));
        rot.add(net.minecraft.nbt.FloatTag.valueOf(0.0f));
        tag.put("Rotation", rot);
        String text = NbtUtils.toPrettyComponent(tag).getString();
        assertEquals(text, rewriter().rewrite(NbtUtils.toPrettyComponent(tag)).getString());
    }
}
