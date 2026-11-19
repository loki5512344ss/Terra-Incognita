package dev.sborka.incognita.lighting.emitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EmitterDefsTest {
    @Test
    void parsesBlocksAndItemsWithDefaults() {
        Map<ResourceLocation, EmitterDefs.Def> blocks = new HashMap<>();
        Map<ResourceLocation, EmitterDefs.Def> items = new HashMap<>();
        EmitterDefs.parse(JsonParser.parseString("""
            {"blocks": {"a:b": {"color": "#ff0000"}, "bad": 3, "c:d": {"color": "zz"}},
             "items": {"x:y": {"color": "#00ff00", "level": 9}, "x:z": {"color": "#0000ff"}}}""").getAsJsonObject(), blocks, items);
        assertEquals(new EmitterDefs.Def(0xFF0000, -1), blocks.get(ResourceLocation.parse("a:b")));
        assertEquals(1, blocks.size());
        assertEquals(9, items.get(ResourceLocation.parse("x:y")).level());
        assertEquals(14, items.get(ResourceLocation.parse("x:z")).level());
        assertNull(items.get(ResourceLocation.parse("q:q")));
    }

    @Test
    void derivesItemLightFromBlockLevel() {
        assertNull(EmitterDefs.derive(0, null));
        assertEquals(new EmitterDefs.Def(EmitterDefs.DEFAULT_COLOR, 15), EmitterDefs.derive(15, null));
        assertEquals(new EmitterDefs.Def(0x112233, 10), EmitterDefs.derive(10, new EmitterDefs.Def(0x112233, -1)));
    }
}
