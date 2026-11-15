package dev.sborka.incognita.compass.client;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.compass.CompassModule;
import dev.sborka.incognita.compass.HomeTarget;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Needles: the vanilla compass follows the player's own spawn point, the origin compass follows block 0 0. */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class CompassClient {
    private static final ResourceLocation ANGLE = ResourceLocation.withDefaultNamespace("angle");
    /** Tint of the origin compass, so it is told apart from the vanilla one (ARGB). */
    private static final int ORIGIN_TINT = 0xFF8FD8FF;

    private CompassClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Replaces the vanilla property (world spawn). A lodestone binding, if one exists, still wins.
            ItemProperties.register(Items.COMPASS, ANGLE, new CompassItemPropertyFunction((level, stack, entity) -> {
                LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                if (tracker != null) {
                    return tracker.target().orElse(null);
                }
                GlobalPos home = HomeTarget.client();
                return home;
            }));
            ItemProperties.register(CompassModule.ORIGIN_COMPASS.get(), ANGLE,
                new CompassItemPropertyFunction((level, stack, entity) -> HomeTarget.ORIGIN));
        });
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> ORIGIN_TINT, CompassModule.ORIGIN_COMPASS.get());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HomeTarget.clearClient();
    }
}
