package com.hollowhours.ghost;

import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CJumpscare;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The mind of one ghost (port of main.js ghostTick + hide.js search + parts of intrude.js).
 *
 * STALK  : appears 22-30 blocks away, stares, creeps closer only when you are not looking (heartbeat, whispers).
 * SNAP   : its neck snaps toward you (jumpscare) and it starts running.
 * CHASE  : it must keep seeing or hearing you; lose it for 3 s and it SEARCHes your last known position.
 * SEARCH : sniffs around, checks closets and beds nearby, drags you out if it finds you.
 * BREACH : (GhostBreach) smashes doors / windows / boards / walls to get to you.
 * VISIT  : (GhostVisit) knocks on your door or stares through your window.
 * KILL   : (KillSequence) grab, scream, maybe the betrayal offer, then the kill animation.
 */
public final class GhostBrain {
    public enum Mode { SPAWNING, STALK, SNAP, CHASE, LOST, SEARCH, BREACH, KNOCK, WATCH, KILL, LEAVE, GLIMPSE }

    // ------------------------------------------------------------------ tuning (from main.js CFG)
    static final int AMBUSH_DIST = 12, CHASE_RANGE = 36, LOSE_RANGE = 48, FAR_REMOVE = 80, LOSE_TICKS = 60;

    public final GhostEntity g;
    public final GhostKind kind;
    public Mode mode = Mode.SPAWNING;
    public long born = -1;
    public double stalkFor;
    public boolean stalking = true;
    public long chaseStart, lostAt, nextBeat, nextCreep;
    public Variety.Mod mod;
    public Curses.Buff buff;
    public int power;
    public double spdMul = 1;
    public final Map<String, Long> timers = new HashMap<>();
    public final Map<String, Double> vals = new HashMap<>();
    public boolean scareOnly;       // indoor scare visitor: not a real hunt
    public boolean glimpse;         // only shows itself, vanishes when looked at
    public int seen;
    public long appeaseUntil;
    private boolean leaving;
    public boolean climbing;
    private double climbStartY;
    public final Map<UUID, Integer> asks = new HashMap<>();

    // perception memory
    public Vec3 lkp;
    public long lkpAt = -100000, lastDetect = -100000, huntStart;
    public boolean wasChasing;
    public String sawHide;
    public long sawHideAt = -100000;
    @Nullable public UUID targetId;

    // movement bookkeeping
    private long nextRepath, lastAttack, nextStep;
    private double appliedSpeed = -1;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;
    private float faceYaw;

    // sub systems
    public final GhostBreach breach = new GhostBreach(this);
    public final GhostVisit visit = new GhostVisit(this);
    public Search search;

    public GhostBrain(GhostEntity g) {
        this.g = g;
        this.kind = g.kind;
    }

    // ------------------------------------------------------------------ helpers
    public ServerLevel level() {
        return (ServerLevel) g.level();
    }

    public long now() {
        return g.level().getGameTime();
    }

    public static boolean targetable(Player p) {
        if (!(p instanceof ServerPlayer sp) || !p.isAlive() || p.isSpectator()) return false;
        GameType gt = sp.gameMode.getGameModeForPlayer();
        return gt != GameType.CREATIVE && gt != GameType.SPECTATOR;
    }

    public static List<GhostEntity> ghostsNear(ServerLevel level, Vec3 at, double r) {
        return level.getEntitiesOfClass(GhostEntity.class, new AABB(at, at).inflate(r), e -> e.isAlive() && e.position().distanceTo(at) <= r);
    }

    public boolean isLeaving() {
        return leaving || mode == Mode.LEAVE;
    }

    public double durMult() {
        return (buff == null ? 1 : buff.dur()) * kind.dur * (mod == null ? 1 : mod.dur());
    }

    public double visMult() {
        return (buff == null ? 1 : buff.vis()) * kind.vis * (mod == null ? 1 : mod.vis());
    }

    public double spdBuff() {
        return (buff == null ? 1 : buff.spd()) * kind.spd * (mod == null ? 1 : mod.spd());
    }

    public float faceYaw() {
        return search != null ? search.faceYaw : g.getYRot();
    }

