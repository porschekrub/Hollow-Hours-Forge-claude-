package com.hollowhours.ghost;

import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ghost lost you: it walks to where it last saw/heard you, sniffs, checks closets and beds nearby
 * (port of hide.js startSearch / planNext / inspect / reveal). If it finds you it rips the closet open or
 * peers under the bed and drags you out (big jumpscare), then the chase goes on. Gives up after a while.
 */
public final class Search {
    public record Alert(String key, String level, long t) {}

    enum Phase { GOTO, LOOK, INSPECT, PEEK, YANK }

    Phase phase = Phase.GOTO;
    Vec3 lkp;
    long t0, until, t1, lookUntil, turnAt, inspectUntil, sfxAt, nextSfx, t2;
    Vec3 goal, goalLook;
    String after = "look";
    Hiding.Spot spot;
    List<Hiding.Spot> spots;
    final Map<String, Integer> checked = new HashMap<>();
    int spotsDone;
    String sawKey;
    boolean sawDone, visitedLkp, wander, forced, breachTried;
    Alert alert;
    public float faceYaw;
    double peak;
    ServerPlayer victim;
    int stall;

    private Search() {}

    public static void start(GhostBrain b, long now, @Nullable Vec3 lkpIn, @Nullable Vec3 near, @Nullable String alertKey, @Nullable String level) {
        GhostEntity g = b.g;
        ServerLevel lv = b.level();
        Vec3 lkp = lkpIn;
        if (lkp == null && b.lkp != null && now - b.lkpAt < 900) lkp = b.lkp;
        if (lkp == null && near != null) {
            // only roughly known: a random reachable cell 2-5 blocks from the player
            for (int i = 0; i < 12 && lkp == null; i++) {
                double a = R.rand(0, Math.PI * 2), d = R.rand(2, 5);
                BlockPos c = W.standCell(lv, near.x + Math.cos(a) * d, near.z + Math.sin(a) * d, Mth.floor(near.y) + 1, 3, 2);
                if (c != null) lkp = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            }
            if (lkp == null) lkp = near;
        }
        if (lkp == null) lkp = g.position();
        Search s = new Search();
        s.lkp = lkp;
        s.t0 = now;
        s.until = now + (long) R.rand(620, 900);
        s.spots = Hiding.findSpots(lv, lkp, 9);
        s.sawKey = b.sawHide != null && now - b.sawHideAt < 900 ? b.sawHide : null;
        s.alert = alertKey != null ? new Alert(alertKey, level == null ? "noise" : level, now) : null;
        s.faceYaw = g.getYRot();
        s.nextSfx = now + 30;
        b.sawHide = null;
        b.search = s;
        b.mode = GhostBrain.Mode.SEARCH;
        b.breach.reset();
        g.getNavigation().stop();
        setPose(b, !b.canStand());
        Fx.sound(lv, R.chance(0.5) ? "ghost.sniff" : "ghost.growl", g.position(), 1.2f, R.pitch(0.6, 0.85), 24);
        planNext(b, now, true);
    }

    static void setPose(GhostBrain b, boolean crawl) {
        if (b.kind.height > 2.05) b.g.setCrawling(crawl);
        b.g.setAnim(b.g.isCrawling() ? GhostAnim.CRAWL_SEARCH : GhostAnim.SEARCH);
    }

    static void end(GhostBrain b) {
        Search s = b.search;
        if (s != null && s.spot != null && s.spot.closet() != null) s.spot.closet().setDoor(ClosetEntity.DOOR_SHUT);
        b.search = null;
        if (b.mode == GhostBrain.Mode.SEARCH) b.mode = GhostBrain.Mode.LOST;
    }

    // ------------------------------------------------------------------ planning
    private static void goTo(GhostBrain b, Search s, Vec3 target, @Nullable Vec3 look, String after) {
        s.phase = Phase.GOTO;
        s.goal = target;
        s.goalLook = look;
        s.after = after;
        s.spot = null;
        s.stall = 0;
        s.wander = false;
        s.t1 = b.now();
        s.breachTried = false;
        moveTo(b, target, 0.75);
    }

    private static void moveTo(GhostBrain b, Vec3 target, double speed) {
        Path p = b.g.getNavigation().createPath(BlockPos.containing(target), 0);
        b.g.getNavigation().moveTo(p, speed);
    }

    private static void goSpot(GhostBrain b, Search s, Hiding.Spot sp, boolean rush) {
        Vec3[] pt = spotPoint(b, sp, false);
        if (pt == null) {
            s.checked.put(sp.key(), 9);
            planNext(b, b.now(), false);
            return;
        }
        goTo(b, s, pt[0], pt[1], "inspect");
        s.spot = sp;
        if (rush) moveTo(b, pt[0], 1.3);
    }

