package com.hollowhours.net;

import com.hollowhours.ghost.PhoneUse;
import com.hollowhours.ghost.Thirst;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Phone screen open / closed / heartbeat / playing a game (PhoneUse), or 10 = sip water (sneak + right-click). */
public record C2SPhone(int action) {
    public static final int SIP = 10;

    public static void encode(C2SPhone m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.action);
    }

    public static C2SPhone decode(FriendlyByteBuf buf) {
        return new C2SPhone(buf.readVarInt());
    }

    public static void handle(C2SPhone m, ServerPlayer p) {
        if (p == null) return;
        if (m.action == SIP) Thirst.sip(p);
        else PhoneUse.action(p, m.action);
    }
}
