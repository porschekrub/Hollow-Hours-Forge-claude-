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
 * Thirst 0-20, working like hunger: it goes down with what you do (running, jumping, fighting, mining, healing), 10%
 * faster than the hunger bar, and drinks also give a hidden "hydration" (like food saturation) that is used up first.
 * Drink: water bottles (+7), the canteen (+15%), milk (+5), other potions, soups, honey, melon, berries... or sneak +
 * right-click water with an empty hand (+2 a sip, river water can make you sick). Below 6 you're slow, below 3 weak,
 * at 0 it hurts.
 */
public final class Thirst {
    private Thirst() {}

    static final String KEY = "hollowhours_thirst", HYD = "hollowhours_hydration";
    public static final float MAX = 20;
    /** thirst goes 10% faster than hunger */
    static final float VS_HUNGER = 1.1f;
    /** a drink's hidden hydration, as a share of what it quenches (like food saturation) */
    static final float HYDRATION = 0.6f;

    /** per player: food + saturation last tick, and how much of it was used up since the last thirst tick */
    private static final java.util.Map<java.util.UUID, float[]> FOOD = new java.util.HashMap<>();

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

    public static float hydration(Player p) {
        return Math.min(get(p), Story.persisted(p).getFloat(HYD));
    }

    public static void setHydration(Player p, float v) {
        Story.persisted(p).putFloat(HYD, Math.max(0, Math.min(get(p), v)));
    }

    /** Drinking: thirst plus a little hidden hydration. */
    public static void drink(Player p, float amount) {
        add(p, amount);
        setHydration(p, hydration(p) + amount * HYDRATION);
    }

    /** Every tick: whatever the hunger bar spends (food or saturation), thirst will spend 10% more. */
    public static void trackFood(ServerPlayer p) {
        float[] f = FOOD.computeIfAbsent(p.getUUID(), k -> new float[] {Float.NaN, 0});
        float total = p.getFoodData().getFoodLevel() + p.getFoodData().getSaturationLevel();
        if (!Float.isNaN(f[0]) && total < f[0]) f[1] += f[0] - total;
        f[0] = total;
    }

    public static void forget(ServerPlayer p) {
        FOOD.remove(p.getUUID());
    }

    /** Every 20 ticks. */
    public static void tick(ServerPlayer p, long now) {
        if (!HHConfig.THIRST.get() || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        float[] f = FOOD.get(p.getUUID());
        float use = f == null ? 0 : f[1] * VS_HUNGER * HHConfig.THIRST_RATE.get().floatValue();
        if (f != null) f[1] = 0;
        Level level = p.level();
        // hydration goes first (like saturation), then the bar; on peaceful the bar itself never drops (like hunger)
        float hyd = hydration(p), fromHyd = Math.min(hyd, use);
        setHydration(p, hyd - fromHyd);
        if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) set(p, get(p) - (use - fromHyd));
        float t = get(p);
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
        if (q > 0) drink(p, q);
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
        drink(p, 2);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_DRINK, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.1f);
        p.serverLevel().sendParticles(ParticleTypes.SPLASH, hit.getLocation().x, hit.getLocation().y + 0.1, hit.getLocation().z, 6, 0.15, 0.05, 0.15, 0.1);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        // untreated river water
        if (R.chance(0.15)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
    }
}
