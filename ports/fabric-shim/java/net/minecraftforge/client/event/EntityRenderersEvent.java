package net.minecraftforge.client.event;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.Event;

import java.util.function.Supplier;

/** Fabric shim: renderers and model layers go straight to Fabric's registries. */
public class EntityRenderersEvent extends Event {
    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
            net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
        }

        public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
            BlockEntityRenderers.register(type, provider);
        }
    }

    /** The player and entity renderers are there: layers (or models) can be changed. */
    public static class AddLayers extends EntityRenderersEvent {
        private final net.minecraftforge.internal.mixin.client.EntityRenderDispatcherAccess dispatcher;

        public AddLayers(net.minecraftforge.internal.mixin.client.EntityRenderDispatcherAccess dispatcher) {
            this.dispatcher = dispatcher;
        }

        //#if MC>=12100
        //$$ public java.util.Set<net.minecraft.client.resources.PlayerSkin.Model> getSkins() {
        //$$     return dispatcher.shim$playerRenderers().keySet();
        //$$ }
        //$$
        //$$ @SuppressWarnings("unchecked")
        //$$ public <R extends net.minecraft.client.renderer.entity.EntityRenderer<? extends net.minecraft.world.entity.player.Player>> R getSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
        //$$     return (R) dispatcher.shim$playerRenderers().get(skin);
        //$$ }
        //$$
        //$$ public <R extends net.minecraft.client.renderer.entity.EntityRenderer<? extends net.minecraft.world.entity.player.Player>> R getPlayerSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
        //$$     return getSkin(skin);
        //$$ }
        //#else
        public java.util.Set<String> getSkins() {
            return dispatcher.shim$playerRenderers().keySet();
        }

        @SuppressWarnings("unchecked")
        public <R extends net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?>> R getSkin(String skin) {
            return (R) dispatcher.shim$playerRenderers().get(skin);
        }
        //#endif

        @SuppressWarnings("unchecked")
        public <R extends net.minecraft.client.renderer.entity.EntityRenderer<?>> R getRenderer(EntityType<?> type) {
            return (R) dispatcher.shim$renderers().get(type);
        }

        public net.minecraft.client.model.geom.EntityModelSet getEntityModels() {
            return dispatcher.shim$entityModels();
        }
    }

    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> supplier) {
            EntityModelLayerRegistry.registerModelLayer(layer, supplier::get);
        }
    }
}
