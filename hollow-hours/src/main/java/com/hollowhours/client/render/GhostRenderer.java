package com.hollowhours.client.render;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/** Ghost renderer: per-ghost scale, glowing eyes/mouth (…_glow.png), no shadow, a cold flicker up close. */
public class GhostRenderer extends GeoEntityRenderer<GhostEntity> {
    private final GhostKind kind;

    public GhostRenderer(EntityRendererProvider.Context ctx, GhostKind kind) {
        super(ctx, new GhostModel(kind));
        this.kind = kind;
        this.shadowRadius = 0f;
        withScale(kind.scaleXZ, kind.scaleY);
        addRenderLayer(new EmissiveLayer<>(this, g -> EmissiveLayer.glowOf(getTextureLocation(g))));
    }

    @Override
    public RenderType getRenderType(GhostEntity g, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public Color getRenderColor(GhostEntity g, float partialTick, int packedLight) {
        // a faint flicker: the closer it is, the more it stutters
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(g.position());
        float t = g.tickCount + partialTick;
        float flick = d < 12 ? (Mth.sin(t * 1.7f + g.seed) > 0.93f ? 0.55f : 1f) : 1f;
        return Color.ofRGBA(flick, flick, flick, 1f);
    }

    @Override
    public void preRender(PoseStack poseStack, GhostEntity g, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, g, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private float clipMargin = 0.3f;

    /**
     * Cubes touching the camera are skipped: an arm swinging through the victim's face (or the arm holding the victim,
     * seen along its length) would fill the screen with stretched polygons. During a kill the limbs, hair and rags get
     * a wider margin than the head, so the face screaming at you stays visible.
     */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, software.bernie.geckolib.cache.object.GeoBone bone, VertexConsumer buffer, int packedLight,
                                  int packedOverlay, float red, float green, float blue, float alpha) {
        clipMargin = marginFor(bone.getName());
        currentBone = bone.getName();
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    static float marginFor(String bone) {
        if (!com.hollowhours.client.KillCam.active()) return 0.3f;
        String n = bone.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("arm") || n.contains("hand") || n.contains("rag") || n.contains("hair")) return 1.0f;
        if (n.contains("leg") || n.contains("shin") || n.contains("foot")) return 0.85f;
        // the victim is held against the ghost: its chest and neck right in front of the eye would fill the screen
        if (n.contains("chest") || n.contains("body") || n.contains("neck") || n.contains("rib") || n.contains("spine") || n.contains("torso")) return 0.8f;
        // the face screaming at you stays; only when it is pressed right against the eye (seen edge-on it turns into a
        // long "road" across the screen) it is not drawn
        return 0.55f;
    }

    // CI diagnostics: which bones come close to the victim's eye during a kill
    static final boolean DEBUG = Boolean.getBoolean("hollowhours.clientsmoke");
    /** Set by the CI smoke test right before a screenshot: the next frame logs every cube near the camera. */
    public static volatile String debugTag;
    private boolean collecting;
    private String currentBone = "";
    private final StringBuilder debugLine = new StringBuilder();
    private final java.util.List<Object[]> near = new java.util.ArrayList<>();

    @Override
    public void renderCube(PoseStack poseStack, software.bernie.geckolib.cache.object.GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha) {
        boolean skip = NearClip.touchesCamera(poseStack, cube, clipMargin);
        if (DEBUG && collecting) {
            float d = NearClip.distance(poseStack, cube);
            if (d < 2.5f) {
                double sz = Math.max(cube.size().x, Math.max(cube.size().y, cube.size().z)) / 16.0;
                near.add(new Object[] {d, currentBone + "=" + String.format("%.2f/%.2f", d, sz) + (skip ? "x" : "")});
            }
        }
        if (skip) return;
        super.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, GhostEntity g, software.bernie.geckolib.cache.object.BakedGeoModel model, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        String tag = debugTag;
        collecting = DEBUG && !isReRender && tag != null;
        if (collecting) near.clear();
        super.actuallyRender(poseStack, g, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (collecting) {
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            var cam = camera.getPosition();
            near.sort((a, b) -> Float.compare((Float) a[0], (Float) b[0]));
            debugLine.setLength(0);
            debugLine.append("look ").append(String.format("%.0f/%.0f", camera.getYRot(), camera.getXRot())).append(" | ");
            for (int i = 0; i < Math.min(30, near.size()); i++) debugLine.append(near.get(i)[1]).append(' ');
            com.hollowhours.HollowHours.LOG.info("[killcam] {} ghost {} at {} cam {} dist {} cubes (dist/size): {}", tag, g.kind.n, g.position(), cam,
                    String.format("%.2f", cam.distanceTo(g.position())), debugLine);
            collecting = false;
            debugTag = null;
        }
    }

    @Override
    protected float getDeathMaxRotation(GhostEntity g) {
        return 0f;
    }

    @Override
    public boolean shouldShowName(GhostEntity g) {
        return false;
    }
}
