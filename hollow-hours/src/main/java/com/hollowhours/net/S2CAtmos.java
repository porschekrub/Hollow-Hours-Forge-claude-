package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Weather of the haunting: blood moon tonight, fog thickness 0..1 (0 = clear), haunted = the story has started. */
public record S2CAtmos(boolean bloodMoon, float fog, boolean haunted) {
    public static void encode(S2CAtmos m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.bloodMoon);
        buf.writeFloat(m.fog);
        buf.writeBoolean(m.haunted);
    }

    public static S2CAtmos decode(FriendlyByteBuf buf) {
        return new S2CAtmos(buf.readBoolean(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(S2CAtmos m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.atmos(m));
    }
}
