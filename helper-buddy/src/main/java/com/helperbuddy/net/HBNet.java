package com.helperbuddy.net;

import com.helperbuddy.HelperBuddy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Forge 1.20.1 networking (the other loaders have their own copy of this class). The packets are listed in {@link Packets}. */
public final class HBNet {
    private static final String VERSION = Packets.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HelperBuddy.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private HBNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Packets.register(new Packets.Registrar() {
            int id;

            @Override
            public <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
                CHANNEL.messageBuilder(type, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(encode).decoder(decode)
                        .consumerMainThread((m, ctx) -> handle.accept(m)).add();
            }

            @Override
            public <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
                CHANNEL.messageBuilder(type, id++, NetworkDirection.PLAY_TO_SERVER).encoder(encode).decoder(decode)
                        .consumerMainThread((m, ctx) -> handle.accept(m, ctx.get().getSender())).add();
            }
        });
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
