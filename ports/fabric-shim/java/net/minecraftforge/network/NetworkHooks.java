package net.minecraftforge.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;

public final class NetworkHooks {
    private NetworkHooks() {}

    /** The vanilla spawn packet: extra spawn data follows it (internal.SpawnData, from the ServerEntity mixin). */
    //#if MC<12100
    public static Packet<ClientGamePacketListener> getEntitySpawningPacket(Entity entity) {
        return new ClientboundAddEntityPacket(entity);
    }
    //#endif
}
