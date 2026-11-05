package com.hollowhours.net;

import com.hollowhours.HollowHours;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.internal.Net;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Fabric networking (the shim's {@link Net}). The packets are listed in {@link Packets}. */
public final class HHNet {
    private static final Net NET = new Net(HollowHours.MODID);
    private static boolean registered;

    private HHNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Packets.register(new Packets.Registrar() {
            @Override
            public <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
                NET.s2c(type, encode, decode, handle);
            }

            @Override
            public <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
                NET.c2s(type, encode, decode, handle);
            }
        });
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        NET.toPlayer(p, msg);
    }

    public static void toTracking(Entity e, Object msg, boolean andSelf) {
        NET.toTracking(e, msg, andSelf);
    }

    public static void toServer(Object msg) {
        NET.toServer(msg);
    }
}
