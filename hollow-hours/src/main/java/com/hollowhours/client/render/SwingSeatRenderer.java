package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.fun.SwingSeatEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/** The swinging seat and its ropes, rotating around the top beam. */
public class SwingSeatRenderer extends EntityRenderer<SwingSeatEntity> {
    //#if MC>=12100 && !FABRIC
    //$$ public static final net.minecraft.client.resources.model.ModelResourceLocation MODEL = com.hollowhours.compat.ClientCompat.model("block/swing_seat");
    //#else
    public static final ResourceLocation MODEL = HollowHours.id("block/swing_seat");
    //#endif

    public SwingSeatRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(SwingSeatEntity e, Frustum f, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 64 * 64;
    }

    @Override
    public void render(SwingSeatEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        var model = mc.getModelManager().getModel(MODEL);
        Direction f = e.facing();
        ps.pushPose();
        // swing around the beam: the axis is horizontal, across the swing
        ps.mulPose(new Quaternionf().rotationAxis(e.angle(partial), -f.getStepZ(), 0, f.getStepX()));
        if (f.getAxis() == Direction.Axis.X) ps.mulPose(Axis.YP.rotationDegrees(90));
        ps.translate(-0.5, -SwingSeatEntity.ROPE, -0.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null, model,
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SwingSeatEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
