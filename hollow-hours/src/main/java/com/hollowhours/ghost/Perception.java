package com.hollowhours.ghost;

import com.hollowhours.entity.GhostEntity;
import com.hollowhours.util.W;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a ghost can sense (port of hide.js): it has to SEE you (nothing blocking, inside its range) or HEAR you
 * (running, walking, jumping, landing, doors, breaking/placing blocks, hitting things). Sneaking is almost silent.
 */
public final class Perception {
    private Perception() {}

    private static final class Noise {
        Vec3 last;
        boolean ground = true;
        double vy;
        double radius;
    }

    private record Impulse(String dim, Vec3 at, double radius, UUID who, long t) {}

    private static final Map<UUID, Noise> NOISE = new HashMap<>();
    private static final ArrayDeque<Impulse> IMPULSES = new ArrayDeque<>();

    public record Sense(ServerPlayer player, double dist, String how) {}

    /** Called every tick for every player: how loud is this player right now? */
    public static void playerTick(ServerPlayer p) {
        Noise n = NOISE.computeIfAbsent(p.getUUID(), k -> new Noise());
        Vec3 l = p.position();
        double sp = n.last == null ? 0 : Math.hypot(l.x - n.last.x, l.z - n.last.z);
        boolean onG = p.onGround();
        double vy = p.getDeltaMovement().y;
        double r = 0;
        if (sp > 0.6) r = 0; // teleport
        else if (sp > 0.025) r = p.isShiftKeyDown() ? 2.5 : (p.isSprinting() || sp > 0.2) ? 16 : sp > 0.09 ? 8 : 5;
        if (n.ground && !onG && vy > 0.25) r = Math.max(r, 9);          // jump
        if (!n.ground && onG && n.vy < -0.55) r = Math.max(r, 12);       // hard landing
        if (Sprint.isDashing(p)) r = Math.max(r, 26);
        n.radius = r;
        n.ground = onG;
        n.vy = vy;
        n.last = l;
    }

    /** A sudden noise made by a player (door, block, hit ...). */
    public static void impulse(ServerPlayer p, Vec3 at, double radius) {
        IMPULSES.addLast(new Impulse(p.level().dimension().location().toString(), at, radius, p.getUUID(), p.serverLevel().getGameTime()));
        while (IMPULSES.size() > 64) IMPULSES.removeFirst();
    }

    public static void forget(ServerPlayer p) {
        NOISE.remove(p.getUUID());
    }

    public static double noiseOf(ServerPlayer p) {
        Noise n = NOISE.get(p.getUUID());
        return n == null ? 0 : n.radius;
    }

    public static boolean canSee(GhostEntity g, ServerPlayer p, double maxD) {
        Vec3 e = g.eye();
        if (e.distanceTo(p.position()) > maxD) return false;
        return W.los(g.level(), e, p.position().add(0, 1.5, 0)) || W.los(g.level(), e, p.position().add(0, 0.6, 0));
    }

    private static boolean inCone(float yawDeg, Vec3 from, Vec3 to, double deg) {
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double dx = to.x - from.x, dz = to.z - from.z, d = Math.max(1e-6, Math.hypot(dx, dz));
        return (fx * dx + fz * dz) / d >= Math.cos(Math.toRadians(deg));
    }

    private static boolean hears(GhostEntity g, ServerPlayer p, long now) {
        Vec3 G = g.position(), L = p.position();
        double d = G.distanceTo(L);
        double r = noiseOf(p);
        if (r > 0 && d <= r) {
            if (d <= r * 0.6 || W.los(g.level(), g.eye(), L.add(0, 1, 0))) return true; // walls muffle
        }
        String dim = g.level().dimension().location().toString();
        for (Impulse im : IMPULSES) {
            if (!im.who.equals(p.getUUID()) || now - im.t > 12 || !im.dim.equals(dim)) continue;
            if (G.distanceTo(im.at) <= im.radius) return true;
        }
        return false;
    }

    /**
     * Which player (not hidden) does this ghost notice right now, and how. Updates the brain's last known position.
     * searching = narrower view cone and shorter ranges (it is sniffing around, not hunting).
     */
    @Nullable
    public static Sense perceive(GhostEntity g, GhostBrain b, long now, boolean searching) {
        if (!(g.level() instanceof ServerLevel sl)) return null;
        ServerPlayer best = null;
        double bestD = 1e9;
        String how = null;
        for (ServerPlayer p : sl.players()) {
            if (!GhostBrain.targetable(p) || Hiding.isHidden(p)) continue;
            double d = g.position().distanceTo(p.position());
            if (d > 64) continue;
            String h = null;
            if (d <= 2.3) h = "touch";
            else {
                boolean lit = Flashlight.isOn(p);
                boolean marked = p.getTags().contains("ghost_marked");
                boolean sneak = p.isShiftKeyDown();
                double range = searching ? (lit ? 34 : sneak ? 9 : 22) : (lit ? 48 : sneak ? 24 : 42);
                range *= b.visMult();
                boolean cone = !searching || d <= 4 || lit || inCone(b.faceYaw(), g.position(), p.position(), 75);
                if (d <= range && cone && canSee(g, p, range)) h = "see";
                else if (marked && d <= 32) h = "see";
                else if (hears(g, p, now)) h = "hear";
            }
            if (h != null && d < bestD) {
                best = p;
                bestD = d;
                how = h;
            }
        }
        if (best != null) {
            b.lkp = best.position();
            b.lkpAt = now;
            b.lastDetect = now;
        }
        return best == null ? null : new Sense(best, bestD, how);
    }
}
