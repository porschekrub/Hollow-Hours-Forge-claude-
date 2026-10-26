package com.hollowhours.ghost;

import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The stalkers (port of stalker.js): peek from afar, follow you like a weeping angel, stand behind you,
 * streak across your path, wait at the end of a tunnel, lure you with a sound, stare through your window or
 * open your door... Seen too long they fade; get too close and they lunge at your face.
 */
public final class Stalkers {
    private Stalkers() {}

    static final List<String> OUTDOOR = List.of("peek", "lunge", "behind", "streak", "follow", "lure");
    static final List<String> CAVE = List.of("tunnel", "behind", "lunge", "lure", "follow", "peek");
    static final List<String> INDOOR = List.of("window", "door", "knock");

    static final class Session {
        final int id;
        final UUID player;
        final StalkerKind kind;
        final String ev;
        final long t0;
        final ServerLevel level;
        StalkerEntity e;
        int seen, phase, k, rounds, steps;
        long end, stepAt, at, rushEnd, turn, lungeEnd, nextKnock, shownAt;
        boolean rush, hit, door;
        Vec3 spot, dir;
        String snd;
        GhostBreach.Cand cand;
        long hb, wh;
        boolean drone;

        Session(int id, ServerPlayer p, StalkerKind kind, String ev) {
            this.id = id;
            this.player = p.getUUID();
            this.kind = kind;
            this.ev = ev;
            this.level = p.serverLevel();
            this.t0 = level.getGameTime();
        }
    }

    private static final Map<Integer, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, List<StalkerKind>> BAG = new HashMap<>();
    private static final Map<UUID, StalkerKind> LAST = new HashMap<>();
    private static final Map<UUID, List<String>> HIST = new HashMap<>();
    private static int seq;

    public static boolean active() {
        return !SESSIONS.isEmpty();
    }

    // ------------------------------------------------------------------ helpers
    static Vec3 head(ServerPlayer p) {
        return p.getEyePosition();
    }

    static double baseAng(ServerPlayer p) {
        Vec3 v = p.getViewVector(1f);
        return Math.atan2(v.z, v.x);
    }

    @Nullable
    static Vec3 spotAround(ServerPlayer p, Double off, double dmin, double dmax, boolean needLos) {
        Level level = p.level();
        Vec3 L = p.position();
        boolean ug = W.isUnderground(level, L);
        double b = baseAng(p);
        for (int t = 0; t < 18; t++) {
            double a = off == null ? R.rand(0, Math.PI * 2) : b + off + R.rand(-0.4, 0.4);
            double d = R.rand(dmin, dmax), x = L.x + Math.cos(a) * d, z = L.z + Math.sin(a) * d;
            double y = W.floorNear(level, x, z, L.y, ug);
            if (Double.isNaN(y) || Math.abs(y - L.y) > (ug ? 7 : 12)) continue;
            if (needLos && !W.los(level, head(p), new Vec3(x, y + 1.5, z))) continue;
            return new Vec3(Mth.floor(x) + 0.5, y, Mth.floor(z) + 0.5);
        }
        return null;
    }

    static boolean sees(ServerPlayer p, Vec3 loc, double h, double thr) {
        if (W.lookDot(p, loc) < thr) return false;
        return W.los(p.level(), head(p), new Vec3(loc.x, loc.y + h * 0.8, loc.z));
    }

    static void tpTo(Session s, double x, double y, double z) {
        ServerPlayer p = player(s);
        if (s.e == null || p == null) return;
        s.e.moveTo(x, y + s.kind.floatY, z);
        face(s);
    }

    static void face(Session s) {
        ServerPlayer p = player(s);
        if (s.e == null || p == null) return;
        Vec3 h = head(p);
        double dx = h.x - s.e.getX(), dz = h.z - s.e.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        s.e.setYRot(yaw);
        s.e.setYHeadRot(yaw);
        s.e.yBodyRot = yaw;
    }

