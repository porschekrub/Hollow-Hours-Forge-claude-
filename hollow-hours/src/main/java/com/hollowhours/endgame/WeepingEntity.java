package com.hollowhours.endgame;

import com.hollowhours.combat.Combat;
import com.hollowhours.combat.Fitness;
import com.hollowhours.combat.MilitaryArmor;
import com.hollowhours.config.HHConfig;
import com.hollowhours.ghost.Director;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CCombat;
import com.hollowhours.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Thing That Weeps Under the Mountain. Buried to the waist in the cavern floor, it does not move - it reaches.
 * <ul>
 * <li>slam - an arm comes down on a marked spot (red ring); the hand stays down a moment: hit it then (x1.5)</li>
 * <li>sweep - an arm sweeps the floor in front of it: jump over it, or roll (V)</li>
 * <li>grab - it picks someone up; mash to break free, or friends hit it hard to make it let go</li>
 * <li>weep - it cries into its hands and black tears rain from the ceiling (dark spots on the floor)</li>
 * <li>scream - later on: darkness, a push away from it</li>
 * </ul>
 * Below two thirds and one third of its health it gets angrier (faster, harder). Only players can hurt it.
 */
public class WeepingEntity extends PathfinderMob implements GeoEntity {
    public static final int A_IDLE = 0, A_RISE = 1, A_SLAM_NEAR_L = 2, A_SLAM_NEAR_R = 3, A_SLAM_FAR_L = 4, A_SLAM_FAR_R = 5, A_SWEEP = 6,
            A_SCREAM = 7, A_WEEP = 8, A_GRAB = 9, A_HOLD = 10, A_THROW = 11, A_STUN = 12, A_DEFEAT = 13;
    public static final String[] ANIMS = {"idle", "rise", "slam_near_l", "slam_near_r", "slam_far_l", "slam_far_r", "sweep", "scream", "weep",
            "grab", "hold", "throw", "stun", "defeat"};
    static final int[] LENGTH = {0, 80, 48, 48, 48, 48, 48, 52, 72, 24, 100, 20, 40, 140};

    static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(WeepingEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> SEQ = SynchedEntityData.defineId(WeepingEntity.class, EntityDataSerializers.INT);

    // where the hands land (blocks, measured on the model): forward, side (+ = its right), for near / far slams
    static final double[] SLAM_NEAR = {4.16, 2.44}, SLAM_FAR = {6.53, 2.68};
    static final double[] GRAB_AT = {4.88, -0.37, 1.66}, HOLD_AT = {3.9, -1.0, 3.8};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.hollowhours.weeping").withStyle(ChatFormatting.GOLD),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);

    static final Map<UUID, WeepingEntity> HOLDING = new HashMap<>();

    int act = -1, actT, nextAttack, phase = 1, noPlayers, stunUntil, cryAt, lastSlamHand;
    boolean screamDue;
    Vec3 aim = Vec3.ZERO;
    float lockedYaw;
    @Nullable
    UUID target, victim;
    float prog, holdDamage, recentDamage;
    int holdEnd, recentAt, pressTick, pressesNow;
    final List<double[]> tears = new ArrayList<>();
    final List<UUID> sweptHit = new ArrayList<>();

    public WeepingEntity(EntityType<? extends WeepingEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.xpReward = 0;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.bar.setDarkenScreen(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 500).add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, A_IDLE);
        entityData.define(SEQ, 0);
    }

    public int anim() {
        return entityData.get(ANIM);
    }

    void play(int a) {
        entityData.set(ANIM, a);
        entityData.set(SEQ, entityData.get(SEQ) + 1);
        act = a == A_IDLE ? -1 : a;
        actT = 0;
    }

