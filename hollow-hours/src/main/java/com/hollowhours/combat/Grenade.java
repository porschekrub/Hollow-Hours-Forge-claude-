package com.hollowhours.combat;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.Perception;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.registry.HHItems;
import com.hollowhours.registry.HHSounds;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** A hand grenade: bounces, goes off after 2.5 s. Hurts mobs, knocks ghosts toward a knock-out, never breaks blocks. */
public final class Grenade {
    private Grenade() {}

    public static class GrenadeItem extends Item {
        public GrenadeItem(Properties p) {
            super(p.stacksTo(16));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
            ItemStack s = p.getItemInHand(hand);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.6f);
            if (!level.isClientSide) {
                Thrown t = new Thrown(level, p);
                t.setItem(s);
                t.shootFromRotation(p, p.getXRot(), p.getYRot(), -5f, 1.1f, 0.6f);
                level.addFreshEntity(t);
            }
            p.getCooldowns().addCooldown(this, 20);
            if (!p.getAbilities().instabuild) s.shrink(1);
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("hollowhours.grenade.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    public static class Thrown extends ThrowableItemProjectile {
        int fuse = 50;

        public Thrown(EntityType<? extends Thrown> t, Level level) {
            super(t, level);
        }

        public Thrown(Level level, LivingEntity owner) {
            super(HHEntities.GRENADE.get(), owner, level);
        }

        @Override
        protected Item getDefaultItem() {
            return HHItems.GRENADE.get();
        }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide) {
                if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
                return;
            }
            if (--fuse <= 0) boom();
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            setDeltaMovement(getDeltaMovement().scale(-0.2));
        }

        @Override
        protected void onHitBlock(BlockHitResult hit) {
            Vec3 v = getDeltaMovement();
            switch (hit.getDirection().getAxis()) {
                case X -> v = new Vec3(-v.x * 0.4, v.y * 0.6, v.z * 0.6);
                case Y -> v = new Vec3(v.x * 0.5, -v.y * 0.35, v.z * 0.5);
                case Z -> v = new Vec3(v.x * 0.6, v.y * 0.6, -v.z * 0.4);
            }
            if (v.lengthSqr() < 0.004) v = Vec3.ZERO;
            setDeltaMovement(v);
            Vec3 at = hit.getLocation();
            setPos(at.x, at.y + (hit.getDirection() == net.minecraft.core.Direction.UP ? 0.05 : 0), at.z);
            if (v.lengthSqr() > 0.01) playSound(SoundEvents.METAL_HIT, 0.4f, 1.6f);
        }

        void boom() {
            ServerLevel level = (ServerLevel) level();
            Vec3 at = position();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 30, 1.2, 0.6, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 20, 0.8, 0.4, 0.8, 0.08);
            level.playSound(null, at.x, at.y, at.z, HHSounds.get("gun.grenade"), SoundSource.PLAYERS, 4f, 0.9f + random.nextFloat() * 0.2f);
            ServerPlayer owner = getOwner() instanceof ServerPlayer sp ? sp : null;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(6))) {
                double d = e.position().distanceTo(at);
                if (d > 6) continue;
                float k = (float) (1 - d / 6);
                if (e instanceof GhostEntity g) {
                    if (owner != null) GhostCombat.poiseHit(g, owner, 55 * k, true);
                } else {
                    e.hurt(owner != null ? level.damageSources().explosion(this, owner) : level.damageSources().explosion(this, null), 14 * k);
                    Vec3 push = e.position().subtract(at).normalize().scale(0.9 * k);
                    e.push(push.x, 0.3 * k, push.z);
                }
            }
            Fx.shakeNear(level, at, 14, 0.6f, 12);
            if (owner != null) Perception.impulse(owner, at, 56);
            discard();
        }
    }
}
