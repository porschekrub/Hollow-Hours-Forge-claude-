package com.hollowhours.client;

import com.hollowhours.net.S2CBlood;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * A drop of blood: flies, falls, and when it hits the ground it flattens into a splat that stays a while and fades.
 */
public class BloodParticle extends TextureSheetParticle {
    static SpriteSet sprites;
    /** drops made so far (the CI tour checks that blood arrives) */
    static int made;
    private boolean landed;
    private float splatAngle, splatSize;
    private final int life2;

    BloodParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int rgb, SpriteSet set) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = 1.1f;
        this.friction = 0.98f;
        this.hasPhysics = true;
        this.quadSize = 0.05f + random.nextFloat() * 0.07f;
        this.lifetime = 50 + random.nextInt(30);
        this.life2 = 160 + random.nextInt(120);
        float shade = 0.8f + random.nextFloat() * 0.3f;
        this.rCol = Math.min(1, ((rgb >> 16) & 255) / 255f * shade);
        this.gCol = Math.min(1, ((rgb >> 8) & 255) / 255f * shade);
        this.bCol = Math.min(1, (rgb & 255) / 255f * shade);
        this.alpha = 0.95f;
        this.splatAngle = random.nextFloat() * Mth.TWO_PI;
        setSize(0.05f, 0.05f);
        if (set != null) pickSprite(set);
    }

    @Override
    public void tick() {
        if (landed) {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) remove();
            float left = lifetime - age;
            if (left < 40) alpha = 0.95f * left / 40f;
            return;
        }
        super.tick();
        if (onGround && !removed) {
            landed = true;
            age = 0;
            lifetime = life2;
            xd = yd = zd = 0;
            splatSize = quadSize * (1.6f + random.nextFloat() * 1.4f);
        }
    }

    @Override
    public void render(VertexConsumer vc, Camera cam, float partial) {
        if (!landed) {
            super.render(vc, cam, partial);
            return;
        }
        // flat on the ground
        Vec3 c = cam.getPosition();
        float px = (float) (x - c.x), py = (float) (y - c.y) + 0.012f, pz = (float) (z - c.z);
        Quaternionf q = new Quaternionf().rotationX(-Mth.HALF_PI).rotateZ(splatAngle);
        Vector3f[] v = {new Vector3f(-1, -1, 0), new Vector3f(-1, 1, 0), new Vector3f(1, 1, 0), new Vector3f(1, -1, 0)};
        for (Vector3f p : v) {
            p.rotate(q);
            p.mul(splatSize);
            p.add(px, py, pz);
        }
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partial);
        float r = rCol * 0.85f, g = gCol * 0.85f, b = bCol * 0.85f;
        vc.vertex(v[0].x(), v[0].y(), v[0].z()).uv(u1, v1).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[1].x(), v[1].y(), v[1].z()).uv(u1, v0).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[2].x(), v[2].y(), v[2].z()).uv(u0, v0).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[3].x(), v[3].y(), v[3].z()).uv(u0, v1).color(r, g, b, alpha).uv2(light).endVertex();
        // and from below (no back-face culling for particles is not guaranteed)
        vc.vertex(v[3].x(), v[3].y(), v[3].z()).uv(u0, v1).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[2].x(), v[2].y(), v[2].z()).uv(u0, v0).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[1].x(), v[1].y(), v[1].z()).uv(u1, v0).color(r, g, b, alpha).uv2(light).endVertex();
        vc.vertex(v[0].x(), v[0].y(), v[0].z()).uv(u1, v1).color(r, g, b, alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** From the server: a burst away from the hit, or drips. */
    static void spawn(S2CBlood m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || sprites == null || !com.hollowhours.config.HHClientConfig.BLOOD.get()) return;
        RandomSource r = mc.level.random;
        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;
        int n = mc.options.particles().get() == net.minecraft.client.ParticleStatus.DECREASED ? Math.max(1, m.count() / 2) : m.count();
        for (int i = 0; i < n; i++) {
            double x = m.x() + (r.nextDouble() - 0.5) * m.width() * 0.6, y = m.y() + (r.nextDouble() - 0.5) * 0.4, z = m.z() + (r.nextDouble() - 0.5) * m.width() * 0.6;
            double vx, vy, vz;
            if (m.kind() == S2CBlood.DRIP) {
                vx = (r.nextDouble() - 0.5) * 0.02;
                vy = -0.02;
                vz = (r.nextDouble() - 0.5) * 0.02;
            } else {
                double sp = 0.12 + r.nextDouble() * 0.22;
                vx = m.dx() * sp + (r.nextDouble() - 0.5) * 0.22;
                vy = 0.08 + r.nextDouble() * 0.22;
                vz = m.dz() * sp + (r.nextDouble() - 0.5) * 0.22;
            }
            mc.particleEngine.add(new BloodParticle(mc.level, x, y, z, vx, vy, vz, m.rgb(), sprites));
            made++;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet set) {
            sprites = set;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BloodParticle(level, x, y, z, vx, vy, vz, 0x8A0606, sprites);
        }
    }
}
