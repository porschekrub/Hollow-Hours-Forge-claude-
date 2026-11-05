package com.hollowhours.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * An item stack's own saved data, the same on every Minecraft version: on 1.20.1 it is the stack's NBT tag, from 1.20.5
 * on the "custom_data" component (read as a copy, so every change goes through {@link #edit} or {@link #set}).
 */
public final class ItemData {
    private ItemData() {}

    /** True when the stack has any data of its own. */
    public static boolean has(ItemStack s) {
        //#if MC>=12100
        //$$ return s.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        //#else
        return s.hasTag();
        //#endif
    }

    /** The stack's data or null; do not change it (use {@link #edit}). */
    @Nullable
    public static CompoundTag tag(ItemStack s) {
        //#if MC>=12100
        //$$ net.minecraft.world.item.component.CustomData d = s.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        //$$ return d == null ? null : d.copyTag();
        //#else
        return s.getTag();
        //#endif
    }

    /** The stack's data, empty when it has none; do not change it (use {@link #edit}). */
    public static CompoundTag get(ItemStack s) {
        CompoundTag t = tag(s);
        return t == null ? new CompoundTag() : t;
    }

    /** Changes the stack's data (created when missing). */
    public static void edit(ItemStack s, Consumer<CompoundTag> change) {
        //#if MC>=12100
        //$$ net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, s, change);
        //#else
        change.accept(s.getOrCreateTag());
        //#endif
    }

    /** Replaces the stack's data (null or empty: none). */
    public static void set(ItemStack s, @Nullable CompoundTag t) {
        //#if MC>=12100
        //$$ if (t == null || t.isEmpty()) s.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        //$$ else s.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(t));
        //#else
        s.setTag(t == null || t.isEmpty() ? null : t);
        //#endif
    }

    public static void remove(ItemStack s, String key) {
        if (!has(s)) return;
        CompoundTag t = get(s);
        if (!t.contains(key)) return;
        t.remove(key);
        set(s, t);
    }

    /** Same item and same data (count ignored). */
    public static boolean sameItemSameData(ItemStack a, ItemStack b) {
        //#if MC>=12100
        //$$ return ItemStack.isSameItemSameComponents(a, b);
        //#else
        return ItemStack.isSameItemSameTags(a, b);
        //#endif
    }

    /** A custom item name. */
    public static void setName(ItemStack s, Component name) {
        //#if MC>=12100
        //$$ s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        //#else
        s.setHoverName(name);
        //#endif
    }

    /** Extra tooltip lines under the name. */
    public static void setLore(ItemStack s, List<Component> lines) {
        //#if MC>=12100
        //$$ s.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        //#else
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        for (Component l : lines) lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(l)));
        s.getOrCreateTagElement("display").put("Lore", lore);
        //#endif
    }
}
