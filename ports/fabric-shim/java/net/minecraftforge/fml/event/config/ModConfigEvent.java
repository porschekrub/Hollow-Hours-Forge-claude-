package net.minecraftforge.fml.event.config;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.config.ModConfig;

/** Posted on the mod bus when Forge Config API Port loads / reloads one of the mod's configs. */
public class ModConfigEvent extends Event {
    private final ModConfig config;

    public ModConfigEvent(ModConfig config) {
        this.config = config;
    }

    public ModConfig getConfig() {
        return config;
    }

    public static class Loading extends ModConfigEvent {
        public Loading(ModConfig config) {
            super(config);
        }
    }

    public static class Reloading extends ModConfigEvent {
        public Reloading(ModConfig config) {
            super(config);
        }
    }
}
