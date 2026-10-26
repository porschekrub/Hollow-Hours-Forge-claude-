package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import javax.annotation.Nullable;

/** The three apparitions. */
public class StalkerRenderer extends GeoEntityRenderer<StalkerEntity> {
    public StalkerRenderer(EntityRendererProvider.Context ctx, StalkerKind kind) {
        super(ctx, new Model(kind));
        this.shadowRadius = 0f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderType getRenderType(StalkerEntity e, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public boolean shouldShowName(StalkerEntity e) {
        return false;
    }

    static final class Model extends GeoModel<StalkerEntity> {
        final ResourceLocation model, texture, anims;

        Model(StalkerKind k) {
            model = HollowHours.id("geo/entity/stalker_" + k.n + ".geo.json");
            texture = HollowHours.id("textures/entity/stalker_" + k.n + ".png");
            anims = HollowHours.id("animations/entity/stalker_" + k.n + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(StalkerEntity e) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(StalkerEntity e) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(StalkerEntity e) {
            return anims;
        }
    }
}
