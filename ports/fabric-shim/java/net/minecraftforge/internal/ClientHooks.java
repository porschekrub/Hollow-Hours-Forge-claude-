package net.minecraftforge.internal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

/** Fabric client callbacks -> the shim's Forge client events. */
public final class ClientHooks {
    private ClientHooks() {}

    private static boolean done;

    public static void init() {
        if (done) return;
        done = true;
        ClientTickEvents.START_CLIENT_TICK.register(mc -> Hooks.post(new TickEvent.ClientTickEvent(TickEvent.Phase.START)));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Hooks.post(new TickEvent.ClientTickEvent(TickEvent.Phase.END)));
        ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> Hooks.post(new ClientPlayerNetworkEvent.LoggingIn()));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> Hooks.post(new ClientPlayerNetworkEvent.LoggingOut()));
        ClientEntityEvents.ENTITY_LOAD.register((e, l) -> Ext.call(e, "onAddedToWorld"));
        ClientEntityEvents.ENTITY_UNLOAD.register((e, l) -> Ext.call(e, "onRemovedFromWorld"));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            List<GuiEventListener> children = new ArrayList<>(screen.children());
            Hooks.post(new ScreenEvent.Init.Post(screen, children));
            ScreenEvents.afterRender(screen).register((s, g, mx, my, partial) -> Hooks.post(new ScreenEvent.Render.Post(s, g, mx, my, partial)));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scan, mods) -> !Hooks.post(new ScreenEvent.KeyPressed.Pre(s, key, scan, mods)));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_ENTITIES, c));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, c));
        WorldRenderEvents.LAST.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_WEATHER, c));
        WorldRenderEvents.END.register(c -> stage(RenderLevelStageEvent.Stage.AFTER_LEVEL, c));
    }

    private static void stage(RenderLevelStageEvent.Stage stage, WorldRenderContext c) {
        //#if MC>=12100
        //$$ float partial = c.tickCounter().getGameTimeDeltaPartialTick(false);
        //$$ com.mojang.blaze3d.vertex.PoseStack ps = c.matrixStack() != null ? c.matrixStack() : new com.mojang.blaze3d.vertex.PoseStack();
        //#else
        float partial = c.tickDelta();
        com.mojang.blaze3d.vertex.PoseStack ps = c.matrixStack();
        //#endif
        Hooks.post(new RenderLevelStageEvent(stage, c.worldRenderer(), ps, c.projectionMatrix(),
                net.minecraft.client.Minecraft.getInstance().levelRenderer == null ? 0 : (int) c.world().getGameTime(), partial, c.camera(), c.frustum()));
    }

    /** A vanilla HUD part about to be drawn: false when a listener cancelled it. */
    public static boolean hudHidden(String name) {
        //#if MC>=12100
        //$$ ResourceLocation id = ResourceLocation.withDefaultNamespace(name);
        //#else
        ResourceLocation id = new ResourceLocation(name);
        //#endif
        return Hooks.post(new RenderGuiOverlayEvent.Pre(null, 0, new NamedGuiOverlay(id)));
    }
}
