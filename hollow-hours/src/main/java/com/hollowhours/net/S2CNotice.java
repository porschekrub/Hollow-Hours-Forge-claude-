package com.hollowhours.net;

import com.hollowhours.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A message popping up on the phone (the rescue story): key + key.title, its arguments, red when it's bad news. */
public record S2CNotice(String key, String[] args, boolean danger) {
    public static void encode(S2CNotice m, FriendlyByteBuf buf) {
        buf.writeUtf(m.key);
        buf.writeVarInt(m.args.length);
        for (String a : m.args) buf.writeUtf(a);
        buf.writeBoolean(m.danger);
    }

    public static S2CNotice decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        String[] args = new String[Math.min(8, buf.readVarInt())];
        for (int i = 0; i < args.length; i++) args[i] = buf.readUtf();
        return new S2CNotice(key, args, buf.readBoolean());
    }

    public static void handle(S2CNotice m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.notice(m));
    }
}
