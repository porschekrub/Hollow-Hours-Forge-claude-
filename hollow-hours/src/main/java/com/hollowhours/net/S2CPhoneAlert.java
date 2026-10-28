package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The emergency alert. phone = the player has a phone with battery (voice from the phone; otherwise distant sirens). */
public record S2CPhoneAlert(boolean phone) {
    public static void encode(S2CPhoneAlert m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.phone);
    }

    public static S2CPhoneAlert decode(FriendlyByteBuf buf) {
        return new S2CPhoneAlert(buf.readBoolean());
    }

    public static void handle(S2CPhoneAlert m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.phoneAlert(m));
    }
}
