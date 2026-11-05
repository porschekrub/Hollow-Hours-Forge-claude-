package net.minecraftforge.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Getting on (or off) a vehicle (cancelled: it does not happen). */
public class EntityMountEvent extends EntityEvent {
    private final Entity mounting, mounted;
    private final Level level;
    private final boolean isMounting;

    public EntityMountEvent(Entity mounting, Entity mounted, Level level, boolean isMounting) {
        super(mounting);
        this.mounting = mounting;
        this.mounted = mounted;
        this.level = level;
        this.isMounting = isMounting;
    }

    public boolean isMounting() {
        return isMounting;
    }

    public boolean isDismounting() {
        return !isMounting;
    }

    public Entity getEntityMounting() {
        return mounting;
    }

    public Entity getEntityBeingMounted() {
        return mounted;
    }

    public Level getLevel() {
        return level;
    }
}
