package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A first-person animation to play (RELOAD = a battery slides into the flashlight). */
public record S2CAnim(int kind) {
    public static final int RELOAD = 0;

    public static void encode(S2CAnim m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.kind);
    }

    public static S2CAnim decode(FriendlyByteBuf buf) {
        return new S2CAnim(buf.readVarInt());
    }

    public static void handle(S2CAnim m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.anim(m));
    }
}
