package net.minecraftforge.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric shim: the few Forge event helpers the mods call (no other mod listens on Fabric). */
public final class ForgeEventFactory {
    private ForgeEventFactory() {}

    public static boolean onEntityDestroyBlock(LivingEntity entity, BlockPos pos, BlockState state) {
        return true;
    }

    /** Whether mobs may change blocks (the mobGriefing rule). */
    public static boolean getMobGriefingEvent(Level level, Entity entity) {
        return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
    }
}
