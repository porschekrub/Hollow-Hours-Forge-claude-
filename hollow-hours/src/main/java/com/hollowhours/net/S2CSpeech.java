package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** A ghost speaks: blood-red text typed out at the bottom of the screen. style: 0 talk, 1 rage, 2 success, 3 whisper. */
public record S2CSpeech(int kind, Component text, int style) {
    public static void encode(S2CSpeech m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.kind);
        buf.writeComponent(m.text);
        buf.writeVarInt(m.style);
    }

    public static S2CSpeech decode(FriendlyByteBuf buf) {
        return new S2CSpeech(buf.readVarInt(), buf.readComponent(), buf.readVarInt());
    }

    public static void handle(S2CSpeech m) {
        ClientPackets.speech(m);
    }
}
