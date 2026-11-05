package net.minecraftforge.internal.mixin.client;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccess {
    @Accessor("playerRenderers")
    //#if MC>=12100
    //$$ Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends Player>> shim$playerRenderers();
    //#else
    Map<String, EntityRenderer<? extends Player>> shim$playerRenderers();
    //#endif

    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?>> shim$renderers();

    @Accessor("entityModels")
    EntityModelSet shim$entityModels();
}
