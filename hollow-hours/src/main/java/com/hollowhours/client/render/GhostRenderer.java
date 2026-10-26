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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/** Ghost renderer: per-ghost scale, glowing eyes/mouth (…_glowmask.png), no shadow, a cold flicker up close. */
public class GhostRenderer extends GeoEntityRenderer<GhostEntity> {
    private final GhostKind kind;

    public GhostRenderer(EntityRendererProvider.Context ctx, GhostKind kind) {
        super(ctx, new GhostModel(kind));
        this.kind = kind;
        this.shadowRadius = 0f;
        withScale(kind.scaleXZ, kind.scaleY);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
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

    @Override
    protected float getDeathMaxRotation(GhostEntity g) {
        return 0f;
    }

    @Override
    public boolean shouldShowName(GhostEntity g) {
        return false;
    }
}