    /** Where to stand to look into a spot: [stand point, look point]. peek = lie down to look under a bed. */
    @Nullable
    static Vec3[] spotPoint(GhostBrain b, Hiding.Spot sp, boolean peek) {
        ServerLevel lv = b.level();
        if (sp.kind() == Hiding.Kind.CLOSET) {
            ClosetEntity e = sp.closet();
            if (e == null || !e.isAlive()) return null;
            Vec3 f = Vec3.directionFromRotation(0, e.getYRot());
            for (double k : new double[]{0.46 + b.kind.inspectReach * 0.6, 2.0, 1.5, 1.1}) {
                double x = e.getX() + f.x * k, z = e.getZ() + f.z * k;
                BlockPos c = W.standCell(lv, x, z, Mth.floor(e.getY()) + 1, 3, 1);
                if (c != null) return new Vec3[]{new Vec3(x, c.getY(), z), e.position().add(0, 1.45, 0)};
            }
            return null;
        }
        // bed: stand on the side the hiding player faces (they see its feet), or any long side
        ServerPlayer occ = Hiding.occupantOf(lv, sp.key());
        Hiding.State oh = occ == null ? null : Hiding.stateOf(occ);
        Direction[] sides = oh != null && oh.side != null ? new Direction[]{oh.side}
                : sp.axis() == Direction.Axis.X ? new Direction[]{Direction.SOUTH, Direction.NORTH} : new Direction[]{Direction.EAST, Direction.WEST};
        Vec3 base = oh != null && oh.bedCell != null ? Vec3.atBottomCenterOf(oh.bedCell) : sp.pos();
        for (Direction d : sides) {
            double[] ks = peek ? new double[]{0.32 + 0.95 + b.kind.peekReach * 0.5} : new double[]{0.5 + b.kind.inspectReach * 0.5, 1.85, 1.5};
            for (double k : ks) {
                double x = base.x + d.getStepX() * k, z = base.z + d.getStepZ() * k;
                BlockPos c = W.standCell(lv, x, z, Mth.floor(sp.pos().y) + 1, 3, 1);
                if (c != null || peek) {
                    double y = c != null ? c.getY() : sp.pos().y;
                    return new Vec3[]{new Vec3(x, y, z), new Vec3(base.x, sp.pos().y + (peek ? 0.1 : 0.4), base.z)};
                }
            }
        }
        return null;
    }

    @Nullable
    private static Hiding.Spot spotByKey(GhostBrain b, Search s, String key) {
        for (Hiding.Spot sp : s.spots) if (sp.key().equals(key)) return sp;
        ServerPlayer occ = Hiding.occupantOf(b.level(), key);
        if (occ != null) {
            Hiding.State h = Hiding.stateOf(occ);
            if (h != null) {
                Hiding.Spot sp = h.seat instanceof ClosetEntity c ? Hiding.closetSpot(c)
                        : new Hiding.Spot(Hiding.Kind.BED, key, h.spot, h.spot.add(0, 0.1, 0), null, h.bedCell, h.bedCell,
                        h.side != null ? h.side.getClockWise().getAxis() : Direction.Axis.X);
                s.spots.add(sp);
                return sp;
            }
        }
        return null;
    }

    static void planNext(GhostBrain b, long now, boolean first) {
        Search s = b.search;
        if (s == null) return;
        if (s.alert != null && !s.alert.key.isEmpty()) {
            Alert a = s.alert;
            s.alert = null;
            Hiding.Spot sp = spotByKey(b, s, a.key);
            if (sp != null) {
                goSpot(b, s, sp, "gasp".equals(a.level));
                return;
            }
        }
        if (s.sawKey != null && !s.sawDone) {
            s.sawDone = true;
            Hiding.Spot sp = spotByKey(b, s, s.sawKey);
            if (sp != null) {
                goSpot(b, s, sp, true);
                return;
            }
        }
        if (first || !s.visitedLkp) {
            s.visitedLkp = true;
            goTo(b, s, s.lkp, null, "look");
            return;
        }
        if (s.spotsDone < 4) {
            Hiding.Spot next = null;
            double bd = 1e9;
            for (Hiding.Spot sp : s.spots) {
                if (s.checked.containsKey(sp.key())) continue;
                double d = sp.pos().distanceTo(b.g.position());
                if (d < bd) {
                    bd = d;
                    next = sp;
                }
            }
            if (next != null) {
                goSpot(b, s, next, false);
                return;
            }
        }
        // wander near the last known point
        Vec3 w = null;
        for (int i = 0; i < 10 && w == null; i++) {
            double a = R.rand(0, Math.PI * 2), d = R.rand(2.5, 9);
            BlockPos c = W.standCell(b.level(), s.lkp.x + Math.cos(a) * d, s.lkp.z + Math.sin(a) * d, Mth.floor(s.lkp.y) + 2, 4, 2);
            if (c != null) w = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
        }
        goTo(b, s, w != null ? w : b.g.position(), null, "look");
        s.wander = true;
    }

