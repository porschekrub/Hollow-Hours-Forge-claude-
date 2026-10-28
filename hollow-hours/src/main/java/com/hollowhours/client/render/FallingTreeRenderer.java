package com.hollowhours.client.render;

import com.hollowhours.physics.FallingTreeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the felled tree's blocks, tipping over around the stump. */
public class FallingTreeRenderer extends EntityRenderer<FallingTreeEntity> {
    public FallingTreeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(FallingTreeEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(FallingTreeEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        var br = Minecraft.getInstance().getBlockRenderer();
        Vector3f axis = e.axis();
        ps.pushPose();
        ps.mulPose(new Quaternionf().rotationAxis(e.angle(partial), axis.x, 0, axis.z));
        BlockPos base = e.blockPosition();
        for (int i = 0; i < e.states.size(); i++) {
            BlockState s = e.states.get(i);
            if (s.getRenderShape() != RenderShape.MODEL) continue;
            BlockPos r = e.rel.get(i);
            ps.pushPose();
            ps.translate(r.getX() - 0.5, r.getY(), r.getZ() - 0.5);
            int l = net.minecraft.client.renderer.LevelRenderer.getLightColor(e.level(), base.offset(r));
            br.renderSingleBlock(s, ps, buffers, Math.max(l, light), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingTreeEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
