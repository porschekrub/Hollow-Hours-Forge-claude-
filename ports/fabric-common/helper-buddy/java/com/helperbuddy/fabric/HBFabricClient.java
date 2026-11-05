package com.helperbuddy.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.minecraftforge.internal.FabricModClient;

public final class HBFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricModClient.client();
    }
}
