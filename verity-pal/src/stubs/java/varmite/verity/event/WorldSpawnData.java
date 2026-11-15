package varmite.verity.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Stand-in (see README.md). */
public class WorldSpawnData extends SavedData {
    public float verityKarma;

    public boolean hasLiveDemon() {
        throw new AssertionError();
    }

    public static WorldSpawnData get(ServerLevel level) {
        throw new AssertionError();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        throw new AssertionError();
    }
}
