package com.hollowhours.net;

import com.hollowhours.ghost.Guide;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client asks for fresh guide status (e.g. after closing a page). */
public record C2SGuideRequest(int page) {
    public static void encode(C2SGuideRequest m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.page);
    }

    public static C2SGuideRequest decode(FriendlyByteBuf buf) {
        return new C2SGuideRequest(buf.readVarInt());
    }

    public static void handle(C2SGuideRequest m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Guide.open(p, m.page);
    }
}
