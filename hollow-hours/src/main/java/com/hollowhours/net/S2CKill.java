package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Kill cinematic control for the victim: phase 0 = start (grab), 1 = scream close-up, 2 = interrogation (betrayal),
 * 3 = kill animation `anim`, 4 = released (sold a friend), 5 = end.
 */
public record S2CKill(int ghostId, int kind, int phase, int anim, int ticks) {
    public static final int GRAB = 0, SCREAM = 1, HOLD = 2, KILL = 3, RELEASE = 4, END = 5;

    public static void encode(S2CKill m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.ghostId);
        buf.writeVarInt(m.kind);
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.anim);
        buf.writeVarInt(m.ticks);
    }

    public static S2CKill decode(FriendlyByteBuf buf) {
        return new S2CKill(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CKill m) {
        ClientPackets.kill(m);
    }
}
