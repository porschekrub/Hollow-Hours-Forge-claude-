package com.hollowhours.net;

import com.hollowhours.HollowHours;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Forge 1.21 networking (ChannelBuilder). The packets are listed in {@link Packets}. */
public final class HHNet {
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(HollowHours.id("main"))
            .networkProtocolVersion(Integer.parseInt(Packets.VERSION)).simpleChannel();
    private static boolean registered;

    private HHNet() {}

    public static void register() {
        if (registered) return;
        registered = true;
        Packets.register(new Packets.Registrar() {
            @Override
            public <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
                CHANNEL.messageBuilder(type, NetworkDirection.PLAY_TO_CLIENT).encoder(encode::accept).decoder(decode::apply)
                        .consumerMainThread((m, ctx) -> handle.accept(m)).add();
            }

            @Override
            public <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
                CHANNEL.messageBuilder(type, NetworkDirection.PLAY_TO_SERVER).encoder(encode::accept).decoder(decode::apply)
                        .consumerMainThread((m, ctx) -> handle.accept(m, ctx.getSender())).add();
            }
        });
        CHANNEL.build();
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null) return;
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(p));
    }

    /** To every player who sees the entity (and the entity itself when it is a player and andSelf). */
    public static void toTracking(Entity e, Object msg, boolean andSelf) {
        CHANNEL.send(msg, (andSelf ? PacketDistributor.TRACKING_ENTITY_AND_SELF : PacketDistributor.TRACKING_ENTITY).with(e));
    }

    public static void toServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }
}
