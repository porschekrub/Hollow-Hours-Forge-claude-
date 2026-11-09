package com.helperbuddy.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Every packet of the mod, in a fixed order. Each loader's HBNet registers them on its own network API:
 * S2C handlers run on the client's main thread, C2S handlers on the server's with the sender.
 */
public final class Packets {
    /** Bump when a packet changes: client and server must have the same set. */
    public static final String VERSION = "2";

    public interface Registrar {
        <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, Consumer<T> handle);

        <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle);
    }

    private Packets() {}

    public static void register(Registrar r) {
        r.s2c(S2COpen.class, S2COpen::encode, S2COpen::decode, S2COpen::handle);
        r.c2s(C2SAction.class, C2SAction::encode, C2SAction::decode, C2SAction::handle);
        r.c2s(C2STalk.class, C2STalk::encode, C2STalk::decode, C2STalk::handle);
        r.s2c(S2CSpeak.class, S2CSpeak::encode, S2CSpeak::decode, S2CSpeak::handle);
        r.s2c(S2CVoice.class, S2CVoice::encode, S2CVoice::decode, S2CVoice::handle);
    }
}
