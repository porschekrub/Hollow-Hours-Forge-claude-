import { world } from "@minecraft/server";
export const HELPER_ID = "helper:helper";
export const CONTRACT_ITEM = "helper:contract";
export const DIR_VEC = {
    north: { x: 0, y: 0, z: -1 },
    south: { x: 0, y: 0, z: 1 },
    east: { x: 1, y: 0, z: 0 },
    west: { x: -1, y: 0, z: 0 },
};
export function opposite(d) {
    return d === "north" ? "south" : d === "south" ? "north" : d === "east" ? "west" : "east";
}
/** Clockwise (seen from above) of a cardinal direction: the "right hand" side when facing it. */
export function rightOf(d) {
    return d === "north" ? "east" : d === "east" ? "south" : d === "south" ? "west" : "north";
}
export function leftOf(d) {
    return opposite(rightOf(d));
}
export function clamp(v, lo, hi) {
    return v < lo ? lo : v > hi ? hi : v;
}
export function v(x, y, z) {
    return { x, y, z };
}
export function add(a, b) {
    return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
}
export function sub(a, b) {
    return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}
export function scale(a, s) {
    return { x: a.x * s, y: a.y * s, z: a.z * s };
}
export function len(a) {
    return Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
}
export function dist(a, b) {
    return len(sub(a, b));
}
export function distXZ(a, b) {
    const dx = a.x - b.x;
    const dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}
