package net.minecraftforge.client.event;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RegisterClientReloadListenersEvent extends Event {
    private int n;

    public void registerReloadListener(PreparableReloadListener listener) {
        //#if MC>=12100
        //$$ ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ModLoadingContext.MOD_ID, "reload_" + n++);
        //#else
        ResourceLocation id = new ResourceLocation(ModLoadingContext.MOD_ID, "reload_" + n++);
        //#endif
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager rm, ProfilerFiller prep, ProfilerFiller apply,
                                                  Executor bg, Executor game) {
                return listener.reload(barrier, rm, prep, apply, bg, game);
            }
        });
    }
}
