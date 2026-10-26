package com.hollowhours.client;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHClientConfig;
import com.hollowhours.registry.HHSounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen jumpscares. A dedicated close-up "face rig" of the ghost (head, shoulders, hands; built for the camera)
 * lunges out of the dark straight into the lens with a perspective camera, screaming, while the screen strobes red,
 * shakes and fills with static. Ghosts 1-5, stalkers 11-13.
 */
public final class Jumpscare {
    private Jumpscare() {}

    public static final int FLASH = 0, STRONG = 1, DEATH = 2, DRAG = 3;

    static int who = -1, strength, variant;
    static long t0;
    static float duration;
    static FaceRig rig;

    public static boolean active() {
        return who >= 0 && ClientFx.now() - t0 < duration * 20;
    }

    static float time(float partial) {
        return (ClientFx.now() - t0 + partial) / 20f;
    }

    // ------------------------------------------------------------------ start
    static void start(int w, int s, int v) {
        if (!HHClientConfig.FACE_CLOSEUPS.get() && s == FLASH) {
            ClientFx.addStatic(0.6f, 10);
            ClientFx.addShake(0.6f, 10);
            return;
        }
        who = w;
        strength = s;
        variant = v;
        t0 = ClientFx.now();
        duration = switch (s) {
            case STRONG -> 1.7f;
            case DEATH -> 2.6f;
            case DRAG -> 1.5f;
            default -> v == 99 ? 0.9f : 0.75f;
        };
        rig = new FaceRig(w, variant);
        float vol = (float) (double) HHClientConfig.JUMPSCARE_VOLUME.get();
        play("ghost.jumpscare.hit", vol, 1f);
        play(w >= 11 ? "ghost.scream.stalker" + (w - 10) : "ghost.scream.g" + Mth.clamp(w, 1, 5), vol * (s == FLASH ? 0.8f : 1f), 0.95f + (float) Math.random() * 0.1f);
        if (s >= STRONG) play("ghost.jumpscare.static", vol * 0.8f, 1f);
        if (s == DEATH) play("ghost.jumpscare.boom", vol, 0.8f);
        ClientFx.addShake(s == FLASH ? 0.9f : 1.6f, (int) (duration * 20));
        ClientFx.addStatic(s == FLASH ? 0.5f : 0.8f, (int) (duration * 20) + 6);
        if (s != FLASH) ClientFx.addBlood(0.7f, (int) (duration * 20) + 30);
    }

