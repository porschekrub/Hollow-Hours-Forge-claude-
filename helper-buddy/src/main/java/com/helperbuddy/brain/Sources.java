package com.helperbuddy.brain;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where things come from: the dimension and biomes a block is found in, the mobs that drop an item, the ore an item is
 * mined from. With it "get me soul sand" works from anywhere: the helper knows it has to go to a soul sand valley in the
 * Nether, and "get ender pearls" means hunting endermen.
 */
public final class Sources {
    private Sources() {}

    public static final String OVERWORLD = "minecraft:overworld", NETHER = "minecraft:the_nether", END = "minecraft:the_end";

    /** dim: the only dimension it is found in (null: any); biomes: where it is common ("#" = a biome tag); structure: or in that structure. */
    public record Where(@Nullable String dim, String[] biomes, @Nullable String structure, String th, String en) {}

    /** where trees are, for a woodcutter with none left around */
    public static final Where FOREST = new Where(OVERWORLD, new String[] {"#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle"}, null, "ป่า", "a forest");

    static final Map<String, Where> BLOCKS = new HashMap<>();
    /** item -> {the mobs that drop it}, with where they live */
    public record Drop(String[] mobs, Where where) {}

    static final Map<String, Drop> DROPS = new HashMap<>();
    static final Map<String, String> ORE_OF = new HashMap<>();

    static void put(Where w, String... blocks) {
        for (String b : blocks) BLOCKS.put(b, w);
    }

    static void drop(String item, Where w, String... mobs) {
        DROPS.put(item, new Drop(mobs, w));
    }

