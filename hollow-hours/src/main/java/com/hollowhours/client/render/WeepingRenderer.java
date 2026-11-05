package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.endgame.WeepingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/** The thing under the mountain: glowing gold veins, no death spin, no red death tint (it sinks away instead). */
public class WeepingRenderer extends GeoEntityRenderer<WeepingEntity> {
    static final ResourceLocation MODEL = HollowHours.id("geo/entity/weeping.geo.json");
    static final ResourceLocation TEXTURE = HollowHours.id("textures/entity/weeping.png");
    static final ResourceLocation ANIMS = HollowHours.id("animations/entity/weeping.animation.json");

    public WeepingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(WeepingEntity e) {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(WeepingEntity e) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(WeepingEntity e) {
                return ANIMS;
            }
        });
        this.shadowRadius = 0f;
        addRenderLayer(new EmissiveLayer<>(this, e -> EmissiveLayer.glowOf(TEXTURE)));
    }

    @Override
    public RenderType getRenderType(WeepingEntity e, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    protected float getDeathMaxRotation(WeepingEntity e) {
        return 0f;
    }

    @Override
    public int getPackedOverlay(WeepingEntity e, float u, float partialTick) {
        if (e.deathTime > 0) return OverlayTexture.NO_OVERLAY;
        return super.getPackedOverlay(e, u, partialTick);
    }
}
