package com.hollowhours.client.render;

import com.hollowhours.config.HHClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dropped items with weight: no more floating and spinning. They tumble while flying and come to rest lying on the
 * ground; blocks sit there as little 3D blocks, flat items lie flat, each at its own angle.
 */
public class ItemPhysicsRenderer extends ItemEntityRenderer {
    private final ItemRenderer items;
    private final RandomSource rnd = RandomSource.create();

    public ItemPhysicsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.12f;
    }

    @Override
    public void render(ItemEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (!HHClientConfig.ITEM_PHYSICS.get()) {
            super.render(e, yaw, partial, ps, buffers, light);
            return;
        }
        ItemStack stack = e.getItem();
        if (stack.isEmpty()) return;
        rnd.setSeed(net.minecraft.world.item.Item.getId(stack.getItem()) * 31L + e.getId());
        BakedModel model = items.getModel(stack, e.level(), null, e.getId());
        boolean block = model.isGui3d();
        int copies = stack.getCount() > 48 ? 5 : stack.getCount() > 32 ? 4 : stack.getCount() > 16 ? 3 : stack.getCount() > 1 ? 2 : 1;
        float restYaw = (e.getId() * 47 % 360);
        boolean resting = e.onGround() || e.isInWater() && Math.abs(e.getDeltaMovement().y) < 0.02;
        float tumble = resting ? 0 : (e.getAge() + partial) * 18f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(restYaw));
        if (block) {
            // a little block sitting on the ground
            float s = 0.5f;
            ps.translate(0, 0.125f, 0);
            if (!resting) {
                ps.mulPose(Axis.XP.rotationDegrees(tumble));
                ps.mulPose(Axis.ZP.rotationDegrees(tumble * 0.7f));
            }
            for (int i = 0; i < copies; i++) {
                ps.pushPose();
                if (i > 0) ps.translate((rnd.nextFloat() - 0.5f) * 0.2f, (i % 2) * 0.12f, (rnd.nextFloat() - 0.5f) * 0.2f);
                ps.scale(s, s, s);
                items.render(stack, ItemDisplayContext.FIXED, false, ps, buffers, light, OverlayTexture.NO_OVERLAY, model);
                ps.popPose();
            }
        } else {
            // flat items lie flat (stacked a hair apart)
            ps.translate(0, 0.02f, 0);
            ps.mulPose(Axis.XP.rotationDegrees(90 + (resting ? 0 : tumble)));
            float s = 0.5f;
            for (int i = 0; i < copies; i++) {
                ps.pushPose();
                ps.translate((rnd.nextFloat() - 0.5f) * 0.15f, (rnd.nextFloat() - 0.5f) * 0.15f, -i * 0.04f);
                ps.mulPose(Axis.ZP.rotationDegrees((rnd.nextFloat() - 0.5f) * 40));
                ps.scale(s, s, s);
                items.render(stack, ItemDisplayContext.FIXED, false, ps, buffers, light, OverlayTexture.NO_OVERLAY, model);
                ps.popPose();
            }
        }
        ps.popPose();
    }
}
