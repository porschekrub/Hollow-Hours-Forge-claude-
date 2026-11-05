package com.hollowhours.net;

import com.hollowhours.HollowHours;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * NeoForge 1.21 networking: every message class of {@link Packets} travels inside its own payload type
 * ("HollowHours id: the class name in lower case"). Handlers run on the main thread.
 */
public final class HHNet {
    private static final Map<Class<?>, CustomPacketPayload.Type<Msg>> TYPES = new HashMap<>();

    /** A message of the mod, as a NeoForge payload. */
    public record Msg(CustomPacketPayload.Type<Msg> type, Object msg) implements CustomPacketPayload {}

    private HHNet() {}

    /** Called from the mod's constructor. */
    public static void register(IEventBus modBus) {
        modBus.addListener(HHNet::onRegister);
    }

    /** Kept for the shared code (common setup): registration happens in the event above. */
    public static void register() {}

    @SuppressWarnings("unchecked")
    private static void onRegister(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar(Packets.VERSION);
        Packets.register(new Packets.Registrar() {
            @Override
            public <T> void s2c(Class<T> cls, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
                CustomPacketPayload.Type<Msg> type = type(cls);
                r.playToClient(type, codec(type, encode, decode), (m, ctx) -> handle.accept((T) m.msg()));
            }

            @Override
            public <T> void c2s(Class<T> cls, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
                CustomPacketPayload.Type<Msg> type = type(cls);
                r.playToServer(type, codec(type, encode, decode), (m, ctx) -> handle.accept((T) m.msg(), (ServerPlayer) ctx.player()));
            }
        });
    }

    private static CustomPacketPayload.Type<Msg> type(Class<?> cls) {
        return TYPES.computeIfAbsent(cls, c -> new CustomPacketPayload.Type<>(HollowHours.id(c.getSimpleName().toLowerCase(Locale.ROOT))));
    }

    @SuppressWarnings("unchecked")
    private static <T> StreamCodec<RegistryFriendlyByteBuf, Msg> codec(CustomPacketPayload.Type<Msg> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode) {
        return StreamCodec.of((buf, m) -> encode.accept((T) m.msg(), buf), buf -> new Msg(type, decode.apply(buf)));
    }

    private static Msg wrap(Object msg) {
        CustomPacketPayload.Type<Msg> type = TYPES.get(msg.getClass());
        if (type == null) throw new IllegalArgumentException("not a registered packet: " + msg.getClass());
        return new Msg(type, msg);
    }

    public static void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null || p instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        PacketDistributor.sendToPlayer(p, wrap(msg));
    }

    /** To every player who sees the entity (and the entity itself when it is a player and andSelf). */
    public static void toTracking(Entity e, Object msg, boolean andSelf) {
        if (andSelf) PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, wrap(msg));
        else PacketDistributor.sendToPlayersTrackingEntity(e, wrap(msg));
    }

    public static void toServer(Object msg) {
        PacketDistributor.sendToServer(wrap(msg));
    }
}