    // ------------------------------------------------------------------ tick
    static void tick(GhostBrain b, long now) {
        Search s = b.search;
        GhostEntity g = b.g;
        if (s == null) {
            b.mode = GhostBrain.Mode.LOST;
            return;
        }
        if (b.isLeaving() || b.appeaseUntil > now) {
            end(b);
            return;
        }
        ServerLevel lv = b.level();
        if (b.playersNear(56).isEmpty()) {
            giveUp(b);
            return;
        }
        boolean busy = s.phase == Phase.PEEK || s.phase == Phase.YANK;
        if (now >= s.until && !busy && s.phase != Phase.INSPECT) {
            giveUp(b);
            return;
        }
        if (!busy && now % 3 == 0) {
            Perception.Sense det = Perception.perceive(g, b, now, true);
            if (det != null) {
                notice(b, det, now);
                return;
            }
        }
        if (s.alert != null && (s.phase == Phase.GOTO || s.phase == Phase.LOOK) && !(s.spot != null && s.spot.key().equals(s.alert.key))) planNext(b, now, false);
        if (!busy && now >= s.nextSfx) {
            s.nextSfx = now + (long) R.rand(70, 140);
            double r = R.next();
            if (r < 0.4) Fx.sound(lv, "ghost.sniff", g.eye(), 1.1f, R.pitch(0.7, 0.9), 20);
            else if (r < 0.65) Fx.sound(lv, "ghost.growl", g.position(), 0.9f, R.pitch(0.45, 0.6), 24);
            for (ServerPlayer p : b.playersNear(20)) {
                Fx.effect(p, MobEffects.DARKNESS, 60, 0);
                if (!Hiding.isHidden(p) && R.chance(0.35)) Fx.actionbar(p, Component.translatable("hollowhours.search.sniff." + R.randInt(0, 3)));
            }
        }
        if (now % 20 == 0 && s.phase != Phase.PEEK && s.phase != Phase.YANK) {
            boolean ok = b.canStand();
            if (g.isCrawling() && ok) setPose(b, false);
            else if (!g.isCrawling() && !ok) setPose(b, true);
        }
        switch (s.phase) {
            case GOTO -> tickGoto(b, s, now);
            case LOOK -> tickLook(b, s, now);
            case INSPECT -> tickInspect(b, s, now);
            case PEEK -> tickPeek(b, s, now);
            case YANK -> tickYank(b, s, now);
        }
    }

    private static void tickGoto(GhostBrain b, Search s, long now) {
        GhostEntity g = b.g;
        g.setAnim(g.isCrawling() ? GhostAnim.CRAWL_SEARCH : GhostAnim.SEARCH);
        double d = Math.hypot(s.goal.x - g.getX(), s.goal.z - g.getZ());
        if (d < 0.6 && Math.abs(s.goal.y - g.getY()) < 1.6) {
            arrive(b, s, now);
            return;
        }
        if (g.getNavigation().isDone()) {
            s.stall += 5;
            if (d > 1.2) moveTo(b, s.goal, 0.75);
        }
        if (s.stall >= 30 || now - s.t1 > 420) {
            s.stall = 0;
            s.t1 = now;
            if (s.wander) {
                planNext(b, now, false);
                return;
            }
            Path p = g.getNavigation().createPath(BlockPos.containing(s.goal), 0);
            boolean reachable = p != null && p.canReach();
            if (!reachable && !s.breachTried && b.breach.breachToward(s.goal, now)) {
                s.breachTried = true;
                return;
            }
            // nobody watching: it simply... is there
            if (!watched(b)) {
                BlockPos c = W.standCell(b.level(), s.goal.x, s.goal.z, Mth.floor(s.goal.y) + 1, 3, 1);
                if (c != null) {
                    for (int i = 0; i < 5; i++) Fx.puff(b.level(), ParticleTypes.SMOKE, g.position(), 0.5);
                    b.teleportFacing(new Vec3(s.goal.x, c.getY(), s.goal.z), s.goalLook != null ? s.goalLook : s.goal.add(1, 1, 0));
                    arrive(b, s, now);
                    return;
                }
            }
            if (s.spot != null) {
                s.checked.put(s.spot.key(), 9);
                s.spotsDone++;
            }
            planNext(b, now, false);
        }
    }

