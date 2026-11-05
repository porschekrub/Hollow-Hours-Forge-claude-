package net.minecraftforge.internal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Fabric shim of Forge's Entity#getPersistentData (kept by a mixin, saved as "ForgeData"). */
public final class Persist {
    private Persist() {}

    public interface Holder {
        CompoundTag shim$persistentData();
    }

    public static CompoundTag of(Entity e) {
        return ((Holder) e).shim$persistentData();
    }
}
