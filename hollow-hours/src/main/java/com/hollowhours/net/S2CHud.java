package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Small HUD state pushed by the server.
 * hide: 0 none, 1 closet, 2 under a bed. breath 0..100, ghostDist (-1 = none near). baseYaw = where the hiding spot faces.
 * stamina 0..1 (negative = hide the bar), turbo, exhausted. soul 0..100.
 */
public record S2CHud(int hide, float breath, float ghostDist, float baseYaw, float stamina, boolean turbo, boolean exhausted, float soul) {
    public static void encode(S2CHud m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.hide);
        buf.writeFloat(m.breath);
        buf.writeFloat(m.ghostDist);
        buf.writeFloat(m.baseYaw);
        buf.writeFloat(m.stamina);
        buf.writeBoolean(m.turbo);
        buf.writeBoolean(m.exhausted);
        buf.writeFloat(m.soul);
    }

    public static S2CHud decode(FriendlyByteBuf buf) {
        return new S2CHud(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readFloat());
    }

    public static void handle(S2CHud m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.hud(m));
    }
}
