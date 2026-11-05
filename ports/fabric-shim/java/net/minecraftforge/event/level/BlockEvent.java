package net.minecraftforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;

public class BlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    /** A player breaks a block (cancelled: it stays). */
    public static class BreakEvent extends BlockEvent {
        private final Player player;
        private int exp;

        public BreakEvent(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        public int getExpToDrop() {
            return exp;
        }

        public void setExpToDrop(int exp) {
            this.exp = exp;
        }
    }

    /** A block was placed by an entity. */
    public static class EntityPlaceEvent extends BlockEvent {
        private final Entity entity;
        private final BlockState placedAgainst;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placed, BlockState placedAgainst, Entity entity) {
            super(level, pos, placed);
            this.entity = entity;
            this.placedAgainst = placedAgainst;
        }

        public Entity getEntity() {
            return entity;
        }

        public BlockState getPlacedBlock() {
            return getState();
        }

        public BlockState getPlacedAgainst() {
            return placedAgainst;
        }
    }
}
