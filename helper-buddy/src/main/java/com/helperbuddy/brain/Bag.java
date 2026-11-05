package com.helperbuddy.brain;

import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The helper's bag (36 slots) + the tool in its hand. */
public final class Bag {
    private Bag() {}

    public enum Kind {
        PICKAXE(ToolActions.PICKAXE_DIG), AXE(ToolActions.AXE_DIG), SHOVEL(ToolActions.SHOVEL_DIG), HOE(ToolActions.HOE_DIG), SWORD(ToolActions.SWORD_DIG),
        SHEARS(ToolActions.SHEARS_DIG);

        public final ToolAction action;

        Kind(ToolAction a) {
            this.action = a;
        }

        public String th() {
            return switch (this) {
                case PICKAXE -> "อีเต้อ";
                case AXE -> "ขวาน";
                case SHOVEL -> "พลั่ว";
                case HOE -> "จอบ";
                case SWORD -> "ดาบ";
                case SHEARS -> "กรรไกร";
            };
        }

        public String en() {
            return switch (this) {
                case PICKAXE -> "a pickaxe";
                case AXE -> "an axe";
                case SHOVEL -> "a shovel";
                case HOE -> "a hoe";
                case SWORD -> "a sword";
                case SHEARS -> "shears";
            };
        }
    }

    public static boolean isKind(ItemStack st, Kind k) {
        return !st.isEmpty() && st.canPerformAction(k.action);
    }

    @Nullable
    public static Kind kindOf(ItemStack st) {
        for (Kind k : Kind.values()) if (isKind(st, k)) return k;
        return null;
    }

    public static boolean isTool(ItemStack st) {
        return kindOf(st) != null || st.getItem() instanceof net.minecraft.world.item.BowItem || st.getItem() instanceof net.minecraft.world.item.CrossbowItem
                || st.getItem() instanceof net.minecraft.world.item.TridentItem || st.getItem() instanceof net.minecraft.world.item.FlintAndSteelItem;
    }

