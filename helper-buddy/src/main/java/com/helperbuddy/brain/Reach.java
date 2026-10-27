package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Getting within reach of a block. Walks with the real path finder; when there is no way (ore inside the rock, a
 * cliff), it digs a 1x2 tunnel or staircase toward the block through natural ground (setting "dig").
 */
public final class Reach {
    public enum St { READY, BUSY, FAIL }

    final Brain b;
    final HelperEntity h;
    @Nullable
    BlockPos goal;
    int fails;
    long started;
    int stuckTicks;

    Reach(Brain b) {
        this.b = b;
        this.h = b.h;
    }

    public void reset(@Nullable BlockPos goal) {
        this.goal = goal;
        fails = 0;
        stuckTicks = 0;
        started = h.level().getGameTime();
        b.mover().resetProgress();
    }

    static Vec3 center(BlockPos p) {
        return Vec3.atCenterOf(p);
    }

    /** First block in the way between the eye and the target, or null when the target can be seen. */
    @Nullable
    BlockPos obstacle(BlockPos target) {
        Vec3 eye = h.getEyePosition();
        Vec3 c = center(target);
        BlockHitResult hit = h.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, h));
        if (hit.getType() == HitResult.Type.MISS) return null;
        if (hit.getBlockPos().equals(target)) return null;
        return hit.getBlockPos();
    }

    /** Natural block it may dig through on the way (needs a pickaxe for stone, shovel/hand for dirt). */
    public boolean digThrough(BlockPos p, boolean quiet) {
        Level l = h.level();
        BlockState s = l.getBlockState(p);
        if (Jobs.isLeaf(s) && Jobs.naturalLeaf(s)) return true;
        if (!h.settings.get("dig")) return false;
        if (!Jobs.diggable(s) || !Breaker.breakable(h, p, s)) return false;
        if (Jobs.touchesLava(l, p)) return false;
        if (underPlayer(p)) return false;
        if (s.requiresCorrectToolForDrops() && !Bag.hasKind(h, Bag.Kind.PICKAXE)) {
            if (!quiet) needTool(h, Bag.Kind.PICKAXE);
            return false;
        }
        return true;
    }

    boolean underPlayer(BlockPos p) {
        for (Player pl : h.level().players())
            if (pl.blockPosition().below().equals(p) || pl.blockPosition().equals(p) || pl.blockPosition().above().equals(p)) return true;
        return false;
    }

    public static void needTool(HelperEntity h, Bag.Kind k) {
        String th = switch (k) {
            case PICKAXE -> "§eอีเต้อ (Pickaxe)§r สำหรับขุดหินและแร่";
            case SHOVEL -> "§eพลั่ว (Shovel)§r สำหรับขุดดิน ทราย และกรวด";
            case AXE -> "§eขวาน (Axe)§r สำหรับตัดไม้";
            case HOE -> "§eจอบ (Hoe)§r";
            case SHEARS -> "§eกรรไกร (Shears)§r";
            case SWORD -> "§eดาบ (Sword)§r";
        };
        String en = switch (k) {
            case PICKAXE -> "a §ePickaxe§r for stone and ores";
            case SHOVEL -> "a §eShovel§r for dirt, sand and gravel";
            case AXE -> "an §eAxe§r for wood";
            case HOE -> "a §eHoe§r";
            case SHEARS -> "§eShears§r";
            case SWORD -> "a §eSword§r";
        };
        Say.notify(h, "need:" + k, 20 * 45, "ผมต้องการ" + th + " ส่งให้ผมหน่อยนะ (คลิกขวาที่ตัวผม หรือใช้หนังสือสั่งงานส่งจากระยะไกลได้)",
                "I need " + en + ". Please give me one (right-click me, or send it from anywhere with the command book)");
    }

    /** Starts breaking an obstacle (by hand if it is soft ground or leaves). */
    boolean breakObstacle(BlockPos p) {
        BlockState s = h.level().getBlockState(p);
        boolean hand = Jobs.isLeaf(s) || !s.requiresCorrectToolForDrops();
        return b.breaker.start(p, hand, null);
    }

    /**
     * One step toward `target`. READY when the eye is within `reach` and the block can be seen (or, with `blind`,
     * just within reach). Obstacles are dug through when allowed.
     */
    public St tick(BlockPos target, double reach, boolean blind) {
        if (goal == null || !goal.equals(target)) reset(target);
        long now = h.level().getGameTime();
        if (now - started > 20 * 90) return St.FAIL;
        Vec3 eye = h.getEyePosition();
        double d = eye.distanceTo(center(target));
        if (d <= reach) {
            if (blind) return St.READY;
            BlockPos ob = obstacle(target);
            if (ob == null) return St.READY;
            if (eye.distanceTo(center(ob)) <= reach + 0.6 && digThrough(ob, false)) {
                if (breakObstacle(ob)) return St.BUSY;
            }
        }
        Mover.Res r = b.mover().moveTo(Vec3.atBottomCenterOf(target), 1.15, 0.6);
        boolean stuck = b.mover().stuck(40);
        boolean pathShort = h.getNavigation().isDone() && d > reach;
        if (r == Mover.Res.NO_PATH || stuck || pathShort || r == Mover.Res.ARRIVED) {
            stuckTicks++;
            if (stuckTicks < 10 && r != Mover.Res.NO_PATH && !stuck) return St.BUSY;
            stuckTicks = 0;
            b.mover().resetProgress();
            Tunnel t = tunnelStep(target);
            if (t == Tunnel.TARGET) return St.READY;
            if (t == Tunnel.STARTED) return St.BUSY;
            if (++fails > 6) return St.FAIL;
        } else stuckTicks = 0;
        return St.BUSY;
    }

    enum Tunnel { STARTED, TARGET, NONE }

    /** Digs (or steps) one block along a 1x2 tunnel / staircase toward the target. */
    Tunnel tunnelStep(BlockPos target) {
        Level l = h.level();
        BlockPos feet = h.blockPosition();
        int dx = target.getX() - feet.getX(), dy = target.getY() - feet.getY(), dz = target.getZ() - feet.getZ();
        Direction dir = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        boolean above = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
        BlockPos f = feet.relative(dir);
        BlockPos[] cand;
        BlockPos next;
        if (above && dy < 0) {
            cand = new BlockPos[] {feet.below()};
            next = feet.below();
        } else if (above && dy > 1) {
            cand = new BlockPos[] {feet.above(2)};
            next = feet.above();
        } else if (dy < -1) {
            cand = new BlockPos[] {f, f.above(), f.below()};
            next = f.below();
        } else if (dy > 1) {
            cand = new BlockPos[] {feet.above(2), f.above(), f.above(2)};
            next = f.above();
        } else {
            cand = new BlockPos[] {f, f.above()};
            next = f;
        }
        for (BlockPos c : cand) {
            if (Mover.passable(l, c)) continue;
            if (c.equals(target)) return Tunnel.TARGET;
            if (!digThrough(c, false)) return Tunnel.NONE;
            return breakObstacle(c) ? Tunnel.STARTED : Tunnel.NONE;
        }
        // the way is open: step into it (and jump up a stair)
        if (next.getY() > feet.getY() && h.onGround()) h.getJumpControl().jump();
        h.getMoveControl().setWantedPosition(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        return Tunnel.STARTED;
    }
}
