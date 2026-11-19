package dev.sborka.incognita.lighting.emitter;

import dev.sborka.incognita.lighting.field.LightColor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sborka.incognita.Incognita;
import java.io.Reader;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Light colors per block id and per item id, loaded from {@code assets/incognita/lighting/emitters.json} of every pack (resource packs can override or extend it):
 * <pre>{"blocks": {"mod:id": {"color": "#ff2020", "level": 15}}, "items": {"minecraft:torch": {"color": "#ff9030"}}}</pre>
 * {@code level} is optional for blocks (the block's own light emission is used) and defaults to 14 for items.
 */
public final class EmitterDefs {
    public record Def(int color, int level) {}

    private static volatile Map<ResourceLocation, Def> blocks = Map.of();
    private static volatile Map<ResourceLocation, Def> items = Map.of();
    private static volatile Map<Block, Def> blockByObject = Map.of();
    private static volatile Map<Item, Def> itemByObject = Map.of();

    /** Colour of a light emitting item whose block has no entry of its own: a warm, slightly desaturated glow. */
    public static final int DEFAULT_COLOR = 0xFFC878;
    private static final Def NONE = new Def(0, 0);
    private static final Map<Item, Def> DERIVED = new ConcurrentHashMap<>();

    private EmitterDefs() {}

    /**
     * Light of an item: its explicit entry, else derived from the light level of its block (a {@link BlockItem}), else
     * null. The derived colour is the block's own entry when it has one, so a sea lantern item glows like the block.
     */
    public static Def itemLight(Item item) {
        Def explicit = itemByObject.get(item);
        if (explicit != null) {
            return explicit;
        }
        Def d = DERIVED.get(item);
        if (d == null) {
            d = NONE;
            if (item instanceof BlockItem bi) {
                Block block = bi.getBlock();
                Def derived = derive(block.defaultBlockState().getLightEmission(), blockByObject.get(block));
                if (derived != null) {
                    d = derived;
                }
            }
            DERIVED.put(item, d);
        }
        return d == NONE ? null : d;
    }

    /** Pure part of {@link #itemLight}: the item def for a block light level and the block's own def (may be null). */
    static Def derive(int blockLevel, Def blockDef) {
        if (blockLevel <= 0) {
            return null;
        }
        return new Def(blockDef != null ? blockDef.color() : DEFAULT_COLOR, blockLevel);
    }

    public static Def block(ResourceLocation id) {
        return blocks.get(id);
    }

    public static Def item(ResourceLocation id) {
        return items.get(id);
    }

    public static Def blockDef(Block block) {
        return blockByObject.get(block);
    }

    public static boolean anyBlocks() {
        return !blockByObject.isEmpty();
    }

    public static Def itemDef(Item item) {
        return itemByObject.get(item);
    }

    /** Parses one emitters.json document into the two maps. Package visible for tests. */
    static void parse(JsonObject root, Map<ResourceLocation, Def> blockOut, Map<ResourceLocation, Def> itemOut) {
        read(root, "blocks", -1, blockOut);
        read(root, "items", 14, itemOut);
    }

    private static void read(JsonObject root, String key, int defaultLevel, Map<ResourceLocation, Def> out) {
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject o = e.getValue().getAsJsonObject();
            int color = LightColor.parseHex(GsonHelper.getAsString(o, "color", ""));
            if (color < 0) {
                continue;
            }
            out.put(id, new Def(color, GsonHelper.getAsInt(o, "level", defaultLevel)));
        }
    }

    public static void reload(ResourceManager manager) {
        Map<ResourceLocation, Def> b = new HashMap<>();
        Map<ResourceLocation, Def> i = new HashMap<>();
        for (var res : manager.getResourceStack(ResourceLocation.fromNamespaceAndPath(Incognita.MOD_ID, "lighting/emitters.json"))) {
            try (Reader r = res.openAsReader()) {
                parse(GsonHelper.parse(r), b, i);
            } catch (Exception ex) {
                Incognita.LOGGER.warn("Bad lighting/emitters.json: {}", ex.toString());
            }
        }
        blocks = Map.copyOf(b);
        items = Map.copyOf(i);
        Map<Block, Def> bo = new IdentityHashMap<>();
        b.forEach((id, def) -> BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blk -> bo.put(blk, def)));
        Map<Item, Def> io = new IdentityHashMap<>();
        i.forEach((id, def) -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(it -> io.put(it, def)));
        DERIVED.clear();
        blockByObject = bo;
        itemByObject = io;
        Incognita.LOGGER.info("Colored light: {} block emitters, {} item emitters", b.size(), i.size());
    }
}
