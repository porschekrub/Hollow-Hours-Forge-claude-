package com.hollowhours.net;

import com.hollowhours.combat.Combat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Fighting input: STRUGGLE (a key mashed while held by a ghost), DODGE (a forward, b strafe input), RELOAD. */
public record C2SCombat(int type, float a, float b) {
    public static final int STRUGGLE = 0, DODGE = 1, RELOAD = 2;

    public static void encode(C2SCombat m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.type);
        buf.writeFloat(m.a);
        buf.writeFloat(m.b);
    }

    public static C2SCombat decode(FriendlyByteBuf buf) {
        return new C2SCombat(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(C2SCombat m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Combat.input(p, m);
    }
}
