package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The haunting's weather on the client: rolling fog and the blood moon (red moon and glow, blood-red night fog).
 * The server decides (ghost.Atmosphere); values fade in and out smoothly here.
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Atmos {
    private Atmos() {}

    static final ResourceLocation MOON = HollowHours.id("textures/environment/blood_moon.png");
    static final ResourceLocation GLOW = HollowHours.id("textures/environment/blood_moon_glow.png");

    static boolean bloodTarget;
    /** The story has started (from the server); hauntAmt fades the horror look in over a few seconds. */
    public static boolean haunted;
    static float hauntAmt, hauntAmtO;
    static float fogTarget;
    /** smoothed 0..1 */
    static float blood, bloodO, fog, fogO;

    static void set(boolean bloodMoon, float fogStrength) {
        bloodTarget = bloodMoon;
        fogTarget = Mth.clamp(fogStrength, 0, 1);
    }

    public static void reset() {
        haunted = false;
        hauntAmt = hauntAmtO = 0;
        bloodTarget = false;
        fogTarget = 0;
        blood = bloodO = fog = fogO = 0;
    }

    /** CI screenshots: jump straight to the target values. */
    static void snap() {
        hauntAmt = hauntAmtO = haunted ? 1 : 0;
        blood = bloodO = bloodTarget ? 1 : 0;
        fog = fogO = fogTarget;
    }

    public static float haunt(float partial) {
        return Mth.lerp(partial, hauntAmtO, hauntAmt);
    }

    static void tick() {
        hauntAmtO = hauntAmt;
        hauntAmt = approach(hauntAmt, haunted ? 1 : 0, 0.008f);
        bloodO = blood;
        fogO = fog;
        blood = approach(blood, bloodTarget ? 1 : 0, 0.01f);
        fog = approach(fog, fogTarget, 0.004f); // fog rolls in over ~10 seconds
    }

    static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    public static float blood(float partial) {
        return Mth.lerp(partial, bloodO, blood);
    }

    public static float fog(float partial) {
        return Mth.lerp(partial, fogO, fog);
    }

    /** 1 at night, 0 by day (smooth around dusk and dawn). */
    static float night(ClientLevel level, float partial) {
        float a = level.getTimeOfDay(partial); // 0 = noon, 0.5 = midnight
        float c = Mth.cos(a * Mth.TWO_PI);   // 1 noon, -1 midnight
        return Mth.clamp(0.5f - c * 1.6f, 0, 1);
    }

    // ------------------------------------------------------------------ fog distance
    @SubscribeEvent
    public static void fogDistance(ViewportEvent.RenderFog e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || e.getType() != FogType.NONE) return;
        if (mc.getCameraEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS))) return;
        float p = (float) e.getPartialTick();
        float f = fog(p);
        float b = blood(p) * night(mc.level, p);
        // the blood moon brings a thin red haze of its own
        float k = Math.max(f, b * 0.45f);
        if (k < 0.005f) return;
        float far = e.getFarPlaneDistance(), near = e.getNearPlaneDistance();
        float thickFar = 10f + 26f * (1 - f);
        float newFar = Mth.lerp(k, far, Math.min(far, thickFar));
        e.setFarPlaneDistance(newFar);
        e.setNearPlaneDistance(Math.min(near, newFar * Mth.lerp(k, 0.75f, -0.1f)));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;
        float p = (float) e.getPartialTick();
        float f = fog(p);
        float b = blood(p) * night(mc.level, p);
        float r = e.getRed(), g = e.getGreen(), bl = e.getBlue();
        if (f > 0.005f) {
            // a grey wall: same brightness as the sky, colour drained, slightly cold
            float lum = 0.3f * r + 0.59f * g + 0.11f * bl;
            float grey = Math.max(0.085f, lum * 1.05f); // even at night the fog is a pale wall you can see
            float k = f * 0.85f;
            r = Mth.lerp(k, r, grey * 0.96f);
            g = Mth.lerp(k, g, grey * 0.98f);
            bl = Mth.lerp(k, bl, grey * 1.04f);
        }
        if (b > 0.005f) {
            r = Mth.lerp(b * 0.8f, r, 0.30f);
            g = Mth.lerp(b * 0.8f, g, 0.025f);
            bl = Mth.lerp(b * 0.8f, bl, 0.03f);
        }
        e.setRed(r);
        e.setGreen(g);
        e.setBlue(bl);
    }

    // ------------------------------------------------------------------ the red moon
    /** Counts rendered frames (for once-per-frame effects). */
    static int frame;

    @SubscribeEvent
    public static void sky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        frame++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        //#if NEOFORGE
        //$$ float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        //#else
        float partial = e.getPartialTick();
        //#endif
        float b = blood(partial);
        if (b < 0.01f) return;
        float alpha = b * (1 - level.getRainLevel(partial));
        //#if MC>=12100 && FORGE
        //$$ PoseStack ps = new PoseStack();
        //$$ ps.mulPose(e.getPoseStack());
        //#else
        PoseStack ps = e.getPoseStack();
        //#endif
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partial) * 360.0F));
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        // a wide red glow (added light), then the moon itself drawn over the vanilla one
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1, 1, 1, alpha * 0.9f);
        RenderSystem.setShaderTexture(0, GLOW);
        quad(m, 70f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        RenderSystem.setShaderTexture(0, MOON);
        quad(m, 23f);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        ps.popPose();
    }

    static void quad(Matrix4f m, float k) {
        //#if MC>=12100
        //$$ BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        //$$ bb.addVertex(m, -k, -100f, k).setUv(1, 1);
        //$$ bb.addVertex(m, k, -100f, k).setUv(0, 1);
        //$$ bb.addVertex(m, k, -100f, -k).setUv(0, 0);
        //$$ bb.addVertex(m, -k, -100f, -k).setUv(1, 0);
        //$$ BufferUploader.drawWithShader(bb.buildOrThrow());
        //#else
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -k, -100f, k).uv(1, 1).endVertex();
        bb.vertex(m, k, -100f, k).uv(0, 1).endVertex();
        bb.vertex(m, k, -100f, -k).uv(0, 0).endVertex();
        bb.vertex(m, -k, -100f, -k).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        //#endif
    }
}
