package net.minecraftforge.server;

import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleHooks {
    private ServerLifecycleHooks() {}

    /** Set by the shim's hooks while a server (integrated or dedicated) runs. */
    public static volatile MinecraftServer currentServer;

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
