package dev.sborka.incognita.compass;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Works out the spot a player's compass points to, and holds the value the server sent to this client. */
public final class HomeTarget {
    static final String ARRIVAL_KEY = "incognita_arrival";
    public static final GlobalPos ORIGIN = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);

    @Nullable
    private static volatile GlobalPos client;

    private HomeTarget() {}

    @Nullable
    public static GlobalPos client() {
        return client;
    }

    static void setClient(@Nullable GlobalPos pos) {
        client = pos;
    }

    public static void clearClient() {
        client = null;
    }

    /** Bed, anchor or forced spawnpoint if there is one, otherwise the spot the player last appeared at. */
    @Nullable
    static GlobalPos of(ServerPlayer player) {
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null) {
            return GlobalPos.of(player.getRespawnDimension(), respawn);
        }
        return arrival(player);
    }

    /** Remembers where the player stands as the place they appeared at. Survives death. */
    static void rememberArrival(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.put("pos", NbtUtils.writeBlockPos(player.blockPosition()));
        persisted.put(ARRIVAL_KEY, tag);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static boolean hasArrival(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(ARRIVAL_KEY);
    }

    @Nullable
    private static GlobalPos arrival(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ARRIVAL_KEY)) {
            return null;
        }
        CompoundTag tag = persisted.getCompound(ARRIVAL_KEY);
        net.minecraft.resources.ResourceLocation dim = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("dim"));
        return dim == null ? null : NbtUtils.readBlockPos(tag, "pos")
            .map(pos -> GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim), pos))
            .orElse(null);
    }
}
