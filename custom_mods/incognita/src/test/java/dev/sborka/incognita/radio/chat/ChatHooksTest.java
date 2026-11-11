package dev.sborka.incognita.radio.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ChatHooksTest {
    @Test
    void insideRadiusIsHeard() {
        assertTrue(ChatHooks.within(new Vec3(0, 64, 0), new Vec3(59, 64, 0), 60));
        assertTrue(ChatHooks.within(new Vec3(0, 64, 0), new Vec3(60, 64, 0), 60));
    }

    @Test
    void outsideRadiusIsNot() {
        assertFalse(ChatHooks.within(new Vec3(0, 64, 0), new Vec3(61, 64, 0), 60));
    }

    @Test
    void distanceIsThreeDimensional() {
        assertFalse(ChatHooks.within(new Vec3(0, 0, 0), new Vec3(40, 40, 40), 60));
    }
}
