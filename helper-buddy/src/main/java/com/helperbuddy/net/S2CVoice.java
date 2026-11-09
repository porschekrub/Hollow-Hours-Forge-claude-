package com.helperbuddy.net;

import net.minecraft.network.FriendlyByteBuf;

/** A piece of a real (AI) voice line: 8-bit mu-law samples; the client plays them when the last piece is there. */
public record S2CVoice(int entity, int clip, int part, boolean last, int rate, byte[] data) {
    public static void encode(S2CVoice m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeInt(m.clip);
        buf.writeVarInt(m.part);
        buf.writeBoolean(m.last);
        buf.writeVarInt(m.rate);
        buf.writeByteArray(m.data);
    }

    public static S2CVoice decode(FriendlyByteBuf buf) {
        return new S2CVoice(buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readByteArray(40000));
    }

    public static void handle(S2CVoice m) {
        com.helperbuddy.client.VoiceClient.audio(m.entity, m.clip, m.part, m.last, m.rate, m.data);
    }
}
