package com.hollowhours.net;

import com.hollowhours.ghost.KillSequence;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** The victim's answer: a friend to sell, or nobody (null) = refuse. */
public record C2SBetrayChoice(UUID friend) {
    public static void encode(C2SBetrayChoice m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.friend != null);
        if (m.friend != null) buf.writeUUID(m.friend);
    }

    public static C2SBetrayChoice decode(FriendlyByteBuf buf) {
        return new C2SBetrayChoice(buf.readBoolean() ? buf.readUUID() : null);
    }

    public static void handle(C2SBetrayChoice m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) KillSequence.onBetrayChoice(p, m.friend);
    }
}
