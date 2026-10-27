package com.helperbuddy.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the whole tree a log belongs to, and whether it is a natural tree (leaves that decay) or a player's build. */
public final class Trees {
    private Trees() {}

    public record Tree(List<BlockPos> logs, List<BlockPos> base, boolean natural, BlockState logState) {}

    public static Tree analyze(Level l, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        List<BlockPos> logs = new ArrayList<>();
        boolean natural = false;
        BlockState logState = l.getBlockState(start);
        while (!q.isEmpty() && logs.size() < 260) {
            BlockPos p = q.poll();
            BlockState s = l.getBlockState(p);
            if (!Jobs.isLog(s)) continue;
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - start.getX()) > 9 || Math.abs(n.getZ() - start.getZ()) > 9 || n.getY() < start.getY() - 14 || n.getY() > start.getY() + 34)
                            continue;
                        if (!seen.add(n)) continue;
                        BlockState ns = l.getBlockState(n);
                        if (Jobs.isLog(ns)) q.add(n);
                        else if (!natural && Jobs.isLeaf(ns) && Jobs.naturalLeaf(ns)) natural = true;
                    }
        }
        int minY = Integer.MAX_VALUE;
        for (BlockPos p : logs) minY = Math.min(minY, p.getY());
        List<BlockPos> base = new ArrayList<>();
        for (BlockPos p : logs) if (p.getY() == minY) base.add(p);
        return new Tree(logs, base, natural, logState);
    }

    public static boolean isNaturalTreeLog(Level l, BlockPos p) {
        return analyze(l, p).natural();
    }
}
