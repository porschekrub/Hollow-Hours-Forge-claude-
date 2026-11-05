package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

/** The player's thirst (0-20, negative = thirst off) and sanity (0-100, negative = sanity off). */
public record S2CStats(float thirst, float sanity) {
    public static void encode(S2CStats m, FriendlyByteBuf buf) {
        buf.writeFloat(m.thirst);
        buf.writeFloat(m.sanity);
    }

    public static S2CStats decode(FriendlyByteBuf buf) {
        return new S2CStats(buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CStats m) {
        ClientPackets.stats(m);
    }
}
