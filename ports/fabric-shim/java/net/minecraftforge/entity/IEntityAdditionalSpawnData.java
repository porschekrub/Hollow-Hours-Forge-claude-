package net.minecraftforge.entity;

import net.minecraft.network.FriendlyByteBuf;

/** Extra data sent with an entity's spawn packet (the shim sends it in the same bundle, see internal.SpawnData). */
public interface IEntityAdditionalSpawnData {
    void writeSpawnData(FriendlyByteBuf buffer);

    void readSpawnData(FriendlyByteBuf additionalData);
}
