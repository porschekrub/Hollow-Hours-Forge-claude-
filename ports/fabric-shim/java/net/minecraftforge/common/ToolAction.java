package net.minecraftforge.common;

import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Fabric shim: what a tool can do, as a test on the stack (Forge asks the item). */
public final class ToolAction {
    private final String name;
    private final Predicate<ItemStack> test;

    public ToolAction(String name, Predicate<ItemStack> test) {
        this.name = name;
        this.test = test;
    }

    public String name() {
        return name;
    }

    public boolean test(ItemStack s) {
        return !s.isEmpty() && test.test(s);
    }

    @Override
    public String toString() {
        return "ToolAction[" + name + "]";
    }
}
