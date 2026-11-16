package com.helperbuddy.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A helper says something. kind 0: the client plays its babble; kind 1: the client speaks the line in the mod's own
 * voice (made on the player's computer, see LocalVoice), babbling when it can't. style: 0 polite, 1 sassy, 2 rude.
 */
public record S2CSpeak(int entity, String text, int seed, int style, int kind, String voice, boolean cartoon) {
    public S2CSpeak(int entity, String text, int seed, int style) {
        this(entity, text, seed, style, 0, "", false);
    }

    public static void encode(S2CSpeak m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeUtf(m.text, 400);
        buf.writeInt(m.seed);
        buf.writeByte(m.style);
        buf.writeByte(m.kind);
        buf.writeUtf(m.voice, 32);
        buf.writeBoolean(m.cartoon);
    }

    public static S2CSpeak decode(FriendlyByteBuf buf) {
        return new S2CSpeak(buf.readVarInt(), buf.readUtf(400), buf.readInt(), buf.readByte(), buf.readByte(), buf.readUtf(32), buf.readBoolean());
    }

    public static void handle(S2CSpeak m) {
        if (m.kind == 1 && com.helperbuddy.client.LocalVoice.say(m.entity, m.text, m.voice, m.cartoon, m.seed)) return;
        com.helperbuddy.client.VoiceClient.babble(m.entity, m.text, m.seed, m.style);
    }
}
