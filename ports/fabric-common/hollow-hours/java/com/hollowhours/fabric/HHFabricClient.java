package com.hollowhours.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.minecraftforge.internal.FabricModClient;

public final class HHFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricModClient.client();
    }
}
