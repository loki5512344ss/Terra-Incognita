package dev.sborka.incognita.coordinates.client;

import dev.sborka.incognita.coordinates.Coordinates;
import dev.sborka.incognita.coordinates.CoordinatesConfig;
import dev.sborka.incognita.coordinates.reveal.RevealState;
import dev.sborka.incognita.coordinates.rewrite.ComponentRewriter;

import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Construction-time masking of translatable arguments, used by the TranslatableContents mixin. Client only, fails open. */
public final class ClientMasking {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientMasking.class);
    private static volatile boolean warned;

    private ClientMasking() {}

    /** Returns the args to store: masked copies for listed keys on the client when not revealed, otherwise the input. */
    public static Object[] maskArgs(String key, Object[] args) {
        try {
            if (args == null || args.length == 0 || FMLEnvironment.dist != Dist.CLIENT || RevealState.isClientRevealed()) {
                return args;
            }
            Set<String> keys = CoordinatesConfig.translationKeys();
            return ComponentRewriter.maskArgsForKey(key, args, keys, () -> Coordinates.spoofer().text());
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Translatable argument masking failed, leaving arguments unchanged", t);
            }
            return args;
        }
    }
}