    static boolean watched(GhostBrain b) {
        for (ServerPlayer p : b.playersNear(48)) {
            if (Hiding.isHidden(p)) continue;
            if (W.lookDot(p, b.g.position()) > 0.8 && Perception.canSee(b.g, p, 48)) return true;
        }
        return false;
    }

    private static void arrive(GhostBrain b, Search s, long now) {
        b.g.getNavigation().stop();
        if ("inspect".equals(s.after) && s.spot != null) {
            startInspect(b, s, now);
            return;
        }
        s.phase = Phase.LOOK;
        s.lookUntil = now + (long) R.rand(40, 75);
        s.turnAt = now + 12;
        Fx.sound(b.level(), "ghost.sniff", b.g.eye(), 1.2f, R.pitch(0.75, 0.95), 16);
    }

    private static void tickLook(GhostBrain b, Search s, long now) {
        b.g.setAnim(b.g.isCrawling() ? GhostAnim.CRAWL_SEARCH : GhostAnim.INSPECT);
        if (now >= s.turnAt) {
            s.turnAt = now + (long) R.rand(16, 26);
            float yaw = s.faceYaw + (R.chance(0.5) ? -1 : 1) * (float) R.rand(50, 120);
            Vec3 f = Vec3.directionFromRotation(0, yaw);
            b.face(b.g.position().add(f.x * 4, b.g.isCrawling() ? 0.6 : 2.6, f.z * 4));
        }
        if (now >= s.lookUntil) planNext(b, now, false);
    }

    private static void startInspect(GhostBrain b, Search s, long now) {
        Hiding.Spot sp = s.spot;
        s.phase = Phase.INSPECT;
        s.peak = 0;
        boolean forced = sp.key().equals(s.sawKey) || (s.alert != null && s.alert.key.equals(sp.key()) && "gasp".equals(s.alert.level));
        s.inspectUntil = now + (forced ? 22 : (long) R.rand(62, 92));
        s.forced = forced;
        b.g.setAnim(GhostAnim.INSPECT);
        b.face(s.goalLook != null ? s.goalLook : sp.lookAt());
        if (sp.closet() != null) sp.closet().setDoor(ClosetEntity.DOOR_RATTLE);
        Fx.sound(b.level(), "ghost.sniff", sp.lookAt(), 1.4f, R.pitch(0.75, 0.95), 16);
        s.sfxAt = now + 20;
    }

    private static void tickInspect(GhostBrain b, Search s, long now) {
        Hiding.Spot sp = s.spot;
        ServerLevel lv = b.level();
        if (sp.closet() != null && !sp.closet().isAlive()) {
            s.checked.put(sp.key(), 9);
            planNext(b, now, false);
            return;
        }
        ServerPlayer occ = Hiding.occupantOf(lv, sp.key());
        if (occ != null) {
            Hiding.State st = Hiding.stateOf(occ);
            if (st != null) {
                s.peak = Math.max(s.peak, st.breath);
                if (now - st.gaspAt < 6) {
                    reveal(b, s, sp, occ, now);
                    return;
                }
            }
        }
        if (now >= s.sfxAt) {
            s.sfxAt = now + (long) R.rand(18, 30);
            if (sp.kind() == Hiding.Kind.CLOSET && R.chance(0.55)) Fx.sound(lv, "ghost.scratch", sp.lookAt(), 1.2f, R.pitch(0.9, 1.15), 16);
            else Fx.sound(lv, "ghost.sniff", sp.lookAt(), 1.3f, R.pitch(0.7, 0.95), 16);
        }
        if (now < s.inspectUntil) return;
        if (sp.closet() != null) sp.closet().setDoor(ClosetEntity.DOOR_SHUT);
        int tries = s.checked.getOrDefault(sp.key(), 0) + 1;
        s.checked.put(sp.key(), tries);
        s.spotsDone++;
        if (occ != null) {
            double chance = sp.kind() == Hiding.Kind.CLOSET ? 0.12 : 0.1;
            if (s.forced) chance = 1;
            if (s.peak >= Hiding.BREATH_WARN) chance += 0.45;
            chance += 0.15 * (tries - 1);
            if (R.chance(chance)) {
                reveal(b, s, sp, occ, now);
                return;
            }
        }
        setPose(b, !b.canStand());
        planNext(b, now, false);
    }

