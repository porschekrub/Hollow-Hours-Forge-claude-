package net.minecraftforge.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityEvent;

public class LivingEvent extends EntityEvent {
    private final LivingEntity living;

    public LivingEvent(LivingEntity entity) {
        super(entity);
        this.living = entity;
    }

    @Override
    public LivingEntity getEntity() {
        return living;
    }

    /** Before the entity's tick (cancel: no tick). */
    public static class LivingTickEvent extends LivingEvent {
        public LivingTickEvent(LivingEntity e) {
            super(e);
        }
    }
}
