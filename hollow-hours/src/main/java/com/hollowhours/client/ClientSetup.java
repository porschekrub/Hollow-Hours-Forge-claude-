package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.client.render.BoardsRenderer;
import com.hollowhours.client.render.ClosetRenderer;
import com.hollowhours.client.render.GhostRenderer;
import com.hollowhours.client.render.StalkerRenderer;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.ghost.Flashlight;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registration: renderers, HUD overlays, item model switches. */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (GhostKind k : GhostKind.values()) e.registerEntityRenderer(HHEntities.ghost(k), ctx -> new GhostRenderer(ctx, k));
        for (StalkerKind k : StalkerKind.values()) e.registerEntityRenderer(HHEntities.stalker(k), ctx -> new StalkerRenderer(ctx, k));
        e.registerEntityRenderer(HHEntities.CLOSET.get(), ClosetRenderer::new);
        e.registerEntityRenderer(HHEntities.BOARDS.get(), BoardsRenderer::new);
        e.registerEntityRenderer(HHEntities.HIDE_SEAT.get(), NoopRenderer::new);
    }

    /** Players get the scared body animations (the model inside both player renderers is swapped). */
    @SubscribeEvent
    public static void playerModels(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> r = e.getSkin(skin);
            if (r == null) continue;
            boolean slim = skin.equals("slim");
            try {
                var part = e.getEntityModels().bakeLayer(slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER);
                for (java.lang.reflect.Field f : net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredFields()) {
                    if (net.minecraft.client.model.EntityModel.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        f.set(r, new com.hollowhours.client.render.ScaredPlayerModel(part, slim));
                        break;
                    }
                }
            } catch (Throwable ex) {
                HollowHours.LOG.warn("[Hollow Hours] could not add the scared player animations to the {} player model", skin, ex);
            }
        }
    }

    @SubscribeEvent
    public static void reloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> HorrorShader.reload());
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "horror_fx", (gui, g, partial, w, h) -> ClientFx.render(g, partial));
        e.registerAboveAll("jumpscare", (gui, g, partial, w, h) -> Jumpscare.render(g, partial));
        e.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "thirst", SurvivalHud::thirst);
        e.registerAbove(VanillaGuiOverlay.ARMOR_LEVEL.id(), "sanity", SurvivalHud::sanity);
        e.registerAboveAll("phone_alert", PhoneAlert::render);
    }

    @SubscribeEvent
    public static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(com.hollowhours.registry.HHParticles.BLOOD.get(), BloodParticle.Provider::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> ItemProperties.register(HHItems.FLASHLIGHT.get(), HollowHours.id("on"),
                (stack, level, entity, seed) -> Flashlight.on(stack) && Flashlight.charge(stack) > 0 ? 1f : 0f));
    }
}
