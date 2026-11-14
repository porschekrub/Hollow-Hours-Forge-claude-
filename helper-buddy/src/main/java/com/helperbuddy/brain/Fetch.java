package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Get me X", for anything in the game: works out where X comes from and plans the trip. An ore is mined, wood is
 * chopped, a block of the world is dug up (in the right dimension and biome: the helper goes there by itself through a
 * nether portal), a crop is harvested, a bucket is filled, something made is crafted or smelted (after fetching what it
 * is made of), and what monsters drop is hunted for. When there really is no way, it says so.
 */
public final class Fetch {
    private Fetch() {}

    /** steps: what to do (null: it can't, th / en say why). */
    public record Result(@Nullable List<Plan.Step> steps, String th, String en) {
        public boolean ok() {
            return steps != null;
        }
    }

    static Result no(String th, String en) {
        return new Result(null, th, en);
    }

    static String name(Item i) {
        return new ItemStack(i).getHoverName().getString();
    }

    static String id(Item i) {
        return BuiltInRegistries.ITEM.getKey(i).toString();
    }

    /**
     * Adds the trip to where the thing is found, when the helper is in the wrong dimension.
     *
     * @return null when it is fine, else why it can't get there
     */
    @Nullable
    static Result travel(HelperEntity h, @Nullable Sources.Where w, String thing, List<Plan.Step> steps) {
        if (w == null || w.dim() == null) return null;
        String here = h.level().dimension().location().toString();
        if (here.equals(w.dim())) return null;
        String dTh = Errands.dimName(w.dim(), true), dEn = Errands.dimName(w.dim(), false);
        if (!Errands.portalWay(here, w.dim()))
            return no("§e" + thing + "§r มีแต่ใน" + w.th() + " จากที่นี่ผมไปเองไม่ได้ พาผมไป" + dTh + "ด้วย แล้วสั่งใหม่ที่นั่นนะ",
                    "§e" + thing + "§r is only found in " + w.en() + ". I can't get to " + dEn + " from here by myself: take me along and ask again there");
        if (Errands.portalFor(h) == null)
            return no("§e" + thing + "§r มีแต่ใน" + w.th() + " แต่แถวนี้ (128 บล็อก) ไม่มีประตูเนเธอร์ให้ผมไป สั่ง \"สร้างประตูเนเธอร์\" (ส่งออบซิเดียน 10 ก้อน + หินเหล็กไฟให้ผมก่อน) หรือพาผมไปเอง แล้วสั่งใหม่",
                    "§e" + thing + "§r is only found in " + w.en() + ", and there is no nether portal within 128 blocks for me to take. Say \"build a nether portal\" (give me 10 obsidian and a flint and steel first), or take me there and ask again");
        steps.add(Plan.task(Tasks.travel(w.dim()), "ไป" + dTh + " (ผ่านประตูเนเธอร์)", "go to " + dEn + " (through the nether portal)"));
        return null;
    }

    public static Result plan(ServerPlayer p, HelperEntity h, String itemId, @Nullable Integer n, boolean full, boolean bring) {
        return plan(p, h, itemId, n, full, bring, 0);
    }