    static boolean spawn(Session s, Vec3 loc) {
        StalkerEntity e = HHEntities.stalker(s.kind).create(s.level);
        if (e == null) return false;
        e.owned = true;
        e.moveTo(loc.x, loc.y + s.kind.floatY, loc.z, 0, 0);
        s.level.addFreshEntity(e);
        s.e = e;
        face(s);
        return true;
    }

    @Nullable
    static ServerPlayer player(Session s) {
        var p = s.level.getPlayerByUUID(s.player);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    static void end(Session s, boolean smoke) {
        SESSIONS.remove(s.id);
        BUSY.remove(s.player);
        if (s.e != null && s.e.isAlive()) {
            if (smoke) for (int k = 0; k < 7; k++) Fx.puff(s.level, ParticleTypes.LARGE_SMOKE, s.e.position(), 0.5);
            s.e.discard();
        }
        if (s.door && s.cand != null) {
            GhostBreach.Cand c = s.cand;
            ServerLevel lv = s.level;
            Later.run(30, () -> {
                GhostVisit.setDoor(lv, c.pos(), false);
                Fx.sound(lv, "random.door_close", GhostVisit.lookPoint(c), 1f, 0.6f, 24);
            });
        }
    }

    static void fade(Session s, @Nullable String snd) {
        if (s.e != null && s.e.isAlive()) Fx.sound(s.level, snd != null ? snd : "mob.endermen.portal", s.e.position(), 0.7f, s.kind == StalkerKind.CHILD ? 1.6f : 0.6f, 24);
        end(s, true);
    }

    /** Jumpscare unique to each stalker. */
    static void scare(Session s, boolean strong) {
        ServerPlayer p = player(s);
        if (p == null) return;
        Jumpscares.stalker(p, s.kind.n, strong);
        switch (s.kind) {
            case LANKY -> {
                Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 50, 1);
                Fx.effect(p, MobEffects.DARKNESS, 100, 0);
            }
            case CHILD -> {
                Fx.effect(p, MobEffects.CONFUSION, 80, 0);
                for (int k = 0; k < 3; k++) Later.run(6 + k * 14, () -> Fx.playAt(p, "ghost.scare.giggle", p.getX() + R.rand(-3, 3), p.getY(), p.getZ() + R.rand(-3, 3), 1.6f, R.pitch(0.8, 1.2)));
            }
            case VEIL -> {
                Fx.effect(p, MobEffects.DARKNESS, 180, 0);
                Fx.effect(p, MobEffects.BLINDNESS, 25, 0);
                Fx.effect(p, MobEffects.WEAKNESS, 100, 0);
            }
        }
    }

