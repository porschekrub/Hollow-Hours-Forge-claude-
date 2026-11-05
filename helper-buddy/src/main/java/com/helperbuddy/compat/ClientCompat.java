package com.helperbuddy.compat;

/** Client-only parts of {@link Compat} (never loaded on a dedicated server). */
public final class ClientCompat {
    private ClientCompat() {}

    //#if MC>=12100
    //$$ static net.minecraft.core.HolderLookup.Provider registries() {
    //$$     var mc = net.minecraft.client.Minecraft.getInstance();
    //$$     return mc.level != null ? mc.level.registryAccess() : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    //$$ }
    //#endif
}
