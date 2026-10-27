package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;
import java.util.function.Function;

/** Walking with vanilla path finding, with "stuck" detection and safe teleports. */
public final class Mover {
    final HelperEntity h;
    private Vec3 lastGoal;
    private Vec3 progressPos = Vec3.ZERO;
    private int progressTick;
    private int noPathTicks;
    private int repath;

    Mover(HelperEntity h) {
        this.h = h;
    }

    public enum Res { MOVING, ARRIVED, NO_PATH }

    /** Walks toward a point; ARRIVED within `arrive` blocks (horizontal) and 2.5 vertical. */
    public Res moveTo(Vec3 goal, double speed, double arrive) {
        Vec3 p = h.position();
        double dxz = Math.hypot(goal.x - p.x, goal.z - p.z);
        if (dxz <= arrive && Math.abs(goal.y - p.y) <= 2.5) {
            stop();
            return Res.ARRIVED;
        }
        boolean newGoal = lastGoal == null || lastGoal.distanceToSqr(goal) > 1.5;
        if (newGoal || h.getNavigation().isDone() || --repath <= 0) {
            lastGoal = goal;
            repath = 30;
            Path path = h.getNavigation().createPath(goal.x, goal.y, goal.z, 0);
            if (path == null) {
                noPathTicks += 10;
                if (noPathTicks > 40) return Res.NO_PATH;
            } else {
                noPathTicks = 0;
                h.getNavigation().moveTo(path, speed);
                if (newGoal) resetProgress();
            }
        }
        return Res.MOVING;
    }

    public Res moveTo(BlockPos b, double speed, double arrive) {
        return moveTo(Vec3.atBottomCenterOf(b), speed, arrive);
    }

    public Res moveTo(Entity e, double speed, double arrive) {
        return moveTo(e.position(), speed, arrive);
    }

    public void stop() {
        h.getNavigation().stop();
        lastGoal = null;
    }

    public void resetProgress() {
        progressPos = h.position();
        progressTick = h.tickCount;
        noPathTicks = 0;
    }

    /** True when the helper has not got closer for `limit` ticks. */
    public boolean stuck(int limit) {
        if (h.position().distanceToSqr(progressPos) > 1.0) {
            progressPos = h.position();
            progressTick = h.tickCount;
            return false;
        }
        return h.tickCount - progressTick > limit;
    }

    public void look(Vec3 at) {
        h.getLookControl().setLookAt(at.x, at.y, at.z, 30f, 30f);
    }

    public void look(BlockPos b) {
        look(Vec3.atCenterOf(b));
    }

    // ------------------------------------------------------------------ safe spots / teleports
    public static boolean passable(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    public static boolean standable(Level l, BlockPos p) {
        BlockState below = l.getBlockState(p.below());
        return below.isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP) && passable(l, p) && passable(l, p.above())
                && l.getFluidState(p).isEmpty();
    }

    /** A free spot next to `base` (the block itself counts), searching a little up and down. */
    @Nullable
    public static BlockPos safeSpotNear(Level l, BlockPos base, int r) {
        int[][] offs = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int ring = 0; ring <= r; ring++)
            for (int[] o : offs)
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos p = base.offset(o[0] * Math.max(1, ring), dy, o[1] * Math.max(1, ring));
                    if (standable(l, p)) return p;
                }
        return null;
    }

    public boolean teleportNear(BlockPos target) {
        BlockPos p = safeSpotNear(h.level(), target, 3);
        if (p == null) return false;
        poof();
        h.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        h.getNavigation().stop();
        h.fallDistance = 0;
        resetProgress();
        h.level().playSound(null, p, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.4f, 1.4f);
        return true;
    }

    void poof() {
        if (h.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 0.8, h.getZ(), 8, 0.3, 0.5, 0.3, 0);
    }

    /** Next to the player, in any dimension. */
    public boolean bringTo(ServerPlayer p) {
        if (p.level() != h.level()) {
            ServerLevel dest = p.serverLevel();
            BlockPos spot = safeSpotNear(dest, p.blockPosition(), 2);
            Vec3 at = spot != null ? Vec3.atBottomCenterOf(spot) : p.position();
            Entity moved = h.changeDimension(dest, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity e, ServerLevel cur, ServerLevel to, float yaw, Function<Boolean, Entity> repos) {
                    Entity n = repos.apply(false);
                    n.moveTo(at.x, at.y, at.z, n.getYRot(), n.getXRot());
                    return n;
                }

                @Override
                public boolean playTeleportSound(ServerPlayer pl, ServerLevel s, ServerLevel d) {
                    return false;
                }
            });
            return moved != null;
        }
        return teleportNear(p.blockPosition());
    }
}
