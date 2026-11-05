package com.hollowhours.combat;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.ghost.Atmosphere;
import com.hollowhours.ghost.GhostBrain;
import com.hollowhours.ghost.GhostTalk;
import com.hollowhours.ghost.KillSequence;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.hollowhours.rescue.Rescue;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fighting a ghost. Ghosts can't die, but every hit wears down their "poise"; at zero they are knocked out: they fall,
 * lie there grumbling, and get up again after a while (angry). A ghost that is holding someone lets go when it is
 * knocked out. Guarding with a melee weapon just as a ghost swings = a parry that staggers it.
 */
public final class GhostCombat {
    private GhostCombat() {}

    static final float[] BASE = {0, 70, 55, 100, 75, 85};
    /** last melee swing of each player: [game time, attack strength 0..1, sprinting 1/0] */
    static final Map<UUID, float[]> SWING = new HashMap<>();
    /** ghosts knocked out, per kind, this session (for the endgame / tests) */
    public static final int[] KO_COUNT = new int[6];

    public static float maxPoise(GhostBrain b) {
        float m = BASE[b.kind.n] * HHConfig.GHOST_TOUGHNESS.get().floatValue();
        m *= 1 + 0.15f * (b.power() - 1);
        if (Atmosphere.bloodMoonFor(b.g.level())) m *= 1.4f;
        if (b.g.getServer() != null) m *= (float) (1 + 0.2 * (Rescue.harshest(b.g.getServer()) - 1));
        return m;
    }

    public static float poise(GhostBrain b) {
        Double v = b.vals.get("poise");
        if (v == null) {
            v = (double) maxPoise(b);
            b.vals.put("poise", v);
        }
        return v.floatValue();
    }

    public static boolean knockedOut(GhostBrain b) {
        return b.mode == GhostBrain.Mode.KO;
    }

    public static boolean staggered(GhostBrain b, long now) {
        return now < b.timers.getOrDefault("stagger", 0L);
    }

    public static void rememberSwing(Player p, float strength) {
        SWING.put(p.getUUID(), new float[]{p.level().getGameTime(), strength, p.isSprinting() ? 1 : 0});
    }

    // ------------------------------------------------------------------ being hit
    /** From GhostEntity.hurt on the server. @return true when the hit counted */
    public static boolean hurt(GhostEntity g, DamageSource src, float amount) {
        if (!(src.getEntity() instanceof ServerPlayer p)) return false;
        GhostBrain b = g.brain();
        if (src.is(DamageTypes.EXPLOSION) || src.is(DamageTypes.PLAYER_EXPLOSION)) return false; // grenades count on their own
        if (!HHConfig.KNOCKOUT.get()) {
            b.onHitBy(p);
            return false;
        }
        ItemStack held = p.getMainHandItem();
        float factor;
        if (src.getDirectEntity() != p) factor = 0.7f;               // arrows, tridents thrown
        else if (held.getItem() instanceof MeleeWeapon w) factor = w.poise;
        else if (held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem || held.getItem() instanceof TridentItem) factor = 0.6f;
        else factor = 0.3f;                                           // fists and odd things
        float[] sw = SWING.get(p.getUUID());
        boolean fresh = sw != null && p.level().getGameTime() - (long) sw[0] <= 1;
        if (fresh && sw[2] > 0 && sw[1] > 0.9f && src.getDirectEntity() == p) factor *= 1.6f; // sprinting full-power blow
        factor *= 1 + 0.05f * Fitness.meleeLevel(p);
        factor *= 1 + 0.4f * Fitness.strength(p) / 100f;
        boolean ok = poiseHit(g, p, amount * factor, false);
        if (ok && src.getDirectEntity() == p) {
            Fitness.addSkill(p, false, 1);
            if (held.isDamageableItem()) held.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        }
        return ok;
    }

