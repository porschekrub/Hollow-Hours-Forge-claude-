package net.minecraftforge.internal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** The experience a block gives when broken with a tool (Forge: getExpDrop): vanilla's own drop, caught. */
public final class BlockXp {
    private BlockXp() {}

    static final ThreadLocal<int[]> CAPTURE = new ThreadLocal<>();

    public static int get(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool) {
        int[] box = {0};
        int[] prev = CAPTURE.get();
        CAPTURE.set(box);
        try {
            state.spawnAfterBreak(level, pos, tool, true);
        } finally {
            CAPTURE.set(prev);
        }
        return box[0];
    }

    /** From the Block#popExperience mixin: true when the experience was caught (not spawned). */
    public static boolean caught(int amount) {
        int[] box = CAPTURE.get();
        if (box == null) return false;
        box[0] += amount;
        return true;
    }
}
