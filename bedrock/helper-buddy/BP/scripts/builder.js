import { BlockPermutation, ItemStack, system } from "@minecraft/server";
import { addXp } from "./combat.js";
import { DIG_SOFT, DIG_STONE, LEAF_LIKE, LOGS, ORES } from "./data.js";
import { billOfMaterials, generateHouse, isExtraType } from "./house_gen.js";
import { face, isStuck, resetProgress, steer, stopMoving } from "./move.js";
import { isNaturalTreeAt } from "./work.js";
import { getName, getSettings, notifyOwner, rt, setAi, setModeProp, setWorking, workMult } from "./state.js";
import { countId, nearbyContainers, playerContainer, pullFromContainers, storageAdd, storageTake, usedSlots } from "./storage.js";
import { DIR_VEC, clamp, distXZ, facingOf, isPassable, isPassableId, itemName, leftOf, opposite, rightOf, safeBlock, tellOwner, } from "./util.js";
const CACHE = new Map();
const SPEEDS = [0.25, 0.5, 1.0];
export const SPEED_NAMES = ["ปกติ (5 บล็อก/วินาที)", "เร็ว (10 บล็อก/วินาที)", "เร็วมาก (20 บล็อก/วินาที)"];
const BUILD_REACH = 7;
const MAX_FILL = 12;
// ------------------------------------------------------------------ persistence
// The live job object stays in memory (mutated every tick) and is written to the entity periodically.
const JOBS = new Map();
export function getJob(h) {
    const s = h.getDynamicProperty("h:build");
    if (typeof s !== "string" || !s.length) {
        JOBS.delete(h.id);
        return undefined;
    }
    const m = JOBS.get(h.id);
    if (m && m.raw === s)
        return m.job;
    try {
        const j = JSON.parse(s);
        if (!j || j.v !== 1)
            return undefined;
        JOBS.set(h.id, { raw: s, job: j });
        return j;
    }
    catch {
        return undefined;
    }
}
export function saveJob(h, j) {
    if (!j) {
        h.setDynamicProperty("h:build", undefined);
        JOBS.delete(h.id);
        CACHE.delete(h.id);
        return;
    }
    const raw = JSON.stringify(j);
    h.setDynamicProperty("h:build", raw);
    JOBS.set(h.id, { raw, job: j });
}
export function getLastOpts(h) {
    const s = h.getDynamicProperty("h:lastHouse");
    if (typeof s !== "string")
        return undefined;
    try {
        return JSON.parse(s);
    }
    catch {
        return undefined;
    }
}
export function saveLastOpts(h, o) {
    h.setDynamicProperty("h:lastHouse", JSON.stringify(o));
}
// ------------------------------------------------------------------ transform
function toWorld(j, x, y, z) {
    const F = DIR_VEC[j.facing];
    const L = DIR_VEC[leftOf(j.facing)];
    return { x: j.ox + x * L.x + z * F.x, y: j.oy + y, z: j.oz + x * L.z + z * F.z };
}
function dirToWorld(j, d) {
    switch (d) {
        case "px": return leftOf(j.facing);
        case "nx": return rightOf(j.facing);
        case "pz": return j.facing;
        case "nz": return opposite(j.facing);
    }
}
function axisToWorld(j, a) {
    if (a === "y")
        return "y";
    const d = a === "x" ? leftOf(j.facing) : j.facing;
    return d === "east" || d === "west" ? "x" : "z";
}
const STAIR_DIR = { east: 0, west: 1, south: 2, north: 3 };
const FACING6 = { north: 2, south: 3, west: 4, east: 5 };
const BED_DIR = { south: 0, west: 1, north: 2, east: 3 };
function permFor(j, b) {
    const st = b.st ?? {};
    const n = b.id.replace("minecraft:", "");
    const states = {};
    if (n.endsWith("_stairs")) {
        states["weirdo_direction"] = STAIR_DIR[dirToWorld(j, st.f ?? "pz")];
        states["upside_down_bit"] = st.half === "top";
    }
    else if (n.endsWith("_slab")) {
        states["minecraft:vertical_half"] = st.half === "top" ? "top" : "bottom";
    }
    else if (n.endsWith("_door")) {
        // Bedrock doors store the placer's facing rotated 90° clockwise.
        states["minecraft:cardinal_direction"] = rightOf(dirToWorld(j, st.f ?? "pz"));
        states["upper_block_bit"] = !!st.upper;
        states["door_hinge_bit"] = !!st.hinge;
        states["open_bit"] = false;
    }
    else if (n === "ladder") {
        states["facing_direction"] = FACING6[dirToWorld(j, st.f ?? "nz")];
    }
    else if (n === "bed") {
        states["direction"] = BED_DIR[dirToWorld(j, st.f ?? "pz")];
        states["head_piece_bit"] = !!st.head;
    }
    else if (["furnace", "chest", "campfire", "smoker", "blast_furnace", "anvil", "stonecutter_block", "lectern"].includes(n)) {
        states["minecraft:cardinal_direction"] = dirToWorld(j, st.f ?? "nz");
    }
    else if (n === "grindstone") {
        states["attachment"] = "standing";
        states["direction"] = BED_DIR[dirToWorld(j, st.f ?? "nz")];
    }
    else if (n === "loom") {
        states["direction"] = BED_DIR[dirToWorld(j, st.f ?? "nz")];
    }
    else if (n === "cauldron") {
        states["cauldron_liquid"] = "water";
        states["fill_level"] = 6;
    }
    else if (n === "water") {
        states["liquid_depth"] = 0;
    }
    else if (n === "portal") {
        states["portal_axis"] = axisToWorld(j, st.axis ?? "x");
    }
    else if (n === "barrel") {
        states["facing_direction"] = 1;
    }
    else if (n === "lantern") {
        states["hanging"] = !!st.hanging;
    }
    else if (n.includes("leaves")) {
        states["persistent_bit"] = true; // decorative hedges must never decay
    }
    else if (n.endsWith("_log") && st.axis) {
        states["pillar_axis"] = axisToWorld(j, st.axis);
    }
    try {
        return BlockPermutation.resolve(b.id, states);
    }
    catch {
        try {
            return BlockPermutation.resolve(b.id);
        }
        catch {
            return undefined;
        }
    }
}
// ------------------------------------------------------------------ plan cache
function lk(x, y, z) {
    return `${x},${y},${z}`;
}
function cacheFor(h, j) {
    let c = CACHE.get(h.id);
    if (c && c.job === j)
        return c;
    const key = JSON.stringify([j.opts, j.ox, j.oy, j.oz, j.facing, j.fill]);
    if (c && c.key === key) {
        c.job = j;
        return c;
    }
    const plan = generateHouse(j.opts);
    const fill = [];
    for (const f of j.fill)
        for (let i = 1; i <= f.n; i++)
            fill.push({ x: f.x, y: -i, z: f.z, id: f.id, ph: 0.5, st: f.id.endsWith("_log") ? { axis: "y" } : undefined });
    const cx = (plan.W - 1) / 2;
    const cz = (plan.D - 1) / 2;
    const all = [...fill, ...plan.blocks];
    const ang = (b) => {
        let a = Math.atan2(b.z - cz, b.x - cx) + Math.PI / 2; // start at the front
        if (a < 0)
            a += Math.PI * 2;
        return a;
    };
    all.sort((a, b) => a.ph - b.ph || a.y - b.y || (a.dep ? 1 : 0) - (b.dep ? 1 : 0) || ang(a) - ang(b));
    const idOf = new Map();
    for (const b of all)
        idOf.set(lk(b.x, b.y, b.z), b.id);
    c = {
        job: j, key, plan, blocks: all, idOf, clearIdx: 0, acc: 0, containers: [], containersAt: -9999, swingUntil: 0,
        waitMsgAt: -9999, standFails: 0,
    };
    CACHE.set(h.id, c);
    return c;
}
// ------------------------------------------------------------------ site analysis
const NATURAL_PLANT_PARTS = [
    "short_grass", "tall_grass", "fern", "dead_bush", "flower", "tulip", "orchid", "allium", "azure_bluet", "oxeye_daisy",
    "cornflower", "lily_of_the_valley", "poppy", "dandelion", "sunflower", "lilac", "rose_bush", "peony", "snow_layer",
    "vine", "sweet_berry_bush", "glow_lichen", "pink_petals", "hanging_roots", "spore_blossom", "mushroom", "bush",
    "leaf_litter", "wildflowers", "dry_grass", "firefly_bush", "moss_carpet", "azalea", "big_dripleaf", "small_dripleaf",
    "pointed_dripstone", "sugar_cane", "reeds", "bamboo", "cactus", "pumpkin", "melon_block", "torchflower", "eyeblossom",
    "web", "sapling", "propagule", "pale_moss_carpet", "pale_hanging_moss", "cactus_flower", "fungus", "_roots", "sprouts",
    "sculk_vein", "seagrass", "deadbush", "large_fern",
];
const NATURAL_EXTRA = new Set([
    "minecraft:snow", "minecraft:ice", "minecraft:packed_ice", "minecraft:mushroom_stem", "minecraft:brown_mushroom_block",
    "minecraft:red_mushroom_block", "minecraft:pale_moss_block", "minecraft:mangrove_roots", "minecraft:muddy_mangrove_roots",
    "minecraft:bee_nest",
]);
function isNatural(b, trees) {
    if (b.isAir)
        return true;
    const id = b.typeId;
    if (LOGS.has(id))
        return trees ? isNaturalTreeAt(b.dimension, b.location, trees) : true;
    if (LEAF_LIKE.has(id)) {
        try {
            return b.permutation.getState("persistent_bit") !== true;
        }
        catch {
            return true;
        }
    }
    if (DIG_SOFT[id] !== undefined || DIG_STONE[id] !== undefined || ORES[id])
        return true;
    if (NATURAL_EXTRA.has(id))
        return true;
    if (id.includes("_wheat") || id === "minecraft:wheat" || id.includes("carrots") || id.includes("potatoes") || id.includes("beetroot"))
        return false;
    for (const p of NATURAL_PLANT_PARTS)
        if (id.includes(p))
            return true;
    return false;
}
/** Positions (local) that must be emptied before building. */
function clearSet(plan, opts) {
    const out = new Map();
    const add = (x, y, z) => out.set(lk(x, y, z), { x, y, z });
    const a = plan.clearArea;
    for (let x = a.x0; x <= a.x1; x++)
        for (let z = a.z0; z <= a.z1; z++)
            for (let y = 0; y <= plan.maxY; y++)
                add(x, y, z);
    for (const cc of plan.clearCells ?? [])
        add(cc.x, cc.y, cc.z); // underground rooms / stairs (v1.4)
    for (const b of plan.blocks) {
        if (b.y < 0)
            continue; // below-ground features (pool, deck) simply replace the natural ground
        add(b.x, b.y, b.z);
        if (b.y >= 0 && (b.x < 0 || b.x >= plan.W || b.z < 0 || b.z >= plan.D)) {
            add(b.x, b.y + 1, b.z);
            add(b.x, b.y + 2, b.z);
        }
    }
    const c = plan.doorX;
    const S = !isExtraType(opts) && opts.style === 3 ? 3 : 0;
    for (let z = plan.front; z < 0; z++)
        for (let y = Math.max(0, S + z + 4); y <= S + 3; y++)
            add(c, y, z);
    const arr = [...out.values()];
    arr.sort((a, b) => b.y - a.y || a.z - b.z || a.x - b.x); // top to bottom
    return arr;
}
export function computeOrigin(p, opts, facing) {
    const plan = generateHouse(opts);
    const F = DIR_VEC[facing];
    const L = DIR_VEC[leftOf(facing)];
    const c = Math.floor((plan.minX + plan.maxX) / 2);
    const px = Math.floor(p.location.x);
    const pz = Math.floor(p.location.z);
    let py = Math.floor(p.location.y + 0.01);
    // standing in the air (flying): use the ground below
    for (let i = 0; i < 24; i++) {
        const below = safeBlock(p.dimension, { x: px, y: py - 1, z: pz });
        if (!below || !isPassable(below))
            break;
        py--;
    }
    const back = plan.minZ - 2;
    return {
        ox: px - c * L.x - back * F.x,
        oy: py,
        oz: pz - c * L.z - back * F.z,
    };
}
export function analyzeSite(dim, j, poolDropped = false) {
    const plan = generateHouse(j.opts);
    const fill = [];
    let clearCount = 0;
    const trees = new Map();
    for (const p of clearSet(plan, j.opts)) {
        const b = safeBlock(dim, toWorld(j, p.x, p.y, p.z));
        if (!b)
            return { ok: false, reason: "พื้นที่ยังโหลดไม่ครบ ลองเดินเข้าไปใกล้ๆ อีกนิด", fill, clearCount };
        if (b.isLiquid || b.typeId.includes("water") || b.typeId.includes("lava")) {
            return { ok: false, reason: "มีน้ำหรือลาวาในพื้นที่ที่จะสร้าง ลองหาที่แห้งๆ นะ", fill, clearCount };
        }
        if (!isNatural(b, trees)) {
            return {
                ok: false,
                reason: `มีบล็อก §e${itemName(b.typeId)}§r ขวางอยู่ที่ (${b.x}, ${b.y}, ${b.z}) ผู้ช่วยจะไม่ทุบของที่คุณสร้างไว้ ลองย้ายที่หรือหันไปทางอื่นนะ`,
                fill,
                clearCount,
            };
        }
        if (!b.isAir)
            clearCount++;
    }
    // below-ground features (swimming pool) need natural ground around them, otherwise they are dropped
    let poolOk = true;
    for (const b of plan.blocks) {
        if (b.tag !== "pool")
            continue;
        const w = safeBlock(dim, toWorld(j, b.x, b.y, b.z));
        if (!w || w.typeId.includes("lava") || (!w.isAir && !w.isLiquid && !isNatural(w, trees)) || (b.id === "minecraft:water" && w.isAir)) {
            poolOk = false;
            break;
        }
    }
    if (!poolOk && j.opts.pool)
        return analyzeSite(dim, { ...j, opts: { ...j.opts, pool: false } }, true);
    // foundation fill below y = 0
    const S = !isExtraType(j.opts) && j.opts.style === 3 ? 3 : 0;
    const cols = new Map();
    for (const b of plan.blocks) {
        if (b.y !== 0 || b.opt)
            continue;
        const inside = b.x > 0 && b.x < plan.W - 1 && b.z > 0 && b.z < plan.D - 1;
        if (inside && S === 0)
            continue;
        const id = S > 0 && b.id.endsWith("_log") ? b.id : b.id.includes("stripped_") ? b.id : plan.fillBlock;
        cols.set(`${b.x},${b.z}`, { x: b.x, z: b.z, id });
    }
    for (const col of cols.values()) {
        let depth = 0;
        for (let y = -1; y >= -MAX_FILL - 1; y--) {
            const b = safeBlock(dim, toWorld(j, col.x, y, col.z));
            if (!b)
                break;
            if (b.typeId.includes("lava"))
                return { ok: false, reason: "มีลาวาอยู่ใต้พื้นที่สร้าง อันตราย! ลองย้ายที่นะ", fill, clearCount };
            if (!isPassable(b) && !b.isLiquid)
                break;
            depth++;
            if (depth > MAX_FILL) {
                return { ok: false, reason: "พื้นตรงนี้ต่ำ/เป็นหน้าผาเกินไป ลองหาที่ราบกว่านี้นะ", fill, clearCount };
            }
        }
        if (depth > 0)
            fill.push({ x: col.x, z: col.z, n: depth, id: col.id });
    }
    return { ok: true, fill, clearCount, poolDropped };
}
// ------------------------------------------------------------------ materials
/** True when the helper builds without using blocks (default). */
/** v1.6: a nether portal always needs real materials (obsidian + flint and steel). */
export function needsRealItems(opts) {
    return !!opts && opts.type === "portal";
}
export function isFreeBuild(h, job) {
    return !getSettings(h).needBlocks && !needsRealItems(job?.opts);
}
export function requiredMaterials(j) {
    const m = billOfMaterials(generateHouse(j.opts));
    for (const f of j.fill) {
        const e = m.get(f.id) ?? { req: 0, opt: 0 };
        e.req += f.n;
        m.set(f.id, e);
    }
    return m;
}
export function remainingMaterials(h, j) {
    const c = cacheFor(h, j);
    const m = new Map();
    const start = j.stage === "clear" ? 0 : j.idx;
    for (let i = start; i < c.blocks.length; i++) {
        const b = c.blocks[i];
        const item = b.item === undefined ? b.id : b.item;
        if (!item)
            continue;
        const e = m.get(item) ?? { req: 0, opt: 0 };
        if (b.opt)
            e.opt++;
        else
            e.req++;
        m.set(item, e);
    }
    return m;
}
/** Text list of missing required materials (what the helper does not carry yet). */
export function missingText(h, j, maxLines = 8) {
    const rem = remainingMaterials(h, j);
    const lines = [];
    let missing = 0;
    for (const [item, n] of rem) {
        const have = countId(h, item);
        const need = n.req - have;
        if (need > 0) {
            missing += need;
            if (lines.length < maxLines)
                lines.push(`§c${itemName(item)} §fขาด ${need}`);
        }
    }
    return { text: lines.join("\n"), missing };
}
/** Moves the materials still needed by the job from the player's inventory into the helper. */
export function transferMaterials(p, h, j) {
    const rem = remainingMaterials(h, j);
    const pc = playerContainer(p);
    if (!pc)
        return { moved: 0, full: false };
    let moved = 0;
    let full = false;
    for (const [item, n] of rem) {
        let need = n.req + n.opt - countId(h, item);
        if (need <= 0)
            continue;
        for (let i = 0; i < pc.size && need > 0; i++) {
            const it = pc.getItem(i);
            if (!it || it.typeId !== item)
                continue;
            const take = Math.min(need, it.amount);
            const part = it.clone();
            part.amount = take;
            const left = storageAdd(h, part);
            const put = take - left;
            if (put <= 0) {
                full = true;
                break;
            }
            if (put >= it.amount)
                pc.setItem(i, undefined);
            else {
                it.amount -= put;
                pc.setItem(i, it);
            }
            moved += put;
            need -= put;
            if (left > 0) {
                full = true;
                break;
            }
        }
    }
    return { moved, full };
}
// ------------------------------------------------------------------ start / stop
/** Plans a house in front of the player (same as the design menu) and checks the site. */
export function makeHouseJob(p, opts) {
    const facing = facingOf(p);
    const o = computeOrigin(p, opts, facing);
    const { speed, ...house } = opts;
    // v1.6: every new design gets its own random variant (unless the player asked for one)
    if (typeof house.variant !== "number")
        house.variant = Math.floor(Math.random() * 1000);
    const job = {
        v: 1, opts: house, ox: o.ox, oy: o.oy, oz: o.oz, facing, dim: p.dimension.id, stage: "clear", idx: 0, fill: [],
        paused: false, speed, total: 0, placed: 0,
    };
    const site = analyzeSite(p.dimension, job);
    if (site.ok) {
        job.fill = site.fill;
        if (site.poolDropped)
            job.opts.pool = false;
    }
    return { job, site };
}
export function startJob(h, j) {
    saveJob(h, j);
    setModeProp(h, "build");
    CACHE.delete(h.id);
}
export function pauseJob(h) {
    const j = getJob(h);
    if (!j)
        return;
    j.paused = true;
    saveJob(h, j);
    setWorking(h, false);
}
export function cancelJob(h) {
    saveJob(h, undefined);
    setWorking(h, false);
}
export function jobProgress(h, j) {
    const c = cacheFor(h, j);
    if (j.stage === "clear")
        return 0;
    return Math.min(100, Math.floor((j.idx / Math.max(1, c.blocks.length)) * 100));
}
// ------------------------------------------------------------------ outline preview
export function showOutline(p, j, seconds) {
    const plan = generateHouse(j.opts);
    const F = DIR_VEC[j.facing];
    const L = DIR_VEC[leftOf(j.facing)];
    // local continuous coordinates -> world (cell (x,z) spans [x, x+1) locally)
    const cw = (u, y, w) => ({
        x: j.ox + 0.5 + (u - 0.5) * L.x + (w - 0.5) * F.x,
        y: j.oy + y,
        z: j.oz + 0.5 + (u - 0.5) * L.z + (w - 0.5) * F.z,
    });
    const pts = [];
    const x0 = plan.minX, x1 = plan.maxX + 1, z0 = plan.minZ, z1 = plan.maxZ + 1;
    for (let x = x0; x <= x1; x += 0.5)
        pts.push(cw(x, 0.15, z0), cw(x, 0.15, z1));
    for (let z = z0; z <= z1; z += 0.5)
        pts.push(cw(x0, 0.15, z), cw(x1, 0.15, z));
    // house body corners
    for (const [x, z] of [[0, 0], [plan.W, 0], [0, plan.D], [plan.W, plan.D]]) {
        for (let y = 0; y <= 5; y += 0.5)
            pts.push(cw(x, y + 0.15, z));
    }
    // door marker
    const c = (plan.W - 1) / 2;
    for (let y = 1; y <= 2.5; y += 0.5)
        pts.push(cw(c + 0.5, y, 0));
    let n = 0;
    const id = system.runInterval(() => {
        n++;
        if (n > seconds * 2 || !p.isValid) {
            system.clearRun(id);
            return;
        }
        for (const q of pts) {
            try {
                p.dimension.spawnParticle("minecraft:villager_happy", q);
            }
            catch {
                /* ignore */
            }
        }
    }, 10);
}
// ------------------------------------------------------------------ helpers
function canReplace(b) {
    if (b.isAir || b.isLiquid)
        return true;
    return isNatural(b);
}
function placeSound(id) {
    const n = id.replace("minecraft:", "");
    if (n.includes("glass"))
        return "use.stone";
    if (n.includes("carpet") || n === "bed")
        return "use.cloth";
    if (n.includes("leaves"))
        return "use.grass";
    if (n === "ladder")
        return "use.ladder";
    if (n.includes("planks") || n.includes("_log") || n.includes("fence") || n.includes("door") || n.includes("bookshelf") ||
        n.includes("chest") || n.includes("barrel") || n.includes("crafting") || n.includes("pressure_plate") ||
        /^(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak)_(stairs|slab)$/.test(n))
        return "use.wood";
    if (n === "dirt" || n === "grass_block")
        return "use.gravel";
    return "use.stone";
}
function isConnectable(id) {
    return id.endsWith("glass_pane") || id === "minecraft:iron_bars" || (id.endsWith("_fence") && id !== "minecraft:nether_brick_fence");
}
function isSolidFull(id) {
    if (isPassableId(id))
        return false;
    const bad = [
        "_stairs", "_slab", "_door", "trapdoor", "_fence", "fence_gate", "_pane", "lantern", "ladder", "bed", "carpet",
        "pressure_plate", "campfire", "chest", "torch", "_wall", "flower_pot", "leaves", "iron_bars",
    ];
    return !bad.some((s) => id.includes(s));
}
function connects(selfId, nb) {
    if (!nb || nb.isAir || nb.isLiquid)
        return false;
    const id = nb.typeId;
    const selfPane = selfId.endsWith("glass_pane") || selfId === "minecraft:iron_bars";
    if (selfPane) {
        if (id.endsWith("glass_pane") || id === "minecraft:iron_bars" || id.endsWith("_wall"))
            return true;
    }
    else {
        if ((id.endsWith("_fence") && id !== "minecraft:nether_brick_fence") || id.endsWith("fence_gate"))
            return true;
    }
    return isSolidFull(id);
}
const DIRS4 = ["north", "south", "east", "west"];
/** Newer versions store pane/fence connections as block states; keep them right after script placement. */
function fixConnections(dim, p) {
    const b = safeBlock(dim, p);
    if (!b || !isConnectable(b.typeId))
        return;
    let states;
    try {
        states = b.permutation.getAllStates();
    }
    catch {
        return;
    }
    if (!("minecraft:connection_north" in states))
        return;
    let perm = b.permutation;
    let changed = false;
    for (const d of DIRS4) {
        const v = DIR_VEC[d];
        const want = connects(b.typeId, safeBlock(dim, { x: p.x + v.x, y: p.y, z: p.z + v.z }));
        if (states["minecraft:connection_" + d] !== want) {
            try {
                perm = perm.withState("minecraft:connection_" + d, want);
                changed = true;
            }
            catch {
                return;
            }
        }
    }
    if (changed) {
        try {
            b.setPermutation(perm);
        }
        catch {
            /* ignore */
        }
    }
}
function afterPlace(dim, p) {
    fixConnections(dim, p);
    for (const d of DIRS4) {
        const v = DIR_VEC[d];
        const q = { x: p.x + v.x, y: p.y, z: p.z + v.z };
        const nb = safeBlock(dim, q);
        if (nb && isConnectable(nb.typeId))
            fixConnections(dim, q);
    }
}
function standPoint(c, b) {
    const p = c.plan;
    const minX = p.minX - 2, maxX = p.maxX + 2, minZ = p.minZ - 2, maxZ = p.maxZ + 2;
    const cx = clamp(b.x, minX, maxX);
    const cz = clamp(b.z, minZ, maxZ);
    const cands = [{ x: cx, z: minZ }, { x: maxX, z: cz }, { x: cx, z: maxZ }, { x: minX, z: cz }];
    let best = cands[0];
    let bd = Infinity;
    for (const q of cands) {
        const d = (q.x - b.x) ** 2 + (q.z - b.z) ** 2;
        if (d < bd - 0.01) {
            bd = d;
            best = q;
        }
    }
    return best;
}
function cellCenter(j, x, y, z) {
    // world block containing local (x,y,z), then its centre
    const w = toWorld(j, x, y, z);
    return { x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 };
}
function teleportNear(h, j, lx, lz) {
    const dim = h.dimension;
    const base = toWorld(j, lx, 0, lz);
    for (let dy = 4; dy >= -10; dy--) {
        const y = base.y + dy;
        const below = safeBlock(dim, { x: base.x, y: y - 1, z: base.z });
        const at = safeBlock(dim, { x: base.x, y, z: base.z });
        const up = safeBlock(dim, { x: base.x, y: y + 1, z: base.z });
        if (below && !isPassable(below) && !below.isLiquid && isPassable(at) && isPassable(up)) {
            try {
                dim.spawnParticle("minecraft:villager_happy", h.location);
                h.teleport({ x: base.x + 0.5, y, z: base.z + 0.5 });
                dim.playSound("mob.shulker.teleport", { x: base.x, y, z: base.z }, { volume: 0.4, pitch: 1.4 });
            }
            catch {
                /* ignore */
            }
            return true;
        }
    }
    return false;
}
function storeCleared(h, id) {
    let item;
    if (LOGS.has(id))
        item = id;
    else if (id === "minecraft:stone")
        item = "minecraft:cobblestone";
    else if (id === "minecraft:deepslate")
        item = "minecraft:cobbled_deepslate";
    else if (["minecraft:grass_block", "minecraft:dirt", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt", "minecraft:rooted_dirt"].includes(id))
        item = "minecraft:dirt";
    else if (["minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:cobblestone"].includes(id))
        item = id;
    if (!item)
        return;
    const u = usedSlots(h);
    if (u.used >= u.total && countId(h, item) % 64 === 0)
        return;
    try {
        storageAdd(h, new ItemStack(item, 1));
    }
    catch {
        /* ignore */
    }
}
// ------------------------------------------------------------------ tick
export function tickBuild(h, owner) {
    const j = getJob(h);
    if (!j) {
        setModeProp(h, "follow");
        return;
    }
    if (j.paused) {
        j.paused = false;
        saveJob(h, j);
    }
    if (h.dimension.id !== j.dim) {
        setAi(h, "follow");
        notifyOwner(h, "dim", "ผู้ช่วยอยู่คนละมิติกับบ้านที่กำลังสร้าง พากลับไปที่เดิมก่อนนะ", 20 * 60);
        return;
    }
    setAi(h, "work");
    const c = cacheFor(h, j);
    const dim = h.dimension;
    const now = system.currentTick;
    if (now > c.swingUntil)
        setWorking(h, false);
    // -------- stage 1: clearing the site
    if (j.stage === "clear") {
        if (!c.clear) {
            c.clear = clearSet(c.plan, j.opts);
            c.clearIdx = 0;
        }
        // stand in front of the house while clearing
        const front = { x: (c.plan.W - 1) / 2, z: c.plan.minZ - 2 };
        const fw = cellCenter(j, front.x, 0, front.z);
        if (distXZ(h.location, fw) > 3) {
            const res = steer(h, fw, 1.2);
            if (res.status !== "moving" || isStuck(h, distXZ(h.location, fw), 60)) {
                teleportNear(h, j, front.x, front.z);
                resetProgress(h);
            }
        }
        else
            stopMoving(h);
        let budget = 10;
        let scans = 0;
        while (budget > 0 && scans++ < 400 && c.clearIdx < c.clear.length) {
            const p = c.clear[c.clearIdx++];
            const w = toWorld(j, p.x, p.y, p.z);
            const b = safeBlock(dim, w);
            if (!b || b.isAir)
                continue;
            if (b.isLiquid || !isNatural(b))
                continue;
            const id = b.typeId;
            budget--;
            try {
                b.setType("minecraft:air");
            }
            catch {
                continue;
            }
            storeCleared(h, id);
            if (budget % 3 === 0) {
                try {
                    dim.spawnParticle("minecraft:basic_crit_particle", { x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 });
                    dim.playSound(LOGS.has(id) ? "dig.wood" : LEAF_LIKE.has(id) ? "dig.grass" : "dig.gravel", w, { volume: 0.5 });
                }
                catch {
                    /* ignore */
                }
            }
            face(h, { x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 });
            setWorking(h, true);
            c.swingUntil = now + 6;
        }
        if (c.clearIdx >= c.clear.length) {
            j.stage = "build";
            j.idx = 0;
            saveJob(h, j);
        }
        return;
    }
    // -------- stage 2: placing blocks (free by default; optional "need blocks" mode uses the helper's bag)
    const free = !getSettings(h).needBlocks && !needsRealItems(j.opts);
    c.acc += SPEEDS[clamp(j.speed, 0, 2)] * workMult(h);
    let guard = 0;
    while (c.acc >= 1 && guard++ < 40) {
        if (j.idx >= c.blocks.length) {
            finish(h, j, owner);
            return;
        }
        const b = c.blocks[j.idx];
        const w = toWorld(j, b.x, b.y, b.z);
        const blk = safeBlock(dim, w);
        if (!blk)
            return; // chunk not loaded
        // already done / cannot touch
        if (blk.typeId === b.id && b.id !== "minecraft:air") {
            j.idx++;
            continue;
        }
        if (!canReplace(blk)) {
            j.idx++;
            continue;
        }
        if (b.dep) {
            const dx = b.x + b.dep[0], dy = b.y + b.dep[1], dz = b.z + b.dep[2];
            const want = c.idOf.get(lk(dx, dy, dz));
            const db = safeBlock(dim, toWorld(j, dx, dy, dz));
            if (!want || !db || db.typeId !== want) {
                j.idx++;
                continue;
            }
        }
        // reach: walk to a stand point around the house (magic reach once there)
        const cc = cellCenter(j, b.x, b.y, b.z);
        if (distXZ(h.location, cc) > BUILD_REACH) {
            const sp = standPoint(c, b);
            const spw = cellCenter(j, sp.x, 0, sp.z);
            const dsp = distXZ(h.location, spw);
            if (dsp > 1.5) {
                const res = steer(h, spw, 0.8);
                if (res.status === "moving" && !isStuck(h, dsp, 50)) {
                    saveIfNeeded(h, j);
                    return;
                }
                if (res.status !== "arrived") {
                    teleportNear(h, j, sp.x, sp.z);
                    resetProgress(h);
                    return;
                }
            }
        }
        stopMoving(h);
        // entities in the way
        let blocked = false;
        try {
            for (const e of dim.getEntitiesAtBlockLocation(w)) {
                if (e.id === h.id || e.typeId === "minecraft:item" || e.typeId === "minecraft:xp_orb")
                    continue;
                blocked = true;
                if (e.typeId === "minecraft:player") {
                    notifyOwner(h, "inway", "ขอทางหน่อยครับ มีคนยืนขวางจุดที่จะวางบล็อก", 20 * 10);
                }
                else {
                    try {
                        e.applyImpulse({ x: (Math.random() - 0.5) * 0.6, y: 0.3, z: (Math.random() - 0.5) * 0.6 });
                    }
                    catch {
                        /* ignore */
                    }
                }
            }
        }
        catch {
            /* ignore */
        }
        if (blocked) {
            c.acc = 0;
            return;
        }
        // materials
        const item = b.item === undefined ? b.id : b.item;
        if (item && !free) {
            if (countId(h, item) <= 0 && getSettings(h).chestBuild) {
                if (now - c.containersAt > 100) {
                    c.containers = nearbyContainers(h, h.location, 10);
                    c.containersAt = now;
                }
                if (c.containers.length) {
                    const rem = remainingMaterials(h, j).get(item);
                    const need = rem ? rem.req + rem.opt : 1;
                    pullFromContainers(h, c.containers, item, Math.min(need, 64 * 4));
                }
            }
            if (countId(h, item) <= 0) {
                if (b.opt) {
                    j.idx++;
                    continue;
                }
                // wait for materials
                c.acc = 0;
                stopMoving(h);
                if (now - c.waitMsgAt > 20 * 25) {
                    c.waitMsgAt = now;
                    const mt = missingText(h, j, 6);
                    tellOwner(owner, getName(h), `§eบล็อกหมดแล้ว รอเราเอามาให้อยู่นะ!§r ยังขาด:\n${mt.text}\n§7(กดที่ตัวผม > งานสร้างบ้าน > ส่งบล็อกจากกระเป๋าเรา หรือถือบล็อกแล้วย่อ+กดที่ตัวผม)`);
                }
                rt(h).busyUntil = now + 20;
                saveIfNeeded(h, j);
                return;
            }
        }
        const perm = permFor(j, b);
        if (!perm) {
            j.idx++;
            continue;
        }
        try {
            blk.setPermutation(perm);
        }
        catch {
            j.idx++;
            continue;
        }
        if (item && !free)
            storageTake(h, item, 1);
        afterPlace(dim, w);
        j.idx++;
        j.placed = (j.placed ?? 0) + 1;
        c.acc -= 1;
        face(h, cc);
        setWorking(h, true);
        c.swingUntil = now + 8;
        try {
            dim.playSound(placeSound(b.id), cc, { volume: 0.7, pitch: 0.9 + Math.random() * 0.2 });
            if (j.idx % 6 === 0)
                dim.spawnParticle("minecraft:villager_happy", cc);
        }
        catch {
            /* ignore */
        }
    }
    if (c.acc > 1)
        c.acc = 1;
    saveIfNeeded(h, j);
}
const lastSave = new Map();
function saveIfNeeded(h, j) {
    const now = system.currentTick;
    if (now - (lastSave.get(h.id) ?? 0) < 40)
        return;
    lastSave.set(h.id, now);
    saveJob(h, j);
}
function finish(h, j, owner) {
    saveJob(h, undefined);
    setModeProp(h, "follow");
    setWorking(h, false);
    setAi(h, "follow");
    const dim = h.dimension;
    const door = toWorld(j, generateHouse(j.opts).doorX, 1, 0);
    try {
        dim.playSound("random.levelup", h.location, { volume: 1 });
        for (let i = 0; i < 12; i++) {
            dim.spawnParticle("minecraft:villager_happy", {
                x: door.x + 0.5 + (Math.random() - 0.5) * 6,
                y: door.y + 1 + Math.random() * 5,
                z: door.z + 0.5 + (Math.random() - 0.5) * 6,
            });
        }
    }
    catch {
        /* ignore */
    }
    tellOwner(owner, getName(h), "§a§lสร้างบ้านเสร็จแล้ว!§r ขอให้มีความสุขกับบ้านใหม่นะครับ");
    addXp(h, ([30, 50, 80][j.opts.size] ?? 50) + 10 * (j.opts.floors - 1));
}
export { generateHouse, billOfMaterials };
