package com.hollowhours.fabric;

import com.hollowhours.HollowHours;
import net.fabricmc.api.ModInitializer;
import net.minecraftforge.internal.FabricMod;

/** Fabric entry point: the mod starts as it does on Forge (its own constructor, events from the shim). */
public final class HHFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricMod.common(HollowHours.MODID, HollowHours::new);
    }
}
