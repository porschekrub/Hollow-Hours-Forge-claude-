package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A full-screen jumpscare: the face of `who` (1-5 ghosts, 11-13 stalkers) lunges at the camera and screams.
 * strength: 0 = quick flash, 1 = strong, 2 = death-level (longest, loudest).
 */
public record S2CJumpscare(int who, int strength, int variant) {
    public static void encode(S2CJumpscare m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.who);
        buf.writeVarInt(m.strength);
        buf.writeVarInt(m.variant);
    }

    public static S2CJumpscare decode(FriendlyByteBuf buf) {
        return new S2CJumpscare(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CJumpscare m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.jumpscare(m));
    }
}