    static void play(String name, float vol, float pitch) {
        if (!HHSounds.has(name)) return;
        SoundEvent se = HHSounds.get(name);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(se, pitch, Mth.clamp(vol, 0, 1)));
    }

    static void tick() {
        if (who >= 0 && !active()) {
            // end: a hard cut to black that lingers a moment (afterimage)
            if (strength != FLASH) ClientFx.fade(0x000000, 0, 3, 10);
            who = -1;
            rig = null;
        }
    }

    // ------------------------------------------------------------------ render
    static void render(GuiGraphics g, float partial) {
        if (!active() || rig == null) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float t = time(partial);
        float k = (float) (double) HHClientConfig.JUMPSCARE_INTENSITY.get();
        boolean calm = HHClientConfig.REDUCE_FLASHING.get();
        // background: black, with red strobes in the first moments
        float bg = Mth.clamp(t / 0.06f, 0, 1);
        g.fill(0, 0, w, h, ClientFx.argb(bg * (strength == FLASH ? 0.85f : 0.97f), 0x000000));
        boolean strobe = !calm && t < 0.45f && ((int) (t * 24)) % 2 == 0;
        if (strobe) g.fill(0, 0, w, h, ClientFx.argb(0.55f * k, 0x8A0000));

        // the face: rushes from the dark into the lens in ~0.12 s, then shudders right in front of you
        float lunge = Mth.clamp(t / 0.12f, 0, 1);
        lunge = 1 - (1 - lunge) * (1 - lunge) * (1 - lunge);
        float dist = Mth.lerp(lunge, 7.5f, strength == FLASH ? 0.78f : 0.6f);
        if (strength == DRAG) dist = Mth.lerp(lunge, 3f, 0.55f);
        float pulse = t > 0.12f ? 0.04f * Mth.sin(t * 60f) * k : 0;
        dist += pulse;
        float jx = (float) (Math.random() - 0.5) * 0.06f * k, jy = (float) (Math.random() - 0.5) * 0.06f * k;
        float tilt = (float) Math.sin(t * 37) * 6f * k + (variant % 3 - 1) * 7f;
        renderFace(rig, w, h, dist, jx, jy, tilt, partial, strobe ? 1.35f : 1f);

        // static over everything, scratches of red
        ClientFx.renderStatic(g, w, h, (strength == FLASH ? 0.18f : 0.28f) + (t < 0.2f ? 0.35f : 0));
        if (!calm && t > 0.15f && Math.random() < 0.18) g.fill(0, (int) (Math.random() * h), w, (int) (Math.random() * h), ClientFx.argb(0.25f, 0x220000));
        // the very end of a death scare: everything goes red
        if (strength == DEATH && t > duration - 0.6f) g.fill(0, 0, w, h, ClientFx.argb((t - (duration - 0.6f)) / 0.6f, 0x3A0000));
    }

    static final Map<Integer, FaceRenderer> RENDERERS = new HashMap<>();

    static FaceRenderer renderer(int who) {
        return RENDERERS.computeIfAbsent(who, FaceRenderer::new);
    }

    /** Renders the face rig with a real perspective projection on top of the GUI. */
    static void renderFace(FaceRig rig, int w, int h, float dist, float jx, float jy, float tiltDeg, float partial, float bright) {
        Minecraft mc = Minecraft.getInstance();
        float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(70), aspect, 0.05f, 100f);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        // light from below and in front: the oldest horror trick
        RenderSystem.setShaderLights(new Vector3f(0.2f, -1f, 0.9f).normalize(), new Vector3f(-0.3f, 0.4f, 0.6f).normalize());
        PoseStack ps = new PoseStack();
        ps.translate(jx, jy - FaceRig.EYE_Y, -dist);
        ps.mulPose(Axis.ZP.rotationDegrees(tiltDeg));
        ps.mulPose(Axis.YP.rotationDegrees(180));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FaceRenderer r = renderer(rig.who);
        r.bright = bright;
        r.render(ps, rig, buffers, null, null, LightTexture.FULL_BRIGHT);
        buffers.endBatch();
        RenderSystem.disableDepthTest();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    // ------------------------------------------------------------------ face rig animatable
    public static final class FaceRig implements GeoAnimatable {
        /** Height of the eyes in the face model (blocks): the camera aims there. */
        static final float EYE_Y = 1.62f;
        final int who, variant;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        FaceRig(int who, int variant) {
            this.who = who;
            this.variant = variant;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar reg) {
            String anim = variant % 2 == 0 ? "animation.face.scream" : "animation.face.scream2";
            RawAnimation a = RawAnimation.begin().thenPlayAndHold(anim);
            reg.add(new AnimationController<>(this, "face", 0, s -> s.setAndContinue(a)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object o) {
            return RenderUtils.getCurrentTick();
        }
    }

    static final class FaceModel extends GeoModel<FaceRig> {
        final ResourceLocation model, texture, anims;

        FaceModel(int who) {
            String id = who >= 11 ? "face_s" + (who - 10) : "face_g" + Mth.clamp(who, 1, 5);
            model = HollowHours.id("geo/face/" + id + ".geo.json");
            texture = HollowHours.id("textures/face/" + id + ".png");
            anims = HollowHours.id("animations/face/face.animation.json");
        }

        @Override
        public ResourceLocation getModelResource(FaceRig r) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(FaceRig r) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(FaceRig r) {
            return anims;
        }
    }

    static final class FaceRenderer extends GeoObjectRenderer<FaceRig> {
        float bright = 1f;

        FaceRenderer(int who) {
            super(new FaceModel(who));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(FaceRig r, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
            return RenderType.entityCutoutNoCull(texture);
        }

        @Override
        public software.bernie.geckolib.core.object.Color getRenderColor(FaceRig r, float partialTick, int packedLight) {
            float b = Mth.clamp(bright, 0, 1);
            return software.bernie.geckolib.core.object.Color.ofRGBA(b, b * (bright > 1 ? 0.75f : 1f), b * (bright > 1 ? 0.75f : 1f), 1f);
        }

        @Override
        public void preRender(PoseStack poseStack, FaceRig animatable, software.bernie.geckolib.cache.object.BakedGeoModel model, MultiBufferSource bufferSource,
                              com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                              float red, float green, float blue, float alpha) {
            // no item-style centering: the rig's origin is at its feet, like an entity
        }
    }
}
