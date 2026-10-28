package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.grave.GraveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;

/** A headstone on a mound of earth, with the owner's name and the time left above it. */
public class GraveRenderer extends EntityRenderer<GraveEntity> {
    public static final ResourceLocation MODEL = HollowHours.id("block/grave");

    public GraveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
    }

    @Override
    public void render(GraveEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
        ps.translate(-0.5, 0, -0.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null, mc.getModelManager().getModel(MODEL),
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        ps.popPose();
        if (entityRenderDispatcher.distanceToSqr(e) < 24 * 24) {
            int s = e.secondsLeft();
            Component label = Component.translatable("hollowhours.grave.label", e.ownerName(), String.format("%d:%02d", s / 60, s % 60));
            renderNameTag(e, label, ps, buffers, light);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(GraveEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
