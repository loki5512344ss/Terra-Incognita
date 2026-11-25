package dev.sborka.incognita.weather.fog.control;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/** The fog override of a world, saved with the overworld so a restart keeps the operators' decision. */
public final class FogSavedData extends SavedData {
    private static final String NAME = "incognita_fog";

    private FogOverride state = FogOverride.NONE;

    static FogSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(new Factory<>(FogSavedData::new, FogSavedData::load), NAME);
    }

    @SuppressWarnings("PMD.UnusedFormalParameter") // the signature SavedData.Factory expects
    private static FogSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FogSavedData data = new FogSavedData();
        int mode = Math.floorMod(tag.getInt("mode"), FogOverride.Mode.values().length);
        data.state = new FogOverride(FogOverride.Mode.values()[mode], tag.getFloat("density"), tag.getLong("since"),
            tag.getLong("until"), tag.contains("frequency") ? tag.getFloat("frequency") : -1.0f);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("mode", state.mode().ordinal());
        tag.putFloat("density", state.density());
        tag.putLong("since", state.since());
        tag.putLong("until", state.until());
        tag.putFloat("frequency", state.frequency());
        return tag;
    }

    FogOverride state() {
        return state;
    }

    /** Stores the new state and tells every player. */
    void update(MinecraftServer server, FogOverride next) {
        state = next;
        setDirty();
        server.getPlayerList().getPlayers().forEach(this::send);
    }

    /** Players on an older build of the mod have no channel for this and are skipped. */
    void send(ServerPlayer player) {
        if (player.connection.hasChannel(FogSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new FogSyncPayload(state));
        }
    }
}
