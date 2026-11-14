package com.helperbuddy.brain;

import com.helperbuddy.HelperBuddy;
import com.helperbuddy.compat.Compat;
import com.helperbuddy.entity.HelperEntity;
import com.helperbuddy.talk.Say;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The helper makes its own tools. With no tool of a kind in the bag it crafts one from what it carries (stone when it
 * has cobblestone, else wood: planks and sticks come from logs), and for a job that needs a better pickaxe than it has
 * (iron for diamonds ...) it plans the way up: wood, stone, iron, diamond.
 */
public final class Toolsmith {
    private Toolsmith() {}

    static final String[] TIERS = {"wooden", "stone", "iron", "diamond"};
    static final String[] STONES = {"minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate", "minecraft:cobbled_deepslate", "minecraft:blackstone"};

    static String word(Bag.Kind k) {
        return switch (k) {
            case PICKAXE -> "pickaxe";
            case AXE -> "axe";
            case SHOVEL -> "shovel";
            case HOE -> "hoe";
            case SWORD -> "sword";
            default -> null;
        };
    }

    static Item tool(int tier, Bag.Kind k) {
        return BuiltInRegistries.ITEM.get(HelperBuddy.rl("minecraft:" + TIERS[tier] + "_" + word(k)));
    }

    /** With no tool of this kind: makes one out of the bag (stone, else wood). At most one try every few seconds. */
    public static boolean make(HelperEntity h, Bag.Kind k) {
        if (word(k) == null || !h.brain().cooldown("toolsmith:" + k, 100)) return false;
        // a pickaxe is a must; the others only when there is wood to spare (the logs may be what it was sent for)
        if (k != Bag.Kind.PICKAXE && wood(h) < 8) return false;
        for (int tier = 1; tier >= 0; tier--) {
            Item it = tool(tier, k);
            if (it == Items.AIR) continue;
            if (Crafter.craft(h, it, 1, 0).made() > 0) {
                String nm = new ItemStack(it).getHoverName().getString();
                Say.notify(h, "made:" + k, 20 * 20, "ผมไม่มี" + k.th() + " เลยทำ §e" + nm + "§r ใช้เองแล้วนะ", "I had no " + k.en().replaceFirst("^an? ", "") + ", so I made myself a §e" + nm + "§r");
                h.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    /** The best pickaxe it carries: -1 none, 0 wood / gold, 1 stone, 2 iron, 3 diamond, 4 netherite. */
    public static int pickTier(HelperEntity h) {
        int best = tierOf(h.tool());
        for (int i = 0; i < h.bag.getContainerSize(); i++) best = Math.max(best, tierOf(h.bag.getItem(i)));
        return best;
    }

    static int tierOf(ItemStack s) {
        if (!Bag.isKind(s, Bag.Kind.PICKAXE) || Bag.nearlyBroken(s)) return -1;
        return s.getItem() instanceof TieredItem ti ? Compat.tierLevel(ti.getTier()) : 2;
    }

    /** The pickaxe an ore needs. */
    public static int tierFor(List<String> ores) {
        int need = 1; // "mine" with nothing said: a stone pickaxe gets most of what is down there
        for (String o : ores) {
            need = Math.max(need, switch (o) {
                case "coal", "nether", "quartz" -> 0;
                case "iron", "copper", "lapis" -> 1;
                case "debris" -> 3;
                default -> 2;
            });
        }
        if (ores.size() == 1 && (ores.get(0).equals("coal") || ores.get(0).equals("nether") || ores.get(0).equals("quartz"))) need = 0;
        return need;
    }

    /** The pickaxe a block needs to drop anything (-1: none). */
    public static int tierFor(Block b) {
        BlockState s = b.defaultBlockState();
        if (!s.requiresCorrectToolForDrops() || !s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return -1;
        if (s.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (s.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (s.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
    }

    static int count(HelperEntity h, java.util.function.Predicate<ItemStack> f) {
        return Bag.count(h, s -> !Bag.isTool(s) && f.test(s));
    }

    /** Logs it could still turn into planks and sticks (planks count as a quarter). */
    static int wood(HelperEntity h) {
        return count(h, s -> s.is(ItemTags.LOGS)) + count(h, s -> s.is(ItemTags.PLANKS)) / 4;
    }

    /**
     * The steps that get the helper a pickaxe of at least this tier, when it has none that good: chop a little wood,
     * dig a few stones, mine and smelt three iron, mine three diamonds, crafting the next pickaxe at every stage.
     */
    public static void prepPick(HelperEntity h, int need, List<Plan.Step> steps) {
        int have = pickTier(h);
        if (need < 0 || have >= need || need > 3) return;
        Level l = h.level();
        boolean overworld = l.dimension() == Level.OVERWORLD;
        // (above ground only: wood and plain stone are not something to go looking for in the Nether or the End)
        if (!overworld && (have < 0 && wood(h) < 2 || need >= 1 && have < 1 && count(h, s -> s.is(ItemTags.STONE_TOOL_MATERIALS)) < 3)) return;
        int logs = (need >= 2 && have < 2 ? 6 : 3) + (have < 0 ? 0 : -1);
        if (wood(h) < logs && (have < 1 || need >= 2 && count(h, s -> s.is(Items.STICK)) < 2))
            steps.add(named(Plan.work("chop", logs, false), "ตัดไม้ " + logs + " ท่อน (ทำด้ามอีเต้อ" + (need >= 2 && have < 2 ? "กับเชื้อเพลิง" : "") + ")", "chop " + logs + " logs (for the pickaxe" + (need >= 2 && have < 2 ? " and fuel" : "") + ")"));
        if (have < 0) steps.add(craft(0));
        if (need >= 1 && have < 1) {
            if (count(h, s -> s.is(ItemTags.STONE_TOOL_MATERIALS)) < 3) steps.add(Plan.gatherFor(STONES, "minecraft:cobblestone", "Cobblestone", 3, false));
            steps.add(craft(1));
        }
        if (need >= 2 && have < 2) {
            int iron = count(h, s -> s.is(Items.IRON_INGOT)) + count(h, s -> s.is(Items.RAW_IRON));
            if (iron < 3) steps.add(Plan.mine(List.of("iron"), 3 - iron, false));
            if (count(h, s -> s.is(Items.IRON_INGOT)) < 3) steps.add(Plan.task(Tasks.smelt("minecraft:iron_ingot", null), "ถลุงเหล็ก", "smelt the iron"));
            steps.add(craft(2));
        }
        if (need >= 3 && have < 3) {
            int dia = count(h, s -> s.is(Items.DIAMOND));
            if (dia < 3) steps.add(Plan.mine(List.of("diamond"), 3 - dia, false));
            steps.add(craft(3));
        }
    }

    static Plan.Step named(Plan.Step s, String th, String en) {
        s.th = th;
        s.en = en;
        return s;
    }

    static Plan.Step craft(int tier) {
        Item it = tool(tier, Bag.Kind.PICKAXE);
        String nm = new ItemStack(it).getHoverName().getString();
        return Plan.task(Tasks.keep(Tasks.craft(BuiltInRegistries.ITEM.getKey(it).toString(), 1)), "ทำ " + nm + " ใช้เอง", "make myself a " + nm);
    }
}
