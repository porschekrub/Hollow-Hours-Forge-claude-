package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.internal.EventBusImpl;

/** Fabric shim: the game event bus (this mod's own; fired from Fabric hooks). */
public final class MinecraftForge {
    private MinecraftForge() {}

    public static final IEventBus EVENT_BUS = new EventBusImpl("game");
}
