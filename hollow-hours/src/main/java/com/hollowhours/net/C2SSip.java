package com.hollowhours.net;

import com.hollowhours.ghost.Thirst;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Sneak + right-click water with an empty hand: a sip, at the water the client saw (the server checks it again). */
public record C2SSip(BlockPos pos) {
    public static void encode(C2SSip m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
    }

    public static C2SSip decode(FriendlyByteBuf buf) {
        return new C2SSip(buf.readBlockPos());
    }

    public static void handle(C2SSip m, ServerPlayer p) {
        if (p != null) Thirst.sip(p, m.pos);
    }
}
