package dev.sborka.incognita;

import dev.sborka.incognita.coordinates.CoordinatesModule;
import dev.sborka.incognita.core.IncognitaModule;
import dev.sborka.incognita.projector.ProjectorModule;
import dev.sborka.incognita.radio.RadioModule;
import dev.sborka.incognita.spawn.SpawnModule;

import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point. Each feature lives in its own module package and is registered here. */
@Mod(Incognita.MOD_ID)
public final class Incognita {
    public static final String MOD_ID = "incognita";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final List<IncognitaModule> MODULES = List.of(
        new CoordinatesModule(),
        new SpawnModule(),
        new RadioModule(),
        new ProjectorModule()
    );

    public Incognita(IEventBus modBus, ModContainer container) {
        for (IncognitaModule module : MODULES) {
            LOGGER.info("Loading module '{}'", module.id());
            module.init(modBus, container);
        }
    }
}
