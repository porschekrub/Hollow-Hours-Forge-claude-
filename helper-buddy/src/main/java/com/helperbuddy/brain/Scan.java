package com.helperbuddy.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.annotation.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Fast "nearest block" search in a big radius (the helper "sees" 100 blocks): whole chunk sections are skipped when
 * their block palette cannot contain what we look for, so even a 100 block radius costs little.
 */
public final class Scan {
    private Scan() {}

    /**
     * @param want  a cheap test on the block state (used on the section palettes)
     * @param accept the full test on a matching block (exposed? reachable? blacklisted?)
     */
    @Nullable
    public static BlockPos nearest(ServerLevel level, BlockPos center, int radius, int vRadius, Predicate<BlockState> want, BiPredicate<BlockPos, BlockState> accept) {
        int ccx = center.getX() >> 4, ccz = center.getZ() >> 4;
        int rc = (radius >> 4) + 1;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - vRadius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + vRadius);
        long r2 = (long) radius * radius;
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int ring = 0; ring <= rc; ring++) {
            // nothing in this ring can beat what we already have
            if (best != null) {
                long ringMin = (long) Math.max(0, (ring - 1) * 16);
                if (ringMin * ringMin > bestD) break;
            }
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    LevelChunk ch = level.getChunkSource().getChunkNow(ccx + dx, ccz + dz);
                    if (ch == null) continue;
                    LevelChunkSection[] secs = ch.getSections();
                    for (int si = 0; si < secs.length; si++) {
                        LevelChunkSection sec = secs[si];
                        if (sec == null || sec.hasOnlyAir()) continue;
                        int sy = level.getSectionYFromSectionIndex(si) << 4;
                        if (sy + 15 < minY || sy > maxY) continue;
                        if (!sec.maybeHas(want)) continue;
                        int bx = (ccx + dx) << 4, bz = (ccz + dz) << 4;
                        for (int y = 0; y < 16; y++) {
                            int wy = sy + y;
                            if (wy < minY || wy > maxY) continue;
                            for (int z = 0; z < 16; z++)
                                for (int x = 0; x < 16; x++) {
                                    BlockState s = sec.getBlockState(x, y, z);
                                    if (!want.test(s)) continue;
                                    long ddx = bx + x - center.getX(), ddy = wy - center.getY(), ddz = bz + z - center.getZ();
                                    long d = ddx * ddx + ddy * ddy * 2 + ddz * ddz; // a block above/below costs a bit more
                                    if (d >= bestD || ddx * ddx + ddz * ddz > r2) continue;
                                    mp.set(bx + x, wy, bz + z);
                                    if (!accept.test(mp, s)) continue;
                                    bestD = d;
                                    best = mp.immutable();
                                }
                        }
                    }
                }
        }
        return best;
    }
}
