package com.hollowhours.combat;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.Perception;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.hollowhours.registry.HHSounds;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Guns: right-click to shoot (hold for automatic ones), R to reload (or shoot when empty), sneak to aim (zoom,
 * steadier). Shots are instant (a hit scan) and loud: ghosts hear them from far away.
 */
public class GunItem extends Item {
    public record Spec(String id, int mag, float damage, float poise, int delay, boolean auto, int pellets, float spread, float range,
                       int reload, float recoil, float zoom, Supplier<Item> ammo) {}

    public final Spec spec;

    public GunItem(Spec spec, Properties p) {
        super(p.stacksTo(1));
        this.spec = spec;
    }

    public static int ammo(ItemStack s) {
        return s.getOrCreateTag().getInt("ammo");
    }

    static void setAmmo(ItemStack s, int n) {
        s.getOrCreateTag().putInt("ammo", n);
    }

    public static boolean reloading(ItemStack s) {
        return s.hasTag() && s.getTag().getLong("reloadAt") > 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        if (spec.auto) p.startUsingItem(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) trigger(sp, s);
        return spec.auto ? InteractionResultHolder.consume(s) : InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int left) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || !spec.auto) return;
        int used = getUseDuration(s) - left;
        if (used > 0 && used % spec.delay == 0) trigger(p, s);
    }

    void trigger(ServerPlayer p, ItemStack s) {
        if (reloading(s)) return;
        long now = p.level().getGameTime();
        CompoundTagAccess t = new CompoundTagAccess(s);
        if (now - t.lastShot() < spec.delay) return;
        if (ammo(s) <= 0) {
            if (!startReload(p, s)) Fx.sound(p.level(), "gun.empty", p.position(), 0.8f, 1.2f, 12);
            return;
        }
        t.setLastShot(now);
        fire(p, s);
    }

    // ------------------------------------------------------------------ shooting
    public void fire(ServerPlayer p, ItemStack s) {
        ServerLevel level = p.serverLevel();
        setAmmo(s, ammo(s) - (p.getAbilities().instabuild ? 0 : 1));
        int lvl = Fitness.gunLevel(p);
        boolean aim = p.isShiftKeyDown();
        float spread = spec.spread * (aim ? 0.35f : 1f) * (1 - 0.06f * lvl) * (p.isSprinting() ? 1.8f : 1f);
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = eye.add(look.scale(1.5)).add(right.scale(0.3)).add(0, -0.18, 0);
        boolean hitGhost = false;
        for (int i = 0; i < spec.pellets; i++) {
            Vec3 dir = jitter(p, look, spread);
            Vec3 end = eye.add(dir.scale(spec.range));
            BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1.0),
                    e -> e != p && e.isAlive() && !e.isSpectator() && (e.isPickable() || e instanceof GhostEntity) && !(e == p.getVehicle()), 0.3f);
            if (eh != null) {
                stop = eh.getLocation();
                Entity e = eh.getEntity();
                float boost = 1 + 0.05f * lvl;
                if (e instanceof GhostEntity g) {
                    hitGhost |= GhostCombat.poiseHit(g, p, spec.poise * boost, true);
                } else {
                    e.invulnerableTime = 0;
                    e.hurt(p.damageSources().playerAttack(p), spec.damage * boost);
                    Vec3 kb = dir.scale(0.15 * (spec.pellets > 1 ? 0.5 : 1));
                    e.push(kb.x, 0.05, kb.z);
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        stop.x, stop.y, stop.z, e instanceof GhostEntity ? 4 : 8, 0.1, 0.1, 0.1, 0.1);
            } else if (bh.getType() == HitResult.Type.BLOCK) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(bh.getBlockPos())), stop.x, stop.y, stop.z, 6, 0.05, 0.05, 0.05, 0.1);
                level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 2, 0.02, 0.02, 0.02, 0.01);
            }
            // tracer
            double len = stop.distanceTo(muzzle);
            Vec3 d = stop.subtract(muzzle).normalize();
            for (double k = 1.2; k < len; k += 2.2) {
                Vec3 q = muzzle.add(d.scale(k));
                level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        // a quick small flash at the muzzle (big flames right in front of the eye looked like a bonfire)
        // (not for the shooter while aiming: through a zoomed scope the flash filled the view)
        for (ServerPlayer o : level.players()) {
            if (o == p && aim) continue;
            if (o.distanceToSqr(muzzle) > 64 * 64) continue;
            level.sendParticles(o, ParticleTypes.SMALL_FLAME, false, muzzle.x, muzzle.y, muzzle.z, 2, 0.01, 0.01, 0.01, 0.0);
            level.sendParticles(o, ParticleTypes.ELECTRIC_SPARK, false, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.05);
            level.sendParticles(o, ParticleTypes.SMOKE, false, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.005);
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), HHSounds.get("gun." + spec.id), SoundSource.PLAYERS, 2.5f, 0.92f + p.getRandom().nextFloat() * 0.16f);
        HHNet.toPlayer(p, new S2CCombat(S2CCombat.RECOIL, spec.recoil * (aim ? 0.7f : 1f), (p.getRandom().nextFloat() - 0.5f) * spec.recoil * 0.4f, 0));
        Perception.impulse(p, p.position(), 48);
        if (hitGhost) Fitness.addSkill(p, true, 1);
    }

    static Vec3 jitter(Player p, Vec3 look, float deg) {
        if (deg <= 0) return look;
        float yaw = p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * 2 * deg;
        float pitch = p.getXRot() + (p.getRandom().nextFloat() - 0.5f) * 2 * deg;
        float f = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI), g = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float h = -Mth.cos(-pitch * Mth.DEG_TO_RAD), i = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        return new Vec3(g * h, i, f * h);
    }

    // ------------------------------------------------------------------ reloading
    public static int ammoIn(Player p, Item ammo) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(ammo)) n += s.getCount();
        return n;
    }

    /** @return true when a reload started */
    public boolean startReload(ServerPlayer p, ItemStack s) {
        if (reloading(s) || ammo(s) >= spec.mag) return false;
        if (!p.getAbilities().instabuild && ammoIn(p, spec.ammo.get()) <= 0) {
            Fx.actionbar(p, Component.translatable("hollowhours.gun.noammo", Component.translatable(spec.ammo.get().getDescriptionId())).withStyle(ChatFormatting.RED));
            return false;
        }
        int ticks = Math.max(10, Math.round(spec.reload * (1 - 0.05f * Fitness.gunLevel(p))));
        s.getOrCreateTag().putLong("reloadAt", p.level().getGameTime() + ticks);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HHSounds.get("gun.reload"), SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || !s.hasTag()) return;
        long at = s.getTag().getLong("reloadAt");
        if (at <= 0) return;
        if (!selected) {
            s.getTag().remove("reloadAt"); // switched away: the reload is lost
            return;
        }
        if (level.getGameTime() < at) return;
        s.getTag().remove("reloadAt");
        int need = spec.mag - ammo(s);
        int got = 0;
        if (p.getAbilities().instabuild) got = need;
        else {
            for (ItemStack inv : p.getInventory().items) {
                if (got >= need) break;
                if (!inv.is(spec.ammo.get())) continue;
                int take = Math.min(need - got, inv.getCount());
                inv.shrink(take);
                got += take;
            }
        }
        setAmmo(s, ammo(s) + got);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), HHSounds.get("gun.cock"), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldS, ItemStack newS, boolean slotChanged) {
        return slotChanged || oldS.getItem() != newS.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13f * ammo(s) / spec.mag);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return reloading(s) ? 0x9A9A9A : 0xE8C547;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("hollowhours.gun.ammo", ammo(s), spec.mag).withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("hollowhours.gun.uses", Component.translatable(spec.ammo.get().getDescriptionId())).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("hollowhours.weapon.gunstats", Math.round(spec.damage * spec.pellets), Math.round(spec.poise * spec.pellets)).withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable(spec.auto ? "hollowhours.gun.hint.auto" : "hollowhours.gun.hint").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack s) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    /** last-shot time kept on the stack (so it works across use ticks and clicks) */
    record CompoundTagAccess(ItemStack s) {
        long lastShot() {
            return s.getOrCreateTag().getLong("shotAt");
        }

        void setLastShot(long t) {
            s.getOrCreateTag().putLong("shotAt", t);
        }
    }
}
