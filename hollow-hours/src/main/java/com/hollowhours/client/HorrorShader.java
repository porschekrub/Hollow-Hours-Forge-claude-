package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHClientConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's own screen shader (assets/hollowhours/shaders/program/haunt.fsh), applied to the world and the hand
 * before the HUD is drawn: drained cold colours, a dark vignette, film grain, colour fringes and a heartbeat pulse
 * when you are afraid, and everything soaked red under a blood moon. Off in the client config (or automatically
 * when a shader-pack loader is installed).
 */
@Mod.EventBusSubscriber(modid = HollowHours.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HorrorShader {
    private HorrorShader() {}

    static final ResourceLocation CHAIN = HollowHours.id("shaders/post/haunt.json");
    private static PostChain chain;
    private static PostPass pass;
    private static boolean failed;
    private static int w, h;
    private static long t0 = -1;

    static boolean shaderPackLoader() {
        return ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris") || ModList.get().isLoaded("optifine");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeHud(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || failed || !HHClientConfig.SHADER.get() || shaderPackLoader()) return;
        float strength = (float) (double) HHClientConfig.SHADER_STRENGTH.get();
        if (strength <= 0.001f) return;
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
                pass = chain.addPass(HollowHours.MODID + ":haunt", main, chain.getTempTarget("swap"));
                chain.addPass("blit", chain.getTempTarget("swap"), main);
                w = h = -1;
                HollowHours.LOG.info("[Hollow Hours] horror shader on");
            }
            if (main.width != w || main.height != h) {
                w = main.width;
                h = main.height;
                chain.resize(w, h);
            }
            float partial = e.getPartialTick();
            if (t0 < 0) t0 = System.nanoTime();
            float clock = ((System.nanoTime() - t0) / 1.0e9f) % 600f;
            var fx = pass.getEffect();
            fx.safeGetUniform("Clock").set(clock);
            fx.safeGetUniform("Fear").set(Fear.local(partial));
            fx.safeGetUniform("Blood").set(Atmos.blood(partial) * (mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? 1 : 0));
            fx.safeGetUniform("Fog").set(Atmos.fog(partial));
            fx.safeGetUniform("Strength").set(strength);
            fx.safeGetUniform("Flashing").set(HHClientConfig.REDUCE_FLASHING.get() ? 0f : 1f);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(partial);
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (Exception ex) {
            failed = true;
            HollowHours.LOG.error("[Hollow Hours] the horror shader could not be loaded, turning it off", ex);
            close();
        }
    }

    public static void close() {
        if (chain != null) chain.close();
        chain = null;
        pass = null;
    }

    /** After a resource reload (F3+T, resource packs) the shader is built again. */
    public static void reload() {
        close();
        failed = false;
    }
}