    static Result plan(ServerPlayer p, HelperEntity h, String itemId, @Nullable Integer n, boolean full, boolean bring, int depth) {
        Item item = BuiltInRegistries.ITEM.get(HelperBuddy.rl(itemId.contains(":") ? itemId : "minecraft:" + itemId));
        if (item == Items.AIR) return no("ผมไม่รู้จักของที่ชื่อ \"" + itemId + "\" เลย", "I don't know anything called \"" + itemId + "\"");
        String nm = name(item), iid = id(item), path = Sources.path(item);
        List<Plan.Step> steps = new ArrayList<>();
        int have = Bag.count(h, item) + (h.tool().is(item) ? h.tool().getCount() : 0);
        // it has them already
        if (!full && n != null && have >= n && depth == 0) {
            steps.add(Plan.hand(iid, n, nm));
            return new Result(steps, "", "");
        }
        boolean done = false;
        // ---- an ore
        String ore = Sources.oreOf(item);
        if (ore != null) {
            Toolsmith.prepPick(h, Toolsmith.tierFor(List.of(ore)), steps);
            Result r = travel(h, Sources.oreWhere(ore), nm, steps);
            if (r != null) return r;
            steps.add(Plan.mine(List.of(ore), full ? null : n == null ? 16 : n, full));
            if (path.endsWith("_ingot") || path.equals("netherite_scrap")) steps.add(Plan.task(Tasks.smelt(iid, null), "ถลุงเป็น " + nm, "smelt it into " + nm));
            done = true;
        }
        // ---- wood (and what falls out of trees)
        if (!done && (Sources.isLog(item) || path.equals("apple") || path.endsWith("_sapling") || path.equals("stick") && Bag.count(h, st -> st.is(net.minecraft.tags.ItemTags.LOGS)
                || st.is(net.minecraft.tags.ItemTags.PLANKS)) == 0)) {
            if (Sources.isLog(item)) {
                Set<Block> own = Sources.blocksFor(item);
                Result r = travel(h, own.isEmpty() ? null : Sources.of(own), nm, steps);
                if (r != null) return r;
            }
            steps.add(Plan.work("chop", full ? null : n == null ? 32 : Sources.isLog(item) ? n : Math.max(16, n * 4), full));
            if (path.equals("stick")) steps.add(Plan.task(Tasks.craft(iid, n == null ? 16 : n), "คราฟต์ " + nm, "craft " + nm));
            done = true;
        }
        // ---- a bucket of something
        if (!done && (item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET || item == Items.MILK_BUCKET)) {
            String fluid = item == Items.LAVA_BUCKET ? "lava" : item == Items.MILK_BUCKET ? "milk" : "water";
            steps.add(Plan.task(Tasks.fill(fluid, n), "ตัก" + nm, "fill a " + nm));
            done = true;
        }
        // ---- fish
        if (!done && (item == Items.COD || item == Items.SALMON || item == Items.PUFFERFISH || item == Items.TROPICAL_FISH || item == Items.COOKED_COD || item == Items.COOKED_SALMON)) {
            steps.add(Plan.task(Tasks.fish(n == null ? 8 : n), "ตกปลา", "go fishing"));
            done = true;
        }
        // ---- a block of the world, or a crop
        if (!done) {
            Set<Block> blocks = Sources.blocksFor(item);
            if (!blocks.isEmpty()) {
                int tier = -1;
                for (Block b : blocks) tier = tier < 0 ? Toolsmith.tierFor(b) : Math.min(tier, Toolsmith.tierFor(b));
                Toolsmith.prepPick(h, tier, steps);
                Result r = travel(h, Sources.of(blocks), nm, steps);
                if (r != null) return r;
                steps.add(Plan.gatherFor(Sources.ids(blocks), iid, nm, full ? null : n == null ? 64 : n, full));
                done = true;
            }
        }
        // ---- something made at a crafting table
        if (!done && Crafter.craftable(h.level(), item)) {
            int want = n == null ? 1 : n;
            // what it is made of first, when the bag does not have it all (down to digging the ore and chopping the wood)
            List<Plan.Step> pre = depth <= 3 ? ingredients(p, h, item, want, depth) : null;
            if (pre == null && depth > 0) return no("ขาดวัตถุดิบทำ " + nm, "I lack what " + nm + " is made of");
            if (pre != null) steps.addAll(pre);
            if (depth > 0) {
                steps.add(Plan.task(Tasks.keep(Tasks.craft(iid, want)), "คราฟต์ " + nm, "craft " + nm));
                return new Result(steps, "", "");
            }
            steps.add(Plan.task(Tasks.craft(iid, want), "คราฟต์ " + nm + (want > 1 ? " " + want + " ชิ้น" : "") + " แล้วเอามาให้คุณ", "craft " + (want > 1 ? want + " " : "") + nm + " and bring it to you"));
            // (the crafting task hands it over itself)
            return new Result(steps, "", "");
        }
        // ---- something that comes out of a furnace: first what it is made of
        if (!done && depth <= 2) {
            for (var rec : Compat.recipesFor(h.getServer(), item)) {
                if (!(rec instanceof AbstractCookingRecipe) || rec.getIngredients().isEmpty()) continue;
                for (ItemStack in : rec.getIngredients().get(0).getItems()) {
                    int raw = Math.max(0, (n == null ? 16 : n) - Bag.count(h, in.getItem()));
                    Result sub = raw == 0 ? new Result(new ArrayList<>(), "", "") : plan(p, h, id(in.getItem()), raw, false, false, depth + 1);
                    if (!sub.ok()) continue;
                    steps.addAll(sub.steps());
                    if (fuel(h) < 1) steps.add(Toolsmith.named(Plan.work("chop", 2, false), "ตัดไม้ 2 ท่อน (เชื้อเพลิง)", "chop 2 logs (fuel)"));
                    steps.add(Plan.task(Tasks.smelt(iid, n), "ถลุงเป็น " + nm + " (ต้องมีเชื้อเพลิงในกระเป๋าผม)", "smelt it into " + nm + " (I need fuel in my bag)"));
                    done = true;
                    break;
                }
                if (done) break;
            }
        }
        // ---- what a monster or an animal drops
        if (!done) {
            Sources.Drop d = Sources.dropOf(item);
            if (d != null) {
                Result r = travel(h, d.where(), nm, steps);
                if (r != null) return r;
                StringBuilder mobs = new StringBuilder();
                for (String m : d.mobs()) mobs.append(mobs.length() > 0 ? " / " : "").append(m.replace('_', ' '));
                steps.add(Plan.task(Tasks.slay(d.mobs(), iid, n), "ล่า " + mobs + " เอา " + nm + (n != null ? " " + n + " ชิ้น" : ""), "hunt " + mobs + " for " + (n != null ? n + " " : "") + nm));
                done = true;
            }
        }
        if (!done) {
            if (have > 0 && depth == 0) {
                steps.add(Plan.hand(iid, n, nm));
                return new Result(steps, "", "");
            }
            boolean treasure = item == Items.SADDLE || item == Items.NAME_TAG || item == Items.NAUTILUS_SHELL || item == Items.ENCHANTED_BOOK;
            return no("ผมหา §e" + nm + "§r มาเองไม่ได้: มันไม่ใช่บล็อกที่ขุดได้ คราฟต์หรือถลุงไม่ได้ และไม่มีตัวไหนดรอป"
                            + (treasure ? " (แต่ตกปลาอาจได้ ลองสั่ง \"ตกปลา\")" : " (ต้องหาจากหีบตามสิ่งก่อสร้าง หรือแลกกับชาวบ้าน: ลองสั่ง \"ซื้อ " + nm + " จากชาวบ้าน\")"),
                    "I can't get §e" + nm + "§r by myself: it can't be dug up, crafted or smelted, and nothing drops it"
                            + (treasure ? " (fishing may bring one up: try \"go fishing\")" : " (it comes from chests in structures, or from villagers: try \"buy " + nm + " from villagers\")"));
        }
        if (bring && depth == 0) steps.add(Plan.hand(iid, full ? null : n, nm));
        return new Result(steps, "", "");
    }

