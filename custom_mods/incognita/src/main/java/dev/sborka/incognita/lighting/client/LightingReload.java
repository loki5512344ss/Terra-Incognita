package dev.sborka.incognita.lighting.client;

import dev.sborka.incognita.lighting.emitter.EmitterDefs;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Reloads the emitter definitions with the resource packs. Registered on the mod bus by the module, client only. */
public final class LightingReload {
    private LightingReload() {}

    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) EmitterDefs::reload);
    }
}
