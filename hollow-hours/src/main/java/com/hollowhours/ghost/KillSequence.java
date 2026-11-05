package com.hollowhours.ghost;

import com.hollowhours.HollowHours;
import com.hollowhours.config.HHConfig;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CBetrayal;
import com.hollowhours.net.S2CKill;
import com.hollowhours.net.S2CScreenFx;
import com.hollowhours.net.S2CSpeech;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * When a ghost catches you (new in the Java edition):
 *
 * 1. GRAB   - it seizes you and lifts you to its face.
 * 2. SCREAM - full-screen close-up: it screams / roars into your face.
 * 3. (25%)  - "Tell me where your friend is... and I might spare you." Pick a friend to sell, or refuse.
 *             You have 10 seconds. Refuse or run out of time = it kills you right away.
 *             Sell = it lets you go and hunts your friend instead.
 * 4. KILL   - one of this ghost's own kill animations (3+ per ghost, random), beaten until dead.
 */
public final class KillSequence {
    enum Phase { GRAB, STRUGGLE, SCREAM, HOLD, KILL, DONE }

    static final class Session {
        final GhostBrain b;
        final UUID victim;
        Phase phase = Phase.GRAB;
        long phaseStart;
        KillTracks.Track track;
        KillTracks.Track killTrack;
        int nextEvent;
        long betrayEnd;
        List<UUID> offered = new ArrayList<>();
        boolean released;
        boolean prone;
        long lastTick;
        // struggling free
        float prog;
        long strEnd, lastPress;
        boolean struggled;

        Session(GhostBrain b, UUID victim) {
            this.b = b;
            this.victim = victim;
        }
    }

    private static final Map<UUID, Session> BY_VICTIM = new HashMap<>();
    /** CI screenshots: the next kill uses this kill track (index into killsFor), -1 = random */
    public static int forceKill = -1;
    /** CI screenshots: the next grab skips the struggle (straight to the kill) */
    public static boolean noStruggleOnce;

    private KillSequence() {}

    public static boolean isVictim(ServerPlayer p) {
        return BY_VICTIM.containsKey(p.getUUID());
    }

    @Nullable
    private static Session sessionOf(GhostBrain b) {
        for (Session s : BY_VICTIM.values()) if (s.b == b) return s;
        return null;
    }

    // ------------------------------------------------------------------ start
    public static boolean begin(GhostBrain b, ServerPlayer p) {
        if (isVictim(p) || sessionOf(b) != null || !p.isAlive() || b.isLeaving() || com.hollowhours.combat.GhostCombat.knockedOut(b)) return false;
        GhostEntity g = b.g;
        KillTracks.Track grab = KillTracks.phase("grab", b.kind.n);
        List<KillTracks.Track> kills = KillTracks.killsFor(b.kind.n);
        if (grab == null || kills.isEmpty()) return false;
        if (Hiding.isHidden(p)) Hiding.exit(p, true, true, g.eye());
        p.closeContainer();
        p.stopUsingItem();
        if (p.isPassenger()) p.stopRiding();
        if (p.isSleeping()) p.stopSleeping();

        Session s = new Session(b, p.getUUID());
        s.killTrack = kills.get(forceKill >= 0 ? forceKill % kills.size() : R.randInt(0, kills.size() - 1));
        forceKill = -1;
        s.struggled = noStruggleOnce;
        noStruggleOnce = false;
        BY_VICTIM.put(p.getUUID(), s);
        b.mode = GhostBrain.Mode.KILL;
        b.search = null;
        b.breach.reset();
        g.getNavigation().stop();
        g.setDeltaMovement(Vec3.ZERO);
        b.face(p.position());
        g.setYRot(g.getYHeadRot());
        // pull the victim right in front of it
        if (!p.startRiding(g, true)) {
            BY_VICTIM.remove(p.getUUID());
            b.mode = GhostBrain.Mode.CHASE;
            return false;
        }
        setPhase(s, Phase.GRAB, grab, p);
        Fx.sound(g.level(), "ghost.kill.grab", p.position(), 2f, R.pitch(0.9, 1.05), 32);
        Fx.sound(g.level(), "ghost.growl", g.position(), 2.4f, 0.6f, 48);
        Director.stopMusicFor(p);
        return true;
    }

