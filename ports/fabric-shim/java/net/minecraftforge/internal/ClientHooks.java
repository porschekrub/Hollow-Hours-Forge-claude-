package net.minecraftforge.internal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraftforge.event.TickEvent;

/** Fabric client callbacks -> the shim's Forge client events. */
public final class ClientHooks {
    private ClientHooks() {}

    private static boolean done;

    public static void init() {
        if (done) return;
        done = true;
        ClientTickEvents.START_CLIENT_TICK.register(mc -> Hooks.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Hooks.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
    }
}
