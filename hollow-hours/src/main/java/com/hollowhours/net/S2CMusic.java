package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Horror music state: "" = stop, or ambient1 / ambient2 / spawn / chase. */
public record S2CMusic(String key) {
    public static void encode(S2CMusic m, FriendlyByteBuf buf) {
        buf.writeUtf(m.key, 32);
    }

    public static S2CMusic decode(FriendlyByteBuf buf) {
        return new S2CMusic(buf.readUtf(32));
    }

    public static void handle(S2CMusic m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.music(m));
    }
}
