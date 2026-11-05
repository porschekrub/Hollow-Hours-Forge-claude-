package net.minecraftforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

public class ExplosionEvent extends Event {
    private final Level level;
    private final Explosion explosion;

    public ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    /** The blocks and entities an explosion hits (both lists can be changed). */
    public static class Detonate extends ExplosionEvent {
        private final List<Entity> entities;

        public Detonate(Level level, Explosion explosion, List<Entity> entities) {
            super(level, explosion);
            this.entities = entities;
        }

        public List<BlockPos> getAffectedBlocks() {
            return getExplosion().getToBlow();
        }

        public List<Entity> getAffectedEntities() {
            return entities;
        }
    }
}