    /** "Craft n of this": the steps that fetch what the bag lacks for it (empty: nothing lacking; null: it can't get it all). */
    @Nullable
    public static List<Plan.Step> craft(ServerPlayer p, HelperEntity h, Item item, int n) {
        if (item == Items.AIR || !Crafter.craftable(h.level(), item)) return null;
        return ingredients(p, h, item, n, 0);
    }

    /** Things in the bag that burn (in furnace loads, roughly). */
    static int fuel(HelperEntity h) {
        return Bag.count(h, st -> !Bag.isTool(st) && !st.is(Items.LAVA_BUCKET) && Compat.burnTime(st) > 0);
    }

    /**
     * What is still needed to craft n of an item, as steps (each ingredient the bag lacks is fetched the way the item
     * itself would be: mined, chopped, dug up, hunted for, smelted or crafted in turn). An empty list: the bag has it
     * all. Null: no recipe whose ingredients it can get.
     */
    @Nullable
    static List<Plan.Step> ingredients(ServerPlayer p, HelperEntity h, Item item, int n, int depth) {
        List<Plan.Step> best = null;
        for (var rec : Crafter.recipesFor(h.level(), item)) {
            int per = Math.max(1, rec.getResultItem(h.level().registryAccess()).getCount());
            int crafts = (n + per - 1) / per;
            // (what is only in another dimension is fetched first, then it comes back for the rest)
            List<Plan.Step> out = new ArrayList<>(), far = new ArrayList<>();
            int logs = 0;
            boolean ok = true, smelts = false;
            for (Object[] e : Crafter.needs(rec)) {
                var ing = (net.minecraft.world.item.crafting.Ingredient) e[0];
                int miss = (Integer) e[1] * crafts - Crafter.have(h, ing);
                if (miss <= 0) continue;
                ItemStack[] opts = ing.getItems();
                boolean planks = false, sticks = false, log = false;
                for (ItemStack o : opts) {
                    planks |= o.is(net.minecraft.tags.ItemTags.PLANKS);
                    sticks |= o.is(Items.STICK);
                    log |= o.is(net.minecraft.tags.ItemTags.LOGS);
                }
                // wood in any shape: logs (planks and sticks are made from them while crafting)
                if (planks || sticks || log) {
                    logs += log ? miss : planks ? (miss + 3) / 4 : (miss + 7) / 8;
                    continue;
                }
                Result sub = null;
                for (int i = 0; i < Math.min(opts.length, 4) && sub == null; i++) {
                    if (opts[i].getItem() == item) continue;
                    Result r = plan(p, h, id(opts[i].getItem()), miss, false, false, depth + 1);
                    if (r.ok()) sub = r;
                }
                if (sub == null) {
                    ok = false;
                    break;
                }
                boolean trip = false;
                for (Plan.Step st : sub.steps()) {
                    smelts |= st.task != null && st.task.getString("kind").equals("smelt");
                    trip |= st.task != null && st.task.getString("kind").equals("travel") || st.k.equals("come");
                }
                (trip ? far : out).addAll(sub.steps());
            }
            if (!ok) continue;
            if (smelts && fuel(h) < 2) logs += 2;
            int have = Bag.count(h, st -> st.is(net.minecraft.tags.ItemTags.LOGS)) + Bag.count(h, st -> st.is(net.minecraft.tags.ItemTags.PLANKS)) / 4;
            if (logs > have) {
                int more = logs - have;
                // (one trip to the trees: wood a step further down already asks for is cut in the same go)
                Plan.Step first = null;
                for (Plan.Step st : out) if (st.k.equals("chop") && st.n != null && !st.full) {
                    first = st;
                    break;
                }
                if (first != null) {
                    out.remove(first);
                    more += first.n;
                }
                out.add(0, Toolsmith.named(Plan.work("chop", more, false), "ตัดไม้ " + more + " ท่อน", "chop " + more + " logs"));
            }
            if (!far.isEmpty()) {
                // one trip for everything that is over there
                List<Plan.Step> trips = new ArrayList<>();
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Plan.Step st : far) {
                    if (st.k.equals("come")) continue;
                    if (st.task != null && st.task.getString("kind").equals("travel") && !seen.add(st.task.getString("dim"))) continue;
                    trips.add(st);
                }
                trips.add(Plan.simple("come", "กลับมาหาคุณ", "come back to you"));
                out.addAll(0, trips);
            }
            if (out.size() > 14) continue;
            if (best == null || out.size() < best.size()) best = out;
            if (best.isEmpty()) break;
        }
        return best;
    }

    /** Mining ores that are only in the Nether, from somewhere else: the trip there first. */
    public static Result mine(HelperEntity h, List<String> ores, @Nullable Integer n, boolean full) {
        List<Plan.Step> steps = new ArrayList<>();
        boolean nether = !ores.isEmpty();
        for (String o : ores) if (Sources.oreWhere(o) == null) nether = false;
        // no pickaxe (or not a good enough one)? it makes its own first
        Toolsmith.prepPick(h, Toolsmith.tierFor(ores), steps);
        boolean trip = nether && !h.level().dimension().location().toString().equals(Sources.NETHER);
        if (!trip && steps.isEmpty()) return new Result(steps, "", "");
        if (trip) {
            Result r = travel(h, Sources.oreWhere(ores.get(0)), com.helperbuddy.talk.Nlu.oreName(ores.get(0), false), steps);
            if (r != null) return r;
        }
        steps.add(Plan.mine(ores, full ? null : n == null ? 16 : n, full));
        steps.add(Plan.simple("deliver", "เอาของมาให้คุณ", "bring you the items"));
        return new Result(steps, "", "");
    }

    /** Collecting blocks that are only in another dimension: the trip there first (an empty result: no trip needed). */
    public static Result gather(HelperEntity h, String[] ids, String th, String en, @Nullable Integer n, boolean full, boolean thai) {
        List<Plan.Step> steps = new ArrayList<>();
        Sources.Where w = null;
        for (String id : ids) {
            Block b = BuiltInRegistries.BLOCK.get(HelperBuddy.rl(id.contains(":") ? id : "minecraft:" + id));
            if (w == null) w = Sources.of(b);
        }
        if (w == null || w.dim() == null || h.level().dimension().location().toString().equals(w.dim())) return new Result(steps, "", "");
        Result r = travel(h, w, thai ? th : en, steps);
        if (r != null) return r;
        steps.add(Plan.gather(ids, th, en, full ? null : n == null ? 64 : n, full, thai));
        steps.add(Plan.simple("deliver", "เอาของมาให้คุณ", "bring you the items"));
        return new Result(steps, "", "");
    }

    /** Where a mob lives (from what it drops), for a hunt. */
    @Nullable
    public static Sources.Where mobWhere(String mob) {
        String m = mob.replace("minecraft:", "");
        for (Sources.Drop d : Sources.DROPS.values()) for (String x : d.mobs()) if (x.equals(m)) return d.where();
        return null;
    }

    /** A hunt for a kind of mob ("kill 5 blazes"): with the trip to its dimension when needed. */
    public static Result slay(HelperEntity h, String[] mobs, @Nullable Integer n) {
        List<Plan.Step> steps = new ArrayList<>();
        String names = String.join(" / ", mobs).replace("minecraft:", "").replace('_', ' ');
        Result r = travel(h, mobWhere(mobs[0]), names, steps);
        if (r != null) return r;
        steps.add(Plan.task(Tasks.slay(mobs, null, n), "ล่า " + names + (n != null ? " " + n + " ตัว" : ""), "hunt " + (n != null ? n + " " : "") + names));
        return new Result(steps, "", "");
    }
}
