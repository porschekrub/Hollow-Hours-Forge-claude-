package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** One chat of the phone, whole (see PhoneChat for the line format); name is the player's name for player contacts. */
public record S2CChat(String contact, String name, List<String> lines) {
    public static void encode(S2CChat m, FriendlyByteBuf buf) {
        buf.writeUtf(m.contact);
        buf.writeUtf(m.name);
        buf.writeVarInt(m.lines.size());
        for (String l : m.lines) buf.writeUtf(l, 1024);
    }

    public static S2CChat decode(FriendlyByteBuf buf) {
        String c = buf.readUtf(), n = buf.readUtf();
        int k = Math.min(64, buf.readVarInt());
        List<String> l = new ArrayList<>();
        for (int i = 0; i < k; i++) l.add(buf.readUtf(1024));
        return new S2CChat(c, n, l);
    }

    public static void handle(S2CChat m) {
        ClientPackets.chat(m);
    }
}
