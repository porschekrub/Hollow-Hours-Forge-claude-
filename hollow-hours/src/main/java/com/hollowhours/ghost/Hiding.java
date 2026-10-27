package com.hollowhours.ghost;

import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.HideSeatEntity;
import com.hollowhours.net.HHNet;
import com.hollowhours.net.S2CHud;
import com.hollowhours.registry.HHEntities;
import com.hollowhours.util.Fx;
import com.hollowhours.util.R;
import com.hollowhours.util.W;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hiding spots (port of hide.js): sit inside a closet, or sneak + right-click a bed to slide under it.
 * While hidden and a ghost is near, moving your camera makes you breathe harder; a full breath bar = you gasp
 * and every ghost close by knows exactly where you are.
 */
public final class Hiding {
    private Hiding() {}

    public static final int BREATH_WARN = 55, BREATH_MAX = 100;

    public enum Kind { CLOSET, BED }

    public static final class State {
        public final Kind kind;
        public final String key;
        public final Entity seat;
        public final Vec3 spot;
        public final Vec3 exit;
        public final float baseYaw;
        public final BlockPos bedCell;
        public final Direction side;
        public final long t0;
        public float lastYaw = Float.NaN, lastPitch = Float.NaN;
        public double breath;
        public long gaspAt = -999, beatAt, hudAt, breathAt, lightAt;
        public boolean sneakReleased;
        /** last tick a vanilla dismount (sneak) was tried; holding sneak tries every tick */
        long lastDismountTry = Long.MIN_VALUE;

        State(Kind kind, String key, Entity seat, Vec3 spot, Vec3 exit, float baseYaw, BlockPos bedCell, Direction side, long t0) {
            this.kind = kind;
            this.key = key;
            this.seat = seat;
            this.spot = spot;
            this.exit = exit;
            this.baseYaw = baseYaw;
            this.bedCell = bedCell;
            this.side = side;
            this.t0 = t0;
        }
    }

    /** A place a ghost can check. */
    public record Spot(Kind kind, String key, Vec3 pos, Vec3 lookAt, @Nullable ClosetEntity closet, @Nullable BlockPos bedA, @Nullable BlockPos bedB, Direction.Axis axis) {}

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static boolean isHidden(ServerPlayer p) {
        return STATES.containsKey(p.getUUID());
    }

    @Nullable
    public static State stateOf(ServerPlayer p) {
        return STATES.get(p.getUUID());
    }