export function floorV(a) {
    return { x: Math.floor(a.x), y: Math.floor(a.y), z: Math.floor(a.z) };
}
export function center(p) {
    return { x: p.x + 0.5, y: p.y + 0.5, z: p.z + 0.5 };
}
export function key(p) {
    return `${p.x},${p.y},${p.z}`;
}
export function normalize(a) {
    const l = len(a);
    return l < 1e-6 ? { x: 0, y: 0, z: 0 } : scale(a, 1 / l);
}
/** Yaw (Minecraft convention: 0 = +Z/south, 90 = -X/west) to look from `from` toward `to`. */
export function yawTo(from, to) {
    const dx = to.x - from.x;
    const dz = to.z - from.z;
    return (Math.atan2(-dx, dz) * 180) / Math.PI;
}
export function pitchTo(from, to) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;
    const h = Math.sqrt(dx * dx + dz * dz);
    return (-Math.atan2(dy, h) * 180) / Math.PI;
}
/** Cardinal direction a player is looking toward (horizontal). */
export function facingOf(e) {
    const yaw = e.getRotation().y;
    const a = ((yaw % 360) + 360) % 360; // 0 south, 90 west, 180 north, 270 east
    if (a >= 45 && a < 135)
        return "west";
    if (a >= 135 && a < 225)
        return "north";
    if (a >= 225 && a < 315)
        return "east";
    return "south";
}
export function randInt(min, max) {
    return min + Math.floor(Math.random() * (max - min + 1));
}
export function safeBlock(dim, p) {
    try {
        return dim.getBlock(p);
    }
    catch {
        return undefined;
    }
}
/** Blocks a mob can stand/walk through. */
const PASSABLE_PARTS = [
    "short_grass", "tall_grass", "fern", "dead_bush", "seagrass", "flower", "tulip", "orchid", "allium",
    "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "poppy", "dandelion", "sunflower",
    "lilac", "rose_bush", "peony", "torch", "sapling", "propagule", "_sign", "button", "pressure_plate",
    "rail", "carpet", "snow_layer", "vine", "redstone_wire", "lever", "tripwire", "wheat", "carrots",
    "potatoes", "beetroot", "sweet_berry_bush", "glow_lichen", "sculk_vein", "pink_petals", "hanging_roots",
    "spore_blossom", "nether_sprouts", "crimson_roots", "warped_roots", "twisting_vines", "weeping_vines",
    "fire", "light_block", "structure_void", "bush", "leaf_litter", "wildflowers", "dry_grass", "short_dry_grass",
    "tall_dry_grass", "firefly_bush", "kelp", "melon_stem", "pumpkin_stem", "torchflower", "pitcher", "closed_eyeblossom",
    "open_eyeblossom", "web", "cave_vines", "wither_rose", "mushroom", "fungus", "large_fern", "cactus_flower",
];
export function isPassableId(id) {
    if (id === "minecraft:air" || id === "minecraft:cave_air" || id === "minecraft:void_air")
        return true;
    if (id.endsWith("mushroom_block") || id.endsWith("_fungus_block"))
        return false;
    for (const p of PASSABLE_PARTS)
        if (id.includes(p))
            return true;
    return false;
}
export function isPassable(b) {
    if (!b)
        return false;
    if (b.isAir)
        return true;
    if (b.isLiquid)
        return b.typeId.includes("water");
    return isPassableId(b.typeId);
}
export function isLava(b) {
    return !!b && b.typeId.includes("lava");
}
export function isReplaceable(b) {
    if (!b)
        return false;
    if (b.isAir || b.isLiquid)
        return true;
    return isPassableId(b.typeId);
}
// ---------- messaging ----------
export function tellOwner(player, name, msg) {
    if (!player)
        return;
    try {
        player.sendMessage(`§6[§e${name}§6]§r ${msg}`);
    }
    catch {
        /* player left */
    }
}
export function findPlayerById(id, nameHint) {
    if (!id && !nameHint)
        return undefined;
    const all = world.getAllPlayers();
    for (const p of all)
        if (p.id === id)
            return p;
    if (nameHint)
        for (const p of all)
            if (p.name === nameHint)
                return p;
    return undefined;
}
// ---------- names ----------
const TH_NAMES = {
    coal: "ถ่านหิน", raw_iron: "เหล็กดิบ", raw_gold: "ทองดิบ", raw_copper: "ทองแดงดิบ", diamond: "เพชร",
    emerald: "มรกต", lapis_lazuli: "ลาพิส", redstone: "เรดสโตน", quartz: "ควอตซ์", gold_nugget: "เศษทอง",
    ancient_debris: "เศษโบราณ", cobblestone: "หินก้อน", cobbled_deepslate: "ดีพสเลทก้อน", dirt: "ดิน",
    stone: "หิน", deepslate: "ดีพสเลท", gravel: "กรวด", sand: "ทราย", flint: "หินเหล็กไฟ", stick: "ไม้แท่ง",
    apple: "แอปเปิล", glass: "กระจก", glass_pane: "กระจกบาน", lantern: "ตะเกียง", torch: "คบเพลิง",
    ladder: "บันไดลิง", bed: "เตียง", chest: "หีบ", barrel: "ถัง", furnace: "เตาเผา", crafting_table: "โต๊ะคราฟต์",
    bookshelf: "ชั้นหนังสือ", campfire: "กองไฟ", red_carpet: "พรมแดง", white_carpet: "พรมขาว",
    stone_bricks: "อิฐหิน", brick_block: "อิฐแดง", deepslate_tiles: "กระเบื้องดีพสเลท", smooth_stone: "หินเรียบ",
    smooth_stone_slab: "แผ่นหินเรียบ", white_concrete: "คอนกรีตขาว", gray_concrete: "คอนกรีตเทา",
    stone_stairs: "บันไดหินก้อน", stone_brick_stairs: "บันไดอิฐหิน", brick_stairs: "บันไดอิฐ",
    deepslate_tile_stairs: "บันไดกระเบื้องดีพสเลท", stone_brick_slab: "แผ่นอิฐหิน", brick_slab: "แผ่นอิฐ",
    deepslate_tile_slab: "แผ่นกระเบื้องดีพสเลท", cobblestone_slab: "แผ่นหินก้อน", wooden_door: "ประตูไม้โอ๊ค",
    trapdoor: "ประตูกลไม้โอ๊ค", wooden_pressure_plate: "แผ่นกดไม้โอ๊ค", fence_gate: "ประตูรั้วไม้โอ๊ค",
    light_gray_carpet: "พรมเทาอ่อน", yellow_carpet: "พรมเหลือง", mangrove_propagule: "ฝักโกงกาง", bone_meal: "ผงกระดูก",
    name_tag: "ป้ายชื่อ", red_sand: "ทรายแดง", clay_ball: "ก้อนดินเหนียว",
    snowball: "ก้อนหิมะ", "helper:contract": "สัญญาจ้างผู้ช่วย",
    smooth_quartz: "ควอตซ์เรียบ", smooth_quartz_slab: "แผ่นควอตซ์เรียบ", light_gray_stained_glass: "กระจกสีเทาอ่อน",
    sea_lantern: "ตะเกียงทะเล", azalea_leaves: "พุ่มอะซาเลีย", azalea_leaves_flowered: "พุ่มอะซาเลียออกดอก",
    white_terracotta: "ดินเผาสีขาว",
    blast_furnace: "เตาถลุงแร่", smoker: "เตารมควัน", anvil: "ทั่ง", grindstone: "หินลับ", stonecutter_block: "เครื่องตัดหิน",
    loom: "กี่ทอผ้า", lectern: "แท่นวางหนังสือ", composter: "ถังหมักปุ๋ย", cauldron: "หม้อต้ม", brewing_stand: "แท่นปรุงยา",
    enchanting_table: "โต๊ะร่ายมนตร์", smithing_table: "โต๊ะช่างตีเหล็ก", cartography_table: "โต๊ะทำแผนที่",
    fletching_table: "โต๊ะทำธนู", jukebox: "เครื่องเล่นแผ่นเสียง", black_concrete: "คอนกรีตดำ", poppy: "ดอกป๊อปปี้",
    red_tulip: "ทิวลิปแดง", grass_block: "บล็อกหญ้า",
};
const ORE_TH = {
    coal: "ถ่านหิน", iron: "เหล็ก", gold: "ทอง", diamond: "เพชร", emerald: "มรกต", lapis: "ลาพิส", redstone: "เรดสโตน",
    copper: "ทองแดง", quartz: "ควอตซ์", nether_gold: "ทองเนเธอร์",
};
const WOOD_TH = {
    oak: "โอ๊ค", spruce: "สปรูซ", birch: "เบิร์ช", jungle: "ป่า", acacia: "อะเคเชีย", dark_oak: "โอ๊คเข้ม",
    mangrove: "โกงกาง", cherry: "เชอร์รี่", pale_oak: "โอ๊คซีด",
};
const PART_TH = {
    planks: "ไม้กระดาน", log: "ซุงไม้", stairs: "บันไดไม้", slab: "แผ่นไม้", fence: "รั้วไม้", door: "ประตูไม้",
    trapdoor: "ประตูกล", sapling: "ต้นกล้า", pressure_plate: "แผ่นกด",
};
const MAT_EN = {
    wooden: "ไม้", stone: "หิน", copper: "ทองแดง", iron: "เหล็ก", golden: "ทอง", diamond: "เพชร", netherite: "เนเธอไรต์",
};
const TOOL_TH = { pickaxe: "อีเต้อ", axe: "ขวาน", shovel: "พลั่ว", hoe: "จอบ", sword: "ดาบ" };
export function itemName(typeId) {
    if (TH_NAMES[typeId])
        return TH_NAMES[typeId];
    const id = typeId.replace("minecraft:", "");
    if (TH_NAMES[id])
        return TH_NAMES[id];
    const ore = /^(lit_)?(deepslate_)?(\w+?)_ore$/.exec(id);
    if (ore && ORE_TH[ore[3]])
        return `แร่${ORE_TH[ore[3]]}${ore[2] ? " (ดีพสเลท)" : ""}`;
    const tool = /^(wooden|stone|copper|iron|golden|diamond|netherite)_(pickaxe|axe|shovel|hoe|sword)$/.exec(id);
    if (tool)
        return `${TOOL_TH[tool[2]]}${MAT_EN[tool[1]]}`;
    const wood = /^(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak)_(planks|log|stairs|slab|fence|door|trapdoor|sapling|pressure_plate)$/.exec(id);
    if (wood)
        return `${PART_TH[wood[2]]}${WOOD_TH[wood[1]]}`;
    // generic prettify
    return id
        .split("_")
        .map((w) => (w.length ? w[0].toUpperCase() + w.slice(1) : w))
        .join(" ");
}
export function roman(n) {
    return ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"][n] ?? String(n);
}
