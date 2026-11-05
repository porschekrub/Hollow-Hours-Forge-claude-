package com.helperbuddy.fabric;

import com.helperbuddy.HelperBuddy;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.internal.FabricMod;

/** Fabric entry point: the mod starts as it does on Forge (its own constructor, events from the shim). */
public final class HBFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricMod.common(HelperBuddy.MODID, HelperBuddy::new);
    }
}
