package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.rescue.HelicopterEntity;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The rescue helicopter: three block models (body, main rotor, tail rotor, see tools/helicopter_art.py) drawn 4x as big,
 * the rotors spinning, the nose dipping as it speeds up.
 */
public class HelicopterRenderer extends EntityRenderer<HelicopterEntity> {
    //#if MC>=12100 && !FABRIC
    //$$ public static final net.minecraft.client.resources.model.ModelResourceLocation BODY = com.hollowhours.compat.ClientCompat.model("block/helicopter"),
    //$$         ROTOR = com.hollowhours.compat.ClientCompat.model("block/helicopter_rotor"), TAIL = com.hollowhours.compat.ClientCompat.model("block/helicopter_tail_rotor");
    //#else
    public static final ResourceLocation BODY = HollowHours.id("block/helicopter"), ROTOR = HollowHours.id("block/helicopter_rotor"),
            TAIL = HollowHours.id("block/helicopter_tail_rotor");
    //#endif

    public HelicopterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 2.5f;
    }

    @Override
    public void render(HelicopterEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
        // nose down when speeding up (tilting around the middle of the cabin)
        float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        ps.translate(0, 1.5, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.translate(0, -1.5, 0);
        ps.scale(4, 4, 4);
        // the model's (8, 0, 4) - the middle of the door - sits on the entity
        ps.translate(-0.5, 0, -0.25);
        draw(BODY, ps, buffers, light);
        ps.pushPose();
        ps.translate(0.5, 17 / 16f, 0.25);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, e.rotorO, e.rotor)));
        ps.translate(-0.5, 0, -0.5);
        draw(ROTOR, ps, buffers, light);
        ps.popPose();
        ps.pushPose();
        ps.translate(9.2 / 16f, 13.5 / 16f, 28.5 / 16f);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, e.tailO, e.tail)));
        ps.translate(-0.5, -0.5, -0.5);
        draw(TAIL, ps, buffers, light);
        ps.popPose();
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    //#if MC>=12100 && !FABRIC
    //$$ static void draw(net.minecraft.client.resources.model.ModelResourceLocation model, PoseStack ps, MultiBufferSource buffers, int light) {
    //#else
    static void draw(ResourceLocation model, PoseStack ps, MultiBufferSource buffers, int light) {
    //#endif
        Minecraft mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null, mc.getModelManager().getModel(model),
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
    }

    @Override
    public ResourceLocation getTextureLocation(HelicopterEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
