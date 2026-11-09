package com.helperbuddy.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** A piece of what the player said into the microphone (8-bit mu-law at `rate`); the server listens when the last piece is in. */
public record C2SVoiceIn(int clip, int part, boolean last, int rate, byte[] data) {
    public static void encode(C2SVoiceIn m, FriendlyByteBuf buf) {
        buf.writeInt(m.clip);
        buf.writeVarInt(m.part);
        buf.writeBoolean(m.last);
        buf.writeVarInt(m.rate);
        buf.writeByteArray(m.data);
    }

    public static C2SVoiceIn decode(FriendlyByteBuf buf) {
        return new C2SVoiceIn(buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readByteArray(30000));
    }

    public static void handle(C2SVoiceIn m, ServerPlayer p) {
        if (p != null) com.helperbuddy.talk.VoiceIn.receive(p, m);
    }
}
