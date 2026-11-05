package net.minecraftforge.items;

import net.minecraft.world.item.ItemStack;

public final class ItemHandlerHelper {
    private ItemHandlerHelper() {}

    public static boolean canItemStacksStack(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || a.getItem() != b.getItem()) return false;
        //#if MC>=12100
        //$$ return ItemStack.isSameItemSameComponents(a, b);
        //#else
        return ItemStack.isSameItemSameTags(a, b);
        //#endif
    }

    public static ItemStack copyStackWithSize(ItemStack s, int size) {
        if (size == 0) return ItemStack.EMPTY;
        ItemStack c = s.copy();
        c.setCount(size);
        return c;
    }

    public static ItemStack insertItem(IItemHandler dest, ItemStack stack, boolean simulate) {
        if (dest == null || stack.isEmpty()) return stack;
        for (int i = 0; i < dest.getSlots(); i++) {
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    /** Fills stacks of the same item first, then empty slots. */
    public static ItemStack insertItemStacked(IItemHandler inv, ItemStack stack, boolean simulate) {
        if (inv == null || stack.isEmpty()) return stack;
        if (!stack.isStackable()) return insertItem(inv, stack, simulate);
        int n = inv.getSlots();
        for (int i = 0; i < n; i++) {
            ItemStack in = inv.getStackInSlot(i);
            if (canItemStacksStack(in, stack)) {
                stack = inv.insertItem(i, stack, simulate);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < n; i++) {
            if (inv.getStackInSlot(i).isEmpty()) {
                stack = inv.insertItem(i, stack, simulate);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    public static void giveItemToPlayer(net.minecraft.world.entity.player.Player p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }
}