    private static void setPhase(Session s, Phase ph, KillTracks.Track tr, ServerPlayer p) {
        GhostEntity g = s.b.g;
        s.phase = ph;
        s.track = tr;
        s.nextEvent = 0;
        s.phaseStart = g.level().getGameTime();
        g.setTrack(tr.index);
        g.setAnim(switch (ph) {
            case GRAB -> GhostAnim.GRAB;
            case SCREAM -> GhostAnim.SCREAM;
            case HOLD, STRUGGLE -> GhostAnim.HOLD;
            default -> GhostAnim.KILL;
        });
        int code = switch (ph) {
            case GRAB -> S2CKill.GRAB;
            case SCREAM -> S2CKill.SCREAM;
            case HOLD, STRUGGLE -> S2CKill.HOLD;
            default -> S2CKill.KILL;
        };
        HHNet.toPlayer(p, new S2CKill(g.getId(), s.b.kind.n, code, tr.index, tr.ticks()));
    }

    // ------------------------------------------------------------------ tick (from the ghost's brain)
    static void tickGhost(GhostBrain b) {
        Session s = sessionOf(b);
        GhostEntity g = b.g;
        if (s == null) {
            b.mode = GhostBrain.Mode.CHASE;
            g.setTrack(-1);
            return;
        }
        long now = g.level().getGameTime();
        if (s.lastTick == now) return;
        s.lastTick = now;
        ServerPlayer p = victim(g.getServer(), s);
        if (p == null || !p.isAlive() || p.level() != g.level()) {
            finish(s, p, false);
            return;
        }
        if (p.getVehicle() != g && s.phase != Phase.DONE) p.startRiding(g, true);
        g.setDeltaMovement(0, Math.min(0, g.getDeltaMovement().y), 0);
        g.getNavigation().stop();
        float t = (now - s.phaseStart) / 20f;
        boolean pr = s.track != null && s.track.proneAt(t);
        if (pr != s.prone) {
            s.prone = pr;
            p.setForcedPose(pr ? net.minecraft.world.entity.Pose.SWIMMING : null);
        }
        fireEvents(s, p, t);
        switch (s.phase) {
            case GRAB -> {
                if (t >= s.track.length && !s.struggled && HHConfig.STRUGGLE.get()) {
                    startStruggle(s, p);
                } else if (t >= s.track.length) {
                    KillTracks.Track scream = KillTracks.phase("scream", b.kind.n);
                    if (scream != null) {
                        setPhase(s, Phase.SCREAM, scream, p); // the scream itself is an event in the track
                    } else startKill(s, p);
                }
            }
            case STRUGGLE -> tickStruggle(s, p, now);
            case SCREAM -> {
                if (t >= s.track.length) {
                    if (!tryBetrayal(s, p)) startKill(s, p);
                }
            }
            case HOLD -> {
                long left = s.betrayEnd - now;
                if (left % 20 == 0 && left > 0) Fx.playSelf(p, "ghost.betray.tick", 1f, left <= 60 ? 1.3f : 1f);
                if (left > 0 && R.chance(0.02)) Fx.sound(g.level(), "ghost.growl", g.eye(), 1.2f, 0.6f, 24);
                if (now >= s.betrayEnd) {
                    HHNet.toPlayer(p, S2CBetrayal.close());
                    speak(p, b.kind, "hollowhours.betray.timeout", 1);
                    startKill(s, p);
                }
            }
            case KILL -> {
                if (t >= s.track.length + 0.3f) killNow(s, p);
            }
            case DONE -> {}
        }
    }

