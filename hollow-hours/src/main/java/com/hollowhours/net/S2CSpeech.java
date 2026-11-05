package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** A ghost speaks: blood-red text typed out at the bottom of the screen. style: 0 talk, 1 rage, 2 success, 3 whisper. */
public record S2CSpeech(int kind, Component text, int style) {
    public static void encode(S2CSpeech m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.kind);
        //#if MC>=12100
        //$$ net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf) buf, m.text);
        //#else
        buf.writeComponent(m.text);
        //#endif
        buf.writeVarInt(m.style);
    }

    public static S2CSpeech decode(FriendlyByteBuf buf) {
        //#if MC>=12100
        //$$ int kind = buf.readVarInt();
        //$$ Component text = net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf);
        //$$ return new S2CSpeech(kind, text, buf.readVarInt());
        //#else
        return new S2CSpeech(buf.readVarInt(), buf.readComponent(), buf.readVarInt());
        //#endif
    }

    public static void handle(S2CSpeech m) {
        ClientPackets.speech(m);
    }
}