    // ------------------------------------------------------------------ found you
    private static void reveal(GhostBrain b, Search s, Hiding.Spot sp, ServerPlayer p, long now) {
        ServerLevel lv = b.level();
        s.victim = p;
        s.t2 = now;
        if (sp.kind() == Hiding.Kind.CLOSET) {
            s.phase = Phase.YANK;
            b.g.playAnim(GhostAnim.YANK);
            if (sp.closet() != null) sp.closet().setDoor(ClosetEntity.DOOR_YANK);
            Fx.sound(lv, "ghost.closet.yank", sp.pos(), 2f, R.pitch(0.9, 1.05), 40);
            Fx.shakeNear(lv, sp.pos(), 10, 0.7f, 12);
            return;
        }
        // bed: it lies down and its face appears at floor level, staring under the bed
        s.phase = Phase.PEEK;
        Vec3[] pt = spotPoint(b, sp, true);
        if (pt != null) b.teleportFacing(pt[0], pt[1]);
        b.g.setCrawling(true);
        b.g.playAnim(GhostAnim.PEEK);
        Fx.sound(lv, "ghost.breath", sp.lookAt(), 1.6f, 0.7f, 12);
        Later.run(12, () -> Fx.sound(lv, "ghost.growl", sp.lookAt(), 1.4f, 0.5f, 20));
    }

    /** Pulled out of the hiding spot: the hardest jumpscare outside of dying. */
    private static void dragOut(GhostBrain b, ServerPlayer p, boolean bed) {
        Hiding.exit(p, true, true, b.g.eye());
        Jumpscares.dragOut(p, b.kind.n, bed);
        Fx.effect(p, MobEffects.CONFUSION, 140, 0);
        Fx.effect(p, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
        Fx.actionbar(p, Component.translatable("hollowhours.search.found." + R.randInt(0, 2)));
    }

    private static void toChase(GhostBrain b, ServerPlayer p, long now) {
        end(b);
        b.lkp = p.position();
        b.lkpAt = now;
        b.lastDetect = now;
        if (b.huntStart == 0) b.huntStart = now;
        if (now - b.huntStart < 5 * 60 * 20) b.chaseStart = Math.max(b.chaseStart == 0 ? now : b.chaseStart, now - 900);
        b.startChase(p, now);
    }

    private static void tickYank(GhostBrain b, Search s, long now) {
        ServerPlayer p = s.victim;
        if (now - s.t2 == 5 && p != null && p.isAlive()) dragOut(b, p, false);
        if (now - s.t2 >= 24) {
            if (s.spot != null && s.spot.closet() != null) {
                ClosetEntity c = s.spot.closet();
                c.setDoor(ClosetEntity.DOOR_OPEN);
                Later.run(60, () -> c.setDoor(ClosetEntity.DOOR_SHUT));
            }
            if (p != null && p.isAlive()) toChase(b, p, now);
            else {
                end(b);
                b.vanish();
            }
        }
    }

    private static void tickPeek(GhostBrain b, Search s, long now) {
        ServerPlayer p = s.victim;
        if (now - s.t2 == 30 && p != null && p.isAlive()) dragOut(b, p, true);
        if (now - s.t2 >= 44) {
            b.g.setCrawling(!b.canStand());
            if (p != null && p.isAlive()) toChase(b, p, now);
            else {
                end(b);
                b.vanish();
            }
        }
    }

    private static void notice(GhostBrain b, Perception.Sense det, long now) {
        ServerPlayer p = det.player();
        Fx.sound(b.level(), "ghost.growl", b.g.position(), 2f, R.pitch(0.5, 0.7), 40);
        b.face(p.getEyePosition());
        Fx.actionbar(p, Component.translatable("hear".equals(det.how()) ? "hollowhours.search.heard" : "hollowhours.search.seen"));
        toChase(b, p, now);
    }

    static void giveUp(GhostBrain b) {
        for (ServerPlayer p : b.playersNear(48)) Fx.actionbar(p, Component.translatable("hollowhours.search.giveup." + R.randInt(0, 2)));
        Fx.sound(b.level(), "mob.ghast.moan", b.g.position(), 1.6f, 0.45f, 48);
        end(b);
        b.vanish();
    }

    /** Called by the breach system after it broke in: keep searching from the new position. */
    static void resumeAfterBreach(GhostBrain b, long now) {
        Search s = b.search;
        if (s == null) return;
        b.mode = GhostBrain.Mode.SEARCH;
        s.stall = 0;
        s.until = Math.max(s.until, now + 360);
        s.spots = Hiding.findSpots(b.level(), s.lkp, 9);
        setPose(b, !b.canStand());
        planNext(b, now, false);
    }
}
