package net.minecraftforge.internal;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;

final class SpawnDataClient {
    private SpawnDataClient() {}

    static void handle(SpawnData.Msg m) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(m.entityId());
        if (e instanceof IEntityAdditionalSpawnData d) d.readSpawnData(new FriendlyByteBuf(Unpooled.wrappedBuffer(m.data())));
    }
}
