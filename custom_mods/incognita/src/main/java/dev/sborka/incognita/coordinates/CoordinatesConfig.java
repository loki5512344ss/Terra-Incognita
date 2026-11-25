package dev.sborka.incognita.coordinates;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config: {@code config/incognita-coordinates.toml}. */
public final class CoordinatesConfig {
    public static final List<String> DEFAULT_KEYS = List.of(
        "commands.locate.*",
        "commands.teleport.success.location.*",
        "commands.setworldspawn.success",
        "commands.spreadplayers.success.*",
        "commands.worldborder.center.success",
        "commands.spawnpoint.success.*",
        "exception.createbigcannons.cannon_mount.*",
        "exception.createbigcannons.casting",
        "commands.sable.info.position",
        "tooltip.toms_storage.adv_wireless_terminal.bound",
        "tooltip.toms_storage.config_overlay.block",
        "create.gui.assembly.exception.unmovableBlock",
        "create.gui.assembly.exception.chunkNotLoaded",
        "create.weighted_ejector.targeting",
        "create.portal_track.blocked",
        "create_radar.monitor_coords",
        "simulated.altitude_sensor.height",
        "commands.forceload.*",
        "chunk.toast.*",
        "message.supplementaries.compass",
        "message.supplementaries.altimeter",
        "filled_map.sliced.tooltip",
        "desc.immersiveengineering.info.attachedTo",
        "desc.immersiveengineering.info.attachedToDim",
        "desc.immersiveengineering.info.coresample.pos",
        "desc.immersivepetroleum.info.projector.center",
        "desc.immersivepetroleum.flavour.surveytool.location",
        "gui.immersivepetroleum.seismicsurvey.worldcoords",
        "item.sophisticatedstorage.storage_tool.overlay.linking",
        "item.storagedrawers.remote_upgrade_bound.bound",
        "item.storagedrawers.remote_group_upgrade_bound.bound",
        "message.storagedrawers.updated_remote_binding",
        "ftbquests.message.configurator_bound",
        // Neruina reports where a ticking entity or block entity was suspended
        "neruina.*",
        "commands.neruina.*",
        // Jade: the optional "coordinates" lines a player can switch on in the Jade settings
        "jade.blockpos",
        "narration.jade.blockpos",
        // Create Aero Radar: only the coordinate-bearing keys (status_drive/loop/ballistics carry RPM and angles)
        "item.create_aero_radar.radar_link.gun_a_selected",
        "item.create_aero_radar.radar_link.gun_b_selected",
        "item.create_aero_radar.radar_link.filterer_selected",
        "item.create_aero_radar.radar_link.radar_selected",
        "item.create_aero_radar.radar_link.muzzle_selected",
        "item.create_aero_radar.radar_link.linked",
        "item.create_aero_radar.radar_link.muzzle_linked",
        "item.create_aero_radar.radar_link.guns_linked",
        "item.create_aero_radar.radar_link.weapon_group_linked",
        "item.create_aero_radar.radar_link.radar_pair_linked",
        "item.create_aero_radar.radar_link.status_target",
        "item.create_aero_radar.radar_link.status_bearing");

    private static final Spoofer.Settings DEFAULT_SETTINGS =
        new Spoofer.Settings("???", 0.0, Spoofer.Mode.CONSTANT, 3);

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<String> PLACEHOLDER_TEXT;
    private static final ModConfigSpec.DoubleValue NUMERIC_FALLBACK;
    private static final ModConfigSpec.EnumValue<Spoofer.Mode> MODE;
    private static final ModConfigSpec.IntValue ROTATING_INTERVAL_SECONDS;
    private static final ModConfigSpec.BooleanValue OP_CAN_REVEAL;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> TRANSLATION_KEYS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PLACEHOLDER_TEXT = builder
            .comment("Text shown instead of coordinates")
            .define("placeholderText", DEFAULT_SETTINGS.placeholderText());
        NUMERIC_FALLBACK = builder
            .comment("Reserved for displays that need a real number. The built-in hooks show placeholderText everywhere, so this has no visible effect yet")
            .defineInRange("numericFallback", DEFAULT_SETTINGS.numericFallback(), -1.0e9, 1.0e9);
        MODE = builder
            .comment("CONSTANT: always the placeholder. ROTATING: random numbers that change every rotatingIntervalSeconds")
            .defineEnum("mode", DEFAULT_SETTINGS.mode());
        ROTATING_INTERVAL_SECONDS = builder
            .defineInRange("rotatingIntervalSeconds", DEFAULT_SETTINGS.rotatingIntervalSeconds(), 1, 3600);
        OP_CAN_REVEAL = builder
            .comment("Allow operators to use /incognita reveal on|off")
            .define("opCanReveal", true);
        TRANSLATION_KEYS = builder
            .comment("Translation keys whose numeric arguments are replaced. A trailing * matches a prefix.",
                "The effective list is this list UNION the mod's built-in defaults, so keys added in new mod versions apply without editing this file.",
                "Entries here can only add keys; a built-in default cannot be removed (use /incognita reveal to see real values)")
            .defineListAllowEmpty("translationKeys", DEFAULT_KEYS, () -> "", o -> o instanceof String);
        SPEC = builder.build();
    }

    private CoordinatesConfig() {}

    public static Spoofer.Settings settings() {
        try {
            return new Spoofer.Settings(
                PLACEHOLDER_TEXT.get(), NUMERIC_FALLBACK.get(), MODE.get(), ROTATING_INTERVAL_SECONDS.get());
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_SETTINGS;
        }
    }

    public static boolean opCanReveal() {
        try {
            return OP_CAN_REVEAL.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** The merged key set for one configured list. The set is read on every text component built, so it is cached. */
    private record KeysCache(java.util.Collection<? extends String> source, Set<String> keys) {}

    private static volatile KeysCache keysCache;
    private static final Set<String> DEFAULTS_ONLY = Set.copyOf(effectiveKeys(List.of()));

    /** Effective key list: configured keys plus {@link #DEFAULT_KEYS}. Immutable, rebuilt only when the config changes. */
    public static Set<String> translationKeys() {
        try {
            List<? extends String> configured = TRANSLATION_KEYS.get();
            KeysCache cache = keysCache;
            if (cache == null || cache.source() != configured) {
                cache = new KeysCache(configured, Set.copyOf(effectiveKeys(configured)));
                keysCache = cache;
            }
            return cache.keys();
        } catch (IllegalStateException notLoaded) {
            return DEFAULTS_ONLY;
        }
    }

    /** Union of the configured keys and the built-in defaults. Pure. */
    static Set<String> effectiveKeys(java.util.Collection<? extends String> configured) {
        Set<String> keys = new HashSet<>(DEFAULT_KEYS);
        keys.addAll(configured);
        return keys;
    }
}
