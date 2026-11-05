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

    /**
     * The haven is an extra world (a datapack dimension), and Minecraft warns about any world with one as "experimental".
     * It's ours and it's fine: press Proceed for the player (can be turned off in the client config).
     */
    @SubscribeEvent
    public static void experimentalWarning(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof net.minecraft.client.gui.screens.ConfirmScreen cs) || !com.hollowhours.config.HHClientConfig.SKIP_EXPERIMENTAL_WARNING.get()) return;
        if (!(cs.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title)
                || !title.getKey().equals("selectWorld.backupQuestion.experimental") && !title.getKey().equals("selectWorld.warning.experimental.title")) return;
        for (var w : e.getListenersList())
            if (w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("gui.proceed")) {
                HollowHours.LOG.info("[Hollow Hours] skipping the experimental settings warning (the haven dimension)");
                Minecraft.getInstance().tell(b::onPress);
                return;
            }
        HollowHours.LOG.warn("[Hollow Hours] the experimental settings warning has no Proceed button we know");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ClientFx.tick();
        Atmos.tick();
        Fear.tick();
        PhoneAlert.tick();
        PhoneNotice.tick();
        FirstPersonAnim.tick();
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
        // fear: heavy breathing, and a stumbling panicked sway while running
        Minecraft mc = Minecraft.getInstance();
        float sway = (float) (double) com.hollowhours.config.HHClientConfig.CAMERA_SWAY.get();
        if (kc == null && sway > 0 && Atmos.haunted && mc.player != null && mc.options.getCameraType().isFirstPerson()) {
            float f = Fear.local(partial) * sway;
            if (f > 0.01f) {
                float t = ClientFx.now() + partial;
                e.setPitch(e.getPitch() + Mth.sin(t * (0.10f + 0.2f * f)) * (0.2f + 1.0f * f) * f);
                e.setRoll(e.getRoll() + Mth.sin(t * 0.071f) * 0.7f * f);
                if (mc.player.isSprinting() && f > 0.3f) {
                    e.setRoll(e.getRoll() + Mth.sin(t * 0.66f) * 1.5f * f);
                    e.setYaw(e.getYaw() + Mth.sin(t * 0.33f) * 0.5f * f);
                }
            }
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
        } else if (ClientFx.outOfBreath() && e.getEntity() == Minecraft.getInstance().player) {
            // out of breath: no running until stamina is back to 15% (a hair less forward push = sprinting can't start)
            e.getEntity().setSprinting(false);
            e.getInput().forwardImpulse = Math.min(e.getInput().forwardImpulse, 0.79f);
        }
    }

    private static long lastSip;

    /** Sneak + right-click water with an empty hand: a sip (the server checks again). */
    static void trySip(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isUseItem() || mc.player == null || mc.level == null || !mc.player.isShiftKeyDown() || !mc.player.getMainHandItem().isEmpty()) return;
        if (SurvivalHud.thirst < 0) return;
        var water = com.hollowhours.ghost.Thirst.waterLookedAt(mc.player, 5.0);
        if (water == null) return;
        // looking at water: never fall through to placing / using anything, even during the short cooldown
        e.setSwingHand(false);
        e.setCanceled(true);
        if (mc.level.getGameTime() - lastSip < 10) return;
        lastSip = mc.level.getGameTime();
        FirstPersonAnim.startSip();
        com.hollowhours.net.HHNet.toServer(new com.hollowhours.net.C2SSip(water));
        e.setSwingHand(true);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        trySip(e);
        if (e.isCanceled()) return;
        if (KillCam.active()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) {
        if (KillCam.active() || Jumpscare.active()) {
            e.setCanceled(true);
            return;
        }
        if (ReloadAnim.render(e)) {
            e.setCanceled(true);
            return;
        }
        // the hands' body language: carefree before the story, scared after (and eating, drinking, sipping)
        if (FirstPersonAnim.render(e)) e.setCanceled(true);
    }

    @SubscribeEvent
    //#if NEOFORGE
    //$$ public static void hud(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e) {
    //$$     if (KillCam.active() && Hud.hiddenInKill(e.getName())) e.setCanceled(true);
    //$$ }
    //#elif MC>=12100
    //$$ public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Pre e) {
    //$$     // Forge 1.21 has no per-layer event: the vanilla HUD stays during the kill cinematic
    //$$ }
    //#else
    public static void hud(RenderGuiOverlayEvent.Pre e) {
        if (KillCam.active() && Hud.hiddenInKill(e.getOverlay().id())) e.setCanceled(true);
    }
    //#endif

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
        Atmos.reset();
        PhoneAlert.reset();
        PhoneNotice.reset();
        ClientPhone.reset();
        SurvivalHud.thirst = SurvivalHud.sanity = -1;
        KillCam.reset();
        HorrorMusic.reset();
        Jumpscare.who = -1;
    }
}
