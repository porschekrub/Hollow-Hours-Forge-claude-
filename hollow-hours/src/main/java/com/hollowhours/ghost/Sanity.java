package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sanity 0-100. Normal life keeps it full; once the haunting starts the dark, the night, being alone, ghosts nearby
 * (worse when you can see one) and every jumpscare wear it down. Light, daylight, friends, pets, a campfire, music,
 * games on your phone (and the playground / arcade things you can build), sleep and comfort food bring it back.
 * Low sanity: whispers, then things that aren't there (footsteps, knocks, a face), then the world bends.
 */
public final class Sanity {
    private Sanity() {}

    static final String KEY = "hollowhours_sanity";
    public static final float MAX = 100;

    public static float get(Player p) {
        CompoundTag d = Story.persisted(p);
        return d.contains(KEY) ? d.getFloat(KEY) : MAX;
    }

    public static void set(Player p, float v) {
        Story.persisted(p).putFloat(KEY, Math.max(0, Math.min(MAX, v)));
    }

    public static void add(Player p, float v) {
        if (!HHConfig.SANITY.get()) return;
        set(p, get(p) + v);
    }

    /** A fright (jumpscare, a ghost face, a kill nearby). */
    public static void shock(ServerPlayer p, float amount) {
        if (!HHConfig.SANITY.get() || p.isCreative() || p.isSpectator()) return;
        add(p, -amount);
    }

    /** Every 20 ticks. */
    public static void tick(ServerPlayer p, long now) {
        if (!HHConfig.SANITY.get() || p.isSpectator() || !p.isAlive()) return;
        if (!Story.activeCached() || p.isCreative()) {
            add(p, 0.5f);
            return;
        }
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        float d = 0;
        boolean night = W.isNight(level);
        int light = level.getMaxLocalRawBrightness(at);
        boolean sky = level.canSeeSky(at);
        // ---- what wears you down
        if (light < 4) d -= 0.10f;
        else if (light < 8) d -= 0.03f;
        if (night && sky) d -= 0.04f;
        if (W.isUnderground(level, p.position()) && light < 8) d -= 0.04f;
        if (Atmosphere.bloodMoonFor(level)) d -= 0.06f;
        boolean ghostNear = false;
        for (GhostEntity g : GhostBrain.ghostsNear(level, p.position(), 40)) {
            if (g.brainIfPresent() != null && g.brain().isLeaving()) continue;
            ghostNear = true;
            double dist = g.distanceTo(p);
            d -= dist < 12 ? 0.45f : 0.15f;
            if (sees(p, g)) d -= 0.55f;
        }
        if (Thirst.get(p) < 4) d -= 0.03f;
        if (p.getFoodData().getFoodLevel() < 4) d -= 0.03f;
        // ---- what holds you together
        if (!ghostNear) {
            if (light >= 12) d += 0.05f;
            if (!night && sky) d += 0.05f;
            AABB box = p.getBoundingBox().inflate(8);
            if (!level.getEntitiesOfClass(Player.class, box, o -> o != p && o.isAlive() && !o.isSpectator()).isEmpty()) d += 0.06f;
            if (!level.getEntitiesOfClass(TamableAnimal.class, box, t -> t.isTame() && p.getUUID().equals(t.getOwnerUUID())).isEmpty()) d += 0.06f;
            if (now % 100 == 0) cozyCache.put(p.getUUID(), cozy(level, at));
            d += cozyCache.getOrDefault(p.getUUID(), 0f);
        }
        add(p, d);
        effects(p, level, now);
    }

    private static final java.util.Map<java.util.UUID, Float> cozyCache = new java.util.HashMap<>();

    /** Music playing nearby, a TV that's on, a lit campfire: 0..0.2 per second. */
    static float cozy(ServerLevel level, BlockPos at) {
        float c = 0;
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(x, z);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (be.getBlockPos().distSqr(at) > 16 * 16) continue;
                    if (be instanceof JukeboxBlockEntity jb && !jb.getFirstItem().isEmpty()) c = Math.max(c, 0.15f);
                    else if (be instanceof com.hollowhours.fun.FunBlocks.TvBlockEntity && be.getBlockState().getValue(com.hollowhours.fun.FunBlocks.LIT)
                            && be.getBlockPos().distSqr(at) < 8 * 8) c = Math.max(c, 0.2f);
                    else if (be.getBlockState().is(Blocks.CAMPFIRE) && be.getBlockState().getValue(CampfireBlock.LIT) && be.getBlockPos().distSqr(at) < 25) c = Math.max(c, 0.08f);
                }
            }
        return c;
    }

    /** Looking at it, with nothing in between. */
    static boolean sees(ServerPlayer p, GhostEntity g) {
        if (g.distanceTo(p) > 32) return false;
        Vec3 to = g.getEyePosition().subtract(p.getEyePosition()).normalize();
        return p.getViewVector(1f).dot(to) > 0.75 && p.hasLineOfSight(g);
    }

    static void effects(ServerPlayer p, ServerLevel level, long now) {
        float s = get(p);
        if (s < 60 && R.chance(s < 35 ? 0.03 : 0.012)) Director.whisper(p);
        if (s < 35 && R.chance(s < 15 ? 0.05 : 0.02)) hallucinate(p);
        if (s < 15) {
            if (R.chance(0.06)) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, true));
            if (R.chance(0.04)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
        }
        if (s <= 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            if (now % 200 == 0) p.hurt(GhostDamage.madness(level), 1.0f);
        }
    }

    /** Things that aren't there. */
    static void hallucinate(ServerPlayer p) {
        Vec3 l = p.position(), v = p.getViewVector(1f);
        double bx = l.x - v.x * 3, bz = l.z - v.z * 3;
        switch (R.randInt(0, 6)) {
            case 0 -> {
                for (int k = 0; k < 5; k++) com.hollowhours.util.Later.run(k * 7, () -> Fx.playAt(p, "step.stone", bx, l.y, bz, 1f, R.pitch(0.8, 1.0)));
            }
            case 1 -> Fx.playAt(p, "ghost.knock.wood", bx, l.y + 1, bz, 1.2f, R.pitch(0.8, 1.0));
            case 2 -> Fx.playAt(p, "ghost.growl", bx, l.y + 1, bz, 0.8f, R.pitch(0.5, 0.8));
            case 3 -> Fx.playAt(p, "ghost.horror.whisper", l.x + v.x, l.y + 1.6, l.z + v.z, 1.4f, R.pitch(0.9, 1.1));
            case 4 -> Jumpscares.scare(p, Director.randomKind().n, Jumpscares.FLASH);
            case 5 -> Fx.actionbar(p, Component.translatable("hollowhours.sanity.behind"));
            default -> Fx.playAt(p, "ghost.scare.giggle", bx, l.y + 1, bz, 0.9f, R.pitch(0.9, 1.1));
        }
    }

    /** Comfort food. */
    public static void onFinishUsing(ServerPlayer p, ItemStack s) {
        if (s.is(Items.COOKIE)) add(p, 3);
        else if (s.is(Items.PUMPKIN_PIE)) add(p, 6);
        else if (s.is(Items.GOLDEN_APPLE) || s.is(Items.GOLDEN_CARROT)) add(p, 8);
        else if (s.is(Items.ENCHANTED_GOLDEN_APPLE)) add(p, 30);
        else if (s.is(Items.HONEY_BOTTLE) || s.is(Items.SWEET_BERRIES)) add(p, 1);
    }

    public static void onWakeUp(ServerPlayer p) {
        add(p, 30);
    }
}
