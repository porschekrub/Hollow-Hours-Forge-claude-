package com.hollowhours.compat;

/** Client-only parts of {@link Compat} (never loaded on a dedicated server). */
public final class ClientCompat {
    private ClientCompat() {}

    //#if MC>=12100
    //$$ static net.minecraft.core.HolderLookup.Provider registries() {
    //$$     var mc = net.minecraft.client.Minecraft.getInstance();
    //$$     return mc.level != null ? mc.level.registryAccess() : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    //$$ }
    //#endif

    /** How far the current frame is between two ticks (0..1). */
    public static float partialTick() {
        //#if MC>=12100
        //$$ return net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        //#else
        return net.minecraft.client.Minecraft.getInstance().getFrameTime();
        //#endif
    }

    //#if MC>=12100
    //$$ /** An extra block model loaded on its own (drawn by an entity renderer). */
    //$$ public static net.minecraft.client.resources.model.ModelResourceLocation model(String path) {
    //$$     return new net.minecraft.client.resources.model.ModelResourceLocation(com.hollowhours.HollowHours.id(path), "standalone");
    //$$ }
    //#endif
}
