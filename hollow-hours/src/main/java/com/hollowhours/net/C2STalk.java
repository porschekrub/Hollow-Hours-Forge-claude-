package com.hollowhours.net;

import com.hollowhours.ghost.GhostTalk;
import com.hollowhours.ghost.Curses;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Text typed in the guide: talk to ghost n (1-5), or mode 1 = ask the nearest ghost for an item. lang = client language. */
public record C2STalk(int mode, int ghost, String text, String lang) {
    public static void encode(C2STalk m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.mode);
        buf.writeVarInt(m.ghost);
        buf.writeUtf(m.text, 256);
        buf.writeUtf(m.lang, 16);
    }

    public static C2STalk decode(FriendlyByteBuf buf) {
        return new C2STalk(buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readUtf(16));
    }

    public static void handle(C2STalk m, ServerPlayer p) {
        if (p == null) return;
        String t = m.text.length() > 200 ? m.text.substring(0, 200) : m.text;
        if (m.mode == 1) Curses.askGhost(p, t);
        else GhostTalk.talk(p, m.ghost, t, m.lang);
    }
}