    @Nullable
    public static ServerPlayer occupantOf(ServerLevel level, String key) {
        for (ServerPlayer p : level.players()) {
            State s = STATES.get(p.getUUID());
            if (s != null && s.key.equals(key)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ entering
    public static boolean enterCloset(ServerPlayer p, ClosetEntity closet) {
        if (isHidden(p) || closet.isVehicle() || KillSequence.isVictim(p)) return false;
        long now = p.serverLevel().getGameTime();
        float yaw = closet.getYRot();
        Vec3 front = Vec3.directionFromRotation(0, yaw);
        Vec3 exit = exitFrom(p.level(), closet.position(), front, closet.getY());
        if (!p.startRiding(closet, true)) return false;
        State s = new State(Kind.CLOSET, "c:" + closet.getUUID(), closet, closet.position().add(0, 0.5, 0), exit, yaw, null, null, now);
        enter(p, s);
        closet.pulseDoor(ClosetEntity.DOOR_OPEN, 10);
        Fx.sound(p.level(), "ghost.closet.open", closet.position(), 0.8f, R.pitch(0.95, 1.1), 16);
        com.hollowhours.util.Later.run(10, () -> Fx.sound(p.level(), "ghost.closet.close", closet.position(), 0.7f, 1f, 16));
        return true;
    }

    public static boolean hideUnderBed(ServerPlayer p, BlockPos clicked) {
        if (isHidden(p) || KillSequence.isVictim(p) || p.isPassenger()) return false;
        Level level = p.level();
        BlockState bs = level.getBlockState(clicked);
        if (!(bs.getBlock() instanceof BedBlock)) return false;
        Direction facing = bs.getValue(BedBlock.FACING);
        BlockPos other = bs.getValue(BedBlock.PART) == BedPart.HEAD ? clicked.relative(facing.getOpposite()) : clicked.relative(facing);
        Direction.Axis axis = facing.getAxis();
        // slide in from the side the player stands on
        Vec3 c = Vec3.atBottomCenterOf(clicked);
        Direction side = axis == Direction.Axis.X
                ? (p.getZ() >= c.z ? Direction.SOUTH : Direction.NORTH)
                : (p.getX() >= c.x ? Direction.EAST : Direction.WEST);
        if (!W.passable(level, clicked.relative(side)) && W.passable(level, clicked.relative(side.getOpposite()))) side = side.getOpposite();
        float yaw = side.toYRot();
        long now = p.serverLevel().getGameTime();
        // the seat sits below the floor so the camera ends up just above the floor, under the mattress
        Vec3 cam = c.add(side.getStepX() * 0.32, 0, side.getStepZ() * 0.32);
        HideSeatEntity seat = HHEntities.HIDE_SEAT.get().create(level);
        if (seat == null) return false;
        seat.moveTo(cam.x, clicked.getY() + 0.12 - HideSeatEntity.EYE_DROP, cam.z, yaw, 0);
        level.addFreshEntity(seat);
        Fx.fade(p, 0x000000, 3, 4, 7);
        if (!p.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        Vec3 exit = exitFrom(level, c, new Vec3(side.getStepX(), 0, side.getStepZ()), clicked.getY());
        String key = "b:" + Math.min(clicked.asLong(), other.asLong());
        State s = new State(Kind.BED, key, seat, new Vec3(c.x, clicked.getY() + 0.3, c.z), exit, yaw, clicked, side, now);
        enter(p, s);
        Fx.sound(level, "dig.cloth", cam, 0.7f, 0.8f, 10);
        return true;
    }

    private static void enter(ServerPlayer p, State s) {
        STATES.put(p.getUUID(), s);
        s.breath = 0;
        p.addTag("ghost_hidden");
        long now = p.serverLevel().getGameTime();
        // ghosts that SEE you climb in remember where you hid
        for (GhostEntity g : GhostBrain.ghostsNear(p.serverLevel(), p.position(), 40)) {
            GhostBrain b = g.brain();
            if (b.isLeaving() || b.scareOnly) continue;
            if (Perception.canSee(g, p, 40)) {
                b.sawHide = s.key;
                b.sawHideAt = now;
                if (g.position().distanceTo(s.spot) <= 18 && b.canBeAlerted()) {
                    b.alert(s.key, "gasp", s.spot, now);
                    Fx.sound(p.level(), "ghost.growl", g.position(), 1.6f, R.pitch(0.6, 0.8), 30);
                    Fx.actionbar(p, Component.translatable("hollowhours.hide.heard"));
                }
            }
        }
        alertGhosts(p.serverLevel(), s.spot, s.kind == Kind.CLOSET ? 6 : 4, s.key, "noise");
        Fx.actionbar(p, Component.translatable(s.kind == Kind.CLOSET ? "hollowhours.hide.closet" : "hollowhours.hide.bed"));
        sendHud(p, s, -1);
    }

    private static Vec3 exitFrom(Level level, Vec3 base, Vec3 dir, double y) {
        for (double k : new double[]{1.0, 1.3, 0.75, 1.7}) {
            double x = base.x + dir.x * k, z = base.z + dir.z * k;
            BlockPos c = W.standCell(level, x, z, Mth.floor(y) + 1, 3, 2);
            if (c != null) return new Vec3(Mth.floor(x) + 0.5, c.getY(), Mth.floor(z) + 0.5);
        }
        return new Vec3(base.x + dir.x, y, base.z + dir.z);
    }

    // ------------------------------------------------------------------ leaving
    /** forced = dragged out by a ghost. */
    public static void exit(ServerPlayer p, boolean forced, boolean quiet, @Nullable Vec3 faceTo) {
        State s = STATES.remove(p.getUUID());
        if (s == null) return;
        p.removeTag("ghost_hidden");
        if (p.getVehicle() == s.seat) p.stopRiding();
        if (s.kind == Kind.BED) {
            Fx.fade(p, 0x000000, 2, 3, 6);
            if (s.seat.isAlive()) s.seat.discard();
        } else if (!forced && s.seat instanceof ClosetEntity c) {
            c.pulseDoor(ClosetEntity.DOOR_OPEN, 12);
            Fx.sound(p.level(), "ghost.closet.open", s.spot, 0.9f, R.pitch(0.95, 1.1), 16);
            com.hollowhours.util.Later.run(12, () -> Fx.sound(p.level(), "ghost.closet.close", s.spot, 0.8f, 1f, 16));
        }
        Vec3 e = s.exit;
        Vec3 look = faceTo != null ? faceTo : new Vec3(e.x + (e.x - s.spot.x), e.y + 1.6, e.z + (e.z - s.spot.z));
        double dx = look.x - e.x, dz = look.z - e.z, dy = look.y - (e.y + 1.62);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        p.connection.teleport(e.x, e.y, e.z, yaw, pitch);
        if (!quiet) alertGhosts(p.serverLevel(), s.spot, 5, s.key, "noise");
        HHNet.toPlayer(p, new S2CHud(0, 0, -1, 0, Sprint.staminaHudValue(p), Sprint.isDashing(p), false, Soul.get(p)));
    }

    /** Vanilla dismount (sneak). Returns false to cancel it (grace period after hiding). */
    public static boolean allowDismount(ServerPlayer p, Entity vehicle) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.seat != vehicle) return true;
        // Sneak + right-click puts you in, and the sneak key is usually still held: vanilla would pull you straight back
        // out. Holding sneak tries to dismount every tick, so a dismount only counts after the key was let go (no try
        // for a few ticks) and not in the first half second.
        long now = p.serverLevel().getGameTime();
        boolean stillHeld = now - s.lastDismountTry <= 3 || now - s.t0 < 10;
        s.lastDismountTry = now;
        if (stillHeld) return false;
        // leaving by choice: run our exit (moves the player in front of the closet / next to the bed)
        com.hollowhours.util.Later.run(1, () -> {
            if (STATES.get(p.getUUID()) == s) exit(p, false, false, null);
        });
        return true;
    }

    // ------------------------------------------------------------------ alerts
    public static void alertGhosts(ServerLevel level, Vec3 pos, double r, String key, String lvl) {
        long now = level.getGameTime();
        for (GhostEntity g : GhostBrain.ghostsNear(level, pos, r)) {
            GhostBrain b = g.brain();
            if (b.isLeaving() || b.scareOnly) continue;
            if (b.canBeAlerted()) b.alert(key, lvl, pos, now);
        }
    }

    // ------------------------------------------------------------------ per tick while hidden
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (!s.seat.isAlive() || p.getVehicle() != s.seat) {
            exit(p, false, true, null);
            return;
        }
        if (!p.isShiftKeyDown()) s.sneakReleased = true;
        if (s.kind == Kind.BED && now % 10 == 0 && !W.isBed(level.getBlockState(s.bedCell))) {
            exit(p, false, false, null);
            return;
        }
        // ---- breathing: turning the camera with a ghost close by
        float yaw = p.getYRot(), pitch = p.getXRot();
        double ang = 0;
        if (!Float.isNaN(s.lastYaw) && now - s.t0 > 12) ang = Math.abs(Mth.wrapDegrees(yaw - s.lastYaw)) + Math.abs(pitch - s.lastPitch);
        s.lastYaw = yaw;
        s.lastPitch = pitch;
        GhostEntity ng = null;
        double gd = 24;
        for (GhostEntity g : GhostBrain.ghostsNear(level, s.spot, 24)) {
            if (g.brain().isLeaving()) continue;
            double d = g.position().distanceTo(s.spot);
            if (d < gd) {
                gd = d;
                ng = g;
            }
        }
        if (gd < 8) s.breath += ang > 1.5 ? Math.min(6, (ang - 1.5) * 0.9) : -1.0;
        else s.breath -= 2.5;
        s.breath = Mth.clamp(s.breath, 0, BREATH_MAX);
        if (s.breath >= BREATH_MAX) {
            s.breath = 40;
            s.gaspAt = now;
            Fx.sound(level, "ghost.gasp", s.spot, 1.4f, R.pitch(0.95, 1.1), 18);
            Fx.actionbar(p, Component.translatable("hollowhours.hide.gasp"));
            alertGhosts(level, s.spot, 11, s.key, "gasp");
        } else if (s.breath >= BREATH_WARN && now - s.breathAt > 24) {
            s.breathAt = now;
            Fx.sound(level, "ghost.breath_hold", s.spot, 0.9f, R.pitch(0.95, 1.1), 10);
            alertGhosts(level, s.spot, 6, s.key, "breath");
        }
        // ---- a flashlight left on leaks light out of the hiding spot
        if (Flashlight.isOn(p) && gd < 12 && now - s.lightAt > 30) {
            s.lightAt = now;
            Fx.actionbar(p, Component.translatable("hollowhours.hide.light"));
            alertGhosts(level, s.spot, 12, s.key, "breath");
        }
        // ---- heartbeat only you hear
        if (ng != null && gd < 18 && now >= s.beatAt) {
            s.beatAt = now + (long) Mth.clamp(gd * 3, 9, 40);
            Fx.playSelf(p, "mob.warden.heartbeat", (float) Mth.clamp(1.6 - gd / 14, 0.35, 1.6), 1f);
        }
        if (now >= s.hudAt) {
            s.hudAt = now + 4;
            sendHud(p, s, ng == null ? -1 : (float) gd);
            if (gd >= 16 && (now - s.t0) % 80 < 4) Fx.actionbar(p, Component.translatable("hollowhours.hide.idle"));
        }
        if (now % 20 == 0) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false, false));
    }

    private static void sendHud(ServerPlayer p, State s, float gd) {
        HHNet.toPlayer(p, new S2CHud(s.kind == Kind.CLOSET ? 1 : 2, (float) s.breath, gd, s.baseYaw, Sprint.staminaHudValue(p), false, false, Soul.get(p)));
    }

    // ------------------------------------------------------------------ hiding spots around a point (for searching ghosts)
    public static List<Spot> findSpots(ServerLevel level, Vec3 c, int r) {
        List<Spot> out = new ArrayList<>();
        for (ClosetEntity e : level.getEntitiesOfClass(ClosetEntity.class, new AABB(c, c).inflate(r + 3))) out.add(closetSpot(e));
        Set<String> seen = new HashSet<>();
        BlockPos base = BlockPos.containing(c);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -2; dy <= 2; dy++) {
            BlockPos q = base.offset(dx, dy, dz);
            BlockState bs = level.getBlockState(q);
            if (!(bs.getBlock() instanceof BedBlock)) continue;
            Direction facing = bs.getValue(BedBlock.FACING);
            BlockPos other = bs.getValue(BedBlock.PART) == BedPart.HEAD ? q.relative(facing.getOpposite()) : q.relative(facing);
            String key = "b:" + Math.min(q.asLong(), other.asLong());
            if (!seen.add(key)) continue;
            Vec3 mid = new Vec3((q.getX() + other.getX()) / 2.0 + 0.5, q.getY(), (q.getZ() + other.getZ()) / 2.0 + 0.5);
            out.add(new Spot(Kind.BED, key, mid, mid.add(0, 0.4, 0), null, q, other, facing.getAxis()));
        }
        out.sort(Comparator.comparingDouble(sp -> sp.pos.distanceTo(c)));
        return out.size() > 6 ? new ArrayList<>(out.subList(0, 6)) : out;
    }

    public static Spot closetSpot(ClosetEntity e) {
        Vec3 f = Vec3.directionFromRotation(0, e.getYRot());
        return new Spot(Kind.CLOSET, "c:" + e.getUUID(), e.position(), e.position().add(f.x * 0.4, 1.45, f.z * 0.4), e, null, null, Direction.Axis.Y);
    }

    public static void cleanup(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        p.removeTag("ghost_hidden");
        if (s != null && s.kind == Kind.BED && s.seat.isAlive()) s.seat.discard();
    }
}
