package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.internal.FabricMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** Forge's AddLayers: after every renderer was (re)made. */
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void shim$addLayers(ResourceManager rm, CallbackInfo ci) {
        FabricMod.modBus().post(new EntityRenderersEvent.AddLayers((EntityRenderDispatcherAccess) this));
    }
}
