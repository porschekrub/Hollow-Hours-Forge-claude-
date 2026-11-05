package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

/** The emergency alert. phone = the player has a phone with battery (voice from the phone; otherwise distant sirens). */
public record S2CPhoneAlert(boolean phone) {
    public static void encode(S2CPhoneAlert m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.phone);
    }

    public static S2CPhoneAlert decode(FriendlyByteBuf buf) {
        return new S2CPhoneAlert(buf.readBoolean());
    }

    public static void handle(S2CPhoneAlert m) {
        ClientPackets.phoneAlert(m);
    }
}
