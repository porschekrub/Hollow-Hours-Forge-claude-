package com.helperbuddy.talk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds the item a sentence talks about (Thai or English): "ทำโต๊ะคราฟ", "craft 4 torches", "ขอดาบเพชร", "smelt raw iron".
 * Pure Java: the game plugs in the real item registry through {@link #EXISTS}.
 */
public final class ItemWords {
    private ItemWords() {}


    static final Set<String> BASIC = new java.util.HashSet<>(java.util.List.of("crafting_table", "furnace", "chest", "torch", "stick", "oak_planks", "bread", "iron_pickaxe", "diamond_sword",
            "stone_pickaxe", "wooden_pickaxe", "iron_ingot", "gold_ingot", "glass", "ladder", "oak_door", "white_bed", "red_bed", "bucket", "shears", "bow",
            "arrow", "shield", "iron_axe", "diamond_pickaxe", "cobblestone", "oak_log", "coal", "charcoal", "copper_ingot", "smooth_stone", "stone", "bricks",
            "brick", "barrel", "boat", "oak_boat", "oak_fence", "lantern", "bookshelf", "paper", "book", "cake", "cookie", "golden_apple", "iron_helmet",
            "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots"));

    /** Does "minecraft:xyz" exist? Replaced by the registry lookup in game. */
    public static Predicate<String> EXISTS = id -> BASIC.contains(id.replace("minecraft:", ""));

    static final Map<String, String> TOOLS_TH = new LinkedHashMap<>();
    static final Map<String, String> MATS_TH = new LinkedHashMap<>();
    static final Map<String, String> ITEMS_TH = new LinkedHashMap<>();

    static {
        String[][] tools = {{"ดาบ", "sword"}, {"ขวาน", "axe"}, {"อีเต้อ", "pickaxe"}, {"อีเตอร์", "pickaxe"}, {"จอบขุด", "pickaxe"}, {"ที่ขุดแร่", "pickaxe"},
                {"ที่ขุด", "pickaxe"}, {"พิคแอ็กซ์", "pickaxe"}, {"พลั่ว", "shovel"}, {"เสียม", "shovel"}, {"จอบ", "hoe"}, {"หมวกเกราะ", "helmet"},
                {"หมวก", "helmet"}, {"เสื้อเกราะ", "chestplate"}, {"เกราะอก", "chestplate"}, {"กางเกง", "leggings"}, {"เกราะขา", "leggings"},
                {"รองเท้า", "boots"}, {"บูท", "boots"}, {"ชุดเกราะ", "chestplate"}, {"เกราะ", "chestplate"}};
        for (String[] t : tools) TOOLS_TH.put(t[0], t[1]);
        String[][] mats = {{"ไม้", "wooden"}, {"หิน", "stone"}, {"เหล็ก", "iron"}, {"ทองคำ", "golden"}, {"ทอง", "golden"}, {"เพชร", "diamond"},
                {"นีเธอไรต์", "netherite"}, {"เนเธอไรต์", "netherite"}, {"หนัง", "leather"}, {"โซ่", "chainmail"}};
        for (String[] m : mats) MATS_TH.put(m[0], m[1]);
        String[][] items = {
                {"โต๊ะคราฟต์", "crafting_table"}, {"โต๊ะคราฟ", "crafting_table"}, {"โต๊ะประดิษฐ์", "crafting_table"}, {"โต๊ะทำงาน", "crafting_table"},
                {"เตาหลอม", "blast_furnace"}, {"เตารมควัน", "smoker"}, {"เตาเผา", "furnace"}, {"เตา", "furnace"}, {"หีบ", "chest"}, {"ถังไม้", "barrel"},
                {"คบเพลิงวิญญาณ", "soul_torch"}, {"คบเพลิง", "torch"}, {"คบไฟ", "torch"}, {"ตะเกียง", "lantern"}, {"แท่งไม้", "stick"}, {"ไม้แท่ง", "stick"},
                {"ไม้กระดาน", "oak_planks"}, {"แผ่นไม้", "oak_planks"}, {"ท่อนไม้", "oak_log"}, {"ซุง", "oak_log"},
                {"เพชร", "diamond"}, {"ทองคำแท่ง", "gold_ingot"}, {"ทองแท่ง", "gold_ingot"}, {"ทองดิบ", "raw_gold"}, {"ทองคำ", "gold_ingot"}, {"ทองแดงดิบ", "raw_copper"},
                {"ทองแดง", "copper_ingot"}, {"ทอง", "gold_ingot"}, {"เหล็กแท่ง", "iron_ingot"}, {"เหล็กดิบ", "raw_iron"}, {"เหล็ก", "iron_ingot"},
                {"ถ่านไม้", "charcoal"}, {"ถ่านหิน", "coal"}, {"ถ่าน", "coal"}, {"มรกต", "emerald"}, {"นีเธอไรต์แท่ง", "netherite_ingot"}, {"นีเธอไรต์", "netherite_ingot"},
                {"ลาพิส", "lapis_lazuli"}, {"เรดสโตน", "redstone"}, {"ควอตซ์", "quartz"},
                {"ก้อนหิน", "cobblestone"}, {"หินเรียบ", "smooth_stone"}, {"หินเหล็กไฟ", "flint"}, {"อิฐหิน", "stone_bricks"}, {"อิฐ", "bricks"}, {"ก้อนอิฐ", "brick"},
                {"หิน", "cobblestone"}, {"เตียง", "red_bed"}, {"ถังน้ำ", "water_bucket"}, {"ถังลาวา", "lava_bucket"}, {"ถังนม", "milk_bucket"}, {"ถังเหล็ก", "bucket"},
                {"ถัง", "bucket"}, {"ลูกธนู", "arrow"}, {"ลูกศร", "arrow"}, {"คันธนู", "bow"}, {"ธนู", "bow"}, {"หน้าไม้", "crossbow"}, {"เบ็ดตกปลา", "fishing_rod"},
                {"เบ็ด", "fishing_rod"}, {"โล่", "shield"}, {"กรรไกร", "shears"}, {"เหล็กไฟ", "flint_and_steel"}, {"ที่จุดไฟ", "flint_and_steel"},
                {"ขนมปัง", "bread"}, {"แอปเปิลทอง", "golden_apple"}, {"แอปเปิล", "apple"}, {"แอปเปิ้ล", "apple"}, {"สเต็ก", "cooked_beef"}, {"สเต๊ก", "cooked_beef"},
                {"เนื้อวัวดิบ", "beef"}, {"เนื้อหมูดิบ", "porkchop"}, {"เนื้อไก่ดิบ", "chicken"}, {"เนื้อหมู", "cooked_porkchop"}, {"เนื้อไก่", "cooked_chicken"},
                {"เนื้อแกะ", "cooked_mutton"}, {"เนื้อ", "cooked_beef"}, {"ไข่", "egg"}, {"เค้ก", "cake"}, {"คุกกี้", "cookie"}, {"พายฟักทอง", "pumpkin_pie"},
                {"สตูว์เห็ด", "mushroom_stew"}, {"ซุปบีทรูท", "beetroot_soup"}, {"มันอบ", "baked_potato"}, {"มันฝรั่งอบ", "baked_potato"},
                {"เอนเดอร์เพิร์ล", "ender_pearl"}, {"ตาเอนเดอร์", "ender_eye"}, {"ทีเอ็นที", "tnt"}, {"ระเบิด", "tnt"}, {"ผ้าขนสัตว์", "white_wool"},
                {"ขนแกะ", "white_wool"}, {"ขนสัตว์", "white_wool"}, {"ด้าย", "string"}, {"เชือกจูง", "lead"}, {"เชือก", "string"}, {"ผงกระดูก", "bone_meal"},
                {"กระดูก", "bone"}, {"ดินปืน", "gunpowder"}, {"ตรีศูล", "trident"}, {"โทเท็ม", "totem_of_undying"}, {"หนังสือ", "book"}, {"กระดาษ", "paper"},
                {"ชั้นหนังสือ", "bookshelf"}, {"ชั้นวางหนังสือ", "bookshelf"}, {"โต๊ะร่ายมนตร์", "enchanting_table"}, {"ทั่ง", "anvil"},
                {"กระจกบาง", "glass_pane"}, {"กระจก", "glass"}, {"แครอททอง", "golden_carrot"}, {"แครอท", "carrot"}, {"มันฝรั่ง", "potato"},
                {"ปลาแซลมอน", "cooked_salmon"}, {"ปลา", "cooked_cod"}, {"น้ำผึ้ง", "honey_bottle"}, {"ขวดแก้ว", "glass_bottle"}, {"ขวด", "glass_bottle"},
                {"ออบซิเดียน", "obsidian"}, {"โอบซิเดียน", "obsidian"}, {"ประตูเหล็ก", "iron_door"}, {"ประตูกล", "oak_trapdoor"}, {"ประตู", "oak_door"},
                {"บันไดลิง", "ladder"}, {"บันไดไม้", "oak_stairs"}, {"บันได", "ladder"}, {"รั้ว", "oak_fence"}, {"ประตูรั้ว", "oak_fence_gate"},
                {"เรือ", "oak_boat"}, {"รถราง", "minecart"}, {"ราง", "rail"}, {"ป้าย", "oak_sign"}, {"ป้ายชื่อ", "name_tag"}, {"กรอบรูป", "item_frame"},
                {"ภาพวาด", "painting"}, {"เข็มทิศ", "compass"}, {"นาฬิกา", "clock"}, {"แผนที่", "map"}, {"อานม้า", "saddle"}, {"ขวดประสบการณ์", "experience_bottle"},
                {"บล็อกเพชร", "diamond_block"}, {"บล็อกเหล็ก", "iron_block"}, {"บล็อกทอง", "gold_block"}, {"บล็อกมรกต", "emerald_block"},
                {"บล็อกถ่านหิน", "coal_block"}, {"บล็อกเรดสโตน", "redstone_block"}, {"ลูกสูบเหนียว", "sticky_piston"}, {"ลูกสูบ", "piston"},
                {"คันโยก", "lever"}, {"ปุ่ม", "stone_button"}, {"แผ่นกดเหล็ก", "heavy_weighted_pressure_plate"}, {"แผ่นกด", "stone_pressure_plate"},
                {"กล่องดนตรี", "jukebox"}, {"กล่องโน้ต", "note_block"}, {"หมวกฟักทอง", "carved_pumpkin"}, {"ตะเกียงฟักทอง", "jack_o_lantern"},
                {"ฟักทอง", "pumpkin"}, {"แตงโม", "melon_slice"}, {"น้ำตาล", "sugar"}, {"ข้าวสาลี", "wheat"}, {"เมล็ด", "wheat_seeds"}, {"หญ้าแห้ง", "hay_block"},
                {"ทราย", "sand"}, {"ดินเหนียว", "clay_ball"}, {"ดิน", "dirt"}, {"กรวด", "gravel"}, {"หนัง", "leather"}, {"ขนนก", "feather"},
                {"กระดิ่ง", "bell"}, {"เหล็กกล้า", "iron_ingot"}, {"โซ่", "chain"}, {"ลูกกรงเหล็ก", "iron_bars"}, {"ลูกกรง", "iron_bars"}, {"คอมพาราเตอร์", "comparator"},
                {"รีพีทเตอร์", "repeater"}, {"ถังทิ้ง", "composter"}, {"ปุ๋ย", "composter"}, {"หินลับมีด", "grindstone"}, {"โต๊ะตีเหล็ก", "smithing_table"},
                {"เครื่องตัดหิน", "stonecutter"}, {"กี่ทอผ้า", "loom"}, {"โต๊ะทำแผนที่", "cartography_table"}, {"โต๊ะทำธนู", "fletching_table"},
                {"แท่นต้มยา", "brewing_stand"}, {"หม้อ", "cauldron"}, {"บีคอน", "beacon"}, {"กล่องชัลเกอร์", "shulker_box"}, {"เอนเดอร์เชสต์", "ender_chest"},
                {"หีบเอนเดอร์", "ender_chest"}, {"ดอกไม้ไฟ", "firework_rocket"}, {"พลุ", "firework_rocket"}, {"หิมะ", "snowball"}, {"ก้อนหิมะ", "snowball"},
                {"ไม้ไผ่", "bamboo"}, {"อ้อย", "sugar_cane"}, {"กระบองเพชร", "cactus"}, {"แป้ง", "wheat"}, {"ผงเรดสโตน", "redstone"}, {"กระดาน", "oak_planks"},
        };
        for (String[] i : items) ITEMS_TH.put(i[0], i[1]);
    }

    static final Set<String> EN_FILL = new java.util.HashSet<>(java.util.List.of("give", "me", "i", "want", "need", "please", "pls", "plz", "some", "a", "an", "the", "of", "piece", "pieces", "pcs",
            "pc", "x", "can", "could", "have", "get", "would", "like", "id", "to", "you", "my", "and", "craft", "crafting", "make", "smelt", "melt", "cook", "bake",
            "bring", "us", "for", "few", "couple", "stack", "stacks", "more", "new", "your", "from", "chest", "out", "take", "fetch", "it", "them", "all", "into",
            "build", "create", "set", "sets", "full", "set", "of", "now", "too", "also", "just", "with", "using", "use", "go", "hand", "over", "dozen", "several",
            "bag", "inventory", "some", "any", "please", "buddy", "helper", "hey", "that", "this", "these", "those", "one", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "twenty", "thirty", "sixty"));

    static final Map<String, String> EN_ALIAS = new LinkedHashMap<>();

    static {
        String[][] a = {{"table", "crafting_table"}, {"crafting_table", "crafting_table"}, {"workbench", "crafting_table"}, {"work_bench", "crafting_table"},
                {"planks", "oak_planks"}, {"plank", "oak_planks"}, {"wooden_planks", "oak_planks"}, {"wood_planks", "oak_planks"}, {"sticks", "stick"},
                {"torches", "torch"}, {"bed", "red_bed"}, {"beds", "red_bed"}, {"door", "oak_door"}, {"doors", "oak_door"}, {"boat", "oak_boat"}, {"fence", "oak_fence"},
                {"gate", "oak_fence_gate"}, {"sign", "oak_sign"}, {"stairs", "oak_stairs"}, {"slab", "oak_slab"}, {"slabs", "oak_slab"}, {"trapdoor", "oak_trapdoor"},
                {"button", "stone_button"}, {"pressure_plate", "stone_pressure_plate"}, {"iron", "iron_ingot"}, {"gold", "gold_ingot"}, {"copper", "copper_ingot"},
                {"netherite", "netherite_ingot"}, {"wood", "oak_planks"}, {"log", "oak_log"}, {"logs", "oak_log"}, {"glass_panes", "glass_pane"},
                {"pickaxe", "iron_pickaxe"}, {"pick", "iron_pickaxe"}, {"axe", "iron_axe"}, {"shovel", "iron_shovel"}, {"hoe", "iron_hoe"}, {"sword", "iron_sword"},
                {"helmet", "iron_helmet"}, {"chestplate", "iron_chestplate"}, {"leggings", "iron_leggings"}, {"boots", "iron_boots"}, {"armor", "iron_chestplate"},
                {"steak", "cooked_beef"}, {"meat", "cooked_beef"}, {"pork", "cooked_porkchop"}, {"fish", "cooked_cod"}, {"wool", "white_wool"},
                {"lapis", "lapis_lazuli"}, {"quartz", "quartz"}, {"brick", "brick"}, {"bricks", "bricks"}, {"stone_bricks", "stone_bricks"},
                {"furnaces", "furnace"}, {"chests", "chest"}, {"cobble", "cobblestone"}, {"stone", "cobblestone"}, {"stones", "cobblestone"},
                {"smooth_stone", "smooth_stone"}, {"glass", "glass"}, {"lantern", "lantern"}, {"lanterns", "lantern"}, {"ladders", "ladder"},
                {"bow", "bow"}, {"arrows", "arrow"}, {"shield", "shield"}, {"bucket", "bucket"}, {"buckets", "bucket"}, {"shears", "shears"},
                {"flint_and_steel", "flint_and_steel"}, {"lighter", "flint_and_steel"}, {"bread", "bread"}, {"cake", "cake"}, {"cookies", "cookie"},
                {"golden_apple", "golden_apple"}, {"enchanting_table", "enchanting_table"}, {"anvil", "anvil"}, {"bookshelf", "bookshelf"},
                {"bookshelves", "bookshelf"}, {"paper", "paper"}, {"book", "book"}, {"books", "book"}, {"compass", "compass"}, {"clock", "clock"},
                {"map", "map"}, {"minecart", "minecart"}, {"rails", "rail"}, {"rail", "rail"}, {"piston", "piston"}, {"barrel", "barrel"},
                {"charcoal", "charcoal"}, {"coal", "coal"}, {"diamond", "diamond"}, {"diamonds", "diamond"}, {"emerald", "emerald"}, {"emeralds", "emerald"},
                {"redstone", "redstone"}, {"string", "string"}, {"leather", "leather"}, {"bone_meal", "bone_meal"}, {"sugar", "sugar"}, {"hay", "hay_block"},
                {"tnt", "tnt"}, {"chain", "chain"}, {"iron_bars", "iron_bars"}, {"bars", "iron_bars"}, {"jukebox", "jukebox"}, {"note_block", "note_block"},
                {"composter", "composter"}, {"smoker", "smoker"}, {"blast_furnace", "blast_furnace"}, {"stonecutter", "stonecutter"}, {"loom", "loom"},
                {"grindstone", "grindstone"}, {"smithing_table", "smithing_table"}, {"cartography_table", "cartography_table"}, {"fletching_table", "fletching_table"},
                {"cauldron", "cauldron"}, {"brewing_stand", "brewing_stand"}, {"bottle", "glass_bottle"}, {"bottles", "glass_bottle"}, {"lead", "lead"},
                {"name_tag", "name_tag"}, {"saddle", "saddle"}, {"fishing_rod", "fishing_rod"}, {"crossbow", "crossbow"}, {"campfire", "campfire"},
                {"raw_iron", "raw_iron"}, {"raw_gold", "raw_gold"}, {"raw_copper", "raw_copper"}};
        for (String[] e : a) EN_ALIAS.put(e[0], e[1]);
    }

    static String thaiLookup(String t) {
        String bestTool = null;
        int ti = -1;
        for (String k : TOOLS_TH.keySet()) {
            int i = t.indexOf(k);
            if (i >= 0 && (bestTool == null || k.length() > bestTool.length())) {
                bestTool = k;
                ti = i;
            }
        }
        // "จอบ" inside "จอบขุด"? handled by length; "ดาบ" etc. A tool word wins unless a longer item word covers it (e.g. "ชั้นหนังสือ")
        String bestItem = null;
        for (String k : ITEMS_TH.keySet()) if (t.contains(k) && (bestItem == null || k.length() > bestItem.length())) bestItem = k;
        if (bestTool != null && (bestItem == null || bestItem.length() <= bestTool.length() || !bestItem.contains(bestTool))) {
            String rest = t.substring(ti + bestTool.length());
            String mat = null;
            for (String k : MATS_TH.keySet()) if (rest.contains(k) && (mat == null || k.length() > mat.length())) mat = k;
            String part = TOOLS_TH.get(bestTool);
            String material = mat != null ? MATS_TH.get(mat) : "iron";
            if (material.equals("leather") && !part.matches("helmet|chestplate|leggings|boots")) material = "iron";
            if (material.equals("wooden") && part.matches("helmet|chestplate|leggings|boots")) material = "leather";
            if (material.equals("stone") && part.matches("helmet|chestplate|leggings|boots")) material = "chainmail";
            return material + "_" + part;
        }
        return bestItem == null ? null : ITEMS_TH.get(bestItem);
    }

    /** Item id ("minecraft:torch") talked about in the sentence, or null. */
    public static String resolve(String raw) {
        String t = raw.trim().toLowerCase().replace("minecraft:", "");
        if (t.isEmpty()) return null;
        List<String> cands = new ArrayList<>();
        String th = thaiLookup(t);
        if (th != null) cands.add(th);
        // english words (wood type words first: "spruce planks" -> spruce_planks)
        StringBuilder en = new StringBuilder();
        for (String w : t.split("[^a-z_]+")) {
            if (w.isEmpty() || EN_FILL.contains(w)) continue;
            if (en.length() > 0) en.append('_');
            en.append(w);
        }
        String e = en.toString();
        if (!e.isEmpty()) {
            String fixed = e.replaceFirst("^gold_(?=sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)", "golden_")
                    .replaceFirst("^wood_(?=sword|pickaxe|axe|shovel|hoe)", "wooden_").replace("pick_axe", "pickaxe").replace("crafting_bench", "crafting_table");
            cands.add(fixed);
            if (fixed.endsWith("s") && fixed.length() > 3) cands.add(fixed.substring(0, fixed.length() - 1));
            if (fixed.endsWith("es") && fixed.length() > 4) cands.add(fixed.substring(0, fixed.length() - 2));
            String alias = EN_ALIAS.get(fixed);
            if (alias != null) cands.add(alias);
            // last word(s) ("a nice big crafting table") -> try suffixes
            String[] parts = fixed.split("_");
            for (int i = 1; i < parts.length; i++) {
                String suf = String.join("_", java.util.Arrays.copyOfRange(parts, i, parts.length));
                cands.add(suf);
                if (EN_ALIAS.containsKey(suf)) cands.add(EN_ALIAS.get(suf));
                if (suf.endsWith("s")) cands.add(suf.substring(0, suf.length() - 1));
            }
            // a tool with a material word anywhere: "diamond pickaxe please"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(wooden|wood|stone|iron|golden|gold|diamond|netherite)_?\\w*?_(sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)").matcher(fixed);
            if (m.find()) cands.add(0, m.group(1).replace("gold", "golden").replace("goldenen", "golden").replace("wood", "wooden").replace("woodenen", "wooden") + "_" + m.group(2));
        }
        for (String c : cands) {
            if (c.isEmpty()) continue;
            String id = "minecraft:" + c;
            if (EXISTS.test(id)) return id;
        }
        return null;
    }
}