    // ------------------------------------------------------------------ body
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity e) {}

    @Override
    public void push(Entity e) {}

    @Override
    public boolean canBeCollidedWith() {
        return isAlive();
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public void checkDespawn() {}

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(9, 4, 9);
    }

    @Override
    public void travel(Vec3 v) {}

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    // ------------------------------------------------------------------ waking up
    public void startRising() {
        play(A_RISE);
    }

    void scaleHealth() {
        int n = Math.max(1, players(48).size());
        double hp = HHConfig.BOSS_HEALTH.get() * (1 + 0.5 * (n - 1));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth((float) hp);
    }

    // ------------------------------------------------------------------ damage
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return super.hurt(src, amount);
        if (isDeadOrDying() || anim() == A_RISE || anim() == A_DEFEAT) return false;
        Entity by = src.getEntity();
        boolean ok = by instanceof Player || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!ok) return false;
        if (!src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount = Math.min(amount, 30f); // (/kill still works)
        if (tickCount < stunUntil) amount *= 1.5f;
        boolean hit = super.hurt(src, amount);
        if (!hit) return false;
        Fx.sound(level(), "boss.hurt", position().add(0, 4, 0), 1.6f, 0.8f + random.nextFloat() * 0.3f, 48);
        if (victim != null) {
            holdDamage += amount;
            if (holdDamage >= 30) release(true, false);
        }
        if (tickCount - recentAt > 60) recentDamage = 0;
        recentAt = tickCount;
        recentDamage += amount;
        // a beating staggers it: hands down, gasping (and even more open)
        if (recentDamage >= 70 && act != A_STUN && act != A_HOLD && act != A_GRAB && !isDeadOrDying()) {
            recentDamage = 0;
            tears.clear();
            play(A_STUN);
            stunUntil = tickCount + LENGTH[A_STUN];
        }
        return true;
    }

    @Override
    public void die(DamageSource src) {
        if (level().isClientSide) {
            super.die(src);
            return;
        }
        release(false, false);
        super.die(src);
        play(A_DEFEAT);
        tears.clear();
        Fx.sound(level(), "boss.defeat", position().add(0, 4, 0), 4f, 1f, 96);
        if (level().getServer() != null) Endgame.onBossDefeated(level().getServer(), this);
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        if (deathTime % 4 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3, getZ(), 6, 1.4, 2.0, 1.4, 0.03);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RAW_GOLD_BLOCK.defaultBlockState()), getX(), getY() + 2, getZ(), 10, 1.6, 2, 1.6, 0.1);
        }
        if (deathTime > 60 && deathTime % 10 == 0) Fx.shakeNear(level(), position(), 32, 0.35f, 10);
        if (deathTime >= LENGTH[A_DEFEAT]) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            bar.removeAllPlayers();
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return; // (sunk away this tick: the boss bar must not be handed out again)
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (tickCount % 10 == 0) updateBar();
        if (isDeadOrDying()) return;
        ServerLevel sl = (ServerLevel) level();
        tickTears(sl);
        if (victim != null) tickHold(sl);
        if (act == A_RISE) {
            if (actT == 0) {
                scaleHealth();
                Fx.sound(level(), "boss.rise", position(), 4f, 1f, 96);
                for (ServerPlayer p : players(64)) {
                    Fx.title(p, Component.translatable("hollowhours.endgame.boss.title").withStyle(ChatFormatting.GOLD),
                            Component.translatable("hollowhours.endgame.boss.sub").withStyle(ChatFormatting.GRAY), 10, 70, 20);
                    Director.music(p, "boss");
                }
            }
            if (actT % 4 == 0) {
                Fx.shakeNear(level(), position(), 40, 0.9f, 8);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), getX(), getY() + 0.5, getZ(), 30, 2.5, 0.4, 2.5, 0.2);
            }
            if (++actT >= LENGTH[A_RISE]) {
                play(A_IDLE);
                nextAttack = tickCount + 30;
            }
            return;
        }
        List<ServerPlayer> ps = players(40);
        if (ps.isEmpty()) {
            if (++noPlayers > 600 && getHealth() < getMaxHealth()) {
                setHealth(getMaxHealth());
                phase = 1;
            }
            if (act >= 0 && act != A_HOLD) play(A_IDLE);
            return;
        }
        noPlayers = 0;
        if (tickCount % 40 == 0) for (ServerPlayer p : ps) Director.music(p, "boss");
        // angrier as it gets hurt
        float f = getHealth() / getMaxHealth();
        int want = f <= 0.33f ? 3 : f <= 0.66f ? 2 : 1;
        if (want > phase) {
            phase = want;
            screamDue = true;
            for (ServerPlayer p : players(64))
                Fx.actionbar(p, Component.translatable("hollowhours.endgame.boss.phase" + phase).withStyle(ChatFormatting.RED));
        }
        if (act < 0) {
            if (tickCount >= cryAt) {
                cryAt = tickCount + 70 + random.nextInt(50);
                Fx.sound(level(), "boss.cry", position().add(0, 5, 0), 3f, 0.95f + random.nextFloat() * 0.1f, 80);
            }
            ServerPlayer t = pickTarget(ps);
            if (t != null) turnTo(t.position(), 5f);
            if (tickCount >= nextAttack && t != null) choose(t, ps);
            return;
        }
        actT++;
        switch (act) {
            case A_SLAM_NEAR_L, A_SLAM_NEAR_R, A_SLAM_FAR_L, A_SLAM_FAR_R -> tickSlam(sl);
            case A_SWEEP -> tickSweep(sl);
            case A_SCREAM -> tickScream(sl);
            case A_WEEP -> tickWeep(sl, ps);
            case A_GRAB -> tickGrab(sl);
            default -> {}
        }
        if (act >= 0 && act != A_HOLD && actT >= LENGTH[act]) endAttack();
    }

    void endAttack() {
        int was = act;
        play(A_IDLE);
        int gap = phase == 3 ? 18 : phase == 2 ? 28 : 38;
        if (was == A_STUN) gap = 10;
        nextAttack = tickCount + gap + random.nextInt(20);
    }

    List<ServerPlayer> players(double r) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel sl)) return out;
        for (ServerPlayer p : sl.players())
            if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.position().distanceTo(position()) < r) out.add(p);
        return out;
    }

    void updateBar() {
        if (isRemoved()) {
            bar.removeAllPlayers();
            return;
        }
        List<ServerPlayer> near = new ArrayList<>();
        if (level() instanceof ServerLevel sl) for (ServerPlayer p : sl.players()) if (p.position().distanceTo(position()) < 56) near.add(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) {
            bar.removePlayer(p);
            Director.music(p, "");
        }
        for (ServerPlayer p : near) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) Director.music(p, "");
            bar.removeAllPlayers();
            release(false, false);
        }
        super.remove(reason);
    }

    @Nullable
    ServerPlayer pickTarget(List<ServerPlayer> ps) {
        ServerPlayer best = null;
        double bd = 1e9;
        for (ServerPlayer p : ps) {
            double d = p.position().distanceTo(position()) + (p.getUUID().equals(target) ? -3 : 0);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best != null) target = best.getUUID();
        return best;
    }

    void turnTo(Vec3 at, float maxStep) {
        float want = (float) Math.toDegrees(Math.atan2(-(at.x - getX()), at.z - getZ()));
        float yaw = Mth.approachDegrees(getYRot(), want, maxStep);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    void face(float yaw) {
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        lockedYaw = yaw;
    }

    Vec3 fwd(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** its right (= the model's +x) */
    Vec3 right(float yaw) {
        Vec3 f = fwd(yaw);
        return new Vec3(-f.z, 0, f.x);
    }

    Vec3 local(float yaw, double fw, double side, double up) {
        return position().add(fwd(yaw).scale(fw)).add(right(yaw).scale(side)).add(0, up, 0);
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    // ------------------------------------------------------------------ choosing what to do
    void choose(ServerPlayer t, List<ServerPlayer> ps) {
        double d = Math.hypot(t.getX() - getX(), t.getZ() - getZ());
        float r = random.nextFloat();
        if (screamDue && phase >= 2) {
            screamDue = false;
            startScream();
            return;
        }
        if (d < 3.6) {
            if (r < 0.45) startGrab(t);
            else if (r < 0.85 || phase < 2) startSweep(t);
            else startScream();
        } else if (d < 6.0) {
            if (r < 0.5) startSlam(t, false);
            else if (r < 0.75) startSweep(t);
            else startGrab(t);
        } else if (d < 9.2) {
            if (r < 0.6) startSlam(t, true);
            else if (r < 0.8) startWeep();
            else startSweep(t);
        } else {
            if (r < 0.65 || phase < 2) startWeep();
            else startScream();
        }
    }

    // ------------------------------------------------------------------ slam
    void startSlam(ServerPlayer t, boolean far) {
        double[] k = far ? SLAM_FAR : SLAM_NEAR;
        lastSlamHand ^= 1;
        int hand = lastSlamHand; // 0 = l (its right side), 1 = r
        double side = hand == 0 ? k[1] : -k[1];
        // turn so that this hand comes down right where they stand
        float base = yawTo(position(), t.position());
        float best = base;
        double bd = 1e9;
        for (int i = -60; i <= 60; i += 2) {
            float yaw = base + i;
            Vec3 p = local(yaw, k[0], side, 0);
            double dd = Math.hypot(p.x - t.getX(), p.z - t.getZ());
            if (dd < bd) {
                bd = dd;
                best = yaw;
            }
        }
        face(best);
        aim = local(best, k[0], side, 0);
        play(far ? (hand == 0 ? A_SLAM_FAR_L : A_SLAM_FAR_R) : (hand == 0 ? A_SLAM_NEAR_L : A_SLAM_NEAR_R));
        Fx.sound(level(), "boss.windup", position().add(0, 4, 0), 2.5f, 0.9f, 64);
    }

    void tickSlam(ServerLevel sl) {
        int hit = 21;
        if (actT < hit && actT % 4 == 0) ring(sl, aim, 3.0, new DustParticleOptions(new Vector3f(0.95f, 0.1f, 0.05f), 0.9f), 20);
        if (actT == hit) {
            float dmg = phase == 3 ? 13 : phase == 2 ? 11 : 9;
            for (ServerPlayer p : players(40)) {
                if (Math.hypot(p.getX() - aim.x, p.getZ() - aim.z) > 3.0 || Math.abs(p.getY() - aim.y) > 3) continue;
                if (Combat.dodging(p)) continue;
                hurtPlayer(p, dmg);
                Vec3 away = p.position().subtract(aim).multiply(1, 0, 1);
                away = away.lengthSqr() < 1e-3 ? fwd(lockedYaw) : away.normalize();
                p.setDeltaMovement(away.x * 0.8, 0.55, away.z * 0.8);
                p.hurtMarked = true;
            }
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), aim.x, aim.y + 0.2, aim.z, 60, 1.6, 0.3, 1.6, 0.25);
            sl.sendParticles(ParticleTypes.EXPLOSION, aim.x, aim.y + 0.4, aim.z, 2, 0.6, 0.1, 0.6, 0);
            Fx.shakeNear(level(), aim, 18, 0.9f, 10);
            Fx.sound(level(), "boss.slam", aim, 4f, 0.9f + random.nextFloat() * 0.2f, 80);
            // the hand stays down a moment: hit it now
            stunUntil = tickCount + 16;
        }
    }

    // ------------------------------------------------------------------ sweep
    void startSweep(ServerPlayer t) {
        face(yawTo(position(), t.position()));
        sweptHit.clear();
        play(A_SWEEP);
        Fx.sound(level(), "boss.windup", position().add(0, 4, 0), 2.5f, 0.7f, 64);
    }

    /** sweep angle (deg, + = its right) of the arm at a tick of the animation */
    static double sweepAngle(int t) {
        double[][] k = {{18, 85}, {22, 10}, {24.4, -17}, {28, -51}, {31, -100}};
        if (t <= k[0][0]) return k[0][1];
        for (int i = 1; i < k.length; i++) if (t <= k[i][0]) {
            double f = (t - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
            return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * f;
        }
        return k[k.length - 1][1];
    }

    void tickSweep(ServerLevel sl) {
        if (actT >= 4 && actT < 18 && actT % 4 == 0) {
            for (int a = -80; a <= 80; a += 16) {
                double rad = Math.toRadians(a);
                for (double r = 3; r <= 6.5; r += 3.5) {
                    Vec3 p = position().add(fwd(lockedYaw).scale(Math.cos(rad) * r)).add(right(lockedYaw).scale(Math.sin(rad) * r));
                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.95f, 0.55f, 0.05f), 0.8f), p.x, p.y + 0.15, p.z, 1, 0.1, 0, 0.1, 0);
                }
            }
        }
        if (actT == 18) Fx.sound(level(), "boss.sweep", position().add(0, 1, 0), 3f, 1f, 64);
        if (actT < 18 || actT > 31) return;
        double a0 = sweepAngle(actT - 1), a1 = sweepAngle(actT);
        double lo = Math.min(a0, a1) - 8, hi = Math.max(a0, a1) + 8;
        float dmg = phase == 3 ? 12 : phase == 2 ? 10 : 8;
        for (ServerPlayer p : players(12)) {
            if (sweptHit.contains(p.getUUID())) continue;
            Vec3 d = p.position().subtract(position());
            double fw = d.dot(fwd(lockedYaw)), sd = d.dot(right(lockedYaw));
            double r = Math.hypot(fw, sd);
            if (r < 1.2 || r > 7.2 || p.getY() - getY() > 2.2) continue;
            double ang = Math.toDegrees(Math.atan2(sd, fw));
            if (ang < lo || ang > hi) continue;
            if (!p.onGround() || Combat.dodging(p)) continue; // jumped over it
            sweptHit.add(p.getUUID());
            hurtPlayer(p, dmg);
            Vec3 push = right(lockedYaw).scale(-1.4).add(0, 0.5, 0);
            p.setDeltaMovement(push);
            p.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ scream
    void startScream() {
        play(A_SCREAM);
    }

    void tickScream(ServerLevel sl) {
        if (actT != 16) return;
        Fx.sound(level(), "boss.scream", position().add(0, 5, 0), 5f, 1f, 96);
        Vec3 head = position().add(0, 5.5, 0);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, head.x, head.y, head.z, 1, 0, 0, 0, 0);
        for (ServerPlayer p : players(32)) {
            if (Combat.dodging(p)) continue;
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, false, false));
            hurtPlayer(p, 3);
            Vec3 away = p.position().subtract(position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1e-3) away = away.normalize();
            double k = Math.max(0.3, 1.4 - p.position().distanceTo(position()) / 24);
            p.setDeltaMovement(away.x * k, 0.4, away.z * k);
            p.hurtMarked = true;
            Fx.shake(p, 1.0f, 20);
            com.hollowhours.ghost.Sanity.set(p, com.hollowhours.ghost.Sanity.get(p) - 8);
        }
    }

    // ------------------------------------------------------------------ weep: tears from the ceiling
    void startWeep() {
        play(A_WEEP);
        Fx.sound(level(), "boss.cry", position().add(0, 5, 0), 4f, 0.8f, 96);
    }

    void tickWeep(ServerLevel sl, List<ServerPlayer> ps) {
        int every = phase == 3 ? 3 : phase == 2 ? 4 : 5;
        if (actT < 14 || actT > 60 || actT % every != 0) return;
        for (ServerPlayer p : ps) {
            Vec3 at;
            if (random.nextInt(3) == 0) at = p.position().add(p.getDeltaMovement().multiply(12, 0, 12));
            else at = p.position().add((random.nextDouble() - 0.5) * 7, 0, (random.nextDouble() - 0.5) * 7);
            boolean rock = phase == 3 && random.nextInt(3) == 0;
            tears.add(new double[]{at.x, p.getY(), at.z, tickCount + 18, rock ? 1 : 0});
            sl.sendParticles(ParticleTypes.SQUID_INK, at.x, p.getY() + 0.1, at.z, 4, 0.45, 0, 0.45, 0);
            sl.sendParticles(rock ? new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DEEPSLATE.defaultBlockState()) : ParticleTypes.FALLING_OBSIDIAN_TEAR,
                    at.x, p.getY() + 9, at.z, 6, 0.3, 0.5, 0.3, 0);
        }
    }

    void tickTears(ServerLevel sl) {
        if (tears.isEmpty()) return;
        for (int i = tears.size() - 1; i >= 0; i--) {
            double[] t = tears.get(i);
            if (tickCount < t[3]) {
                if ((tickCount - (int) t[3]) % 4 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, t[0], t[1] + 0.1, t[2], 2, 0.4, 0, 0.4, 0);
                continue;
            }
            tears.remove(i);
            boolean rock = t[4] > 0;
            float dmg = rock ? 7 : phase == 3 ? 6 : 5;
            for (ServerPlayer p : players(48)) {
                if (Math.hypot(p.getX() - t[0], p.getZ() - t[2]) > (rock ? 1.8 : 1.4) || Math.abs(p.getY() - t[1]) > 2.5 || Combat.dodging(p)) continue;
                hurtPlayer(p, dmg);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true));
            }
            if (rock) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState()), t[0], t[1] + 0.3, t[2], 25, 0.5, 0.2, 0.5, 0.2);
            else sl.sendParticles(ParticleTypes.SQUID_INK, t[0], t[1] + 0.3, t[2], 14, 0.4, 0.2, 0.4, 0.08);
            Fx.sound(level(), rock ? "boss.slam" : "boss.tear", new Vec3(t[0], t[1], t[2]), rock ? 1.2f : 1f, rock ? 1.5f : 1f, 32);
        }
    }

    // ------------------------------------------------------------------ grab
    void startGrab(ServerPlayer t) {
        face(yawTo(position(), t.position()));
        play(A_GRAB);
        Fx.sound(level(), "boss.windup", position().add(0, 4, 0), 2.5f, 1.2f, 64);
    }

    void tickGrab(ServerLevel sl) {
        if (actT != 11 || victim != null) return;
        Vec3 g = local(lockedYaw, GRAB_AT[0], GRAB_AT[1], 0);
        ServerPlayer best = null;
        double bd = 2.6;
        for (ServerPlayer p : players(12)) {
            double d = Math.hypot(p.getX() - g.x, p.getZ() - g.z);
            if (d < bd && Math.abs(p.getY() - getY()) < 3 && !Combat.dodging(p) && !HOLDING.containsKey(p.getUUID())) {
                bd = d;
                best = p;
            }
        }
        if (best == null) return;
        victim = best.getUUID();
        HOLDING.put(victim, this);
        prog = 0.25f;
        holdDamage = 0;
        holdEnd = tickCount + 13 + 100;
        best.stopRiding();
        Fx.sound(level(), "boss.grab", best.position(), 3f, 1f, 48);
        HHNet.toPlayer(best, new S2CCombat(S2CCombat.STRUGGLE, prog, holdEnd - tickCount, 0));
    }

    void tickHold(ServerLevel sl) {
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(victim);
        if (p == null || !p.isAlive() || p.level() != level()) {
            release(false, false);
            return;
        }
        if (act == A_GRAB && actT >= LENGTH[A_GRAB]) play(A_HOLD);
        Vec3 h = local(lockedYaw, HOLD_AT[0], HOLD_AT[1], HOLD_AT[2]);
        if (act == A_GRAB) {
            double k = Math.min(1, actT / (double) LENGTH[A_GRAB]);
            Vec3 g = local(lockedYaw, GRAB_AT[0], GRAB_AT[1], 0);
            h = g.add(h.subtract(g).scale(k * k * (3 - 2 * k)));
        }
        p.teleportTo(h.x, h.y, h.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        float pull = 0.0095f * (1 + 0.18f * (phase - 1));
        if (prog < 1f) prog -= pull; // (a full bar wins before it pulls again)
        if (tickCount % 2 == 0) HHNet.toPlayer(p, new S2CCombat(S2CCombat.STRUGGLE, Math.max(0, prog), holdEnd - tickCount, 0));
        if (tickCount % 10 == 0) sl.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, h.x, h.y + 1.5, h.z, 3, 0.4, 0.2, 0.4, 0);
        if (prog >= 1) release(true, true);
        else if (prog <= 0 || tickCount >= holdEnd) crush(p);
    }

    /** One press of the struggle key while it holds this player. @return true when it was holding them */
    public static boolean struggle(ServerPlayer p) {
        WeepingEntity w = HOLDING.get(p.getUUID());
        if (w == null || w.isRemoved()) return false;
        if (w.tickCount != w.pressTick) {
            w.pressTick = w.tickCount;
            w.pressesNow = 0;
        }
        if (++w.pressesNow > 2) return true;
        float inc = 0.05f * (1 + 0.9f * Fitness.strength(p) / 100f + MilitaryArmor.struggle(p) + 0.03f * Fitness.meleeLevel(p));
        w.prog = Math.min(1f, w.prog + inc);
        return true;
    }

    void release(boolean freed, boolean byStruggle) {
        if (victim == null) return;
        UUID id = victim;
        victim = null;
        HOLDING.remove(id);
        ServerPlayer p = level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(id);
        if (p != null) {
            HHNet.toPlayer(p, new S2CCombat(S2CCombat.STRUGGLE, -1, 0, freed ? 1 : 2));
            if (freed) {
                Vec3 away = fwd(lockedYaw);
                p.setDeltaMovement(away.x * 0.6, 0.3, away.z * 0.6);
                p.hurtMarked = true;
                p.fallDistance = 0;
                p.invulnerableTime = 30;
                Combat.grantDodge(p, 30);
                if (byStruggle) Fitness.addStrength(p, 0.8f);
            }
        }
        if (freed && !isDeadOrDying()) {
            play(A_STUN);
            stunUntil = tickCount + LENGTH[A_STUN];
            Fx.sound(level(), "boss.hurt", position().add(0, 4, 0), 3f, 0.6f, 64);
        }
    }

    void crush(ServerPlayer p) {
        UUID id = victim;
        victim = null;
        HOLDING.remove(id);
        HHNet.toPlayer(p, new S2CCombat(S2CCombat.STRUGGLE, -1, 0, 2));
        play(A_THROW);
        hurtPlayer(p, phase == 3 ? 16 : 13);
        Vec3 away = right(lockedYaw).scale(-1).add(fwd(lockedYaw)).normalize();
        p.setDeltaMovement(away.x * 1.5, 0.7, away.z * 1.5);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Fx.sound(level(), "boss.slam", p.position(), 2f, 1.4f, 48);
    }

    /** CI: do this attack now, at this player ("slam_near", "slam_far", "sweep", "scream", "weep", "grab"). */
    public void ciAttack(String what, ServerPlayer t) {
        if (victim != null) release(false, false);
        switch (what) {
            case "slam_near" -> startSlam(t, false);
            case "slam_far" -> startSlam(t, true);
            case "sweep" -> startSweep(t);
            case "scream" -> startScream();
            case "weep" -> startWeep();
            case "grab" -> startGrab(t);
            default -> play(A_IDLE);
        }
        nextAttack = tickCount + 100000;
    }

    /** CI: no attacks of its own for a while (screenshots) */
    public void ciHold(int ticks) {
        nextAttack = tickCount + ticks;
    }

    public boolean holding(ServerPlayer p) {
        return p.getUUID().equals(victim);
    }

    public int phase() {
        return phase;
    }

    // ------------------------------------------------------------------ helpers
    void hurtPlayer(ServerPlayer p, float dmg) {
        dmg *= (float) (1 - MilitaryArmor.ghostCut(p) * 0.5);
        p.invulnerableTime = 0;
        p.hurt(damageSources().mobAttack(this), dmg);
    }

    static void ring(ServerLevel sl, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions o, int n) {
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            sl.sendParticles(o, c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(o, c.x, c.y + 0.12, c.z, 2, r * 0.3, 0, r * 0.3, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("phase", phase);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = Math.max(1, t.getInt("phase"));
    }

    // ------------------------------------------------------------------ animation
    private int lastSeq = -1;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 5, st -> {
            int a = Mth.clamp(entityData.get(ANIM), 0, ANIMS.length - 1);
            int seq = entityData.get(SEQ);
            if (seq != lastSeq) {
                lastSeq = seq;
                st.getController().forceAnimationReset();
            }
            String name = "animation.weeping." + ANIMS[a];
            boolean loop = a == A_IDLE || a == A_HOLD;
            return st.setAndContinue(loop ? RawAnimation.begin().thenLoop(name) : RawAnimation.begin().thenPlayAndHold(name));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
