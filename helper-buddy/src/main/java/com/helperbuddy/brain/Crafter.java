package com.helperbuddy.brain;

import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Crafting with the real recipes of the game (mods included): ingredients come from the bag and nearby chests,
 * missing planks and sticks are made from logs on the way.
 */
public final class Crafter {
    private Crafter() {}

    public record Result(int made, @Nullable List<Ingredient> missing) {}

    static List<CraftingRecipe> recipesFor(Level l, Item target) {
        RegistryAccess ra = l.registryAccess();
        List<CraftingRecipe> out = new ArrayList<>();
        //#if MC>=12100
        //$$ for (var holder : l.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
        //$$     CraftingRecipe r = holder.value();
        //#else
        for (CraftingRecipe r : l.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
        //#endif
            if (r.isSpecial()) continue;
            ItemStack res = r.getResultItem(ra);
            if (!res.isEmpty() && res.is(target) && !r.getIngredients().isEmpty()) out.add(r);
        }
        return out;
    }

    public static boolean craftable(Level l, Item target) {
        return !recipesFor(l, target).isEmpty();
    }

    /** Groups equal ingredients: [ingredient, count]. */
    static List<Object[]> needs(Recipe<?> r) {
        List<Object[]> out = new ArrayList<>();
        NonNullList<Ingredient> ings = r.getIngredients();
        outer:
        for (Ingredient ing : ings) {
            if (ing.isEmpty()) continue;
            for (Object[] e : out)
                if (same((Ingredient) e[0], ing)) {
                    e[1] = (Integer) e[1] + 1;
                    continue outer;
                }
            out.add(new Object[] {ing, 1});
        }
        return out;
    }

    static boolean same(Ingredient a, Ingredient b) {
        ItemStack[] x = a.getItems(), y = b.getItems();
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (!ItemStack.isSameItem(x[i], y[i])) return false;
        return true;
    }

    static int have(HelperEntity h, Ingredient ing) {
        return Bag.count(h, s -> !Bag.isTool(s) && ing.test(s));
    }

    /** Takes `n` items matching the ingredient, keeping crafting leftovers (empty buckets ...). */
    static void consume(HelperEntity h, Ingredient ing, int n) {
        for (int i = 0; i < h.bag.getContainerSize() && n > 0; i++) {
            ItemStack s = h.bag.getItem(i);
            if (s.isEmpty() || Bag.isTool(s) || !ing.test(s)) continue;
            int take = Math.min(n, s.getCount());
            ItemStack rem = s.getCraftingRemainingItem();
            s.shrink(take);
            n -= take;
            if (!rem.isEmpty()) Bag.addOrDrop(h, rem.copyWithCount(take), null);
        }
        h.bag.setChanged();
    }

    /** Makes planks / sticks (from logs) when an ingredient wants them and the bag has none. */
    static boolean makeBasic(HelperEntity h, Ingredient ing, int need, int depth) {
        if (depth > 2) return false;
        ItemStack[] opts = ing.getItems();
        for (ItemStack o : opts) {
            if (o.is(ItemTags.PLANKS) || o.is(Items.STICK) || o.is(Items.CRAFTING_TABLE)) {
                int before = have(h, ing);
                Result r = craft(h, o.getItem(), need - before, depth + 1);
                if (have(h, ing) >= need) return true;
                if (r.made() > 0 && have(h, ing) > before) continue;
            }
        }
        return have(h, ing) >= need;
    }

    /** Crafts up to `n` of `target`. */
    public static Result craft(HelperEntity h, Item target, int n, int depth) {
        Level l = h.level();
        List<CraftingRecipe> rs = recipesFor(l, target);
        if (rs.isEmpty()) return new Result(0, null);
        int made = 0;
        List<Ingredient> missing = null;
        int guard = 0;
        while (made < n && guard++ < 200) {
            boolean ok = false;
            for (CraftingRecipe r : rs) {
                List<Object[]> need = needs(r);
                boolean all = true;
                List<Ingredient> miss = new ArrayList<>();
                for (Object[] e : need) {
                    Ingredient ing = (Ingredient) e[0];
                    int c = (Integer) e[1];
                    if (have(h, ing) < c && !makeBasic(h, ing, c, depth)) {
                        all = false;
                        miss.add(ing);
                    }
                }
                if (!all) {
                    if (missing == null || miss.size() < missing.size()) missing = miss;
                    continue;
                }
                for (Object[] e : need) consume(h, (Ingredient) e[0], (Integer) e[1]);
                ItemStack res = r.getResultItem(l.registryAccess()).copy();
                made += res.getCount();
                Bag.addOrDrop(h, res, null);
                ok = true;
                break;
            }
            if (!ok) break;
        }
        return new Result(made, made >= n ? null : missing);
    }

    /** Names of what is missing ("Iron Ingot, Stick"). */
    public static String missingNames(List<Ingredient> miss) {
        StringBuilder sb = new StringBuilder();
        for (Ingredient i : miss) {
            ItemStack[] it = i.getItems();
            if (it.length == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(it[0].getHoverName().getString());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ smelting
    @Nullable
    public static SmeltingRecipe smeltRecipe(Level l, ItemStack in) {
        if (in.isEmpty()) return null;
        RecipeManager rm = l.getRecipeManager();
        //#if MC>=12100
        //$$ return rm.getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.item.crafting.SingleRecipeInput(in.copyWithCount(1)), l).map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        //#else
        return rm.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(in.copyWithCount(1)), l).orElse(null);
        //#endif
    }

    public static ItemStack smeltResult(Level l, ItemStack in) {
        SmeltingRecipe r = smeltRecipe(l, in);
        return r == null ? ItemStack.EMPTY : r.getResultItem(l.registryAccess());
    }
}