    public int power() {
        int p = Math.max(1, power);
        if (g.isRaged()) p++;
        if (Atmosphere.bloodMoonFor(g.level())) p++;
        return Math.min(3, p);
    }

    long timer(String k) {
        return timers.getOrDefault(k, 0L);
    }

    void setTimer(String k, long v) {
        timers.put(k, v);
    }

    /** Face a point without moving. */
    public void face(Vec3 target) {
        double dx = target.x - g.getX(), dz = target.z - g.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        g.setYRot(yaw);
        g.setYHeadRot(yaw);
        g.yBodyRot = yaw;
        faceYaw = yaw;
        if (search != null) search.faceYaw = yaw;
        g.getLookControl().setLookAt(target.x, target.y, target.z, 60, 60);
    }

    public void teleportFacing(Vec3 pos, Vec3 lookAt) {
        g.getNavigation().stop();
        g.moveTo(pos.x, pos.y, pos.z, g.getYRot(), 0);
        face(lookAt);
    }

    private void init() {
        long now = now();
        born = now;
        mod = Variety.rollMod();
        stalkFor = R.rand(14 * 20, 30 * 20) * mod.stalk() * kind.stalk;
        buff = Curses.takeBuff(level());
        if (buff != null) stalkFor *= buff.dur();
        power = Director.forcedPower > 0 ? Director.forcedPower : GhostBreach.rollPower(buff != null && R.chance(0.5));
        nextCreep = now + (long) R.rand(80, 160);
        if (mode == Mode.SPAWNING) g.setAnim(GhostAnim.SPAWN); // a kill that began on the spawn tick keeps its grab animation
    }

