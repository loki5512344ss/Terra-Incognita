package dev.sborka.incognita.projector.source;

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** Immersive Engineering multiblocks. Only touch this class when IE is loaded. */
public final class IeSources {
    private IeSources() {}

    public static List<ProjectionSource> all() {
        List<ProjectionSource> out = new ArrayList<>();
        for (MultiblockHandler.IMultiblock mb : MultiblockHandler.getMultiblocks()) {
            out.add(new ProjectionSource() {
                @Override
                public ResourceLocation id() {
                    return mb.getUniqueName();
                }

                @Override
                public Component name() {
                    return mb.getDisplayName();
                }

                @Override
                public List<StructureBlockInfo> blocks(Level level) {
                    return mb.getStructure(level);
                }
            });
        }
        return out;
    }
}
