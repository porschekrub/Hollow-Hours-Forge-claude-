// Static game data used by the helper (Bedrock identifiers).
export const ORE_GROUPS = [
    { id: "coal", th: "ถ่านหิน" },
    { id: "copper", th: "ทองแดง" },
    { id: "iron", th: "เหล็ก" },
    { id: "gold", th: "ทอง" },
    { id: "redstone", th: "เรดสโตน" },
    { id: "lapis", th: "ลาพิส" },
    { id: "diamond", th: "เพชร" },
    { id: "emerald", th: "มรกต" },
    { id: "nether", th: "ควอตซ์ / ทองเนเธอร์" },
    { id: "debris", th: "เศษโบราณ (Ancient Debris)" },
];
function ore(drop, min, max, fortune, xp, tier, hardness, silk, group) {
    return { drop, min, max, fortune, xp, tier, hardness, silk, group };
}
const M = "minecraft:";
export const ORES = {
    [M + "coal_ore"]: ore(M + "coal", 1, 1, "ore", [0, 2], 0, 3, M + "coal_ore", "coal"),
    [M + "deepslate_coal_ore"]: ore(M + "coal", 1, 1, "ore", [0, 2], 0, 4.5, M + "deepslate_coal_ore", "coal"),
    [M + "copper_ore"]: ore(M + "raw_copper", 2, 5, "ore", [0, 0], 1, 3, M + "copper_ore", "copper"),
    [M + "deepslate_copper_ore"]: ore(M + "raw_copper", 2, 5, "ore", [0, 0], 1, 4.5, M + "deepslate_copper_ore", "copper"),
    [M + "iron_ore"]: ore(M + "raw_iron", 1, 1, "ore", [0, 0], 1, 3, M + "iron_ore", "iron"),
    [M + "deepslate_iron_ore"]: ore(M + "raw_iron", 1, 1, "ore", [0, 0], 1, 4.5, M + "deepslate_iron_ore", "iron"),
    [M + "gold_ore"]: ore(M + "raw_gold", 1, 1, "ore", [0, 0], 2, 3, M + "gold_ore", "gold"),
    [M + "deepslate_gold_ore"]: ore(M + "raw_gold", 1, 1, "ore", [0, 0], 2, 4.5, M + "deepslate_gold_ore", "gold"),
    [M + "redstone_ore"]: ore(M + "redstone", 4, 5, "uniform", [1, 5], 2, 3, M + "redstone_ore", "redstone"),
    [M + "lit_redstone_ore"]: ore(M + "redstone", 4, 5, "uniform", [1, 5], 2, 3, M + "redstone_ore", "redstone"),
    [M + "deepslate_redstone_ore"]: ore(M + "redstone", 4, 5, "uniform", [1, 5], 2, 4.5, M + "deepslate_redstone_ore", "redstone"),
    [M + "lit_deepslate_redstone_ore"]: ore(M + "redstone", 4, 5, "uniform", [1, 5], 2, 4.5, M + "deepslate_redstone_ore", "redstone"),
    [M + "lapis_ore"]: ore(M + "lapis_lazuli", 4, 9, "ore", [2, 5], 1, 3, M + "lapis_ore", "lapis"),
    [M + "deepslate_lapis_ore"]: ore(M + "lapis_lazuli", 4, 9, "ore", [2, 5], 1, 4.5, M + "deepslate_lapis_ore", "lapis"),
    [M + "diamond_ore"]: ore(M + "diamond", 1, 1, "ore", [3, 7], 2, 3, M + "diamond_ore", "diamond"),
    [M + "deepslate_diamond_ore"]: ore(M + "diamond", 1, 1, "ore", [3, 7], 2, 4.5, M + "deepslate_diamond_ore", "diamond"),
    [M + "emerald_ore"]: ore(M + "emerald", 1, 1, "ore", [3, 7], 2, 3, M + "emerald_ore", "emerald"),
    [M + "deepslate_emerald_ore"]: ore(M + "emerald", 1, 1, "ore", [3, 7], 2, 4.5, M + "deepslate_emerald_ore", "emerald"),
    [M + "nether_gold_ore"]: ore(M + "gold_nugget", 2, 6, "ore", [0, 1], 0, 3, M + "nether_gold_ore", "nether"),
    [M + "quartz_ore"]: ore(M + "quartz", 1, 1, "ore", [2, 5], 0, 3, M + "quartz_ore", "nether"),
    [M + "ancient_debris"]: ore(M + "ancient_debris", 1, 1, "none", [0, 0], 3, 30, M + "ancient_debris", "debris"),
};
// Natural stone-like blocks the helper may dig through to reach ores (pickaxe blocks).
export const DIG_STONE = {
    [M + "stone"]: 1.5, [M + "granite"]: 1.5, [M + "diorite"]: 1.5, [M + "andesite"]: 1.5, [M + "tuff"]: 1.5,
    [M + "deepslate"]: 3, [M + "calcite"]: 0.75, [M + "dripstone_block"]: 1.5, [M + "netherrack"]: 0.4,
    [M + "blackstone"]: 1.5, [M + "basalt"]: 1.25, [M + "smooth_basalt"]: 1.25, [M + "end_stone"]: 3,
    [M + "sandstone"]: 0.8, [M + "red_sandstone"]: 0.8, [M + "magma"]: 0.5, [M + "amethyst_block"]: 1.5,
};
// Soft natural blocks (shovel blocks, no tool needed to drop).
export const DIG_SOFT = {
    [M + "dirt"]: 0.5, [M + "coarse_dirt"]: 0.5, [M + "rooted_dirt"]: 0.5, [M + "grass_block"]: 0.6,
    [M + "podzol"]: 0.5, [M + "mycelium"]: 0.6, [M + "gravel"]: 0.6, [M + "sand"]: 0.5, [M + "red_sand"]: 0.5,
    [M + "clay"]: 0.6, [M + "soul_sand"]: 0.5, [M + "soul_soil"]: 0.5, [M + "mud"]: 0.5, [M + "moss_block"]: 0.1,
    [M + "snow"]: 0.2, [M + "suspicious_gravel"]: 0.25, [M + "suspicious_sand"]: 0.25,
};
export const LOGS = new Set([
    M + "oak_log", M + "spruce_log", M + "birch_log", M + "jungle_log", M + "acacia_log", M + "dark_oak_log",
    M + "mangrove_log", M + "cherry_log", M + "pale_oak_log", M + "crimson_stem", M + "warped_stem",
]);
export const LEAF_LIKE = new Set([
    M + "oak_leaves", M + "spruce_leaves", M + "birch_leaves", M + "jungle_leaves", M + "acacia_leaves",
    M + "dark_oak_leaves", M + "mangrove_leaves", M + "cherry_leaves", M + "pale_oak_leaves", M + "azalea_leaves",
    M + "azalea_leaves_flowered", M + "nether_wart_block", M + "warped_wart_block", M + "shroomlight",
]);
export const SAPLING_FOR_LOG = {
    [M + "oak_log"]: M + "oak_sapling", [M + "spruce_log"]: M + "spruce_sapling", [M + "birch_log"]: M + "birch_sapling",
    [M + "jungle_log"]: M + "jungle_sapling", [M + "acacia_log"]: M + "acacia_sapling",
    [M + "dark_oak_log"]: M + "dark_oak_sapling", [M + "mangrove_log"]: M + "mangrove_propagule",
    [M + "cherry_log"]: M + "cherry_sapling", [M + "pale_oak_log"]: M + "pale_oak_sapling",
};
export const PLANTABLE_SOIL = new Set([
    M + "dirt", M + "grass_block", M + "podzol", M + "coarse_dirt", M + "rooted_dirt", M + "moss_block", M + "mud",
    M + "muddy_mangrove_roots", M + "mycelium", M + "pale_moss_block",
]);
// Items the helper picks up from the ground while chopping (leaf drops).
export function isTreeLoot(id) {
    return id.endsWith("_sapling") || id === M + "mangrove_propagule" || id === M + "stick" || id === M + "apple" ||
        id === M + "azalea" || id === M + "flowering_azalea";
}
const MATERIALS = {
    wooden: { speed: 2, level: 0 },
    stone: { speed: 4, level: 1 },
    copper: { speed: 5, level: 1 },
    iron: { speed: 6, level: 2 },
    golden: { speed: 12, level: 0 },
    diamond: { speed: 8, level: 3 },
    netherite: { speed: 9, level: 4 },
};
export function toolInfoFromId(typeId, tags) {
    const id = typeId.replace(/^[^:]*:/, "");
    let kind = "other";
    if (tags.includes("minecraft:is_pickaxe") || id.endsWith("_pickaxe"))
        kind = "pickaxe";
    else if (tags.includes("minecraft:is_axe") || id.endsWith("_axe"))
        kind = "axe";
    else if (tags.includes("minecraft:is_shovel") || id.endsWith("_shovel"))
        kind = "shovel";
    else if (tags.includes("minecraft:is_hoe") || id.endsWith("_hoe"))
        kind = "hoe";
    else if (tags.includes("minecraft:is_sword") || id.endsWith("_sword"))
        kind = "sword";
    let material = id.split("_")[0];
    let m = MATERIALS[material];
    if (!m) {
        // Try tier tags (custom tools from other add-ons)
        const tierTag = tags.find((t) => t.endsWith("_tier"));
        if (tierTag) {
            material = tierTag.replace("minecraft:", "").replace("_tier", "");
            if (material === "gold")
                material = "golden";
            if (material === "wood")
                material = "wooden";
            m = MATERIALS[material];
        }
    }
    if (!m)
        m = { speed: 4, level: 1 };
    return { kind, speed: m.speed, level: m.level, material };
}
// Block hardness for break time (seconds = hardness * 1.5 / speed).
export function hardnessOf(id) {
    const o = ORES[id];
    if (o)
        return o.hardness;
    if (DIG_STONE[id] !== undefined)
        return DIG_STONE[id];
    if (DIG_SOFT[id] !== undefined)
        return DIG_SOFT[id];
    if (LOGS.has(id))
        return 2;
    if (LEAF_LIKE.has(id))
        return 0.2;
    if (id === M + "obsidian" || id === M + "crying_obsidian")
        return 50;
    if (id.includes("infested"))
        return 0.75;
    if (id.includes("deepslate"))
        return 3.5;
    if (id === M + "cobblestone" || id === M + "mossy_cobblestone")
        return 2;
    return 1.5;
}
// Man-made / other stone blocks met while tunnelling (dungeons, mineshafts, our own cobblestone seals).
const PICK_EXTRA = /(cobble|stone_brick|deepslate|terracotta|polished_|tuff|blackstone|basalt|_bricks|bricks$|prismarine|purpur|quartz_block|concrete$|nylium|_ore$|stone$|infested)/;
function isPickExtra(id) {
    if (DIG_SOFT[id] !== undefined || LOGS.has(id) || id.includes("glowstone"))
        return false;
    return PICK_EXTRA.test(id.replace(/^[^:]*:/, ""));
}
export function toolKindFor(id) {
    if (ORES[id] || DIG_STONE[id] !== undefined || isPickExtra(id))
        return "pickaxe";
    if (DIG_SOFT[id] !== undefined)
        return "shovel";
    if (LOGS.has(id))
        return "axe";
    return "hand";
}
/** Whether the block only drops when mined with a (suitable) pickaxe. */
export function needsPickaxeToDrop(id) {
    if (ORES[id])
        return true;
    if (DIG_STONE[id] !== undefined)
        return true;
    if (isPickExtra(id) && !id.includes("infested"))
        return true;
    return false;
}
export function requiredLevel(id) {
    const o = ORES[id];
    if (o)
        return o.tier;
    if (id === M + "obsidian" || id === M + "crying_obsidian")
        return 3;
    return 0;
}
// Common blocks dug while tunnelling. Without "keep junk", only one stack of each is kept (for bridging).
export const JUNK = new Set([
    M + "cobblestone", M + "cobbled_deepslate", M + "dirt", M + "gravel", M + "granite", M + "diorite",
    M + "andesite", M + "tuff", M + "netherrack", M + "calcite", M + "dripstone_block", M + "blackstone",
    M + "basalt", M + "smooth_basalt", M + "sand", M + "red_sand", M + "sandstone", M + "red_sandstone",
    M + "flint", M + "soul_sand", M + "soul_soil", M + "end_stone", M + "magma", M + "clay_ball", M + "snowball",
]);
// Blocks the helper never breaks.
export const NEVER_BREAK = new Set([
    M + "bedrock", M + "barrier", M + "obsidian", M + "crying_obsidian", M + "end_portal_frame", M + "end_portal",
    M + "portal", M + "mob_spawner", M + "trial_spawner", M + "vault", M + "reinforced_deepslate", M + "command_block",
    M + "chain_command_block", M + "repeating_command_block", M + "structure_block", M + "jigsaw", M + "allow", M + "deny",
    M + "border_block", M + "light_block", M + "budding_amethyst",
]);
