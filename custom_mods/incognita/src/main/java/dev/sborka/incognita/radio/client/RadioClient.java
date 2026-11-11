package dev.sborka.incognita.radio.client;

import dev.sborka.incognita.Incognita;
import dev.sborka.incognita.radio.RadioModule;
import dev.sborka.incognita.radio.link.RadioState;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Switches the radio model between the on and off texture. */
@EventBusSubscriber(modid = Incognita.MOD_ID, value = Dist.CLIENT)
public final class RadioClient {
    private RadioClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(RadioModule.RADIO.get(),
            ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "on"),
            (stack, level, entity, seed) -> RadioState.of(stack).on() ? 1.0f : 0.0f));
    }
}
