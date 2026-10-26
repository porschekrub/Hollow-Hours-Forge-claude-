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

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "horror_fx", (gui, g, partial, w, h) -> ClientFx.render(g, partial));
        e.registerAboveAll("jumpscare", (gui, g, partial, w, h) -> Jumpscare.render(g, partial));
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> ItemProperties.register(HHItems.FLASHLIGHT.get(), HollowHours.id("on"),
                (stack, level, entity, seed) -> Flashlight.on(stack) && Flashlight.charge(stack) > 0 ? 1f : 0f));
    }
}
