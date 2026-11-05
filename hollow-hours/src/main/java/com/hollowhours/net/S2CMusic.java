package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

/** Horror music state: "" = stop, or ambient1 / ambient2 / spawn / chase. */
public record S2CMusic(String key) {
    public static void encode(S2CMusic m, FriendlyByteBuf buf) {
        buf.writeUtf(m.key, 32);
    }

    public static S2CMusic decode(FriendlyByteBuf buf) {
        return new S2CMusic(buf.readUtf(32));
    }

    public static void handle(S2CMusic m) {
        ClientPackets.music(m);
    }
}
