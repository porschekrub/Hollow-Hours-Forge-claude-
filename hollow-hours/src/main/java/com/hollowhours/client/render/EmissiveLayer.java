package com.hollowhours.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Function;

/**
 * Glowing eyes / mouths: the model is drawn a second time with an emissive texture (…_glow.png, black = no glow)
 * through vanilla's additive "eyes" render type, like spider and enderman eyes, so it glows in the dark.
 */
public class EmissiveLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
    private final Function<T, ResourceLocation> texture;

    public EmissiveLayer(GeoRenderer<T> renderer, Function<T, ResourceLocation> texture) {
        super(renderer);
        this.texture = texture;
    }

    /** "textures/entity/ghost_1.png" -> "textures/entity/ghost_1_glow.png" */
    public static ResourceLocation glowOf(ResourceLocation base) {
        String p = base.getPath();
        return new ResourceLocation(base.getNamespace(), p.substring(0, p.length() - 4) + "_glow.png");
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
                       VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ResourceLocation tex = texture.apply(animatable);
        if (tex == null) return;
        RenderType eyes = RenderType.eyes(tex);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, eyes, bufferSource.getBuffer(eyes), partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
    }
}
