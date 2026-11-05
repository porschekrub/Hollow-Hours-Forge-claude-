package com.hollowhours.client.recipes;

import com.hollowhours.compat.ItemData;
import com.hollowhours.HollowHours;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every item of the game (other mods too) and every recipe the server sent to this client, indexed by what they make
 * and by what they use. Built the first time the recipe book opens, thrown away when recipes or tags change.
 */
public final class RecipeIndex {
    private RecipeIndex() {}

    public enum Kind {
        CRAFTING("โต๊ะคราฟต์", "Crafting Table"), SMELTING("เตาเผา", "Furnace"), BLASTING("เตาถลุงแร่", "Blast Furnace"),
        SMOKING("เตารมควัน", "Smoker"), CAMPFIRE("กองไฟ", "Campfire"), STONECUTTING("เครื่องตัดหิน", "Stonecutter"),
        SMITHING("โต๊ะช่างตีเหล็ก", "Smithing Table"), OTHER(null, null);

        final String th, en;

        Kind(String th, String en) {
            this.th = th;
            this.en = en;
        }
    }

    /** One recipe, ready to draw: slots (each a list of the items that fit), the grid size, the result. */
    public static final class Shown {
        public final Kind kind;
        public final String typeId, id;
        public final List<List<ItemStack>> in;
        public final int cols, rows;
        public final ItemStack out;
        public final boolean shapeless;
        public final int time;
        public final float xp;

        Shown(Kind kind, String typeId, String id, List<List<ItemStack>> in, int cols, int rows, ItemStack out, boolean shapeless, int time, float xp) {
            this.kind = kind;
            this.typeId = typeId;
            this.id = id;
            this.in = in;
            this.cols = Math.max(1, cols);
            this.rows = Math.max(1, rows);
            this.out = out;
            this.shapeless = shapeless;
            this.time = time;
            this.xp = xp;
        }

        /** The block it is made in (shown as a little icon). */
        public ItemStack station() {
            return switch (kind) {
                case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
                case SMELTING -> new ItemStack(Items.FURNACE);
                case BLASTING -> new ItemStack(Items.BLAST_FURNACE);
                case SMOKING -> new ItemStack(Items.SMOKER);
                case CAMPFIRE -> new ItemStack(Items.CAMPFIRE);
                case STONECUTTING -> new ItemStack(Items.STONECUTTER);
                case SMITHING -> new ItemStack(Items.SMITHING_TABLE);
                default -> {
                    // a mod's machine is often named like its recipe type ("somemod:crusher")
                    ResourceLocation rl = ResourceLocation.tryParse(typeId);
                    Item it = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
                    yield it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
                }
            };
        }

        public String label(boolean thai) {
            if (kind != Kind.OTHER) return thai ? kind.th : kind.en;
            ItemStack st = station();
            return st.isEmpty() ? typeId : st.getHoverName().getString();
        }
    }

    private static List<ItemStack> items;
    private static List<Shown> all;
    private static Map<Item, List<Shown>> byOut, byIn;

    public static synchronized void clear() {
        items = null;
        all = null;
        byOut = byIn = null;
    }

    public static synchronized boolean ready() {
        return all != null;
    }

    public static synchronized void ensure() {
        if (all != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long t0 = System.nanoTime();
        RegistryAccess ra = mc.level.registryAccess();
        FeatureFlagSet features = mc.player.connection.enabledFeatures();
        // the item list: everything the creative search tab shows (potions, enchanted books... of every mod),
        // then any item that sits in no tab at all
        List<ItemStack> list = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        try {
            CreativeModeTabs.tryRebuildTabContents(features, false, ra);
            for (ItemStack s : CreativeModeTabs.searchTab().getDisplayItems()) {
                if (s.isEmpty()) continue;
                list.add(s.copyWithCount(1));
                seen.add(s.getItem());
            }
        } catch (Throwable ex) {
            HollowHours.LOG.warn("[recipes] creative tabs could not be read", ex);
        }
        for (Item it : BuiltInRegistries.ITEM)
            if (it != Items.AIR && !seen.contains(it) && it.isEnabled(features)) {
                list.add(new ItemStack(it));
                seen.add(it);
            }
        // one plain stack of each item (used to find what fits a smithing recipe)
        List<ItemStack> plain = new ArrayList<>();
        Set<Item> once = new HashSet<>();
        for (ItemStack s : list) if (once.add(s.getItem())) plain.add(new ItemStack(s.getItem()));

        List<Shown> shown = new ArrayList<>();
        int failed = 0;
        //#if MC>=12100
        //$$ for (var holder : mc.level.getRecipeManager().getRecipes()) {
        //$$     Recipe<?> r = holder.value();
        //$$     ResourceLocation rid = holder.id();
        //#else
        for (Recipe<?> r : mc.level.getRecipeManager().getRecipes()) {
            ResourceLocation rid = r.getId();
        //#endif
            try {
                Shown s = shown(r, rid, ra, plain);
                if (s != null) shown.add(s);
            } catch (Throwable ex) {
                failed++;
            }
        }
        shown.sort(Comparator.<Shown>comparingInt(s -> s.kind.ordinal()).thenComparing(s -> s.id));
        Map<Item, List<Shown>> outs = new HashMap<>(), ins = new HashMap<>();
        for (Shown s : shown) {
            outs.computeIfAbsent(s.out.getItem(), k -> new ArrayList<>()).add(s);
            Set<Item> used = new LinkedHashSet<>();
            for (List<ItemStack> slot : s.in) for (ItemStack st : slot) used.add(st.getItem());
            for (Item it : used) ins.computeIfAbsent(it, k -> new ArrayList<>()).add(s);
        }
        items = list;
        all = shown;
        byOut = outs;
        byIn = ins;
        HollowHours.LOG.info("[recipes] {} items, {} recipes indexed ({} skipped) in {} ms", list.size(), shown.size(), failed, (System.nanoTime() - t0) / 1_000_000);
    }

    public static synchronized List<ItemStack> items() {
        return items == null ? List.of() : items;
    }

    public static synchronized int recipeCount() {
        return all == null ? 0 : all.size();
    }

    /** Recipes that make this item (uses = false) or that need it (uses = true). */
    public static synchronized List<Shown> forItem(ItemStack focus, boolean uses) {
        if (all == null || focus.isEmpty()) return List.of();
        List<Shown> l = (uses ? byIn : byOut).getOrDefault(focus.getItem(), List.of());
        if (uses || !ItemData.has(focus)) return l;
        // a special variant (a named potion...): prefer recipes making exactly that one
        List<Shown> exact = new ArrayList<>();
        for (Shown s : l) if (!ItemData.has(s.out) || ItemData.sameItemSameData(s.out, focus)) exact.add(s);
        return exact.isEmpty() ? l : exact;
    }

    static List<ItemStack> alts(Ingredient ing) {
        if (ing == null || ing.isEmpty()) return List.of();
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : ing.getItems()) if (!s.isEmpty()) l.add(s);
        return List.copyOf(l);
    }