    static {
        String[] none = {};
        // ---- the Nether
        put(new Where(NETHER, new String[] {"minecraft:soul_sand_valley"}, null, "หุบเขาทรายวิญญาณในเนเธอร์", "a soul sand valley in the Nether"), "soul_sand", "soul_soil", "bone_block");
        put(new Where(NETHER, new String[] {"minecraft:crimson_forest"}, null, "ป่าคริมสันในเนเธอร์", "a crimson forest in the Nether"), "crimson_stem", "crimson_nylium", "crimson_fungus",
                "crimson_roots", "weeping_vines", "weeping_vines_plant", "nether_wart_block");
        put(new Where(NETHER, new String[] {"minecraft:warped_forest"}, null, "ป่าวาร์ปในเนเธอร์", "a warped forest in the Nether"), "warped_stem", "warped_nylium", "warped_fungus",
                "warped_roots", "twisting_vines", "twisting_vines_plant", "warped_wart_block", "nether_sprouts");
        put(new Where(NETHER, new String[] {"minecraft:crimson_forest", "minecraft:warped_forest"}, null, "ป่าในเนเธอร์", "a forest in the Nether"), "shroomlight");
        put(new Where(NETHER, new String[] {"minecraft:basalt_deltas"}, null, "ดินแดนบะซอลต์ในเนเธอร์", "the basalt deltas in the Nether"), "basalt", "blackstone");
        put(new Where(NETHER, none, null, "เนเธอร์", "the Nether"), "netherrack", "glowstone", "nether_quartz_ore", "nether_gold_ore", "ancient_debris", "magma_block");
        put(new Where(NETHER, none, "minecraft:fortress", "ป้อมเนเธอร์", "a nether fortress"), "nether_wart", "nether_bricks");
        // ---- the End
        put(new Where(END, none, null, "ดิเอนด์", "the End"), "end_stone", "chorus_plant", "chorus_flower", "purpur_block");
        // ---- the Overworld
        put(new Where(OVERWORLD, new String[] {"minecraft:desert", "#minecraft:is_beach"}, null, "ทะเลทรายหรือชายหาด", "a desert or a beach"), "sand", "sandstone");
        put(new Where(OVERWORLD, new String[] {"#minecraft:is_badlands"}, null, "เมซา (ดินแดง)", "the badlands"), "red_sand", "red_sandstone", "terracotta", "orange_terracotta",
                "yellow_terracotta", "red_terracotta", "brown_terracotta", "white_terracotta", "light_gray_terracotta");
        put(new Where(OVERWORLD, new String[] {"minecraft:desert", "#minecraft:is_badlands"}, null, "ทะเลทราย", "a desert"), "cactus", "dead_bush");
        put(new Where(OVERWORLD, new String[] {"minecraft:ice_spikes", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:frozen_river"}, null, "ที่หนาวจัด (ทะเลน้ำแข็ง)",
                "a frozen place"), "ice", "packed_ice", "blue_ice");
        put(new Where(OVERWORLD, new String[] {"minecraft:snowy_plains", "minecraft:grove", "minecraft:snowy_slopes", "minecraft:snowy_taiga", "minecraft:ice_spikes"}, null, "ทุ่งหิมะ",
                "a snowy place"), "snow_block", "snow", "powder_snow");
        put(new Where(OVERWORLD, new String[] {"minecraft:swamp", "minecraft:lush_caves", "#minecraft:is_river"}, null, "แม่น้ำหรือหนองน้ำ", "a river or a swamp"), "clay");
        put(new Where(OVERWORLD, new String[] {"minecraft:mangrove_swamp"}, null, "ป่าชายเลน", "a mangrove swamp"), "mud", "mangrove_log", "mangrove_roots", "muddy_mangrove_roots");
        put(new Where(OVERWORLD, new String[] {"minecraft:bamboo_jungle", "#minecraft:is_jungle"}, null, "ป่าดงดิบ", "a jungle"), "bamboo", "melon", "cocoa", "jungle_log");
        put(new Where(OVERWORLD, new String[] {"#minecraft:is_river", "minecraft:swamp", "#minecraft:is_beach"}, null, "ริมน้ำ", "a river bank"), "sugar_cane");
        put(new Where(OVERWORLD, new String[] {"minecraft:lush_caves"}, null, "ถ้ำเขียว", "lush caves"), "moss_block", "moss_carpet", "azalea", "flowering_azalea", "big_dripleaf",
                "small_dripleaf", "cave_vines", "cave_vines_plant", "spore_blossom");
        put(new Where(OVERWORLD, new String[] {"minecraft:dripstone_caves"}, null, "ถ้ำหินย้อย", "dripstone caves"), "pointed_dripstone", "dripstone_block");
        put(new Where(OVERWORLD, new String[] {"minecraft:mushroom_fields", "minecraft:dark_forest", "minecraft:swamp"}, null, "เกาะเห็ดหรือป่ามืด", "a mushroom island or a dark forest"),
                "red_mushroom", "brown_mushroom", "red_mushroom_block", "brown_mushroom_block", "mushroom_stem", "mycelium");
        put(new Where(OVERWORLD, new String[] {"minecraft:flower_forest", "minecraft:meadow", "minecraft:sunflower_plains"}, null, "ทุ่งดอกไม้", "a flower forest"), "dandelion", "poppy",
                "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower",
                "lilac", "rose_bush", "peony");
        put(new Where(OVERWORLD, new String[] {"#minecraft:is_ocean"}, null, "ทะเล", "the ocean"), "kelp", "kelp_plant", "seagrass", "tall_seagrass", "sea_pickle", "prismarine");
        put(new Where(OVERWORLD, new String[] {"minecraft:warm_ocean"}, null, "ทะเลอุ่น (แนวปะการัง)", "a warm ocean (coral reef)"), "tube_coral_block", "brain_coral_block",
                "bubble_coral_block", "fire_coral_block", "horn_coral_block");
        put(new Where(OVERWORLD, new String[] {"minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:bamboo_jungle"}, null, "ป่าสนโบราณ", "an old growth taiga"),
                "podzol");
        put(new Where(OVERWORLD, new String[] {"minecraft:cherry_grove"}, null, "ป่าซากุระ", "a cherry grove"), "cherry_log", "cherry_leaves", "pink_petals");
        put(new Where(OVERWORLD, new String[] {"#minecraft:is_savanna"}, null, "ซาวันนา", "a savanna"), "acacia_log");
        put(new Where(OVERWORLD, new String[] {"minecraft:dark_forest"}, null, "ป่ามืด", "a dark forest"), "dark_oak_log");
        put(new Where(OVERWORLD, new String[] {"#minecraft:is_taiga"}, null, "ป่าสน", "a taiga"), "spruce_log", "sweet_berry_bush");
        put(new Where(OVERWORLD, new String[] {"minecraft:birch_forest", "minecraft:old_growth_birch_forest"}, null, "ป่าเบิร์ช", "a birch forest"), "birch_log");
        put(new Where(OVERWORLD, new String[] {"minecraft:swamp", "minecraft:mangrove_swamp"}, null, "หนองน้ำ", "a swamp"), "lily_pad");
        put(new Where(OVERWORLD, new String[] {"minecraft:deep_dark"}, null, "ดีพดาร์ก", "the deep dark"), "sculk", "sculk_vein", "sculk_catalyst", "sculk_sensor");
        put(new Where(OVERWORLD, none, "#minecraft:mineshaft", "เหมืองร้าง", "a mineshaft"), "cobweb");
        put(new Where(OVERWORLD, none, null, "โลกปกติ", "the Overworld"), "dirt", "grass_block", "stone", "cobblestone", "gravel", "granite", "diorite", "andesite", "tuff", "deepslate",
                "cobbled_deepslate", "pumpkin", "oak_log", "oak_leaves", "obsidian", "calcite", "amethyst_block");

        // ---- what monsters and animals drop
        Where night = new Where(OVERWORLD, none, null, "โลกปกติ (ตอนกลางคืนหรือในถ้ำ)", "the Overworld (at night or in caves)");
        Where fort = new Where(NETHER, none, "minecraft:fortress", "ป้อมเนเธอร์", "a nether fortress");
        drop("ender_pearl", new Where(null, new String[] {"minecraft:warped_forest"}, null, "ที่มืดๆ ตอนกลางคืน ป่าวาร์ป หรือดิเอนด์", "dark places at night, a warped forest or the End"), "enderman");
        drop("blaze_rod", fort, "blaze");
        drop("wither_skeleton_skull", fort, "wither_skeleton");
        drop("gunpowder", night, "creeper", "witch");
        drop("string", night, "spider", "cave_spider");
        drop("spider_eye", night, "spider", "cave_spider");
        drop("bone", night, "skeleton", "stray", "bogged");
        drop("arrow", night, "skeleton", "stray");
        drop("rotten_flesh", night, "zombie", "husk", "drowned", "zombie_villager");
        drop("slime_ball", new Where(OVERWORLD, new String[] {"minecraft:swamp"}, null, "หนองน้ำตอนกลางคืน", "a swamp at night"), "slime");
        drop("phantom_membrane", night, "phantom");
        drop("leather", new Where(OVERWORLD, none, null, "ทุ่งหญ้า", "grassland"), "cow", "mooshroom");
        drop("feather", new Where(OVERWORLD, none, null, "ทุ่งหญ้า", "grassland"), "chicken");
        drop("rabbit_hide", new Where(OVERWORLD, new String[] {"minecraft:desert", "minecraft:flower_forest", "#minecraft:is_taiga"}, null, "ทะเลทรายหรือป่าสน", "a desert or a taiga"), "rabbit");
        drop("rabbit_foot", new Where(OVERWORLD, new String[] {"minecraft:desert", "minecraft:flower_forest", "#minecraft:is_taiga"}, null, "ทะเลทรายหรือป่าสน", "a desert or a taiga"), "rabbit");
        drop("ink_sac", new Where(OVERWORLD, new String[] {"#minecraft:is_ocean", "#minecraft:is_river"}, null, "ทะเลหรือแม่น้ำ", "the ocean or a river"), "squid");
        drop("glow_ink_sac", new Where(OVERWORLD, none, null, "ถ้ำใต้ดินที่มีน้ำ", "dark water underground"), "glow_squid");
        drop("ghast_tear", new Where(NETHER, new String[] {"minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:nether_wastes"}, null, "เนเธอร์", "the Nether"), "ghast");
        drop("magma_cream", new Where(NETHER, new String[] {"minecraft:basalt_deltas"}, null, "ดินแดนบะซอลต์ในเนเธอร์", "the basalt deltas in the Nether"), "magma_cube");
        drop("prismarine_shard", new Where(OVERWORLD, none, "minecraft:monument", "วิหารใต้ทะเล", "an ocean monument"), "guardian", "elder_guardian");
        drop("prismarine_crystals", new Where(OVERWORLD, none, "minecraft:monument", "วิหารใต้ทะเล", "an ocean monument"), "guardian", "elder_guardian");
        drop("shulker_shell", new Where(END, none, "minecraft:end_city", "เมืองเอนด์", "an end city"), "shulker");
        drop("breeze_rod", new Where(OVERWORLD, none, "minecraft:trial_chambers", "ห้องทดสอบ", "trial chambers"), "breeze");

        // ---- what is mined from an ore (the ore groups of the mining job)
        String[][] ores = {{"coal", "coal"}, {"raw_iron", "iron"}, {"iron_ingot", "iron"}, {"iron_nugget", "iron"}, {"raw_copper", "copper"}, {"copper_ingot", "copper"},
                {"raw_gold", "gold"}, {"gold_ingot", "gold"}, {"redstone", "redstone"}, {"lapis_lazuli", "lapis"}, {"diamond", "diamond"}, {"emerald", "emerald"},
                {"quartz", "nether"}, {"gold_nugget", "nether"}, {"ancient_debris", "debris"}, {"netherite_scrap", "debris"},
                {"coal_ore", "coal"}, {"iron_ore", "iron"}, {"copper_ore", "copper"}, {"gold_ore", "gold"}, {"redstone_ore", "redstone"}, {"lapis_ore", "lapis"},
                {"diamond_ore", "diamond"}, {"emerald_ore", "emerald"}, {"nether_quartz_ore", "nether"}, {"nether_gold_ore", "nether"}};
        for (String[] o : ores) ORE_OF.put(o[0], o[1]);
    }

    static String path(Block b) {
        var id = BuiltInRegistries.BLOCK.getKey(b);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    static String path(Item i) {
        var id = BuiltInRegistries.ITEM.getKey(i);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }

    @Nullable
    public static Where of(Block b) {
        return BLOCKS.get(path(b));
    }

    /** Where these blocks are found, when all of them agree on a dimension (the first known one). */
    @Nullable
    public static Where of(Collection<Block> blocks) {
        for (Block b : blocks) {
            Where w = of(b);
            if (w != null) return w;
        }
        return null;
    }

    @Nullable
    public static Drop dropOf(Item item) {
        Drop d = DROPS.get(path(item));
        if (d == null) return null;
        // only mobs that exist in this version of the game
        List<String> mobs = new ArrayList<>();
        for (String m : d.mobs()) if (EntityType.byString("minecraft:" + m).isPresent()) mobs.add(m);
        return mobs.isEmpty() ? null : new Drop(mobs.toArray(new String[0]), d.where());
    }

    /** The ore group ("iron", "diamond" ...) an item is mined from, or null. */
    @Nullable
    public static String oreOf(Item item) {
        return ORE_OF.get(path(item));
    }

    /** Where an ore group is found. */
    @Nullable
    public static Where oreWhere(String group) {
        if (group.equals("nether") || group.equals("debris")) return BLOCKS.get("netherrack");
        return null;
    }

    public static boolean isLog(Item item) {
        return new ItemStack(item).is(ItemTags.LOGS);
    }

    /**
     * A block that is part of the world (ground, stone, plants ...), so collecting it means going out to dig it up.
     * Planks, glass, doors and the like are made, not found: those are crafted instead (and never torn out of a house).
     */
    public static boolean natural(Block b) {
        if (b == Blocks.AIR) return false;
        if (BLOCKS.containsKey(path(b))) return true;
        BlockState s = b.defaultBlockState();
        if (Jobs.diggable(s) || Jobs.isLeaf(s)) return true;
        if (s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.NYLIUM)
                || s.is(BlockTags.WART_BLOCKS) || s.is(BlockTags.CROPS) || s.is(BlockTags.CAVE_VINES) || s.is(BlockTags.TERRACOTTA)) return true;
        return b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN || b == Blocks.GLOWSTONE || b == Blocks.VINE || b == Blocks.LILY_PAD || b == Blocks.COBWEB
                || b == Blocks.SEA_LANTERN || b == Blocks.SPONGE || b == Blocks.WET_SPONGE || b == Blocks.BEE_NEST || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.GRASS_BLOCK
                || b == Blocks.BASALT || b == Blocks.BLACKSTONE || b == Blocks.PRISMARINE || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.TALL_GRASS
                || b == Blocks.DEAD_BUSH || b == Blocks.HANGING_ROOTS || b == Blocks.GLOW_LICHEN || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM
                || b == Blocks.PUMPKIN || b == Blocks.MELON || b == Blocks.NETHER_WART || b == Blocks.COCOA;
    }

    /** The blocks to dig up for an item: its own block, and the plant a crop item grows on ("wheat" -> the wheat crop). */
    public static Set<Block> blocksFor(Item item) {
        Set<Block> out = new LinkedHashSet<>();
        if (item instanceof BlockItem bi && natural(bi.getBlock())) out.add(bi.getBlock());
        String p = path(item);
        Block plant = switch (p) {
            case "wheat" -> Blocks.WHEAT;
            case "carrot" -> Blocks.CARROTS;
            case "potato" -> Blocks.POTATOES;
            case "beetroot" -> Blocks.BEETROOTS;
            case "melon_slice" -> Blocks.MELON;
            case "sweet_berries" -> Blocks.SWEET_BERRY_BUSH;
            case "glow_berries" -> Blocks.CAVE_VINES;
            case "cocoa_beans" -> Blocks.COCOA;
            case "clay_ball" -> Blocks.CLAY;
            case "snowball" -> Blocks.SNOW_BLOCK;
            case "flint" -> Blocks.GRAVEL;
            case "glowstone_dust" -> Blocks.GLOWSTONE;
            case "chorus_fruit" -> Blocks.CHORUS_PLANT;
            case "amethyst_shard" -> Blocks.AMETHYST_CLUSTER;
            default -> null;
        };
        if (plant != null) out.add(plant);
        if (p.equals("stone")) out.add(Blocks.STONE);
        if (p.equals("cobblestone")) out.add(Blocks.STONE);
        return out;
    }

    /** The blocks of a gather order as the ids the work job takes. */
    public static String[] ids(Collection<Block> blocks) {
        List<String> out = new ArrayList<>();
        for (Block b : blocks) out.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        return out.toArray(new String[0]);
    }
}