    // ------------------------------------------------------------------ events
    static boolean start(Session s) {
        ServerPlayer p = player(s);
        if (p == null) return false;
        long t0 = s.t0;
        switch (s.ev) {
            case "peek" -> {
                Vec3 sp = spotAround(p, R.rand(-0.8, 0.8), 17, 27, true);
                if (sp == null || !spawn(s, sp)) return false;
                s.end = t0 + 30 * 20;
                s.stepAt = t0 + 100;
                return true;
            }
            case "lunge" -> {
                Vec3 sp = spotAround(p, R.rand(-0.7, 0.7), 14, 22, true);
                if (sp == null || !spawn(s, sp)) return false;
                s.end = t0 + 26 * 20;
                return true;
            }
            case "behind" -> {
                Vec3 sp = spotAround(p, Math.PI, 6, 9, false);
                if (sp == null || !spawn(s, sp)) return false;
                s.end = t0 + 14 * 20;
                s.stepAt = t0 + 40;
                Fx.playAt(p, "ghost.scare." + R.pick(s.kind.sounds), sp, 1.2f, 1f);
                return true;
            }
            case "streak" -> {
                Vec3 P = p.position();
                double b = baseAng(p);
                int side = R.chance(0.5) ? 1 : -1;
                boolean ug = W.isUnderground(p.level(), P);
                double ax = P.x + Math.cos(b) * 7, az = P.z + Math.sin(b) * 7;
                double px = Math.cos(b + Math.PI / 2) * side, pz = Math.sin(b + Math.PI / 2) * side;
                double sx = ax + px * 12, sz = az + pz * 12;
                double y = W.floorNear(p.level(), sx, sz, P.y, ug);
                if (Double.isNaN(y) || Math.abs(y - P.y) > 8) return false;
                if (!spawn(s, new Vec3(sx, y, sz))) return false;
                s.dir = new Vec3(-px, 0, -pz);
                s.steps = 0;
                s.end = t0 + 70;
                s.e.setStalkPose(StalkerEntity.POSE_RUN);
                Fx.playAt(p, "ghost.scare." + R.pick(s.kind.sounds), sx, y, sz, 1.5f, 1f);
                return true;
            }
            case "follow" -> {
                Vec3 sp = spotAround(p, Math.PI + R.rand(-1, 1), 17, 25, false);
                if (sp == null || !spawn(s, sp)) return false;
                s.end = t0 + 60 * 20;
                s.stepAt = t0 + 40;
                return true;
            }
            case "tunnel" -> {
                if (!W.isUnderground(p.level(), p.position())) return false;
                Vec3 sp = spotAround(p, R.rand(-0.5, 0.5), 12, 24, true);
                if (sp == null || !spawn(s, sp)) return false;
                s.end = t0 + 25 * 20;
                return true;
            }
            case "lure" -> {
                Vec3 sp = spotAround(p, null, 10, 18, true);
                if (sp == null) return false;
                s.spot = sp;
                s.phase = 0;
                s.at = t0 + 90;
                s.end = t0 + 90 + 20 * 22;
                s.snd = R.pick(s.kind.sounds);
                Fx.playAt(p, "ghost.scare." + s.snd, sp, 1.8f, 1f);
                return true;
            }
            case "window", "door" -> {
                List<GhostBreach.Cand> cands = GhostVisit.knockTargets(s.level, p, false);
                GhostBreach.Cand c = null;
                for (GhostBreach.Cand q : cands) {
                    if (s.ev.equals("window") ? q.info() != null && q.info().fam().equals("glass") : GhostVisit.doorOpenable(s.level, q)) {
                        c = q;
                        break;
                    }
                }
                if (c == null) return false;
                s.cand = c;
                s.door = s.ev.equals("door");
                s.phase = 0;
                s.at = t0 + (long) R.rand(50, 90);
                GhostVisit.scareSoundAt(s.level, p, c, R.pick(s.kind.sounds));
                return true;
            }
            case "knock" -> {
                List<GhostBreach.Cand> cands = GhostVisit.knockTargets(s.level, p, false);
                if (cands.isEmpty()) return false;
                s.cand = cands.get(R.randInt(0, Math.min(3, cands.size()) - 1));
                s.phase = 0;
                s.rounds = R.chance(0.5) ? 2 : 3;
                s.k = 0;
                s.nextKnock = t0 + 10;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    static void tick(Session s, long now) {
        ServerPlayer p = player(s);
        if (p == null) {
            end(s, false);
            return;
        }
        StalkerEntity g = s.e;
        switch (s.ev) {
            case "peek" -> {
                double d = g.position().distanceTo(p.position());
                if (now % 6 == 0) face(s);
                if (sees(p, g.position(), s.kind.height, 0.9)) {
                    s.seen += 2;
                    if (d < 8) {
                        scare(s, true);
                        fade(s, "ghost.snap");
                        return;
                    }
                    if (s.seen >= s.kind.seen) {
                        fade(s, null);
                        return;
                    }
                } else {
                    s.seen = Math.max(0, s.seen - 1);
                    if (now >= s.stepAt && d > 6) {
                        s.stepAt = now + (long) R.rand(60, 130);
                        double f = (s.kind.hop > 0 ? s.kind.hop : 2) / d;
                        hopToward(s, p, f);
                    }
                }
                if (now >= s.end) fade(s, null);
            }
            case "lunge" -> {
                Vec3 P = p.position();
                double d = g.position().distanceTo(P);
                if (!s.rush) {
                    if (now % 6 == 0) face(s);
                    if (sees(p, g.position(), s.kind.height, 0.9)) s.seen += 2;
                    else s.seen = Math.max(0, s.seen - 1);
                    if (s.seen >= Math.max(6, s.kind.seen * 0.5)) {
                        s.rush = true;
                        s.rushEnd = now + 70;
                        g.setStalkPose(StalkerEntity.POSE_LUNGE);
                        Fx.sound(s.level, "ghost.growl", g.position(), 1.8f, 0.7f, 40);
                    } else if (now >= s.end) fade(s, null);
                    return;
                }
                if (d < 2.2) {
                    scare(s, true);
                    fade(s, "ghost.snap");
                    return;
                }
                if (now >= s.rushEnd) {
                    fade(s, null);
                    return;
                }
                double f = Math.min(1, (s.kind.rush * 2) / d);
                Vec3 L = g.position();
                double x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f;
                double y = W.floorNear(s.level, x, z, L.y, W.isUnderground(s.level, L));
                tpTo(s, x, Double.isNaN(y) ? L.y - s.kind.floatY : y, z);
            }
            case "behind" -> {
                double d = g.position().distanceTo(p.position());
                if (s.turn > 0) {
                    if (now >= s.turn) fade(s, "ghost.snap");
                    return;
                }
                if (now % 6 == 0) face(s);
                if (sees(p, g.position(), s.kind.height * 0.6, 0.8)) {
                    scare(s, false);
                    s.turn = now + 6;
                    return;
                }
                if (d < 2.4) {
                    scare(s, true);
                    fade(s, "ghost.snap");
                    return;
                }
                if (now >= s.stepAt) {
                    s.stepAt = now + 30;
                    hopToward(s, p, 1 / d);
                    Fx.playAt(p, "step.stone", g.position(), 0.8f, 0.8f);
                }
                if (now >= s.end) fade(s, null);
            }
            case "streak" -> {
                Vec3 L = g.position();
                double nx = L.x + s.dir.x * 1.6, nz = L.z + s.dir.z * 1.6;
                double y = W.floorNear(s.level, nx, nz, L.y, W.isUnderground(s.level, L));
                if (Double.isNaN(y) || now >= s.end) {
                    fade(s, null);
                    return;
                }
                g.moveTo(nx, y + s.kind.floatY, nz);
                float yaw = (float) (Mth.atan2(s.dir.z, s.dir.x) * Mth.RAD_TO_DEG) - 90f;
                g.setYRot(yaw);
                g.setYHeadRot(yaw);
                g.yBodyRot = yaw;
                if (!s.hit && g.position().distanceTo(p.position()) < 9 && W.lookDot(p, g.position()) > 0.55) {
                    s.hit = true;
                    scare(s, false);
                }
                if (++s.steps >= 15) fade(s, null);
            }
            case "follow" -> {
                Vec3 L = g.position(), P = p.position();
                double d = L.distanceTo(P);
                boolean looked = sees(p, L, s.kind.height, 0.85);
                if (now % 6 == 0) face(s);
                if (looked) {
                    s.seen += 2;
                    if (d <= 4) {
                        scare(s, true);
                        fade(s, "ghost.snap");
                        return;
                    }
                    if (d > 9 && s.seen >= s.kind.seen) {
                        fade(s, null);
                        return;
                    }
                } else {
                    s.seen = Math.max(0, s.seen - 1);
                    if (d < 1.9) {
                        scare(s, true);
                        fade(s, "ghost.snap");
                        return;
                    }
                    if (now >= s.stepAt) {
                        double hop = s.kind.hop > 0 ? s.kind.hop : (s.kind == StalkerKind.VEIL ? 1.6 : 1.1);
                        s.stepAt = now + (s.kind.hop > 0 ? 26 : 8);
                        hopToward(s, p, Math.min(hop, d - 1.6) / d);
                        if (s.kind.hop > 0) Fx.playAt(p, "ghost.scare.giggle", g.position(), 0.9f, R.pitch(1.1, 1.5));
                    }
                }
                if (now >= s.end) fade(s, null);
            }
            case "tunnel" -> {
                double d = g.position().distanceTo(p.position());
                if (now % 6 == 0) face(s);
                if (sees(p, g.position(), s.kind.height, 0.93)) {
                    s.seen += 2;
                    if (s.seen >= Math.max(8, s.kind.seen * 0.6)) {
                        if (d < 14 && s.kind != StalkerKind.CHILD) scare(s, false);
                        fade(s, null);
                        return;
                    }
                } else s.seen = Math.max(0, s.seen - 1);
                if (now >= s.end) fade(s, null);
            }
            case "lure" -> {
                if (s.phase == 0) {
                    if (now == s.t0 + 40) Fx.playAt(p, "ghost.scare." + s.snd, s.spot, 2f, 0.9f);
                    if (now >= s.at) {
                        if (!spawn(s, s.spot)) {
                            end(s, false);
                            return;
                        }
                        s.phase = 1;
                        s.shownAt = now;
                    }
                    return;
                }
                if (now % 6 == 0) face(s);
                if (sees(p, g.position(), s.kind.height, 0.9)) {
                    s.seen += 2;
                    if (s.seen >= 6) {
                        scare(s, false);
                        fade(s, "ghost.snap");
                        return;
                    }
                } else s.seen = Math.max(0, s.seen - 1);
                if (now >= s.end) fade(s, null);
            }
            case "window", "door" -> tickWatch(s, p, now);
            case "knock" -> tickKnock(s, p, now);
            default -> end(s, false);
        }
    }

    static void hopToward(Session s, ServerPlayer p, double f) {
        Vec3 L = s.e.position(), P = p.position();
        double x = L.x + (P.x - L.x) * f, z = L.z + (P.z - L.z) * f;
        double y = W.floorNear(s.level, x, z, L.y - s.kind.floatY, W.isUnderground(s.level, L));
        if (!Double.isNaN(y)) tpTo(s, x, y, z);
    }

    static void tickWatch(Session s, ServerPlayer p, long now) {
        if (s.phase == 0) {
            if (now < s.at) return;
            Vec3 T = GhostVisit.reachPoint(s.level, s.cand, s.kind == StalkerKind.LANKY ? 1.3 : 1.0);
            if (!spawn(s, T)) {
                end(s, false);
                return;
            }
            if (s.door) {
                GhostVisit.setDoor(s.level, s.cand.pos(), true);
                Fx.sound(s.level, "random.door_open", GhostVisit.lookPoint(s.cand), 1f, 0.5f, 24);
            }
            s.phase = 1;
            s.end = now + 30 * 20;
            return;
        }
        if (s.phase == 2) {
            if (now >= s.lungeEnd) fade(s, "ghost.snap");
            return;
        }
        if (now % 6 == 0) face(s);
        double d = p.position().distanceTo(s.e.position());
        if (d < 2.2) {
            lungeAtFace(s, p, now);
            return;
        }
        boolean looking = sees(p, s.e.position(), s.kind.height, 0.93)
                || (s.cand.info() != null && s.cand.info().fam().equals("glass") && W.lookingAtBlock(p, s.cand.pos(), 20));
        if (looking && d < 22) s.seen += 2;
        else s.seen = Math.max(0, s.seen - 1);
        if (s.seen >= 6) {
            lungeAtFace(s, p, now);
            return;
        }
        if (now >= s.end) fade(s, null);
    }

    static void lungeAtFace(Session s, ServerPlayer p, long now) {
        Vec3 v = p.getViewVector(1f), l = p.position();
        double hl = Math.max(1e-6, Math.hypot(v.x, v.z));
        Vec3 spot = null;
        for (double d = 1.5 + 0.3 * s.kind.height; d >= 1.2 && spot == null; d -= 0.3) {
            double x = l.x + v.x / hl * d, z = l.z + v.z / hl * d;
            double y = W.floorNear(s.level, x, z, l.y, true);
            if (!Double.isNaN(y) && Math.abs(y - l.y) < 1.5) spot = new Vec3(x, y, z);
        }
        if (spot != null) {
            s.e.noPhysics = true;
            tpTo(s, spot.x, spot.y, spot.z);
            s.e.setStalkPose(StalkerEntity.POSE_LUNGE);
        }
        scare(s, true);
        s.phase = 2;
        s.lungeEnd = now + 12;
    }

    static void tickKnock(Session s, ServerPlayer p, long now) {
        GhostBreach.Cand c = s.cand;
        if (s.phase == 0) {
            if (now >= s.nextKnock) {
                Fx.sound(s.level, "ghost.scare.wallknock", GhostVisit.lookPoint(c), 1.7f, R.pitch(0.95, 1.05), 40);
                s.nextKnock = now + (s.k % 3 == 2 ? 70 : 12);
                s.k++;
                if (s.k >= s.rounds * 3) {
                    s.phase = 1;
                    s.at = now + 40;
                }
            }
            return;
        }
        if (s.phase == 1) {
            if (now < s.at) return;
            if (c.info() == null || !c.info().fam().equals("glass")) {
                Fx.sound(s.level, "ghost.scare.whisper", GhostVisit.lookPoint(c), 1.4f, 0.9f, 24);
                end(s, false);
                return;
            }
            Vec3 T = GhostVisit.reachPoint(s.level, c, 1.1);
            if (!spawn(s, T)) {
                end(s, false);
                return;
            }
            s.phase = 2;
            s.end = now + 8 * 20;
            return;
        }
        if (now % 6 == 0) face(s);
        boolean looking = sees(p, s.e.position(), s.kind.height, 0.9) || W.lookingAtBlock(p, c.pos(), 20);
        if (looking) s.seen += 2;
        else s.seen = Math.max(0, s.seen - 1);
        if (s.seen >= 8 || now >= s.end) fade(s, s.seen >= 8 ? "ghost.scare.giggle" : null);
    }

    // ------------------------------------------------------------------ choosing who and what
    static StalkerKind pickType(ServerPlayer p) {
        List<StalkerKind> bag = BAG.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (bag.isEmpty()) {
            bag.addAll(List.of(StalkerKind.values()));
            Collections.shuffle(bag);
        }
        StalkerKind last = LAST.get(p.getUUID());
        int i = 0;
        for (int j = 0; j < bag.size(); j++) if (bag.get(j) != last) {
            i = j;
            break;
        }
        StalkerKind n = bag.remove(i);
        LAST.put(p.getUUID(), n);
        return n;
    }

    static boolean begin(ServerPlayer p, List<String> pool) {
        if (p == null || !p.isAlive() || BUSY.contains(p.getUUID())) return false;
        StalkerKind kind = pickType(p);
        List<String> tries = new ArrayList<>(pool);
        List<String> h = HIST.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        for (int k = 0; k < 4 && !tries.isEmpty(); k++) {
            List<String> list = new ArrayList<>();
            for (String e : tries) if (!h.contains(e)) list.add(e);
            if (list.isEmpty()) list = tries;
            String ev = R.weighted(kind.weights, list);
            tries.remove(ev);
            Session s = new Session(++seq, p, kind, ev);
            boolean ok;
            try {
                ok = start(s);
            } catch (Exception e) {
                ok = false;
            }
            if (!ok) {
                if (s.e != null && s.e.isAlive()) s.e.discard();
                continue;
            }
            SESSIONS.put(s.id, s);
            BUSY.add(p.getUUID());
            h.add(ev);
            while (h.size() > 2) h.remove(0);
            return true;
        }
        return false;
    }

    public static boolean indoor(ServerPlayer p) {
        return begin(p, INDOOR);
    }

    public static boolean outdoor(ServerPlayer p) {
        return begin(p, W.isUnderground(p.level(), p.position()) ? CAVE : OUTDOOR);
    }

    /** Test command: force a stalker type and event. */
    public static String test(ServerPlayer p, int n, @Nullable String ev) {
        if (BUSY.contains(p.getUUID())) return "busy";
        StalkerKind kind = StalkerKind.values()[Math.max(0, Math.min(2, n - 1))];
        List<String> pool = ev != null ? List.of(ev) : new ArrayList<>(OUTDOOR);
        for (String e : pool) {
            Session s = new Session(++seq, p, kind, e);
            if (start(s)) {
                SESSIONS.put(s.id, s);
                BUSY.add(p.getUUID());
                return kind.display + " / " + e;
            }
            if (s.e != null) s.e.discard();
        }
        return "no spot";
    }

    // ------------------------------------------------------------------ loop
    /** Pressure before the scare: closer = faster heartbeat + whispers behind you. */
    static void dread(Session s, ServerPlayer p, long now) {
        if (s.e == null || s.phase == 0 && (s.ev.equals("lure") || s.ev.equals("window") || s.ev.equals("door") || s.ev.equals("knock"))) return;
        double d = p.position().distanceTo(s.e.position());
        if (d > 40) return;
        double k = Math.max(0, Math.min(1, 1 - d / 40));
        Vec3 l = p.position();
        if (!s.drone) {
            s.drone = true;
            Fx.playAt(p, "ghost.horror.drone", l, 1.3f, 1f);
        }
        if (now >= s.hb) {
            Fx.playAt(p, "ghost.horror.heart", l, (float) (0.6 + 1.8 * k), (float) (0.85 + 0.45 * k));
            s.hb = now + Math.round(34 - 20 * k);
        }
        if (k > 0.3 && now >= s.wh) {
            Vec3 v = p.getViewVector(1f);
            Fx.playAt(p, "ghost.horror.whisper", l.x - v.x * 2, l.y + 1.6, l.z - v.z * 2, 1.3f, R.pitch(0.9, 1.05));
            s.wh = now + Math.round(R.rand(150, 300) * (1.3 - k));
        }
    }

    public static void tick(MinecraftServer server, long now) {
        if (now % 2 != 0) return;
        for (Iterator<Session> it = new ArrayList<>(SESSIONS.values()).iterator(); it.hasNext(); ) {
            Session s = it.next();
            try {
                ServerPlayer p = player(s);
                if (p == null || p.level() != s.level) {
                    end(s, false);
                    continue;
                }
                if (s.e != null && !s.e.isAlive() && s.phase != 0) {
                    SESSIONS.remove(s.id);
                    BUSY.remove(s.player);
                    continue;
                }
                if (Hiding.isHidden(p) && s.e != null) {
                    fade(s, null);
                    continue;
                }
                if (s.e != null && s.e.position().distanceTo(p.position()) > 70) {
                    end(s, false);
                    continue;
                }
                dread(s, p, now);
                tick(s, now);
            } catch (Exception e) {
                end(s, false);
            }
        }
    }

    public static void schedule(MinecraftServer server, long now) {
        if (Director.ghostBusy(server)) return;
        ServerLevel over = server.overworld();
        boolean night = W.isNight(over);
        for (ServerPlayer p : over.players()) {
            if (!GhostBrain.targetable(p) || BUSY.contains(p.getUUID()) || Hiding.isHidden(p)) continue;
            Long at = NEXT.get(p.getUUID());
            if (at == null) {
                NEXT.put(p.getUUID(), now + (long) R.rand(5 * 1200, 9 * 1200));
                continue;
            }
            if (now < at || W.isSheltered(p)) continue;
            NEXT.put(p.getUUID(), now + (long) (R.rand(9 * 1200, 16 * 1200) / (Variety.moodEv() * (night ? 1.5 : 1))));
            if (outdoor(p)) break;
        }
    }

    public static void clear() {
        for (Session s : new ArrayList<>(SESSIONS.values())) end(s, false);
        SESSIONS.clear();
        BUSY.clear();
    }
}
