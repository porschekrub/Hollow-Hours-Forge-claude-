package net.minecraftforge.internal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client half of {@link Net} (only loaded on the client). */
public final class NetClient {
    private NetClient() {}

    /** Every mod's S2C messages (called once, from the client entry point). */
    public static void registerReceivers() {
        for (Net n : Net.ALL) for (Net.Entry<?> e : n.s2c) register(e);
    }

    @SuppressWarnings("unchecked")
    private static <T> void register(Net.Entry<T> e) {
        //#if MC>=12100
        //$$ ClientPlayNetworking.registerGlobalReceiver(e.type, (m, ctx) -> e.clientHandle.accept((T) m.msg()));
        //#else
        ClientPlayNetworking.registerGlobalReceiver(e.id, (client, handler, buf, sender) -> {
            T m = e.decode.apply(buf);
            client.execute(() -> e.clientHandle.accept(m));
        });
        //#endif
    }

    static void send(Net n, Object msg) {
        //#if MC>=12100
        //$$ ClientPlayNetworking.send(n.wrap(msg));
        //#else
        ClientPlayNetworking.send(n.entry(msg).id, n.write(msg));
        //#endif
    }
}
