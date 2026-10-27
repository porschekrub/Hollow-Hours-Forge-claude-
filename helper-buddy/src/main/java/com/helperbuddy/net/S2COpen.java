package com.helperbuddy.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Opens a screen ("menu", "book") or refreshes the open one ("refresh") with fresh data. */
public record S2COpen(String screen, CompoundTag data) {
    public static void encode(S2COpen m, FriendlyByteBuf buf) {
        buf.writeUtf(m.screen, 32);
        buf.writeNbt(m.data);
    }

    public static S2COpen decode(FriendlyByteBuf buf) {
        return new S2COpen(buf.readUtf(32), buf.readNbt());
    }

    @SuppressWarnings("removal")
    public static void handle(S2COpen m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.helperbuddy.client.ClientPackets.open(m.screen, m.data == null ? new CompoundTag() : m.data));
    }
}
