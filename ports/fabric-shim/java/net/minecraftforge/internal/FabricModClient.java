package net.minecraftforge.internal;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The client half of {@link FabricMod} (from the mod's ClientModInitializer). */
public final class FabricModClient {
    private FabricModClient() {}

    public static void client() {
        ClientHooks.init();
        FabricMod.subscribers(true);
        NetClient.registerReceivers();
        FabricMod.modBus().post(new EntityRenderersEvent.RegisterRenderers());
        FabricMod.modBus().post(new EntityRenderersEvent.RegisterLayerDefinitions());
        FabricMod.modBus().post(new FMLClientSetupEvent());
    }
}