    static List<List<ItemStack>> nonEmpty(NonNullList<Ingredient> ings) {
        List<List<ItemStack>> in = new ArrayList<>();
        for (Ingredient i : ings) {
            List<ItemStack> a = alts(i);
            if (!a.isEmpty()) in.add(a);
        }
        return in;
    }

    static Shown shown(Recipe<?> r, ResourceLocation rid, RegistryAccess ra, List<ItemStack> plain) {
        ItemStack out = r.getResultItem(ra);
        if (out == null || out.isEmpty()) return null;
        out = out.copy();
        RecipeType<?> type = r.getType();
        ResourceLocation tk = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        String typeId = tk == null ? "?" : tk.toString();
        String id = rid == null ? "" : rid.toString();
        if (type == RecipeType.CRAFTING) {
            NonNullList<Ingredient> ing = r.getIngredients();
            if (ing.isEmpty()) return null;
            //#if MC>=12100
            //$$ if (r instanceof net.minecraft.world.item.crafting.ShapedRecipe sh) {
            //$$     int w = sh.getWidth(), h = sh.getHeight();
            //#else
            if (r instanceof IShapedRecipe<?> sh) {
                int w = sh.getRecipeWidth(), h = sh.getRecipeHeight();
            //#endif
                int gw = Math.max(3, w), gh = Math.max(3, h);
                List<List<ItemStack>> in = new ArrayList<>();
                for (int i = 0; i < gw * gh; i++) in.add(List.of());
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        if (i < ing.size()) in.set(y * gw + x, alts(ing.get(i)));
                    }
                return new Shown(Kind.CRAFTING, typeId, id, in, gw, gh, out, false, 0, 0);
            }
            List<List<ItemStack>> in = nonEmpty(ing);
            if (in.isEmpty()) return null;
            int n = in.size(), cols = n <= 1 ? 1 : n <= 4 ? 2 : 3;
            return new Shown(Kind.CRAFTING, typeId, id, in, cols, (n + cols - 1) / cols, out, true, 0, 0);
        }
        if (r instanceof AbstractCookingRecipe c) {
            Kind k = type == RecipeType.SMELTING ? Kind.SMELTING : type == RecipeType.BLASTING ? Kind.BLASTING
                    : type == RecipeType.SMOKING ? Kind.SMOKING : type == RecipeType.CAMPFIRE_COOKING ? Kind.CAMPFIRE : Kind.OTHER;
            List<List<ItemStack>> in = nonEmpty(c.getIngredients());
            if (in.isEmpty()) return null;
            return new Shown(k, typeId, id, in, 1, 1, out, false, c.getCookingTime(), c.getExperience());
        }
        if (type == RecipeType.STONECUTTING) {
            List<List<ItemStack>> in = nonEmpty(r.getIngredients());
            if (in.isEmpty()) return null;
            return new Shown(Kind.STONECUTTING, typeId, id, in, 1, 1, out, false, 0, 0);
        }
        if (r instanceof SmithingRecipe sm) {
            List<ItemStack> t = new ArrayList<>(), b = new ArrayList<>(), a = new ArrayList<>();
            for (ItemStack s : plain) {
                if (sm.isTemplateIngredient(s)) t.add(s);
                if (sm.isBaseIngredient(s)) b.add(s);
                if (sm.isAdditionIngredient(s)) a.add(s);
            }
            if (b.isEmpty()) return null;
            return new Shown(Kind.SMITHING, typeId, id, Arrays.asList(List.copyOf(t), List.copyOf(b), List.copyOf(a)), 3, 1, out, false, 0, 0);
        }
        // a mod's own kind of recipe: show what goes in and what comes out
        List<List<ItemStack>> in = nonEmpty(r.getIngredients());
        if (in.isEmpty()) return null;
        int n = in.size(), cols = n <= 3 ? n : n <= 9 ? 3 : 4;
        return new Shown(Kind.OTHER, typeId, id, in, cols, (n + cols - 1) / cols, out, false, 0, 0);
    }
}
