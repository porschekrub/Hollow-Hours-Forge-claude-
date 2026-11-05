package net.minecraftforge.internal;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class Sides {
    private Sides() {}

    /** The client's world (null on a server): ClientExt is only loaded on the client. */
    public static Level clientLevel() {
        return FMLEnvironment.dist.isClient() ? ClientExt.level() : null;
    }
}
