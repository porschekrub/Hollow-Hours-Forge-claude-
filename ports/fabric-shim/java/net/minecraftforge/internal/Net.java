package net.minecraftforge.internal;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

//#if MC>=12100
//$$ import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//$$ import net.minecraft.network.RegistryFriendlyByteBuf;
//$$ import net.minecraft.network.codec.StreamCodec;
//$$ import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//#else
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//#endif

/**
 * Fabric networking for a mod's message classes: one channel per class ("modid:class name"), handlers on the main thread.
 * S2C receivers are hooked up on the client by {@link NetClient}.
 */
public final class Net {
    static final List<Net> ALL = new ArrayList<>();

    final String modid;
    final Map<Class<?>, Entry<?>> byClass = new HashMap<>();
    final List<Entry<?>> s2c = new ArrayList<>();

    static final class Entry<T> {
        final ResourceLocation id;
        final BiConsumer<T, FriendlyByteBuf> encode;
        final Function<FriendlyByteBuf, T> decode;
        final Consumer<T> clientHandle;
        //#if MC>=12100
        //$$ CustomPacketPayload.Type<Msg> type;
        //#endif

        Entry(ResourceLocation id, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> clientHandle) {
            this.id = id;
            this.encode = encode;
            this.decode = decode;
            this.clientHandle = clientHandle;
        }
    }

    //#if MC>=12100
    //$$ /** A message as a payload. */
    //$$ public record Msg(CustomPacketPayload.Type<Msg> type, Object msg) implements CustomPacketPayload {}
    //$$
    //$$ @SuppressWarnings("unchecked")
    //$$ static <T> StreamCodec<RegistryFriendlyByteBuf, Msg> codec(Entry<T> e) {
    //$$     return StreamCodec.of((buf, m) -> e.encode.accept((T) m.msg(), buf), buf -> new Msg(e.type, e.decode.apply(buf)));
    //$$ }
    //#endif

    public Net(String modid) {
        this.modid = modid;
        ALL.add(this);
    }

    private ResourceLocation id(Class<?> cls) {
        String path = cls.getSimpleName().toLowerCase(Locale.ROOT);
        //#if MC>=12100
        //$$ return ResourceLocation.fromNamespaceAndPath(modid, path);
        //#else
        return new ResourceLocation(modid, path);
        //#endif
    }

    public <T> void s2c(Class<T> cls, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
        Entry<T> e = new Entry<>(id(cls), encode, decode, handle);
        byClass.put(cls, e);
        s2c.add(e);
        //#if MC>=12100
        //$$ e.type = new CustomPacketPayload.Type<>(e.id);
        //$$ PayloadTypeRegistry.playS2C().register(e.type, codec(e));
        //#endif
    }

    @SuppressWarnings("unchecked")
    public <T> void c2s(Class<T> cls, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
        Entry<T> e = new Entry<>(id(cls), encode, decode, null);
        byClass.put(cls, e);
        //#if MC>=12100
        //$$ e.type = new CustomPacketPayload.Type<>(e.id);
        //$$ PayloadTypeRegistry.playC2S().register(e.type, codec(e));
        //$$ ServerPlayNetworking.registerGlobalReceiver(e.type, (m, ctx) -> handle.accept((T) m.msg(), ctx.player()));
        //#else
        ServerPlayNetworking.registerGlobalReceiver(e.id, (server, player, handler, buf, sender) -> {
            T m = decode.apply(buf);
            server.execute(() -> handle.accept(m, player));
        });
        //#endif
    }

    @SuppressWarnings("unchecked")
    <T> Entry<T> entry(Object msg) {
        Entry<T> e = (Entry<T>) byClass.get(msg.getClass());
        if (e == null) throw new IllegalArgumentException("not a registered packet: " + msg.getClass());
        return e;
    }

    //#if MC>=12100
    //$$ Msg wrap(Object msg) {
    //$$     return new Msg(entry(msg).type, msg);
    //$$ }
    //#else
    @SuppressWarnings("unchecked")
    <T> FriendlyByteBuf write(Object msg) {
        Entry<T> e = entry(msg);
        FriendlyByteBuf buf = PacketByteBufs.create();
        e.encode.accept((T) msg, buf);
        return buf;
    }
    //#endif

    /** A message as a ready packet (to send with others, in a bundle). */
    public net.minecraft.network.protocol.Packet<?> packet(Object msg) {
        //#if MC>=12100
        //$$ return ServerPlayNetworking.createS2CPacket(wrap(msg));
        //#else
        return ServerPlayNetworking.createS2CPacket(entry(msg).id, write(msg));
        //#endif
    }

    public void toPlayer(ServerPlayer p, Object msg) {
        if (p == null || p.connection == null || p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
        //#if MC>=12100
        //$$ ServerPlayNetworking.send(p, wrap(msg));
        //#else
        ServerPlayNetworking.send(p, entry(msg).id, write(msg));
        //#endif
    }

    /** To every player who sees the entity (and the entity itself when it is a player and andSelf). */
    public void toTracking(Entity e, Object msg, boolean andSelf) {
        for (ServerPlayer p : PlayerLookup.tracking(e)) if (p != e) toPlayer(p, msg);
        if (andSelf && e instanceof ServerPlayer self) toPlayer(self, msg);
    }

    public void toServer(Object msg) {
        NetClient.send(this, msg);
    }
}
