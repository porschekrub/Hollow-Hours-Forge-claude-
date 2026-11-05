package com.hollowhours.net;

import com.hollowhours.client.CombatClient;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Fighting state for the client.
 * STATS: a strength 0..100, b melee level, c gun level. STRUGGLE: a progress 0..1 (negative = over), b ticks left, c result
 * (0 going, 1 broke free, 2 failed). RECOIL: a pitch kick, b yaw jitter. DODGE: a/b world velocity x/z.
 */
public record S2CCombat(int type, float a, float b, float c) {
    public static final int STATS = 0, STRUGGLE = 1, RECOIL = 2, DODGE = 3, KO = 4;

    public static void encode(S2CCombat m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.type);
        buf.writeFloat(m.a);
        buf.writeFloat(m.b);
        buf.writeFloat(m.c);
    }

    public static S2CCombat decode(FriendlyByteBuf buf) {
        return new S2CCombat(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CCombat m) {
        CombatClient.packet(m);
    }
}
