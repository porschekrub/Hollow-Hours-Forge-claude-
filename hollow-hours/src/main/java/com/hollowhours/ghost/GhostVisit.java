package com.hollowhours.ghost;

import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.entity.GhostAnim;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.Later;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ghosts at your door (port of intrude.js knock/watch): it knocks on a door or window while you hide inside.
 * Sometimes it only knocks and vanishes, sometimes it stares through the glass and lunges when you look,
 * sometimes the knocking turns into smashing its way in.
 */
public final class GhostVisit {
    private final GhostBrain b;
    GhostBreach.Cand cand;
    @Nullable UUID player;
    long t0, loop0, end;
    boolean escalate, visit, door;
    int rounds, watch, seen;
    Vec3 standAt;

    GhostVisit(GhostBrain b) {
        this.b = b;
    }

    // ------------------------------------------------------------------ candidate openings around a player
    /** Doors/windows near the player whose outside is not an enclosed room (port of knockTargets). */
    public static List<GhostBreach.Cand> knockTargets(ServerLevel level, ServerPlayer p, boolean relaxed) {
        Vec3 P = p.position();
        List<GhostBreach.Cand> out = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        BlockPos base = p.blockPosition();
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) for (int dy = -2; dy <= 3; dy++) {
            BlockPos q = base.offset(dx, dy, dz);
            BlockState s = level.getBlockState(q);
            Breach.Info info = Breach.classify(s);
            if (info == null || info.kind().equals("trapdoor") || info.kind().equals("gate")) continue;
            if (info.isDoorLike() && Breach.isOpen(s)) continue;
            BlockPos pos = Breach.basePos(s, q);
            if (!seen.add(pos.asLong())) continue;
            GhostBreach.Cand c = GhostBreach.sidesOf(level, pos, info, s, null, P);
            if (c == null) continue;
            double dp = Math.hypot(pos.getX() + 0.5 - P.x, pos.getZ() + 0.5 - P.z);
            if (dp < (relaxed ? 1.2 : 2.5)) continue;
            if (!relaxed && enclosed(level, c.stand())) continue;
            double score = Math.abs(dp - 6) + (info.fam().equals("glass") ? -1.5 : 0) + R.rand(0, 3);
            out.add(new GhostBreach.Cand(c.pos(), info, c.dir(), c.stand(), c.inside(), false, score));
        }
        out.sort((a, c) -> Double.compare(a.score(), c.score()));
        return out;
    }

    static boolean enclosed(ServerLevel level, BlockPos c) {
        boolean roof = false;
        for (int dy = 2; dy <= 8; dy++) {
            BlockState s = level.getBlockState(c.above(dy));
            if (!s.isAir() && !(s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) {
                roof = true;
                break;
            }
        }
        if (!roof) return false;
        int walls = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int k = 1; k <= 6; k++) {
                if (W.solid(level, c.offset(d[0] * k, 1, d[1] * k))) {
                    walls++;
                    break;
                }
            }
        }
        return walls >= 4;
    }

    public static Vec3 lookPoint(GhostBreach.Cand c) {
        return Vec3.atBottomCenterOf(c.pos()).add(0, c.info() != null && c.info().kind().equals("door") ? 1.0 : 0.5, 0);
    }

    /** Back away from the opening so a big body's face/hand just touches it. */
    public static Vec3 reachPoint(ServerLevel level, GhostBreach.Cand c, double reach) {
        BlockPos s = c.stand();
        Vec3 d = c.dir();
        Vec3 C0 = new Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        double want = reach - 0.6;
        if (want <= 0) return C0;
        double room = 0.35;
        for (int k = 1; k <= Math.ceil(want); k++) {
            BlockPos cell = new BlockPos(s.getX() - (int) Math.signum(d.x) * k, s.getY(), s.getZ() - (int) Math.signum(d.z) * k);
            if (!(W.passable(level, cell) && W.passable(level, cell.above()) && W.solid(level, cell.below()))) break;
            room = k + 0.35;
        }
        double bk = Math.min(want, room);
        return new Vec3(C0.x - Math.signum(d.x) * bk, s.getY(), C0.z - Math.signum(d.z) * bk);
    }

    public static boolean doorOpenable(ServerLevel level, GhostBreach.Cand c) {
        if (c.info() == null || !c.info().kind().equals("door") || !c.info().fam().equals("wood")) return false;
        BoardsEntity boards = BoardsEntity.at(level, c.pos());
        if (boards != null && boards.count() > 0) return false;
        return !Breach.isOpen(level.getBlockState(c.pos()));
    }

    public static void setDoor(ServerLevel level, BlockPos pos, boolean open) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof DoorBlock door) door.setOpen(null, level, s, pos, open);
    }

    public static void scareSoundAt(ServerLevel level, ServerPlayer p, GhostBreach.Cand c, String key) {
        Vec3 L = lookPoint(c), d = c.dir();
        Vec3 loc = L;
        float vol = 1.3f;
        switch (key) {
            case "roof" -> {
                loc = p.position().add(0, 3.6, 0);
                vol = 1.4f;
            }
            case "bell", "scream" -> {
                loc = new Vec3(L.x - d.x * 18, L.y + 2, L.z - d.z * 18);
                vol = 3f;
            }
            case "wallknock" -> {
                loc = new Vec3((L.x + p.getX()) / 2, p.getY() + 1, (L.z + p.getZ()) / 2);
                vol = 1.2f;
            }
            default -> {}
        }
        Fx.sound(level, "ghost.scare." + key, loc, vol, R.pitch(0.95, 1.03), 40);
    }

    // ------------------------------------------------------------------ spawning a visitor
    @Nullable
    private static GhostEntity spawnAt(ServerPlayer p, GhostKind kind, GhostBreach.Cand c, boolean watchPose) {
        ServerLevel level = p.serverLevel();
        Vec3 T = reachPoint(level, c, watchPose ? kind.inspectReach * 0.6 + 0.05 : kind.knockReach * 0.6);
        GhostEntity g = HHEntities.ghost(kind).create(level);
        if (g == null) return null;
        g.moveTo(T.x, T.y, T.z, 0, 0);
        level.addFreshEntity(g);
        GhostBrain b = g.brain();
        b.born = level.getGameTime() - 200;
        b.stalking = false;
        b.mod = Variety.rollMod();
        b.power = Director.forcedPower > 0 ? Director.forcedPower : GhostBreach.rollPower(false);
        GhostVisit v = b.visit;
        v.cand = c;
        v.player = p.getUUID();
        v.standAt = T;
        b.targetId = p.getUUID();
        if (kind.height > 2.05 && !b.canStand()) g.setCrawling(true);
        b.face(lookPoint(c));
        return g;
    }

    /** Knock (escalate = turns into a real break-in). */
    public static boolean startKnock(ServerPlayer p, GhostKind kind, boolean escalate) {
        List<GhostBreach.Cand> cands = knockTargets(p.serverLevel(), p, false);
        if (cands.isEmpty()) return false;
        GhostBreach.Cand c = cands.get(R.randInt(0, Math.min(3, cands.size()) - 1));
        GhostEntity g = spawnAt(p, kind, c, false);
        if (g == null) return false;
        GhostVisit v = g.brain().visit;
        long now = p.serverLevel().getGameTime();
        g.brain().mode = GhostBrain.Mode.KNOCK;
        g.setAnim(GhostAnim.KNOCK);
        v.t0 = now;
        v.loop0 = now + 6;
        v.end = now + (long) R.rand(14, 26) * 20;
        v.escalate = escalate;
        v.watch = 0;
        return true;
    }

    /** Breaks in right away (2-3 hard knocks, then smashing). */
    public static boolean startBreakIn(ServerPlayer p, GhostKind kind) {
        List<GhostBreach.Cand> cands = knockTargets(p.serverLevel(), p, false);
        if (cands.isEmpty()) cands = knockTargets(p.serverLevel(), p, true);
        if (cands.isEmpty()) return false;
        GhostEntity g = spawnAt(p, kind, cands.get(0), false);
        if (g == null) return false;
        GhostVisit v = g.brain().visit;
        long now = p.serverLevel().getGameTime();
        g.brain().mode = GhostBrain.Mode.KNOCK;
        g.setAnim(GhostAnim.KNOCK);
        v.t0 = now;
        v.loop0 = now + 4;
        v.end = now + 70;
        v.escalate = true;
        return true;
    }

    /** Indoor scare: knocks once or twice and is gone 7 s later. Not a real hunt. */
    public static boolean startKnockVisit(ServerPlayer p, GhostKind kind) {
        List<GhostBreach.Cand> cands = knockTargets(p.serverLevel(), p, false);
        if (cands.isEmpty()) return false;
        GhostEntity g = spawnAt(p, kind, cands.get(R.randInt(0, Math.min(3, cands.size()) - 1)), false);
        if (g == null) return false;
        GhostBrain b = g.brain();
        b.scareOnly = true;
        GhostVisit v = b.visit;
        long now = p.serverLevel().getGameTime();
        b.mode = GhostBrain.Mode.KNOCK;
        g.setAnim(GhostAnim.KNOCK);
        v.t0 = now;
        v.loop0 = now + 6;
        v.visit = true;
        v.rounds = R.chance(0.5) ? 1 : 2;
        v.end = v.loop0 + (v.rounds - 1) * 80L + 12 + 140;
        v.escalate = false;
        return true;
    }

    /** Strange sound, then something stands at your window (or your door creaks open). Lunges when you see it. */
    public static boolean startWatcher(ServerPlayer p, GhostKind kind, @Nullable String key) {
        ServerLevel level = p.serverLevel();
        List<GhostBreach.Cand> cands = knockTargets(level, p, false);
        GhostBreach.Cand c = null;
        boolean door = false;
        for (GhostBreach.Cand q : cands) if (q.info() != null && q.info().fam().equals("glass")) {
            c = q;
            break;
        }
        if (c == null) for (GhostBreach.Cand q : cands) if (doorOpenable(level, q)) {
            c = q;
            door = true;
            break;
        }
        if (c == null) return false;
        scareSoundAt(level, p, c, key != null ? key : Director.nextScareSound(p));
        final GhostBreach.Cand fc = c;
        final boolean fdoor = door;
        Later.run((int) R.rand(50, 90), () -> {
            if (!p.isAlive() || p.level() != level) return;
            GhostEntity g = spawnAt(p, kind, fc, true);
            if (g == null) return;
            GhostBrain b = g.brain();
            b.scareOnly = true;
            GhostVisit v = b.visit;
            long now = level.getGameTime();
            b.mode = GhostBrain.Mode.WATCH;
            g.setAnim(GhostAnim.INSPECT);
            v.t0 = now;
            v.end = now + 30 * 20;
            v.seen = 0;
            v.door = fdoor;
            if (fdoor) {
                setDoor(level, fc.pos(), true);
                Fx.sound(level, "random.door_open", lookPoint(fc), 1f, 0.5f, 24);
            }
            b.face(p.getEyePosition());
        });
        return true;
    }

    // ------------------------------------------------------------------ tick
    void tick(long now) {
        if (b.mode == GhostBrain.Mode.KNOCK) tickKnock(now);
        else tickWatch(now);
    }

    @Nullable
    private ServerPlayer player() {
        if (player == null) return null;
        var p = b.level().getPlayerByUUID(player);
        return p instanceof ServerPlayer sp && sp.isAlive() ? sp : null;
    }

    private void knockSound(ServerLevel level, Breach.Info info) {
        Vec3 loc = lookPoint(cand);
        switch (info.fam()) {
            case "glass" -> Fx.sound(level, "ghost.knock.glass", loc, 1.8f, R.pitch(0.9, 1.1), 28);
            case "iron" -> Fx.sound(level, "ghost.knock.metal", loc, 1.8f, R.pitch(0.85, 1.05), 32);
            default -> Fx.sound(level, "ghost.knock.wood", loc, 2f, R.pitch(0.9, 1.08), 32);
        }
    }

    private void tickKnock(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        ServerPlayer p = player();
        if (p == null || p.level() != level) {
            g.discard();
            return;
        }
        BlockState s = level.getBlockState(cand.pos());
        Breach.Info info = Breach.classify(s);
        if (info == null || (info.isDoorLike() && Breach.isOpen(s))) {
            vanish(p, "open");
            return;
        }
        g.setDeltaMovement(0, g.getDeltaMovement().y, 0);
        if (now % 10 == 0) b.face(Vec3.atBottomCenterOf(cand.pos()).add(0, info.kind().equals("door") ? 1.4 : 0.6, 0));
        long t = (now - loop0) % 80;
        boolean knocking = now >= loop0 && !(visit && now - loop0 >= rounds * 80L);
        if (knocking) {
            if (t == 2 || t == 7 || t == 12) knockSound(level, info);
            if (t == 34 && R.chance(0.55)) Fx.sound(level, R.chance(0.5) ? "ghost.scratch" : "ghost.breath", lookPoint(cand), 1.1f, R.pitch(0.8, 1.0), 20);
            if (t == 22 && R.chance(0.6) && p.position().distanceTo(Vec3.atCenterOf(cand.pos())) < 16) {
                boolean glass = info.fam().equals("glass");
                Fx.actionbar(p, Component.translatable((glass && R.chance(0.4) ? "hollowhours.knock.window." + R.randInt(0, 3) : "hollowhours.knock.door." + R.randInt(0, 7))));
            }
        }
        boolean hid = Hiding.isHidden(p);
        if (!hid && info.fam().equals("glass") && p.position().distanceTo(Vec3.atCenterOf(cand.pos())) < 11) {
            if (W.lookingAtBlock(p, cand.pos(), 12) || W.lookDot(p, g.position()) > 0.96) watch++;
            else watch = Math.max(0, watch - 1);
            if (watch >= 8) {
                if (escalate) {
                    Fx.sound(level, "ghost.growl", lookPoint(cand), 2f, 0.7f, 32);
                    for (ServerPlayer q : b.playersNear(20)) Fx.actionbar(q, Component.translatable("hollowhours.knock.seen"));
                    toBreakIn(p, now);
                    return;
                }
                vanish(p, "seen");
                return;
            }
        }
        if (!hid && p.position().distanceTo(g.position()) < 1.9) {
            if (escalate) toBreakIn(p, now);
            else vanish(p, "close");
            return;
        }
        if (now >= end) {
            if (escalate) {
                for (ServerPlayer q : b.playersNear(20)) Fx.actionbar(q, Component.translatable("hollowhours.knock.breakin"));
                toBreakIn(p, now);
                return;
            }
            vanish(p, visit ? "visit" : "timeout");
        }
    }

    private void toBreakIn(ServerPlayer p, long now) {
        b.scareOnly = false;
        b.targetId = p.getUUID();
        b.breach.player = p;
        b.breach.forSearch = false;
        b.breach.cand = cand;
        b.breach.alt = new ArrayList<>();
        b.breach.sub = GhostBreach.Sub.APPROACH;
        b.breach.t0 = now;
        b.mode = GhostBrain.Mode.BREACH;
        b.chaseStart = now;
    }

    private void vanish(ServerPlayer p, String why) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        switch (why) {
            case "seen", "close" -> {
                Jumpscares.scare(p, b.kind.n, "close".equals(why) ? 1 : 0);
                g.discard();
            }
            case "open" -> {
                g.discard();
                Later.run(25, () -> {
                    Vec3 v = p.getViewVector(1f);
                    Fx.sound(level, "ghost.breath", new Vec3(p.getX() - v.x * 1.5, p.getY() + 1.4, p.getZ() - v.z * 1.5), 1.2f, 0.8f, 8);
                    Fx.actionbar(p, Component.literal("..."));
                });
            }
            case "visit" -> {
                Fx.sound(level, "ghost.breath", lookPoint(cand), 0.7f, 0.75f, 16);
                Fx.puff(level, ParticleTypes.SMOKE, g.position(), 0.4);
                g.discard();
            }
            default -> {
                Breach.Info info = cand.info();
                String snd = info != null && info.fam().equals("glass") ? "ghost.knock.glass" : info != null && info.fam().equals("wood") ? "ghost.bang.wood" : "ghost.bang.metal";
                Fx.sound(level, snd, lookPoint(cand), 1.6f, 0.6f, 40);
                Fx.shakeNear(level, lookPoint(cand), 10, 0.4f, 8);
                g.discard();
            }
        }
    }

    private void tickWatch(long now) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        ServerPlayer p = player();
        if (p == null || p.level() != level) {
            endWatch(false);
            return;
        }
        g.setDeltaMovement(0, g.getDeltaMovement().y, 0);
        if (now % 10 == 0) b.face(p.getEyePosition());
        boolean hid = Hiding.isHidden(p);
        double d = p.position().distanceTo(g.position());
        if (!hid && d < 2.2) {
            lunge(p);
            return;
        }
        if (!hid && d < 20) {
            Vec3 head = g.position().add(0, Math.min(b.kind.height * 0.82, 3.4), 0);
            boolean looking = W.lookDot(p, g.position()) > 0.93 || (cand.info() != null && cand.info().fam().equals("glass") && W.lookingAtBlock(p, cand.pos(), 20));
            if (looking && (W.los(level, p.getEyePosition(), head) || W.los(level, p.getEyePosition(), g.position().add(0, 1.2, 0)))) seen++;
            else seen = Math.max(0, seen - 1);
            if (seen >= 3) {
                lunge(p);
                return;
            }
        }
        if (now >= end) endWatch(true);
    }

    /** It saw you see it: it throws itself at you, face first. */
    private void lunge(ServerPlayer p) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        Vec3 v = p.getViewVector(1f);
        double hl = Math.max(1e-6, Math.hypot(v.x, v.z));
        Vec3 spot = null;
        for (double d = 1.3 + 0.35 * Math.min(b.kind.height, 4); d >= 1.2 && spot == null; d -= 0.4) {
            double x = p.getX() + v.x / hl * d, z = p.getZ() + v.z / hl * d;
            if (W.passable(level, x, p.getY(), z) && W.passable(level, x, p.getY() + 1, z)) spot = new Vec3(x, p.getY(), z);
        }
        if (spot != null) {
            g.noPhysics = true;
            b.teleportFacing(spot, p.getEyePosition());
            g.playAnim(GhostAnim.LUNGE);
        }
        Jumpscares.scare(p, b.kind.n, 1);
        b.mode = GhostBrain.Mode.LEAVE;
        Later.run(spot != null ? 14 : 1, () -> endWatch(true));
    }

    private void endWatch(boolean smoke) {
        GhostEntity g = b.g;
        ServerLevel level = b.level();
        if (smoke) for (int k = 0; k < 6; k++) Fx.puff(level, ParticleTypes.SMOKE, g.position(), 0.5);
        g.discard();
        if (door && cand != null) {
            BlockPos pos = cand.pos();
            Later.run(30, () -> {
                if (Breach.isOpen(level.getBlockState(pos))) {
                    setDoor(level, pos, false);
                    Fx.sound(level, "random.door_close", Vec3.atCenterOf(pos), 1f, 0.6f, 24);
                }
            });
        }
    }
}
