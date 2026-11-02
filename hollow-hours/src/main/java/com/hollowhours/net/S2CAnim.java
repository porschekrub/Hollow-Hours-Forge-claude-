package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * An animation to play: RELOAD = a battery slides into the flashlight (first person, for that player);
 * SIP = a player scoops water with both hands and drinks (seen by everyone around).
 */
public record S2CAnim(int kind, int entity) {
    public static final int RELOAD = 0, SIP = 1;

    public static void encode(S2CAnim m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.kind);
        buf.writeVarInt(m.entity);
    }

    public static S2CAnim decode(FriendlyByteBuf buf) {
        return new S2CAnim(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CAnim m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.anim(m));
    }
}
