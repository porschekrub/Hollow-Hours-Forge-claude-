package com.veritypal.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

/** The client side: the settings page (Mods > Verity Pal > Config) and the client test. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new PalConfigScreen(parent)));
        if (ClientSmoke.ON) MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ClientSmoke.tick();
        });
    }
}
