package dev.sborka.incognita.projector.source;

import dev.sborka.incognita.Incognita;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Structures stored as vanilla/Create NBT files (the Create schematic format). Client only. Two places:
 * <ul>
 *   <li>{@code assets/<namespace>/projections/<name>.nbt} in any mod or resource pack (this is how OC2R can ship its own);</li>
 *   <li>the game's {@code schematics/} folder, i.e. schematics saved by Create.</li>
 * </ul>
 */
public final class NbtSources {
    private NbtSources() {}

    public static List<ProjectionSource> all() {
        List<ProjectionSource> out = new ArrayList<>();
        Minecraft.getInstance().getResourceManager()
            .listResources("projections", id -> id.getPath().endsWith(".nbt"))
            .forEach((id, resource) -> out.add(fromResource(id, resource)));
        Path dir = FMLPaths.GAMEDIR.get().resolve("schematics");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".nbt")).sorted()
                    .forEach(f -> out.add(fromFile(f)));
            } catch (IOException e) {
                Incognita.LOGGER.warn("Cannot list {}", dir, e);
            }
        }
        return out;
    }

    private static ProjectionSource fromResource(ResourceLocation id, Resource resource) {
        String path = id.getPath();
        String shortName = path.substring(path.indexOf('/') + 1, path.length() - ".nbt".length());
        return new NbtSource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), shortName), shortName, () -> resource.open());
    }

    private static ProjectionSource fromFile(Path file) {
        String name = file.getFileName().toString();
        String shortName = name.substring(0, name.length() - ".nbt".length());
        String safe = shortName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return new NbtSource(ResourceLocation.fromNamespaceAndPath("schematic", safe), shortName, () -> Files.newInputStream(file));
    }

    private interface Opener {
        InputStream open() throws IOException;
    }

    private record NbtSource(ResourceLocation id, String displayName, Opener opener) implements ProjectionSource {
        @Override
        public Component name() {
            return Component.literal(displayName);
        }

        @Override
        public List<StructureBlockInfo> blocks(Level level) {
            try (InputStream in = opener.open()) {
                return parse(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
            } catch (IOException | RuntimeException e) {
                Incognita.LOGGER.warn("Cannot read projection {}", id, e);
                return List.of();
            }
        }
    }

    static List<StructureBlockInfo> parse(CompoundTag root) {
        ListTag paletteTag = root.getList("palette", 10);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }
        List<StructureBlockInfo> out = new ArrayList<>();
        ListTag blocks = root.getList("blocks", 10);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int index = b.getInt("state");
            if (index < 0 || index >= palette.length) continue;
            BlockState state = palette[index];
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) continue;
            ListTag pos = b.getList("pos", 3);
            out.add(new StructureBlockInfo(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state, null));
        }
        return out;
    }
}
