package net.minecraftforge.items.wrapper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** A vanilla container as an item handler. */
public class InvWrapper implements IItemHandler {
    private final Container inv;

    public InvWrapper(Container inv) {
        this.inv = inv;
    }

    public Container getInv() {
        return inv;
    }

    /** The container of a block (a double chest: both halves), or null. */
    public static IItemHandler at(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock chest) {
            Container c = ChestBlock.getContainer(chest, st, level, pos, true);
            return c == null ? null : new InvWrapper(c);
        }
        return be instanceof Container c ? new InvWrapper(c) : null;
    }

    @Override
    public int getSlots() {
        return inv.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv.getItem(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!inv.canPlaceItem(slot, stack)) return stack;
        ItemStack in = inv.getItem(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!in.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(in, stack)) return stack;
            int room = limit - in.getCount();
            if (room <= 0) return stack;
            int move = Math.min(room, stack.getCount());
            if (!simulate) {
                ItemStack c = in.copy();
                c.grow(move);
                inv.setItem(slot, c);
                inv.setChanged();
            }
            return stack.getCount() == move ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - move);
        }
        int move = Math.min(limit, stack.getCount());
        if (!simulate) {
            inv.setItem(slot, ItemHandlerHelper.copyStackWithSize(stack, move));
            inv.setChanged();
        }
        return stack.getCount() == move ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - move);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) return ItemStack.EMPTY;
        ItemStack in = inv.getItem(slot);
        if (in.isEmpty()) return ItemStack.EMPTY;
        int n = Math.min(amount, in.getCount());
        if (simulate) return ItemHandlerHelper.copyStackWithSize(in, n);
        ItemStack out = inv.removeItem(slot, n);
        inv.setChanged();
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return inv.canPlaceItem(slot, stack);
    }
}
