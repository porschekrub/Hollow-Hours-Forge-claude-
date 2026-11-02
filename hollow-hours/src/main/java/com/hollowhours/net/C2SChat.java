package com.hollowhours.net;

import com.hollowhours.phone.PhoneChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** From the phone: a message, or your location (kind 1), to a contact. */
public record C2SChat(String contact, int kind, String text) {
    public static void encode(C2SChat m, FriendlyByteBuf buf) {
        buf.writeUtf(m.contact, 64);
        buf.writeVarInt(m.kind);
        buf.writeUtf(m.text, 256);
    }

    public static C2SChat decode(FriendlyByteBuf buf) {
        return new C2SChat(buf.readUtf(64), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(C2SChat m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) PhoneChat.send(p, m.contact, m.kind, m.text);
    }
}
