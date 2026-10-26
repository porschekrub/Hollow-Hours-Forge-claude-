package com.hollowhours.client;

import com.hollowhours.HollowHours;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client game events: camera (shake, kill cam), input lock during a kill, hidden HUD, death screen. */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ClientFx.tick();
        Jumpscare.tick();
        KillCam.tick();
        HorrorMusic.tick();
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float partial = (float) e.getPartialTick();
        float[] kc = KillCam.camera(partial);
        if (kc != null) {
            e.setYaw(kc[0]);
            e.setPitch(Mth.clamp(kc[1], -89, 89));
            e.setRoll(kc[2]);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // keep the body looking where the camera looks, so letting go doesn't snap the view
                mc.player.setYRot(kc[0]);
                mc.player.setXRot(Mth.clamp(kc[1], -89, 89));
                mc.player.yRotO = kc[0];
                mc.player.xRotO = mc.player.getXRot();
            }
        }
        float[] s = ClientFx.shakeOffsets(partial);
        if (s[0] != 0 || s[1] != 0) {
            e.setYaw(e.getYaw() + s[0]);
            e.setPitch(e.getPitch() + s[1]);
            e.setRoll(e.getRoll() + s[2]);
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (KillCam.active()) e.setFOV(e.getFOV() * 0.92);
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent e) {
        if (KillCam.active() || Jumpscare.active() && Jumpscare.strength == Jumpscare.DRAG) {
            var in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (KillCam.active()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (KillCam.active() || Jumpscare.active()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (!KillCam.active()) return;
        var id = e.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id()) || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()))
            e.setCanceled(true);
    }

    /** The death screen after a ghost kill: static, blood, and its face flickering in the noise. */
    @SubscribeEvent
    public static void deathScreen(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof DeathScreen) || !com.hollowhours.config.HHClientConfig.DEATH_SCREEN_FX.get()) return;
        if (ClientFx.now() - KillCam.endedAt > 20 * 30) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        ClientFx.renderStatic(g, w, h, 0.12f);
        ClientFx.texture(g, ClientFx.BLOOD, w, h, 0.55f, 1, 1, 1);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientFx.reset();
        KillCam.reset();
        HorrorMusic.reset();
        Jumpscare.who = -1;
    }
}
