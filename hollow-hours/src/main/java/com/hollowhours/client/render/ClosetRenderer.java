package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.ClosetEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The wardrobe and its doors. */
public class ClosetRenderer extends GeoEntityRenderer<ClosetEntity> {
    public ClosetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GeoModel<>() {
            final ResourceLocation model = HollowHours.id("geo/entity/closet.geo.json");
            final ResourceLocation texture = HollowHours.id("textures/entity/closet.png");
            final ResourceLocation anims = HollowHours.id("animations/entity/closet.animation.json");

            @Override
            public ResourceLocation getModelResource(ClosetEntity e) {
                return model;
            }

            @Override
            public ResourceLocation getTextureResource(ClosetEntity e) {
                return texture;
            }

            @Override
            public ResourceLocation getAnimationResource(ClosetEntity e) {
                return anims;
            }
        });
        this.shadowRadius = 0.5f;
    }
}
