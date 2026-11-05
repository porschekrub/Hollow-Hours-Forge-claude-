package net.minecraftforge.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class LivingEntityUseItemEvent extends LivingEvent {
    private final ItemStack item;
    private int duration;

    public LivingEntityUseItemEvent(LivingEntity entity, ItemStack item, int duration) {
        super(entity);
        this.item = item;
        this.duration = duration;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    /** Let go of the use key before the end (bows ...); cancelled: the item is not released. */
    public static class Stop extends LivingEntityUseItemEvent {
        public Stop(LivingEntity entity, ItemStack item, int duration) {
            super(entity, item, duration);
        }
    }

    /** Eaten / drunk: the result can be changed. */
    public static class Finish extends LivingEntityUseItemEvent {
        private final ItemStack original;
        private ItemStack result;

        public Finish(LivingEntity entity, ItemStack item, int duration, ItemStack result) {
            super(entity, item, duration);
            this.original = item.copy();
            this.result = result;
        }

        public ItemStack getResultStack() {
            return result;
        }

        public void setResultStack(ItemStack result) {
            this.result = result;
        }

        public ItemStack getOriginal() {
            return original;
        }
    }
}