    // ------------------------------------------------------------------ main tick (server)
    public void tick() {
        if (born < 0) init();
        long now = now();
        if (mode == Mode.KILL) {
            KillSequence.tickGhost(this);
            return;
        }
        if (leaving) {
            tickFarewell(now);
            return;
        }
        climbTick();
        footsteps(now);
        if (mode == Mode.BREACH) {
            breach.tick(now);
            if (now % 5 == 0) chaseFx(now);
            return;
        }
        if (mode == Mode.KNOCK || mode == Mode.WATCH) {
            visit.tick(now);
            return;
        }
        if (mode == Mode.SEARCH) {
            Search.tick(this, now);
            return;
        }
        if (now % 5 != 0 && mode != Mode.CHASE) return;

        // ---- who is around (hidden players are found by searching)
        ServerPlayer best = null, hid = null;
        double bestD = Double.MAX_VALUE, hidD = Double.MAX_VALUE;
        for (ServerPlayer p : level().players()) {
            if (!targetable(p)) continue;
            double d = p.position().distanceTo(g.position());
            if (Hiding.isHidden(p)) {
                if (d < hidD) {
                    hidD = d;
                    hid = p;
                }
                continue;
            }
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if ((best == null && hid == null) || Math.min(bestD, hidD) > FAR_REMOVE * visMult()) {
            vanish();
            return;
        }
        long age = now - born;
        if (g.isRaged() && R.chance(0.5)) Fx.puff(g.level(), ParticleTypes.ANGRY_VILLAGER, g.position(), 0.8);
        if (age < 60) {
            if (best != null) face(best.getEyePosition());
            return;
        }
        // ---- everyone here is hidden: it has to look for them
        if (best == null) {
            if (glimpse) {
                vanish();
                return;
            }
            if (appeaseUntil > now) return;
            if (mode == Mode.SNAP) return;
            Search.start(this, now, null, hid.position(), null, null);
            return;
        }
        targetId = best.getUUID();
        // ---- bribed / asked for an item: stands still and stares, then hunts again
        if (appeaseUntil > 0) {
            if (now < appeaseUntil) {
                g.getNavigation().stop();
                face(best.getEyePosition());
                return;
            }
            appeaseUntil = 0;
            stalking = false;
            chaseStart = 0;
            lostAt = 0;
            startChase(best, now);
            Jumpscares.scare(best, kind.n, 1);
            return;
        }
        // ---- a glimpse: stands still; looked at long enough -> jumpscare and gone
        if (glimpse) {
            g.getNavigation().stop();
            face(best.getEyePosition());
            if (W.lookDot(best, g.position()) > 0.75 && bestD < 45) seen++;
            if (seen >= 2) {
                Jumpscares.scare(best, kind.n, 0);
                vanish();
            } else if (age > 70 + 25 * 20 * durMult()) vanish();
            return;
        }
        switch (mode) {
            case SPAWNING -> mode = Mode.STALK;
            case STALK -> tickStalk(best, bestD, now, age);
            case SNAP -> {
                face(best.getEyePosition());
                if (now >= timer("snapEnd")) startChase(best, now);
            }
            case CHASE -> tickChase(best, bestD, now);
            case LOST -> {
                if (lostAt == 0) lostAt = now;
                g.setAnim(GhostAnim.STALK);
                if (now - lostAt > 400 * durMult()) vanish();
                else if (bestD < 20 && Perception.perceive(g, this, now, false) != null) startChase(best, now);
            }
            default -> {}
        }
        if (mode == Mode.STALK && stalking) Personas.tick(this, best, bestD, now, false);
        Personas.gimmick(this, best, bestD, now, mode == Mode.CHASE);
    }

    // ------------------------------------------------------------------ stalking
    private void tickStalk(ServerPlayer best, double bestD, long now, long age) {
        if (!stalking) {
            mode = Mode.LOST;
            return;
        }
        g.getNavigation().stop();
        g.setAnim(GhostAnim.STALK);
        face(best.getEyePosition());
        boolean done = age > 70 + stalkFor;
        if (now >= nextBeat) {
            Fx.playAt(best, "mob.warden.heartbeat", best.getX(), best.getY(), best.getZ(), (float) Math.max(0.4, 1.6 - bestD / 25), 1f);
            nextBeat = now + (long) Math.max(18, Math.min(70, bestD * 2.2));
        }
        if (bestD <= 26) Fx.effect(best, MobEffects.DARKNESS, 50, 0);
        if (bestD <= 28 && R.chance(0.12)) {
            Vec3 v = best.getViewVector(1f);
            double vl = Math.max(1e-6, Math.hypot(v.x, v.z)), dd = R.rand(3, 6);
            Fx.playAt(best, R.chance(0.5) ? "step.stone" : "step.gravel", best.getX() - v.x / vl * dd, best.getY(), best.getZ() - v.z / vl * dd, 1.2f, R.pitch(0.5, 0.8));
        }
        if (bestD <= 24 && R.chance(0.03)) Director.whisper(best);
        if (bestD <= 34 && R.chance(0.05)) Fx.playAt(best, "mob.ghast.moan", g.getX(), g.getY(), g.getZ(), 1.6f, R.pitch(0.45, 0.7));
        // creeps closer only while you are not looking
        if (now >= nextCreep && bestD > AMBUSH_DIST + 3) {
            nextCreep = now + (long) R.rand(kind.creepMin, kind.creepMax);
            if (W.lookDot(best, g.position()) < 0.25) creepToward(best, R.rand(kind.creepStepMin, kind.creepStepMax));
        }
        if (bestD <= AMBUSH_DIST * visMult() || (done && bestD <= CHASE_RANGE * visMult())) snap(best);
        else if (done) vanish();
    }

    void creepToward(ServerPlayer p, double step) {
        double dx = p.getX() - g.getX(), dz = p.getZ() - g.getZ(), len = Math.max(1e-6, Math.hypot(dx, dz));
        int x = Mth.floor(g.getX() + dx / len * step), z = Mth.floor(g.getZ() + dz / len * step);
        int gy = Mth.floor(g.getY());
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            BlockPos c = new BlockPos(x, gy + dy, z);
            if (W.passable(g.level(), c) && W.passable(g.level(), c.above()) && W.passable(g.level(), c.above(2)) && !W.passable(g.level(), c.below())) {
                teleportFacing(new Vec3(x + 0.5, c.getY(), z + 0.5), p.getEyePosition());
                return;
            }
        }
    }

