package com.helperbuddy.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** A button pressed in a helper screen. */
public record C2SAction(int entity, UUID uuid, String action, CompoundTag args) {
    public static void encode(C2SAction m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entity);
        buf.writeUUID(m.uuid);
        buf.writeUtf(m.action, 64);
        buf.writeNbt(m.args);
    }

    public static C2SAction decode(FriendlyByteBuf buf) {
        return new C2SAction(buf.readVarInt(), buf.readUUID(), buf.readUtf(64), buf.readNbt());
    }

    public static void handle(C2SAction m, ServerPlayer p) {
        if (p == null) return;
        Actions.run(p, m.entity, m.uuid, m.action, m.args == null ? new CompoundTag() : m.args);
    }
}
