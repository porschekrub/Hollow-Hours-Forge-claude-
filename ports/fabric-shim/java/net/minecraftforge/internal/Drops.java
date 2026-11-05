package net.minecraftforge.internal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;

/** Forge's Entity#captureDrops: while an entity dies its drops are collected for LivingDropsEvent. */
public final class Drops {
    private Drops() {}

    public interface Holder {
        List<ItemEntity> shim$captured();

        void shim$capture(List<ItemEntity> list);
    }

    /** True when the item was collected (the caller must not add it to the level). */
    public static boolean caught(Entity owner, Entity item) {
        if (!(item instanceof ItemEntity ie)) return false;
        List<ItemEntity> l = ((Holder) owner).shim$captured();
        if (l == null) return false;
        l.add(ie);
        return true;
    }
}
