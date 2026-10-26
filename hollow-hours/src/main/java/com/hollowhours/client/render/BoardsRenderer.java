package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.BoardsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Nailed boards. One model holds every variant (door boards 1-4 along either axis, a window cross, the hammer),
 * the right bones are shown from the synced state; the texture shows how damaged the outer board is.
 */
public class BoardsRenderer extends GeoEntityRenderer<BoardsEntity> {
    static final ResourceLocation[] TEX = {
            HollowHours.id("textures/entity/boards/boards_0.png"), HollowHours.id("textures/entity/boards/boards_1.png"),
            HollowHours.id("textures/entity/boards/boards_2.png"), HollowHours.id("textures/entity/boards/boards_3.png")};
    static final String[] MAT = {"w", "s", "i"};

    public BoardsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GeoModel<>() {
            final ResourceLocation model = HollowHours.id("geo/entity/boards.geo.json");
            final ResourceLocation anims = HollowHours.id("animations/entity/boards.animation.json");

            @Override
            public ResourceLocation getModelResource(BoardsEntity e) {
                return model;
            }

            @Override
            public ResourceLocation getTextureResource(BoardsEntity e) {
                return TEX[Math.max(0, Math.min(3, e.dmgStage()))];
            }

            @Override
            public ResourceLocation getAnimationResource(BoardsEntity e) {
                return anims;
            }
        });
        this.shadowRadius = 0f;
    }

    @Override
    public RenderType getRenderType(BoardsEntity e, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void preRender(PoseStack poseStack, BoardsEntity e, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        super.preRender(poseStack, e, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        int ax = e.axis(), kind = e.kind(), n = e.count(), side = e.side(), fx = e.fx();
        for (GeoBone b : model.topLevelBones()) b.setHidden(false);
        for (String a : new String[]{"z", "x"}) {
            boolean axisOn = (a.equals("z") ? 0 : 1) == ax;
            for (int i = 1; i <= 4; i++) {
                boolean on = axisOn && kind == BoardsEntity.KIND_DOOR && n >= i;
                set(model, a + "b" + i, on);
                for (int m = 0; m < 3; m++) set(model, a + "b" + i + MAT[m], on && e.mat(i - 1) == m);
                for (int k = 1; k <= 3; k++) set(model, a + "b" + i + "n" + k, on);
            }
            boolean g = axisOn && kind == BoardsEntity.KIND_GLASS && n >= 1;
            set(model, a + "g", g);
            for (int m = 0; m < 3; m++) set(model, a + "g" + MAT[m], g && e.mat(0) == m);
            for (int k = 1; k <= 3; k++) set(model, a + "gn" + k, g);
            // the hammer is on the side the player stands on
            boolean hammer = axisOn && fx == 1;
            set(model, a + "hp", hammer && (a.equals("z") ? side == 0 : side == 1));
            set(model, a + "hn", hammer && (a.equals("z") ? side == 1 : side == 0));
        }
    }

    static void set(BakedGeoModel model, String bone, boolean visible) {
        model.getBone(bone).ifPresent(b -> b.setHidden(!visible));
    }

    @Override
    public boolean shouldShowName(BoardsEntity e) {
        return false;
    }
}
