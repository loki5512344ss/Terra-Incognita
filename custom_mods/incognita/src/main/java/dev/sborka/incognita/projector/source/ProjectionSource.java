package dev.sborka.incognita.projector.source;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Something the projector can show: a block layout in template coordinates (any corner, any origin).
 * Other mods (for example OC2R) can register their own with {@link ProjectionRegistry#register}.
 */
public interface ProjectionSource {
    ResourceLocation id();

    Component name();

    /** Blocks of the structure. Called on the client when the source is selected, never every frame. */
    List<StructureBlockInfo> blocks(Level level);
}
