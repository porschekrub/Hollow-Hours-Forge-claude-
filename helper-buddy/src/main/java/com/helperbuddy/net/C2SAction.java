package com.helperbuddy.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

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

    public static void handle(C2SAction m, Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null) return;
        Actions.run(p, m.entity, m.uuid, m.action, m.args == null ? new CompoundTag() : m.args);
    }
}