    /** Wears a ghost's poise down; knocks it out at zero. */
    public static boolean poiseHit(GhostEntity g, ServerPlayer p, float amount, boolean ranged) {
        GhostBrain b = g.brain();
        if (!HHConfig.KNOCKOUT.get()) {
            b.onHitBy(p);
            return false;
        }
        if (knockedOut(b) || b.isLeaving() || b.scareOnly || b.glimpse || amount <= 0) return false;
        ServerLevel level = (ServerLevel) g.level();
        float left = poise(b) - amount;
        b.vals.put("poise", (double) left);
        level.broadcastDamageEvent(g, p.damageSources().playerAttack(p));
        Vec3 c = g.position().add(0, g.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 3, 0.2, 0.3, 0.2, 0.02);
        if (!ranged) level.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1f, 0.7f);
        b.onHitBy(p);
        if (b.mode == GhostBrain.Mode.STALK || b.mode == GhostBrain.Mode.SEARCH || b.mode == GhostBrain.Mode.LOST) b.startChase(p, b.now());
        if (left <= 0) knockOut(b, p);
        return true;
    }

    // ------------------------------------------------------------------ knocked out
    public static void knockOut(GhostBrain b, ServerPlayer by) {
        GhostEntity g = b.g;
        ServerLevel level = (ServerLevel) g.level();
        KillSequence.rescueFrom(b);
        b.mode = GhostBrain.Mode.KO;
        b.search = null;
        b.breach.reset();
        long now = b.now();
        b.timers.put("koUntil", now + HHConfig.KO_SECONDS.get() * 20L);
        b.timers.put("koMutter", now + 160 + R.randInt(0, 200));
        g.setKnockedOut(true);
        g.setRaged(false);
        g.getNavigation().stop();
        g.setDeltaMovement(Vec3.ZERO);
        g.setAnim(GhostAnim.IDLE);
        g.setTrack(-1);
        Vec3 c = g.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, g.getX(), g.getY(), g.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.4f, 0.5f);
        Fx.sound(level, "ghost.voice.g" + b.kind.n + ".rage", g.eye(), 2.2f, 0.8f, 40);
        KO_COUNT[b.kind.n]++;
        com.hollowhours.endgame.Endgame.onKnockOut(b);
        Fitness.addSkill(by, false, 10);
        for (ServerPlayer o : level.players()) {
            if (o.position().distanceTo(g.position()) > 40) continue;
            GhostTalk.say(o, g, "ko", 1);
            Fx.actionbar(o, Component.translatable("hollowhours.ko.down", HHConfig.KO_SECONDS.get()).withStyle(ChatFormatting.GOLD));
            HHNet.toPlayer(o, new S2CCombat(S2CCombat.KO, b.kind.n, 0, 0));
        }
        com.hollowhours.ghost.Director.stopMusicFor(by);
    }

    /** Called every tick while knocked out (from GhostBrain.tick). */
    public static void tickKnockedOut(GhostBrain b, long now) {
        GhostEntity g = b.g;
        g.getNavigation().stop();
        Vec3 v = g.getDeltaMovement();
        g.setDeltaMovement(0, Math.min(0, v.y), 0);
        ServerLevel level = (ServerLevel) g.level();
        if (now % 10 == 0) {
            Vec3 c = g.position().add(0, 0.6, 0);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 2, 0.4, 0.1, 0.4, 0.02);
        }
        if (now >= b.timers.getOrDefault("koMutter", Long.MAX_VALUE)) {
            b.timers.put("koMutter", now + 220 + R.randInt(0, 260));
            for (ServerPlayer o : level.players())
                if (o.position().distanceTo(g.position()) < 16) GhostTalk.say(o, g, "mutter", 3);
        }
        if (now < b.timers.getOrDefault("koUntil", 0L)) return;
        if (com.hollowhours.endgame.Endgame.keepDown(b)) return;
        wake(b, now);
    }

    public static void wake(GhostBrain b, long now) {
        GhostEntity g = b.g;
        g.setKnockedOut(false);
        b.vals.put("poise", (double) maxPoise(b) * 0.7);
        b.mode = GhostBrain.Mode.LOST;
        b.lostAt = now;
        g.setRaged(true);
        ServerLevel level = (ServerLevel) g.level();
        Fx.sound(level, "ghost.growl", g.eye(), 2.4f, 0.6f, 48);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, g.getX(), g.getY() + 1, g.getZ(), 15, 0.4, 0.6, 0.4, 0.02);
        ServerPlayer near = null;
        double bd = 40;
        for (ServerPlayer o : level.players()) {
            double d = o.position().distanceTo(g.position());
            if (d < 40) GhostTalk.say(o, g, "wake", 1);
            if (GhostBrain.targetable(o) && d < bd) {
                bd = d;
                near = o;
            }
        }
        if (near != null) b.startChase(near, now);
    }

    // ------------------------------------------------------------------ defending against a ghost's swing
    /**
     * @return the damage that still lands: unchanged, reduced (guard, armour), 0 (dodged) or -1 (parried: no hit at
     * all, and the ghost is staggered)
     */
    public static float defend(ServerPlayer p, GhostEntity g, float dmg) {
        if (Combat.dodging(p)) {
            Fx.sound(p.level(), "ghost.dash", p.position(), 0.8f, 1.4f, 12);
            return 0;
        }
        if (MeleeWeapon.guarding(p)) {
            Vec3 to = g.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            Vec3 look = p.getViewVector(1f).multiply(1, 0, 1).normalize();
            if (to.dot(look) > 0.2) {
                ItemStack w = p.getUseItem();
                w.hurtAndBreak(2, p, pl -> pl.broadcastBreakEvent(p.getUsedItemHand()));
                if (p.getTicksUsingItem() <= 7) {
                    parry(p, g);
                    return -1;
                }
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 0.8f);
                dmg *= 0.4f;
            }
        }
        return dmg * (1 - MilitaryArmor.ghostCut(p));
    }

    static void parry(ServerPlayer p, GhostEntity g) {
        GhostBrain b = g.brain();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.8f);
        ((ServerLevel) p.level()).sendParticles(ParticleTypes.CRIT, g.getX(), g.getY() + 1.4, g.getZ(), 16, 0.4, 0.4, 0.4, 0.3);
        b.timers.put("stagger", b.now() + 40);
        Fx.actionbar(p, Component.translatable("hollowhours.combat.parry").withStyle(ChatFormatting.AQUA));
        Fitness.addSkill(p, false, 3);
        poiseHit(g, p, maxPoise(b) * 0.22f, false);
    }
}
