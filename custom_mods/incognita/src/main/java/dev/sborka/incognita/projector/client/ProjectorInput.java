package dev.sborka.incognita.projector.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.projector.ProjectorModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Controls while the projector is in hand: sneak + scroll picks the structure, right click on a block puts
 * the ghost there, R rotates, Z mirrors, X removes the ghost.
 */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class ProjectorInput {
    private static final String CATEGORY = "key.categories.incognita";
    static final KeyMapping ROTATE = new KeyMapping("key.incognita.projector.rotate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    static final KeyMapping MIRROR = new KeyMapping("key.incognita.projector.mirror", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    static final KeyMapping CLEAR = new KeyMapping("key.incognita.projector.clear", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private ProjectorInput() {}

    @EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(ROTATE);
            event.register(MIRROR);
            event.register(CLEAR);
        }
    }

    private static void ensureLoaded() {
        if (!ProjectionState.loaded()) {
            ProjectionState.reload();
        }
    }

    private static void status(Minecraft mc) {
        if (mc.player == null) return;
        var source = ProjectionState.current();
        Component line = source == null
            ? Component.translatable("incognita.projector.none")
            : Component.translatable("incognita.projector.status", source.name(),
                ProjectionState.position(), ProjectionState.count(), ProjectionState.turns() * 90,
                Component.translatable(ProjectionState.mirrored() ? "incognita.projector.mirrored" : "incognita.projector.normal"));
        mc.player.displayClientMessage(line, true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isShiftKeyDown() || !ProjectionState.holdingProjector(mc.player)) {
            return;
        }
        ensureLoaded();
        ProjectionState.cycle(event.getScrollDeltaY() > 0 ? -1 : 1);
        status(mc);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
            || !event.getItemStack().is(ProjectorModule.PROJECTOR.get())) {
            return;
        }
        ensureLoaded();
        ProjectionState.setAnchor(event.getPos().relative(event.getFace()));
        status(Minecraft.getInstance());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ProjectionState.holdingProjector(mc.player)) {
            while (ROTATE.consumeClick() || MIRROR.consumeClick() || CLEAR.consumeClick()) {
                // keys only work with the projector in hand
            }
            return;
        }
        boolean changed = false;
        while (ROTATE.consumeClick()) {
            ProjectionState.rotate();
            changed = true;
        }
        while (MIRROR.consumeClick()) {
            ProjectionState.flip();
            changed = true;
        }
        while (CLEAR.consumeClick()) {
            ProjectionState.clear();
            changed = true;
        }
        if (changed) {
            ensureLoaded();
            status(mc);
        }
    }
}
