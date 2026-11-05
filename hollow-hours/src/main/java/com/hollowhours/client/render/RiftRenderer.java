package com.hollowhours.client.render;

import com.hollowhours.HollowHours;
import com.hollowhours.endgame.RiftEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A tear of white light, always facing you, slowly turning and breathing. */
public class RiftRenderer extends EntityRenderer<RiftEntity> {
    static final ResourceLocation SWIRL = HollowHours.id("textures/entity/rift.png");

    public RiftRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(RiftEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + partial;
        float grow = Math.min(1f, t / 40f);
        ps.pushPose();
        ps.translate(0, 1.2, 0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(SWIRL));
        for (int layer = 0; layer < 3; layer++) {
            ps.pushPose();
            float s = grow * (1.6f + 0.35f * layer + 0.12f * Mth.sin(t * 0.08f + layer)) * (layer == 2 ? 0.55f : 1f);
            ps.mulPose(Axis.ZP.rotationDegrees((layer % 2 == 0 ? 1 : -1) * t * (1.5f + layer)));
            ps.scale(s, s * 1.35f, s);
            quad(ps, vc, layer == 2 ? 255 : 200 - layer * 50);
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }

    static void quad(PoseStack ps, VertexConsumer vc, int a) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, -1, -1, 0, 1, a);
        v(vc, m, n, 1, -1, 1, 1, a);
        v(vc, m, n, 1, 1, 1, 0, a);
        v(vc, m, n, -1, 1, 0, 0, a);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv, int a) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0, 0, 1).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity e) {
        return SWIRL;
    }
}