    // ------------------------------------------------------------------ counting / adding / taking
    public static int count(HelperEntity h, Predicate<ItemStack> f) {
        int n = 0;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (!s.isEmpty() && f.test(s)) n += s.getCount();
        }
        ItemStack t = h.tool();
        if (!t.isEmpty() && f.test(t)) n += t.getCount();
        return n;
    }

    public static int count(HelperEntity h, Item item) {
        return count(h, s -> s.is(item));
    }

    public static boolean has(HelperEntity h, Predicate<ItemStack> f) {
        return count(h, f) > 0;
    }

    /** Puts a stack in the bag; returns what did not fit. */
    public static ItemStack add(HelperEntity h, ItemStack in) {
        if (in.isEmpty()) return ItemStack.EMPTY;
        ItemStack st = in.copy();
        for (int i = 0; i < h.bag.getContainerSize() && !st.isEmpty(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, st) && s.getCount() < s.getMaxStackSize()) {
                int mv = Math.min(st.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(mv);
                st.shrink(mv);
            }
        }
        for (int i = 0; i < h.bag.getContainerSize() && !st.isEmpty(); i++) {
            if (h.bag.getItem(i).isEmpty()) {
                h.bag.setItem(i, st.copy());
                st = ItemStack.EMPTY;
            }
        }
        h.bag.setChanged();
        return st;
    }

    /** Puts it in the bag, drops the rest at the helper's feet (or `at`). Returns false when something was dropped. */
    public static boolean addOrDrop(HelperEntity h, ItemStack st, @Nullable Vec3 at) {
        ItemStack left = add(h, st);
        if (left.isEmpty()) return true;
        Vec3 p = at != null ? at : h.position();
        ItemEntity e = new ItemEntity(h.level(), p.x, p.y + 0.3, p.z, left);
        e.setDefaultPickUpDelay();
        h.level().addFreshEntity(e);
        return false;
    }

    /** Takes up to n items matching f out of the bag (the hand counts too). Returns the taken stacks merged. */
    public static int take(HelperEntity h, Predicate<ItemStack> f, int n) {
        int left = n;
        for (int i = 0; i < h.bag.getContainerSize() && left > 0; i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || !f.test(s)) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            left -= t;
        }
        if (left > 0) {
            ItemStack t = h.tool();
            if (!t.isEmpty() && f.test(t)) {
                int k = Math.min(left, t.getCount());
                t.shrink(k);
                left -= k;
            }
        }
        h.bag.setChanged();
        return n - left;
    }

    public static int take(HelperEntity h, Item item, int n) {
        return take(h, s -> s.is(item), n);
    }

    public static int usedSlots(HelperEntity h) {
        int u = 0;
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (!h.bag.getItem(i).isEmpty()) u++;
        return u;
    }

    public static boolean isFull(HelperEntity h) {
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize() / 2) return false;
        }
        return true;
    }

    public static boolean hasRoomFor(HelperEntity h, ItemStack st) {
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || ItemStack.isSameItemSameTags(s, st) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tools
    static float speedOn(ItemStack st, BlockState state) {
        float sp = st.getDestroySpeed(state);
        if (sp > 1f) {
            int eff = st.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY);
            if (eff > 0) sp += eff * eff + 1;
        }
        return sp;
    }

    /** Can this stack harvest the block (drops)? */
    public static boolean canHarvest(ItemStack st, BlockState state) {
        return !state.requiresCorrectToolForDrops() || (!st.isEmpty() && st.isCorrectToolForDrops(state));
    }

    /** Swaps the best tool for this block into the hand. Returns true when the block will drop its item with what it holds now. */
    public static boolean equipFor(HelperEntity h, BlockState state) {
        ItemStack best = h.tool();
        int bestSlot = -1;
        float bestScore = score(best, state);
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || kindOf(s) == null) continue;
            float sc = score(s, state);
            if (sc > bestScore + 0.01f) {
                bestScore = sc;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) swapIntoHand(h, bestSlot);
        return canHarvest(h.tool(), state);
    }

    static float score(ItemStack st, BlockState state) {
        if (st.isEmpty()) return state.requiresCorrectToolForDrops() ? -1 : 1;
        if (state.requiresCorrectToolForDrops() && !st.isCorrectToolForDrops(state)) return speedOn(st, state) > 1 ? 0.5f : -0.5f;
        float sp = speedOn(st, state);
        // a nearly broken tool is used only when nothing else works
        if (st.isDamageableItem() && st.getMaxDamage() - st.getDamageValue() <= 1) sp *= 0.1f;
        return sp;
    }

    public static void swapIntoHand(HelperEntity h, int slot) {
        ItemStack cur = h.tool().copy();
        h.setTool(h.bag.getItem(slot).copy());
        h.bag.setItem(slot, cur);
        h.bag.setChanged();
    }

    /** Best tool of a kind into the hand; false when it has none. */
    public static boolean equip(HelperEntity h, Kind k) {
        if (isKind(h.tool(), k) && !nearlyBroken(h.tool())) return true;
        int best = -1;
        double bs = -1;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (!isKind(s, k)) continue;
            double sc = tierScore(s) + (nearlyBroken(s) ? -100 : 0);
            if (sc > bs) {
                bs = sc;
                best = i;
            }
        }
        if (best >= 0) {
            swapIntoHand(h, best);
            return true;
        }
        return isKind(h.tool(), k);
    }

    public static boolean hasKind(HelperEntity h, Kind k) {
        if (isKind(h.tool(), k)) return true;
        for (int i = 0; i < h.bag.getContainerSize(); i++) if (isKind(h.bag.getItem(i), k)) return true;
        return false;
    }

    static boolean nearlyBroken(ItemStack s) {
        return s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 1;
    }

    public static double tierScore(ItemStack s) {
        if (s.getItem() instanceof net.minecraft.world.item.TieredItem ti) return ti.getTier().getSpeed() + ti.getTier().getLevel() * 2;
        return 1;
    }

    /** Uses up one point of durability; returns true when the tool broke. */
    public static boolean damageTool(HelperEntity h) {
        ItemStack t = h.tool();
        if (t.isEmpty() || !t.isDamageableItem()) return false;
        Compat.hurtItem(t, 1, h, net.minecraft.world.InteractionHand.MAIN_HAND);
        return t.isEmpty();
    }

    /** Mending: XP repairs the tool first; returns the XP left. */
    public static int mend(HelperEntity h, int xp) {
        ItemStack t = h.tool();
        if (xp <= 0 || t.isEmpty() || !t.isDamaged() || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, t) <= 0) return xp;
        int repair = Math.min(xp * 2, t.getDamageValue());
        t.setDamageValue(t.getDamageValue() - repair);
        return xp - repair / 2;
    }

    // ------------------------------------------------------------------ owner / world
    /** Gives everything (but tools and torches when keepTools) to the player; returns the number of items given. */
    public static int giveAllTo(HelperEntity h, Player p, Predicate<ItemStack> filter) {
        int n = 0;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || !filter.test(s)) continue;
            int c = s.getCount();
            ItemStack give = s.copy();
            if (!p.getInventory().add(give)) {
                p.drop(give, false);
            }
            h.bag.setItem(i, ItemStack.EMPTY);
            n += c;
        }
        h.bag.setChanged();
        return n;
    }

    public static void giveTo(Player p, ItemStack st) {
        ItemStack c = st.copy();
        if (!p.getInventory().add(c) && !c.isEmpty()) p.drop(c, false);
    }

    public static void dropAll(HelperEntity h) {
        Containers.dropContents(h.level(), h.blockPosition(), h.bag);
    }

    public record Store(BlockPos pos, IItemHandler handler) {}

    /** Chests, barrels, shulker boxes ... around a spot. */
    public static List<Store> containersNear(Level level, BlockPos center, int r) {
        List<Store> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            BlockEntity be = level.getBlockEntity(p);
            if (be == null) continue;
            String id = be.getType().toString();
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(hd -> {
                if (hd.getSlots() >= 5 && !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)
                        && !(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity)
                        && !(be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity))
                    out.add(new Store(p.immutable(), hd));
            });
            if (id.isEmpty()) continue;
        }
        out.sort((a, b) -> Double.compare(a.pos.distSqr(center), b.pos.distSqr(center)));
        return out;
    }

    /** Moves the bag into the containers, keeping what `keep` says. Returns the number moved. */
    public static int deposit(HelperEntity h, List<Store> stores, Predicate<ItemStack> keep) {
        int moved = 0;
        for (int i = 0; i < h.bag.getContainerSize(); i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || keep.test(s)) continue;
            ItemStack rest = s.copy();
            for (Store st : stores) {
                rest = ItemHandlerHelper.insertItemStacked(st.handler, rest, false);
                if (rest.isEmpty()) break;
            }
            moved += s.getCount() - rest.getCount();
            h.bag.setItem(i, rest);
        }
        h.bag.setChanged();
        return moved;
    }

    /** Takes up to n items of a kind out of the containers into the bag. */
    public static int pull(HelperEntity h, List<Store> stores, Predicate<ItemStack> want, int n) {
        int got = 0;
        for (Store st : stores) {
            IItemHandler hd = st.handler;
            for (int i = 0; i < hd.getSlots() && got < n; i++) {
                ItemStack s = hd.getStackInSlot(i);
                if (s.isEmpty() || !want.test(s)) continue;
                ItemStack ex = hd.extractItem(i, Math.min(n - got, s.getCount()), true);
                if (ex.isEmpty() || !hasRoomFor(h, ex)) continue;
                ex = hd.extractItem(i, ex.getCount(), false);
                ItemStack left = add(h, ex);
                got += ex.getCount() - left.getCount();
                if (!left.isEmpty()) ItemHandlerHelper.insertItemStacked(hd, left, false);
            }
            if (got >= n) break;
        }
        return got;
    }
}
