package com.helperbuddy.net;

import net.minecraft.network.FriendlyByteBuf;

/** A helper says something: the client plays its babble voice (style: 0 polite, 1 sassy, 2 rude). */
public record S2CSpeak(int entity, String text, int seed, int style) {
    public static void encode(S2CSpeak m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeUtf(m.text, 400);
        buf.writeInt(m.seed);
        buf.writeByte(m.style);
    }

    public static S2CSpeak decode(FriendlyByteBuf buf) {
        return new S2CSpeak(buf.readVarInt(), buf.readUtf(400), buf.readInt(), buf.readByte());
    }

    public static void handle(S2CSpeak m) {
        com.helperbuddy.client.VoiceClient.babble(m.entity, m.text, m.seed, m.style);
    }
}
