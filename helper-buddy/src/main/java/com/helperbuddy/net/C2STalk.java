package com.helperbuddy.net;

import com.helperbuddy.Helpers;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Talk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Text typed in the talk screen; entity -1 = let the helpers decide who is meant. */
public record C2STalk(int entity, UUID uuid, String text) {
    public static void encode(C2STalk m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeUUID(m.uuid);
        buf.writeUtf(m.text, 512);
    }

    public static C2STalk decode(FriendlyByteBuf buf) {
        return new C2STalk(buf.readVarInt(), buf.readUUID(), buf.readUtf(512));
    }

    public static void handle(C2STalk m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        String t = m.text.length() > 400 ? m.text.substring(0, 400) : m.text;
        if (m.entity < 0) {
            Talk.command(p, t);
            return;
        }
        HelperEntity h = Helpers.byId(p.server, m.entity, m.uuid);
        if (h == null || !h.isOwner(p)) {
            Talk.command(p, t);
            return;
        }
        Talk.handle(p, h, t);
    }
}
