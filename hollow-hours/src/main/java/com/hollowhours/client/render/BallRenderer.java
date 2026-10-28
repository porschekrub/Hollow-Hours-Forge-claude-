package com.hollowhours.client.render;

import com.hollowhours.fun.BallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** A ball: its 3D item model, spinning the way it rolls. */
public class BallRenderer extends EntityRenderer<BallEntity> {
    public BallRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.22f;
        this.shadowStrength = 0.7f;
    }

    @Override
    public void render(BallEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.translate(0, 0.25, 0);
        // roll around the axis across the direction of travel
        ps.mulPose(Axis.YP.rotation(-e.rollYaw));
        ps.mulPose(Axis.ZN.rotation(Mth.lerp(partial, e.rollO, e.roll)));
        Minecraft.getInstance().getItemRenderer().renderStatic(e.item(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BallEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
