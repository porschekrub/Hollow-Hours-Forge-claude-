package net.minecraftforge.fml;

import net.minecraftforge.fml.config.ModConfig;

/** Fabric shim: configs go to Forge Config API Port (same files, same Forge spec classes). */
public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    /** Set by the mod's Fabric entry point before the mod is made. */
    public static String MOD_ID = "";

    private ModLoadingContext() {}

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public void registerConfig(ModConfig.Type type, net.minecraftforge.common.ForgeConfigSpec spec, String fileName) {
        //#if MC>=12100
        //$$ fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry.INSTANCE.register(MOD_ID, type, spec, fileName);
        //#else
        fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry.INSTANCE.register(MOD_ID, type, spec, fileName);
        //#endif
    }
}
