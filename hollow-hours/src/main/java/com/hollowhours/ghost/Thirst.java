package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.util.R;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Thirst 0-20 (like hunger). Drops over time, faster when sprinting, in hot places and in the Nether.
 * Drink: water bottles (+7), milk (+5), other potions, soups, honey, melon, berries... or sneak + right-click water
 * with an empty hand (+2 a sip, river water can make you sick). Below 6 you're slow, below 3 weak, at 0 it hurts.
 */
public final class Thirst {
    private Thirst() {}

    static final String KEY = "hollowhours_thirst";
    public static final float MAX = 20;

    public static float get(Player p) {
        CompoundTag d = Story.persisted(p);
        return d.contains(KEY) ? d.getFloat(KEY) : MAX;
    }

    public static void set(Player p, float v) {
        Story.persisted(p).putFloat(KEY, Math.max(0, Math.min(MAX, v)));
    }

    public static void add(Player p, float v) {
        set(p, get(p) + v);
    }

    /** Every 20 ticks. */
    public static void tick(ServerPlayer p, long now) {
        if (!HHConfig.THIRST.get() || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        // a full bar: about an hour walking around, half an hour sprinting
        float loss = 0.0055f * HHConfig.THIRST_RATE.get().floatValue();
        if (p.isSprinting()) loss *= 2f;
        Level level = p.level();
        if (level.dimension() == Level.NETHER) loss *= 2f;
        else if (level.getBiome(p.blockPosition()).value().getBaseTemperature() > 1.0f) loss *= 1.4f;
        if (p.isInWater()) loss *= 0.5f;
        float t = get(p) - loss;
        set(p, t);
        if (t <= 0) {
            if (now % 80 == 0) p.hurt(GhostDamage.thirst(level), 1.0f);
        } else if (t < 3) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
            if (R.chance(0.02)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
        } else if (t < 6) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
        }
    }

    /** How much a finished item quenches (0 = not a drink). */
    public static float quench(ItemStack s) {
        if (s.is(Items.POTION)) return PotionUtils.getPotion(s) == Potions.WATER ? 7 : 4;
        if (s.is(Items.MILK_BUCKET)) return 5;
        if (s.is(Items.HONEY_BOTTLE)) return 3;
        if (s.is(Items.MUSHROOM_STEW) || s.is(Items.BEETROOT_SOUP) || s.is(Items.RABBIT_STEW) || s.is(Items.SUSPICIOUS_STEW)) return 3;
        if (s.is(Items.MELON_SLICE)) return 2;
        if (s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE)) return 1;
        return 0;
    }

    public static void onFinishUsing(ServerPlayer p, ItemStack s) {
        float q = quench(s);
        if (q > 0) add(p, q);
    }

    /** Sneak + right-click water with an empty hand (sent by the client, checked here). */
    public static void sip(ServerPlayer p) {
        if (!HHConfig.THIRST.get() || !p.getMainHandItem().isEmpty()) return;
        long now = p.serverLevel().getGameTime();
        CompoundTag d = Story.persisted(p);
        if (now - d.getLong("hollowhours_sip") < 12) return;
        Vec3 eye = p.getEyePosition();
        Vec3 to = eye.add(p.getViewVector(1f).scale(4.5));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, p));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        if (!p.level().getFluidState(pos).is(FluidTags.WATER)) return;
        d.putLong("hollowhours_sip", now);
        add(p, 2);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_DRINK, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.1f);
        p.serverLevel().sendParticles(ParticleTypes.SPLASH, hit.getLocation().x, hit.getLocation().y + 0.1, hit.getLocation().z, 6, 0.15, 0.05, 0.15, 0.1);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        // untreated river water
        if (R.chance(0.15)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
    }
}
