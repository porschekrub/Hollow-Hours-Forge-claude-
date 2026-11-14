package com.helperbuddy.brain;

import com.helperbuddy.compat.Compat;
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

    /**
     * Somewhere to stand in the column (x, z), as close to nearY as possible. The chunk is loaded for it. In a
     * dimension with a ceiling (the Nether) it never picks the roof: the top of the world there is bedrock.
     */
    @Nullable
    public static BlockPos landNear(ServerLevel l, int x, int z, int nearY) {
        return landNear(l, x, z, nearY, true);
    }

    /** surface = false: stays at its depth when it can (a miner moving on underground). */
    @Nullable
    public static BlockPos landNear(ServerLevel l, int x, int z, int nearY, boolean surface) {
        l.getChunk(x >> 4, z >> 4);
        int min = l.getMinBuildHeight() + 1, max = l.getMaxBuildHeight() - 2;
        boolean roof = l.dimensionType().hasCeiling();
        if (roof) max = Math.min(max, l.getMinBuildHeight() + l.dimensionType().logicalHeight() - 3);
        else if (surface) {
            int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, top, z);
            if (standable(l, p)) return p;
            // under a tree, in a cave, on a ledge: the open spot closest to where it was meant to be
            if (Math.abs(nearY - top) > 48) nearY = top;
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= 80; d++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int y = nearY + d * sign;
                if (y >= min && y <= max && standable(l, p.set(x, y, z))) return p.immutable();
                if (d == 0) break;
            }
        }
        return null;
    }

    /** landNear, trying the spot itself and then rings around it (water, lava and cliffs are stepped around). */
    @Nullable
    public static BlockPos landAround(ServerLevel l, int x, int z, int nearY) {
        return landAround(l, x, z, nearY, true);
    }

    @Nullable
    public static BlockPos landAround(ServerLevel l, int x, int z, int nearY, boolean surface) {
        BlockPos p = landNear(l, x, z, nearY, surface);
        if (p == null && !surface) p = landNear(l, x, z, nearY, true);
        if (p != null) return p;
        for (int r = 5; r <= 25; r += 10)
            for (int a = 0; a < 8; a++) {
                p = landNear(l, x + (int) Math.round(Math.cos(a * Math.PI / 4) * r), z + (int) Math.round(Math.sin(a * Math.PI / 4) * r), nearY, true);
                if (p != null) return p;
            }
        return null;
    }

    /** Far away in one hop (exploring, travelling): lands on safe ground and keeps the place loaded. */
    public boolean jumpTo(BlockPos spot) {
        if (!(h.level() instanceof ServerLevel sl)) return false;
        com.helperbuddy.Helpers.hold(sl, spot);
        poof();
        h.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        h.getNavigation().stop();
        h.fallDistance = 0;
        resetProgress();
        sl.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.4f, 1.4f);
        return true;
    }

    public boolean teleportNear(BlockPos target) {
        BlockPos p = safeSpotNear(h.level(), target, 3);
        if (p == null) return false;
        // the chunks there stay loaded until the helper's own ticket takes over (else it could be unloaded on arrival)
        if (h.level() instanceof ServerLevel sl) com.helperbuddy.Helpers.hold(sl, p);
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
            com.helperbuddy.Helpers.hold(dest, BlockPos.containing(at));
            Entity moved = Compat.toDimension(h, dest, at);
            return moved != null;
        }
        return teleportNear(p.blockPosition());
    }
}
