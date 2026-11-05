package com.hollowhours.net;

import com.hollowhours.client.EndingClient;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** The end: BOOM = the flash and the boom of the rift, then the credits (with the names of everyone who was there). */
public record S2CEnding(int type, List<String> names) {
    public static final int BOOM = 0, CREDITS = 1;

    public static void encode(S2CEnding m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.type);
        buf.writeVarInt(m.names.size());
        for (String n : m.names) buf.writeUtf(n, 64);
    }

    public static S2CEnding decode(FriendlyByteBuf buf) {
        int type = buf.readVarInt();
        int n = Math.min(64, buf.readVarInt());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) names.add(buf.readUtf(64));
        return new S2CEnding(type, names);
    }

    public static void handle(S2CEnding m) {
        EndingClient.packet(m);
    }
}
