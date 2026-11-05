package net.minecraftforge.internal;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** The client half of {@link FabricMod} (from the mod's ClientModInitializer). */
public final class FabricModClient {
    private FabricModClient() {}

    public static void client() {
        ClientHooks.init();
        FabricMod.subscribers(true);
        NetClient.registerReceivers();
        IEventBus mod = FabricMod.modBus();
        mod.post(new EntityRenderersEvent.RegisterRenderers());
        mod.post(new EntityRenderersEvent.RegisterLayerDefinitions());
        mod.post(new RegisterKeyMappingsEvent());
        mod.post(new RegisterParticleProvidersEvent());
        mod.post(new RegisterClientReloadListenersEvent());
        ModelEvent.RegisterAdditional models = new ModelEvent.RegisterAdditional();
        mod.post(models);
        if (!models.models.isEmpty()) ModelLoadingPlugin.register(ctx -> ctx.addModels(models.models));
        RegisterGuiOverlaysEvent overlays = new RegisterGuiOverlaysEvent();
        mod.post(overlays);
        if (!overlays.overlays.isEmpty()) {
            //#if MC>=12100
            //$$ HudRenderCallback.EVENT.register((g, dt) -> {
            //$$     float partial = dt.getGameTimeDeltaPartialTick(false);
            //#else
            HudRenderCallback.EVENT.register((g, partial) -> {
            //#endif
                if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
                for (IGuiOverlay o : overlays.overlays) o.render(null, g, partial, g.guiWidth(), g.guiHeight());
            });
        }
        mod.post(new FMLClientSetupEvent());
    }
}
