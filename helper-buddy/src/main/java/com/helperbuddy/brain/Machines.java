package com.helperbuddy.brain;

import com.helperbuddy.compat.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//#if FABRIC
//$$ import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
//$$ import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
//$$ import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
//$$ import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
//$$ import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
//#else
import net.minecraftforge.items.IItemHandler;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Any block with an inventory, other mods' machines too, through the loader's item access (Forge / NeoForge item
 * handlers, Fabric's transfer API), side by side like hoppers do: what goes in on top, fuel from the side, results out
 * of the bottom.
 */
public final class Machines {
    private Machines() {}

    static final Direction[] IN = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};
    static final Direction[] FUEL_IN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    static final Direction[] OUT = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

    //#if FABRIC
    //$$ static Storage<ItemVariant> side(Level l, BlockPos p, Direction d) {
    //$$     return ItemStorage.SIDED.find(l, p, d);
    //$$ }
    //#else
    static IItemHandler side(Level l, BlockPos p, Direction d) {
        //#if NEOFORGE
        //$$ return l.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, p, d);
        //#else
        var be = l.getBlockEntity(p);
        return be == null ? null : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, d).orElse(null);
        //#endif
    }
    //#endif

    /** Has an inventory other blocks (hoppers, pipes) can use. */
    public static boolean isMachine(Level l, BlockPos p) {
        for (Direction d : IN) if (side(l, p, d) != null) return true;
        return false;
    }

    /** Everything inside, seen from every side (a stack may show up more than once). */
    public static List<ItemStack> contents(Level l, BlockPos p) {
        List<ItemStack> out = new ArrayList<>();
        //#if FABRIC
        //$$ for (Direction d : OUT) {
        //$$     Storage<ItemVariant> s = side(l, p, d);
        //$$     if (s == null) continue;
        //$$     for (StorageView<ItemVariant> v : s) if (!v.isResourceBlank() && v.getAmount() > 0) out.add(v.getResource().toStack((int) Math.min(64, v.getAmount())));
        //$$ }
        //#else
        for (Direction d : OUT) {
            IItemHandler h = side(l, p, d);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) out.add(h.getStackInSlot(i).copy());
        }
        //#endif
        return out;
    }

    /** Puts the stack in (fuel from the side first); returns how many went in. */
    public static int insert(Level l, BlockPos p, ItemStack st) {
        if (st.isEmpty()) return 0;
        boolean fuel = Compat.burnTime(st) > 0 && Compat.smelt(l, st).isEmpty();
        int in = 0;
        ItemStack rest = st.copy();
        for (Direction d : fuel ? FUEL_IN : IN) {
            if (rest.isEmpty()) break;
            //#if FABRIC
            //$$ Storage<ItemVariant> s = side(l, p, d);
            //$$ if (s == null || !s.supportsInsertion()) continue;
            //$$ try (Transaction tx = Transaction.openOuter()) {
            //$$     long n = s.insert(ItemVariant.of(rest), rest.getCount(), tx);
            //$$     tx.commit();
            //$$     in += (int) n;
            //$$     rest.shrink((int) n);
            //$$ }
            //#else
            IItemHandler h = side(l, p, d);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots() && !rest.isEmpty(); i++) {
                int before = rest.getCount();
                rest = h.insertItem(i, rest, false);
                in += before - rest.getCount();
            }
            //#endif
        }
        return in;
    }

    /** Takes out up to max items that `want` likes, from the bottom first. */
    public static List<ItemStack> extract(Level l, BlockPos p, Predicate<ItemStack> want, int max) {
        List<ItemStack> got = new ArrayList<>();
        int left = max;
        for (Direction d : OUT) {
            if (left <= 0) break;
            //#if FABRIC
            //$$ Storage<ItemVariant> s = side(l, p, d);
            //$$ if (s == null || !s.supportsExtraction()) continue;
            //$$ List<ItemVariant> kinds = new ArrayList<>();
            //$$ for (StorageView<ItemVariant> v : s) if (!v.isResourceBlank() && v.getAmount() > 0 && want.test(v.getResource().toStack()) && !kinds.contains(v.getResource())) kinds.add(v.getResource());
            //$$ for (ItemVariant k : kinds) {
            //$$     if (left <= 0) break;
            //$$     try (Transaction tx = Transaction.openOuter()) {
            //$$         long n = s.extract(k, Math.min(left, k.toStack().getMaxStackSize()), tx);
            //$$         tx.commit();
            //$$         if (n > 0) {
            //$$             got.add(k.toStack((int) n));
            //$$             left -= (int) n;
            //$$         }
            //$$     }
            //$$ }
            //#else
            IItemHandler h = side(l, p, d);
            if (h == null) continue;
            for (int i = 0; i < h.getSlots() && left > 0; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || !want.test(s)) continue;
                ItemStack ex = h.extractItem(i, Math.min(left, s.getCount()), false);
                if (ex.isEmpty()) continue;
                got.add(ex);
                left -= ex.getCount();
            }
            //#endif
        }
        return got;
    }
}
