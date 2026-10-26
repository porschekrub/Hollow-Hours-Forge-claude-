package com.hollowhours.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import javax.annotation.Nullable;

/** World helpers shared by all systems (port of util.js / hide.js los). */
public final class W {
    private W() {}

    public static BlockState at(Level level, double x, double y, double z) {
        return level.getBlockState(BlockPos.containing(x, y, z));
    }

    /** Can an entity walk through this block (air, plants, torches, open doors ...)? */
    public static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return !s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && s.getCollisionShape(level, pos).isEmpty();
        Block b = s.getBlock();
        if (b instanceof DoorBlock || b instanceof FenceGateBlock) return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN);
        if (b instanceof TrapDoorBlock) return true;
        return s.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean passable(Level level, double x, double y, double z) {
        return passable(level, BlockPos.containing(x, y, z));
    }

    public static boolean solid(Level level, BlockPos pos) {
        return !passable(level, pos);
    }

    /** First cell (from yTop down `depth` cells) with `h` free cells and a solid floor. */
    @Nullable
    public static BlockPos standCell(Level level, double x, double z, int yTop, int depth, int h) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int y = yTop; y >= yTop - depth; y--) {
            boolean ok = true;
            for (int k = 0; k < h; k++) {
                if (!passable(level, new BlockPos(bx, y + k, bz))) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            BlockPos under = new BlockPos(bx, y - 1, bz);
            if (!passable(level, under)) return new BlockPos(bx, y, bz);
        }
        return null;
    }

    /** Highest non-air block between fromY (inclusive) and toY. */
    @Nullable
    public static BlockPos topBlock(Level level, double x, double z, int fromY, int toY) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = Math.min(fromY, level.getMaxBuildHeight() - 1), bot = Math.max(toY, level.getMinBuildHeight());
        for (int y = top; y >= bot; y--) {
            BlockPos p = new BlockPos(bx, y, bz);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && !s.is(Blocks.LIGHT)) return p;
        }
        return null;
    }

    /** Feet y of a cell an entity can stand on near (x, z), close to y. NaN when nothing fits. */
    public static double floorNear(Level level, double x, double z, double y, boolean underground) {
        BlockPos c = standCell(level, x, z, Mth.floor(y) + 4, 10, 2);
        if (c != null) return c.getY();
        if (!underground) {
            BlockPos top = topBlock(level, x, z, Mth.floor(y) + 40, Mth.floor(y) - 40);
            if (top != null) {
                BlockState s = level.getBlockState(top);
                if (s.getFluidState().isEmpty() && !(s.getBlock() instanceof LeavesBlock) && !s.is(BlockTags.ICE)) return top.getY() + 1;
            }
        }
        return Double.NaN;
    }

    /** Player is under a roof with walls around (inside a house / base). */
    public static boolean isSheltered(Player p) {
        Level level = p.level();
        BlockPos base = p.blockPosition();
        boolean roof = false;
        for (int dy = 2; dy <= 10; dy++) {
            BlockState s = level.getBlockState(base.above(dy));
            if (!s.isAir() && !(s.getBlock() instanceof LeavesBlock) && !s.is(Blocks.LIGHT)) {
                roof = true;
                break;
            }
        }
        if (!roof) return false;
        int walls = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            for (int k = 1; k <= 8; k++) {
                if (solid(level, base.offset(d[0] * k, 1, d[1] * k))) {
                    walls++;
                    break;
                }
            }
        }
        return walls >= 3;
    }

    /** Deep underground (cave): solid blocks more than 6 above the head. */
    public static boolean isUnderground(Level level, Vec3 at) {
        BlockPos b = BlockPos.containing(at);
        for (int dy = 7; dy <= 40; dy++) {
            BlockState s = level.getBlockState(b.above(dy));
            if (!s.isAir() && !(s.getBlock() instanceof LeavesBlock) && !s.is(BlockTags.LOGS) && !s.is(Blocks.LIGHT)) return true;
        }
        return false;
    }

    /** Covered from the sky (no rain falls on this column). */
    public static boolean outdoors(Level level, Vec3 at) {
        BlockPos b = BlockPos.containing(at);
        for (int dy = 2; dy <= 14; dy++) {
            BlockPos q = b.above(dy);
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && !(s.getBlock() instanceof LeavesBlock) && !passable(level, q)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ line of sight (glass, bars, fences ... do not block)
    public static boolean blocksSight(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty()) return false;
        if (passable(level, pos)) return false;
        Block b = s.getBlock();
        if (b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof LadderBlock
                || b instanceof TrapDoorBlock || b instanceof LeavesBlock) return false;
        if (s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES)) return false;
        if (b == Blocks.COBWEB || b == Blocks.SCAFFOLDING || b == Blocks.CHAIN || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN
                || b == Blocks.BELL || b == Blocks.END_ROD || b == Blocks.LIGHTNING_ROD || b == Blocks.POINTED_DRIPSTONE) return false;
        return true;
    }

    /** Voxel walk a -> b; true when nothing in between blocks the view. */
    public static boolean los(Level level, Vec3 a, Vec3 b) {
        int x = Mth.floor(a.x), y = Mth.floor(a.y), z = Mth.floor(a.z);
        int tx = Mth.floor(b.x), ty = Mth.floor(b.y), tz = Mth.floor(b.z);
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        int sx = (int) Math.signum(dx), sy = (int) Math.signum(dy), sz = (int) Math.signum(dz);
        double ddx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double ddy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double ddz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double mx = dx == 0 ? Double.POSITIVE_INFINITY : (sx > 0 ? x + 1 - a.x : a.x - x) * ddx;
        double my = dy == 0 ? Double.POSITIVE_INFINITY : (sy > 0 ? y + 1 - a.y : a.y - y) * ddy;
        double mz = dz == 0 ? Double.POSITIVE_INFINITY : (sz > 0 ? z + 1 - a.z : a.z - z) * ddz;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 300; i++) {
            if (x == tx && y == ty && z == tz) return true;
            if (mx <= my && mx <= mz) {
                if (mx > 1) return true;
                x += sx;
                mx += ddx;
            } else if (my <= mz) {
                if (my > 1) return true;
                y += sy;
                my += ddy;
            } else {
                if (mz > 1) return true;
                z += sz;
                mz += ddz;
            }
            if (x == tx && y == ty && z == tz) return true;
            m.set(x, y, z);
            if (blocksSight(level, m)) return false;
        }
        return true;
    }

    /** cos of the angle between where the player looks (flat) and the direction to `target` (1 = straight at it). */
    public static double lookDot(Entity p, Vec3 target) {
        Vec3 v = p.getViewVector(1f);
        double dx = target.x - p.getX(), dz = target.z - p.getZ();
        double dl = Math.sqrt(dx * dx + dz * dz), vl = Math.sqrt(v.x * v.x + v.z * v.z);
        if (dl < 1e-6 || vl < 1e-6) return 1;
        return (v.x * dx + v.z * dz) / (dl * vl);
    }

    /** Is the player actually looking at block `pos` (raycast within range)? */
    public static boolean lookingAtBlock(Player p, BlockPos pos, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(range));
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public static boolean isBed(BlockState s) {
        return s.getBlock() instanceof BedBlock;
    }

    /** Feet height on a half slab is half a block lower. */
    public static double floorY(Level level, BlockPos cell) {
        BlockState s = level.getBlockState(cell.below());
        if (s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) return cell.getY() - 0.5;
        return cell.getY();
    }

    public static double dist(Vec3 a, Vec3 b) {
        return a.distanceTo(b);
    }

    public static double dist2d(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isNight(Level level) {
        long t = level.getDayTime() % 24000L;
        return t >= 13000 && t <= 23000;
    }
}