    /** Neck snaps toward the player: jumpscare, then the chase begins. */
    public void snap(ServerPlayer p) {
        stalking = false;
        mode = Mode.SNAP;
        g.getNavigation().stop();
        g.playAnim(GhostAnim.SNAP);
        setTimer("snapEnd", now() + 34);
        Jumpscares.scare(p, kind.n, 1);
        Fx.effect(p, MobEffects.CONFUSION, 100, 0);
        Fx.sound(g.level(), "ghost.snap", g.position(), 2f, 0.8f, 48);
    }

    // ------------------------------------------------------------------ chase
    public void startChase(ServerPlayer p, long now) {
        mode = Mode.CHASE;
        stalking = false;
        search = null;
        targetId = p.getUUID();
        if (chaseStart == 0) chaseStart = now;
        if (huntStart == 0) huntStart = now;
        lastDetect = now;
        lkp = p.position();
        lkpAt = now;
        wasChasing = true;
        nextRepath = 0;
        updateStance(p);
        g.setAnim(g.isCrawling() ? GhostAnim.CRAWL_CHASE : GhostAnim.CHASE);
    }

    private void tickChase(ServerPlayer best, double bestD, long now) {
        // must still see or hear someone
        Perception.Sense sense = Perception.perceive(g, this, now, false);
        if (sense == null && now - lastDetect > LOSE_TICKS) {
            for (ServerPlayer p : level().players()) {
                if (p.position().distanceTo(g.position()) < 40 && !Hiding.isHidden(p)) {
                    Fx.actionbar(p, Component.translatable("hollowhours.chase.lost." + R.randInt(0, 2)));
                }
            }
            Search.start(this, now, lkp, null, null, null);
            return;
        }
        ServerPlayer target = sense != null ? sense.player() : best;
        double d = target.position().distanceTo(g.position());
        if (d > LOSE_RANGE * visMult()) {
            if (g.isCrawling()) {
                vanish();
                return;
            }
            mode = Mode.LOST;
            lostAt = now;
            g.getNavigation().stop();
            return;
        }
        if (now - chaseStart > HHConfig.CHASE_MAX_SECONDS.get() * 20L * durMult()) {
            vanish();
            return;
        }
        if (now % 5 == 0) {
            Personas.tick(this, target, d, now, true);
            updateStance(target);
            chaseFx(now);
        }
        applySpeed();
        // ---- move
        if (now >= nextRepath || g.getNavigation().isDone()) {
            nextRepath = now + (d < 12 ? 5 : 12);
            Path path = g.getNavigation().createPath(target, 0);
            g.getNavigation().moveTo(path, 1.0);
            if (path == null || !path.canReach()) stuckTicks += 8;
        }
        g.getLookControl().setLookAt(target, 30, 30);
        g.setAnim(g.isCrawling() ? GhostAnim.CRAWL_CHASE : GhostAnim.CHASE);
        // ---- reach and hit
        double reach = 1.6 + g.getBbWidth() / 2 + (g.isCrawling() ? 0.3 : kind.height * 0.12);
        if (d <= reach && Math.abs(target.getY() - g.getY()) < 2.6 && now - lastAttack >= 20) {
            lastAttack = now;
            meleeHit(target);
            if (mode != Mode.CHASE) return;
        }
        // ---- stuck? (closed house, pillar, hole): plan a way in
        double moved = g.position().distanceTo(lastPos);
        lastPos = g.position();
        if (moved < 0.05 && d > 3) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 2);
        if (stuckTicks > 40 && HHConfig.BREAK_DOORS.get()) {
            stuckTicks = 0;
            breach.onStuck(target, now);
        }
    }

    /** Stand when there is room above, crawl (smaller, fits through doors) when there is not or the target is indoors. */
    public void updateStance(@Nullable ServerPlayer target) {
        if (kind.height <= 2.05) {
            g.setCrawling(false);
            return;
        }
        boolean roomAbove = canStand();
        boolean wantLow = target != null && (Hiding.isHidden(target) || W.isSheltered(target) || W.isUnderground(target.level(), target.position()))
                && target.position().distanceTo(g.position()) < 24;
        g.setCrawling(!roomAbove || wantLow);
    }

    public boolean canStand() {
        float w = kind.width, h = kind.height;
        AABB box = new AABB(g.getX() - w / 2, g.getY() + 0.05, g.getZ() - w / 2, g.getX() + w / 2, g.getY() + h, g.getZ() + w / 2);
        return g.level().noCollision(g, box);
    }

    public void applySpeed() {
        double base = 0.30 * HHConfig.SPEED_MULT.get() * spdBuff() * spdMul;
        if (g.isRaged()) base *= HHConfig.RAGE_SPEED.get();
        if (Atmosphere.bloodMoonFor(g.level())) base *= HHConfig.BLOOD_SPEED_MULT.get();
        if (g.isCrawling()) base *= 0.82;
        if (Math.abs(base - appliedSpeed) > 1e-4) {
            appliedSpeed = base;
            var attr = g.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) attr.setBaseValue(base);
        }
    }

    void chaseFx(long now) {
        ServerPlayer best = target();
        if (best == null) return;
        double d = best.position().distanceTo(g.position());
        if (R.chance(0.35)) Fx.playAt(best, "mob.warden.heartbeat", best.getX(), best.getY(), best.getZ(), 1.8f, 1.3f);
        if (R.chance(0.06)) Fx.playAt(best, "ghost.growl", g.getX(), g.getY(), g.getZ(), 1.5f, R.pitch(0.6, 1.1));
        if (R.chance(0.05)) Fx.playAt(best, "mob.ghast.scream", g.getX(), g.getY(), g.getZ(), 1.6f, R.pitch(0.4, 0.7));
        if (R.chance(0.03)) Director.whisper(best);
        if (d <= 6) Fx.effect(best, MobEffects.CONFUSION, 80, 0);
        if (d <= 18 && R.chance(0.05)) Fx.effect(best, MobEffects.BLINDNESS, 12, 0);
        if (d <= 8 && R.chance(0.25)) Fx.shake(best, 0.25f, 8);
        if (d <= 30) Fx.effect(best, MobEffects.DARKNESS, 60, 0);
    }

    private void footsteps(long now) {
        if (now < nextStep) return;
        double sp = g.getDeltaMovement().horizontalDistance();
        if (sp < 0.03 || mode == Mode.KILL) return;
        nextStep = now + (g.isCrawling() ? 9 : mode == Mode.SEARCH ? 11 : 7);
        Fx.sound(g.level(), g.isCrawling() || climbing ? "mob.spider.step" : "mob.zombie.step", g.position(),
                mode == Mode.SEARCH ? 0.7f : 1.1f, R.pitch(0.45, 0.65), mode == Mode.SEARCH ? 20 : 28);
    }

    /** Spider-like climbing up walls, limited to how high this ghost can climb. */
    private void climbTick() {
        boolean want = g.horizontalCollision && (mode == Mode.CHASE || mode == Mode.SEARCH) && !g.isInWater();
        if (want && !climbing) {
            climbing = true;
            climbStartY = g.getY();
        }
        if (climbing && (!g.horizontalCollision || g.getY() - climbStartY > kind.climbUp + 0.5)) climbing = false;
        if (climbing && g.tickCount % 10 == 0) Fx.sound(g.level(), "ghost.scratch", g.position(), 0.8f, R.pitch(0.7, 0.9), 20);
    }

    @Nullable
    public ServerPlayer target() {
        if (targetId == null) return null;
        Player p = g.level().getPlayerByUUID(targetId);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    // ------------------------------------------------------------------ attacking
    public boolean meleeHit(Entity t) {
        if (!(t instanceof ServerPlayer p) || !targetable(p) || KillSequence.isVictim(p)) return false;
        float dmg = g.attackDamage();
        if (buff != null && buff.dmg() > 0) dmg *= (float) (1 + buff.dmg());
        dmg *= (float) Soul.damageMult(p);
        g.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        boolean onCatch = HHConfig.KILL_MODE.get() == HHConfig.KillMode.ON_CATCH;
        float after = CombatRules.getDamageAfterAbsorb(dmg, (float) p.getArmorValue(), (float) p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        boolean lethal = after >= p.getHealth() + p.getAbsorptionAmount();
        if ((onCatch || lethal) && KillSequence.begin(this, p)) return true;
        DamageSource src = GhostDamage.hit(g);
        boolean ok = p.hurt(src, dmg);
        if (ok) {
            Fx.sound(g.level(), "ghost.kill.punch", p.position(), 1.2f, R.pitch(0.8, 1.0), 20);
            HHNetFx.hitFlash(p);
        }
        return ok;
    }

    public void onHitBy(ServerPlayer p) {
        // hitting a ghost only makes it angrier at you
        if (mode == Mode.STALK && stalking && !glimpse && !scareOnly) snap(p);
        if (R.chance(0.15)) Fx.sound(g.level(), "ghost.growl", g.position(), 1.6f, 0.5f, 24);
    }

    // ------------------------------------------------------------------ alerts from hiding players (noise, gasp ...)
    public boolean canBeAlerted() {
        if (scareOnly || glimpse || leaving || mode == Mode.KILL) return false;
        if (appeaseUntil > now()) return false;
        if (mode == Mode.SEARCH) return true;
        if (mode == Mode.SNAP || mode == Mode.KNOCK || mode == Mode.WATCH || mode == Mode.BREACH) return false;
        return mode == Mode.CHASE || mode == Mode.STALK || mode == Mode.LOST;
    }

    public void alert(String key, String level, Vec3 pos, long now) {
        if (mode == Mode.SEARCH && search != null) {
            search.alert = new Search.Alert(key, level, now);
            return;
        }
        if (mode == Mode.STALK && !stalking) return;
        Search.start(this, now, pos, null, key, level);
    }

    // ------------------------------------------------------------------ leaving
    public void vanish() {
        if (!g.isAlive()) return;
        Fx.sound(g.level(), "mob.warden.nearby_close", g.position(), 1f, 0.5f, 32);
        for (int i = 0; i < 8; i++) Fx.puff(g.level(), ParticleTypes.LARGE_SMOKE, g.position(), 0.6);
        g.ejectPassengers();
        g.discard();
    }

    /** Negotiation worked: it sinks into the ground in smoke. */
    public void farewell(ServerPlayer p) {
        leaving = true;
        mode = Mode.LEAVE;
        g.getNavigation().stop();
        g.playAnim(GhostAnim.FAREWELL);
        g.noPhysics = true;
        setTimer("leave0", now());
        face(p.getEyePosition());
        Fx.playAt(p, "mob.ghast.moan", g.getX(), g.getY(), g.getZ(), 1.2f, 0.5f);
    }

    private void tickFarewell(long now) {
        long t = now - timer("leave0");
        g.setDeltaMovement(0, -0.09, 0);
        g.setPos(g.getX(), g.getY() - 0.09, g.getZ());
        if (t % 2 == 0) {
            Fx.puff(g.level(), ParticleTypes.SMOKE, g.position(), 0.7);
            Fx.puff(g.level(), ParticleTypes.TOTEM_OF_UNDYING, g.position(), 0.6);
        }
        if (t == 18) Fx.sound(g.level(), "ghost.creak", g.position(), 1.2f, 0.6f, 24);
        if (t >= 50) {
            Fx.sound(g.level(), "mob.warden.nearby_close", g.position(), 1f, 0.5f, 32);
            g.discard();
        }
    }

    /** Jumpscare packet + the layered sounds (port of main.js jumpscare()). */
    public static final class HHNetFx {
        static void hitFlash(ServerPlayer p) {
            HHNet.toPlayer(p, com.hollowhours.net.S2CScreenFx.blood(0.6f, 14));
            Fx.shake(p, 0.5f, 8);
        }
    }

    public List<ServerPlayer> playersNear(double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level().players()) if (p.position().distanceTo(g.position()) <= r) out.add(p);
        return out;
    }

    /** Used by the guide (doing...) and talk topics. */
    public String doingKey() {
        if (mode == Mode.SEARCH) return "searching";
        if (mode == Mode.CHASE) return "chasing";
        if (mode == Mode.BREACH) return breach.squeezing() ? "squeezing" : "breaking";
        if (mode == Mode.KNOCK) return "knocking";
        if (mode == Mode.KILL) return "killing";
        return "watching";
    }

    static void sendJumpscare(ServerPlayer p, int who, int strength, int variant) {
        HHNet.toPlayer(p, new S2CJumpscare(who, strength, variant));
    }
}
