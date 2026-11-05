package net.minecraftforge.internal;

import net.minecraft.server.level.ServerPlayer;

/** A player's game language ("th_th", ...). */
public final class Lang {
    private Lang() {}

    public static String of(ServerPlayer p) {
        //#if MC>=12100
        //$$ return p.clientInformation().language();
        //#else
        return ((net.minecraftforge.internal.mixin.ServerPlayerAccess) p).shim$language();
        //#endif
    }
}