    private static void fireEvents(Session s, ServerPlayer p, float t) {
        List<KillTracks.Event> evs = s.track.events;
        while (s.nextEvent < evs.size() && evs.get(s.nextEvent).t() <= t) {
            KillTracks.Event e = evs.get(s.nextEvent++);
            GhostEntity g = s.b.g;
            switch (e.type()) {
                case "sound" -> Fx.sound(g.level(), e.arg(), p.position().add(0, 1, 0), 1.6f * e.strength(), R.pitch(0.92, 1.06), 40);
                case "hit" -> hurt(p, g, e.strength());
                case "final" -> killNow(s, p);
                default -> {} // screen effects are run by the client from the same track
            }
        }
    }

    /** A blow that hurts but never kills on its own (the final event does that). */
    private static void hurt(ServerPlayer p, GhostEntity g, float strength) {
        float amount = Math.min(p.getHealth() - 1f, 2f + 3f * strength);
        if (amount > 0.1f) {
            p.invulnerableTime = 0;
            p.hurt(GhostDamage.hit(g), amount);
        }
        Vec3 c = p.position().add(0, 1.2, 0);
        ((ServerLevel) g.level()).sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()), c.x, c.y, c.z, (int) (10 + 14 * strength), 0.25, 0.35, 0.25, 0.2);
    }

    // ------------------------------------------------------------------ struggling free
    static final float[] PULL = {0, 1.0f, 0.9f, 1.3f, 1.1f, 1.2f};

    private static void startStruggle(Session s, ServerPlayer p) {
        s.struggled = true;
        KillTracks.Track hold = KillTracks.phase("hold", s.b.kind.n);
        if (hold == null) hold = s.track;
        setPhase(s, Phase.STRUGGLE, hold, p);
        s.prog = 0.22f;
        long now = s.b.g.level().getGameTime();
        s.strEnd = now + Math.round(HHConfig.STRUGGLE_SECONDS.get() * 20);
        HHNet.toPlayer(p, new com.hollowhours.net.S2CCombat(com.hollowhours.net.S2CCombat.STRUGGLE, s.prog, s.strEnd - now, 0));
        Fx.sound(s.b.g.level(), "ghost.growl", s.b.g.eye(), 2f, 0.7f, 32);
    }

    /** One press of the struggle key (from the client). */
    public static void struggle(ServerPlayer p) {
        Session s = BY_VICTIM.get(p.getUUID());
        if (s == null || s.phase != Phase.STRUGGLE) return;
        long now = p.level().getGameTime();
        if (now == s.lastPress) return;
        s.lastPress = now;
        float inc = 0.055f * (1 + 0.9f * com.hollowhours.combat.Fitness.strength(p) / 100f + com.hollowhours.combat.MilitaryArmor.struggle(p)
                + 0.03f * com.hollowhours.combat.Fitness.meleeLevel(p));
        s.prog = Math.min(1f, s.prog + inc);
    }

    private static void tickStruggle(Session s, ServerPlayer p, long now) {
        GhostBrain b = s.b;
        float pull = 0.0068f * PULL[b.kind.n] * (Atmosphere.bloodMoonFor(b.g.level()) ? 1.3f : 1f)
                * (float) Math.min(2.0, Math.sqrt(com.hollowhours.rescue.Rescue.harshness(p, Atmosphere.bloodMoonFor(b.g.level()))));
        s.prog -= pull;
        if (now % 2 == 0) HHNet.toPlayer(p, new com.hollowhours.net.S2CCombat(com.hollowhours.net.S2CCombat.STRUGGLE, s.prog, s.strEnd - now, 0));
        if (s.prog >= 1f) {
            HHNet.toPlayer(p, new com.hollowhours.net.S2CCombat(com.hollowhours.net.S2CCombat.STRUGGLE, -1, 0, 1));
            com.hollowhours.combat.Fitness.addStrength(p, 0.6f);
            for (ServerPlayer o : b.level().players())
                if (o.position().distanceTo(b.g.position()) < 32) GhostTalk.say(o, b.g, "freed", 1);
            breakFree(s, p, 50);
            return;
        }
        if (s.prog <= 0 || now >= s.strEnd) {
            HHNet.toPlayer(p, new com.hollowhours.net.S2CCombat(com.hollowhours.net.S2CCombat.STRUGGLE, -1, 0, 2));
            KillTracks.Track scream = KillTracks.phase("scream", b.kind.n);
            if (scream != null) setPhase(s, Phase.SCREAM, scream, p);
            else startKill(s, p);
        }
    }

    /** The victim gets away: thrown clear, a moment of safety, the ghost reeling. */
    private static void breakFree(Session s, ServerPlayer p, int stagger) {
        GhostEntity g = s.b.g;
        finish(s, p, false);
        p.stopRiding();
        Vec3 away = p.position().subtract(g.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-3) away = g.getLookAngle().multiply(1, 0, 1);
        away = away.normalize();
        Vec3 spot = g.position().add(away.scale(1.6));
        p.teleportTo(spot.x, Math.max(p.getY(), g.getY()), spot.z);
        p.setDeltaMovement(away.x * 0.9, 0.42, away.z * 0.9);
        p.hurtMarked = true;
        p.invulnerableTime = 40;
        s.b.timers.put("stagger", s.b.now() + stagger);
        com.hollowhours.combat.Combat.grantDodge(p, 40);
        Fx.sound(g.level(), "ghost.growl", g.eye(), 2f, 0.8f, 32);
    }

    /** Somebody knocked out the ghost that holds a victim: they drop free. */
    public static void rescueFrom(GhostBrain b) {
        Session s = sessionOf(b);
        if (s == null || s.phase == Phase.DONE) return;
        ServerPlayer p = victim(b.g.getServer(), s);
        if (p == null) {
            finish(s, null, false);
            return;
        }
        HHNet.toPlayer(p, new com.hollowhours.net.S2CCombat(com.hollowhours.net.S2CCombat.STRUGGLE, -1, 0, 1));
        HHNet.toPlayer(p, S2CBetrayal.close());
        breakFree(s, p, 0);
    }

    // ------------------------------------------------------------------ betrayal
    private static boolean tryBetrayal(Session s, ServerPlayer p) {
        if (!R.chance(HHConfig.BETRAYAL_CHANCE.get())) return false;
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o == p || !o.isAlive() || o.isSpectator() || isVictim(o)) continue;
            if (!HHConfig.BETRAYAL_ACROSS_DIMENSIONS.get() && o.level() != p.level()) continue;
            ids.add(o.getUUID());
            names.add(o.getGameProfile().getName());
        }
        if (ids.isEmpty()) return false;
        KillTracks.Track hold = KillTracks.phase("hold", s.b.kind.n);
        if (hold == null) return false;
        s.offered = ids;
        setPhase(s, Phase.HOLD, hold, p);
        int sec = HHConfig.BETRAYAL_SECONDS.get();
        s.betrayEnd = s.b.g.level().getGameTime() + sec * 20L;
        HHNet.toPlayer(p, new S2CBetrayal(true, s.b.kind.n, sec, ids, names));
        speak(p, s.b.kind, "hollowhours.betray.offer." + s.b.kind.n, 3);
        Fx.playSelf(p, "ghost.betray.whisper", 1.6f, 0.9f);
        return true;
    }

    public static void onBetrayChoice(ServerPlayer p, @Nullable UUID friend) {
        Session s = BY_VICTIM.get(p.getUUID());
        if (s == null || s.phase != Phase.HOLD) return;
        HHNet.toPlayer(p, S2CBetrayal.close());
        if (friend == null || !s.offered.contains(friend)) {
            speak(p, s.b.kind, "hollowhours.betray.refused." + R.randInt(0, 2), 1);
            Fx.sound(p.level(), "ghost.growl", s.b.g.eye(), 2.5f, 0.5f, 40);
            startKill(s, p);
            return;
        }
        MinecraftServer server = p.getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayer(friend);
        if (target == null || !target.isAlive()) {
            speak(p, s.b.kind, "hollowhours.betray.gone", 1);
            startKill(s, p);
            return;
        }
        // "...I might spare you": usually it keeps its word
        if (R.chance(HHConfig.BETRAYAL_LIE_CHANCE.get())) {
            speak(p, s.b.kind, "hollowhours.betray.lied", 1);
            Fx.playSelf(p, "ghost.betray.laugh", 2f, 0.8f);
            startKill(s, p);
            huntFriend(s.b.kind, target, p);
            return;
        }
        speak(p, s.b.kind, "hollowhours.betray.sold", 2);
        Fx.playSelf(p, "ghost.betray.laugh", 2f, 0.9f);
        release(s, p);
        huntFriend(s.b.kind, target, p);
    }

    /** The ghost goes after the friend who was sold out: appears behind them and charges. */
    private static void huntFriend(GhostKind kind, ServerPlayer friend, ServerPlayer seller) {
        Fx.title(friend, Component.translatable("hollowhours.betray.victim.title"),
                Component.translatable("hollowhours.betray.victim.sub", seller.getDisplayName()), 0, 50, 20);
        Fx.playSelf(friend, "ghost.betray.sold", 2f, 1f);
        Later.run(50, () -> {
            if (!friend.isAlive()) return;
            ServerLevel level = friend.serverLevel();
            Vec3 spot = Director.findSpot(friend, true, 8, 12);
            if (spot == null) spot = friend.position().add(friend.getViewVector(1f).scale(-6));
            GhostEntity g = HHEntities.ghost(kind).create(level);
            if (g == null) return;
            g.moveTo(spot.x, spot.y, spot.z, 0, 0);
            level.addFreshEntity(g);
            GhostBrain nb = g.brain();
            nb.born = level.getGameTime() - 200;
            nb.mod = Variety.rollMod();
            nb.power = GhostBreach.rollPower(true);
            nb.huntStart = level.getGameTime();
            nb.chaseStart = level.getGameTime();
            g.setRaged(true);
            nb.snap(friend);
        });
    }

    private static void release(Session s, ServerPlayer p) {
        p.setForcedPose(null);
        s.released = true;
        GhostEntity g = s.b.g;
        BY_VICTIM.remove(p.getUUID());
        s.phase = Phase.DONE;
        p.stopRiding();
        HHNet.toPlayer(p, new S2CKill(g.getId(), s.b.kind.n, S2CKill.RELEASE, -1, 0));
        Fx.effect(p, MobEffects.BLINDNESS, 40, 0);
        Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
        Fx.effect(p, MobEffects.DARKNESS, 200, 0);
        g.setTrack(-1);
        // the ghost leaves this player alone and vanishes (it is off to the friend)
        for (int i = 0; i < 10; i++) Fx.puff(g.level(), ParticleTypes.LARGE_SMOKE, g.position(), 0.8);
        s.b.vanish();
    }

    // ------------------------------------------------------------------ kill
    private static void startKill(Session s, ServerPlayer p) {
        setPhase(s, Phase.KILL, s.killTrack, p);
        Fx.sound(s.b.g.level(), "ghost.growl", s.b.g.eye(), 2.6f, 0.5f, 48);
    }

    private static void killNow(Session s, ServerPlayer p) {
        p.setForcedPose(null);
        if (s.phase == Phase.DONE) return;
        s.phase = Phase.DONE;
        GhostEntity g = s.b.g;
        BY_VICTIM.remove(p.getUUID());
        Fx.sound(g.level(), "ghost.kill.final", p.position(), 2.4f, 1f, 48);
        HHNet.toPlayer(p, new S2CKill(g.getId(), s.b.kind.n, S2CKill.END, -1, 0));
        p.stopRiding();
        p.invulnerableTime = 0;
        GhostEvents.markKilledByGhost(p, s.b.kind.n);
        p.hurt(GhostDamage.kill(g), Float.MAX_VALUE);
        g.setTrack(-1);
        if (p.isAlive()) {
            // a totem saved them: the ghost is gone, for now
            HHNet.toPlayer(p, S2CScreenFx.fade(0xFFFFFF, 1, 4, 20));
            s.b.vanish();
            return;
        }
        // satisfied... it laughs, then hunts whoever is left nearby or fades away
        g.playAnim(GhostAnim.LAUGH);
        Fx.sound(g.level(), "ghost.betray.laugh", g.eye(), 2f, 0.7f, 48);
        GhostBrain b = s.b;
        b.mode = GhostBrain.Mode.LOST;
        b.lostAt = g.level().getGameTime();
        b.chaseStart = 0;
        Later.run(50, () -> {
            if (!g.isAlive()) return;
            ServerPlayer next = null;
            double bd = 30;
            for (ServerPlayer o : b.level().players()) {
                if (!GhostBrain.targetable(o) || Hiding.isHidden(o)) continue;
                double d = o.position().distanceTo(g.position());
                if (d < bd) {
                    bd = d;
                    next = o;
                }
            }
            if (next != null) b.startChase(next, b.now());
            else b.vanish();
        });
    }

    private static void finish(Session s, @Nullable ServerPlayer p, boolean killed) {
        if (p != null) p.setForcedPose(null);
        s.phase = Phase.DONE;
        BY_VICTIM.remove(s.victim);
        GhostEntity g = s.b.g;
        g.setTrack(-1);
        g.ejectPassengers();
        if (p != null) {
            HHNet.toPlayer(p, new S2CKill(g.getId(), s.b.kind.n, S2CKill.END, -1, 0));
            HHNet.toPlayer(p, S2CBetrayal.close());
        }
        s.b.mode = GhostBrain.Mode.LOST;
        s.b.lostAt = g.level().getGameTime();
    }

    @Nullable
    private static ServerPlayer victim(@Nullable MinecraftServer server, Session s) {
        if (server == null) return null;
        return server.getPlayerList().getPlayer(s.victim);
    }

    private static void speak(ServerPlayer p, GhostKind kind, String key, int style) {
        HHNet.toPlayer(p, new S2CSpeech(kind.n, Component.translatable(key, p.getDisplayName()), style));
    }

    // ------------------------------------------------------------------ housekeeping
    /** Victims can't climb out of the ghost's grip. */
    public static boolean allowDismount(ServerPlayer p, Entity vehicle) {
        Session s = BY_VICTIM.get(p.getUUID());
        return s == null || s.released || s.phase == Phase.DONE || vehicle != s.b.g;
    }

    public static void onLogout(ServerPlayer p) {
        Session s = BY_VICTIM.remove(p.getUUID());
        if (s != null) {
            s.b.g.setTrack(-1);
            s.b.mode = GhostBrain.Mode.LOST;
            // leaving the game does not save you: it waits for you
            GhostEvents.markLoggedOutInKill(p, s.b.kind.n);
        }
    }

    public static void onGhostRemoved(GhostEntity g) {
        for (Iterator<Map.Entry<UUID, Session>> it = BY_VICTIM.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            if (e.getValue().b.g == g) {
                it.remove();
                ServerPlayer p = g.getServer() == null ? null : g.getServer().getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    HHNet.toPlayer(p, new S2CKill(g.getId(), g.kind.n, S2CKill.END, -1, 0));
                    HHNet.toPlayer(p, S2CBetrayal.close());
                }
            }
        }
    }

    public static void clear() {
        BY_VICTIM.clear();
    }

    static {
        HollowHours.LOG.debug("kill sequence ready");
    }

    static float clamp01(float v) {
        return Mth.clamp(v, 0, 1);
    }
}
