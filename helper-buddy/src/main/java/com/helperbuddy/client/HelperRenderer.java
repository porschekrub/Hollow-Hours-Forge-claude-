package com.helperbuddy.client;

import com.helperbuddy.entity.HelperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/** The helper: GeckoLib model, glowing goggles and antenna lamp, the tool in its hand and a two-line name tag. */
public class HelperRenderer extends GeoEntityRenderer<HelperEntity> {
    public HelperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HelperModel());
        this.shadowRadius = 0.4f;
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack poseStack, HelperEntity h, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers, VertexConsumer buffer,
                               float partialTick, int light, int overlay) {
                if (h.isKO()) return;
                ResourceLocation base = getTextureLocation(h);
                String p = base.getPath();
                ResourceLocation glow = new ResourceLocation(base.getNamespace(), p.substring(0, p.length() - 4) + "_glow.png");
                RenderType eyes = RenderType.eyes(glow);
                getRenderer().reRender(model, poseStack, buffers, h, eyes, buffers.getBuffer(eyes), partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
        });
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getStackForBone(GeoBone bone, HelperEntity h) {
                return bone.getName().equals("rightItem") && !h.isKO() ? h.getMainHandItem() : null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, HelperEntity h) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, HelperEntity h, MultiBufferSource buffers, float partialTick,
                                              int light, int overlay) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
                poseStack.translate(0, 0.1, -0.06);
                super.renderStackForBone(poseStack, bone, stack, h, buffers, partialTick, light, overlay);
            }
        });
    }

    @Override
    public RenderType getRenderType(HelperEntity h, ResourceLocation texture, @Nullable MultiBufferSource buffers, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    static boolean thai() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        return l != null && l.startsWith("th");
    }

    @Override
    protected void renderNameTag(HelperEntity h, Component name, PoseStack ps, MultiBufferSource buffers, int light) {
        boolean th = thai();
        int hp = (int) Math.ceil(h.shownHp());
        MutableComponent line = Component.literal(h.helperName()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("  ❤ " + hp + "/" + h.maxHp()).withStyle(hp < h.maxHp() * 0.35 ? ChatFormatting.RED : ChatFormatting.GREEN))
                .append(Component.literal("  Lv" + h.helperLevel()).withStyle(ChatFormatting.AQUA));
        super.renderNameTag(h, line, ps, buffers, light);
        String st = h.isKO() ? (th ? "สลบ! ป้อนอาหารเพื่อปลุก" : "Knocked out! Feed me") : h.mode().label(th) + (h.status().isEmpty() ? "" : " • " + h.status());
        ps.pushPose();
        ps.translate(0, 0.27, 0);
        super.renderNameTag(h, Component.literal(st).withStyle(h.isKO() ? ChatFormatting.RED : ChatFormatting.GRAY), ps, buffers, light);
        ps.popPose();
    }
}
