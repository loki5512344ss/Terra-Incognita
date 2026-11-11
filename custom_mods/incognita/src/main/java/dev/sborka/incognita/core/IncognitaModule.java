package dev.sborka.incognita.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** A self-contained feature of the mod (coordinates, ...). */
public interface IncognitaModule {
    /** Short lowercase identifier, used for logging and config sections. */
    String id();

    /** Called once from the mod constructor on both sides. Register events/config here. */
    void init(IEventBus modBus, ModContainer container);
}
