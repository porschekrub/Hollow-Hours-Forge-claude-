// Extra house types (v1.4): underground, tower, castle, treehouse, igloo, desert, pyramid, nether, box.
// Pure functions (no Minecraft imports). Same local coordinates as house_gen.js:
// x = left->right seen from the front, z = front->back, y = up, y = 0 is one block above the ground.
// Underground parts use negative y. The surface (top ground block) is y = -1.
import { furnishRoom, rolesFor } from "./interior.js";
import { BUNKER_VARIANTS, generateBunker } from "./house_bunker.js";
import { MORE_TYPES, MORE_VARIANTS, generateMore, isMoreType } from "./house_more.js";
import { MODELS, STATUE_TYPES, generateStatue, modelName } from "./house_statue.js";
import { variantOf } from "./house_common.js";
const M = "minecraft:";
const WOOD_IDS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"];
export const TYPES = [
    { id: "standard", th: "บ้านทั่วไป (ใช้ 'สไตล์บ้าน' ด้านล่าง)" },
    { id: "underground", th: "บ้านใต้ดิน (ขุดลงไป มีบันไดทางเข้า)" },
    { id: "tower", th: "หอคอย (หลังคาแหลม)" },
    { id: "castle", th: "ปราสาท (หอป้อม 4 มุม + กำแพงเมือง)" },
    { id: "treehouse", th: "บ้านต้นไม้ (ต้นไม้ยักษ์ + บ้านบนต้น + ระเบียง)" },
    { id: "mansion", th: "บ้านหรู / คฤหาสน์ (เสาหินอ่อน ระเบียง สวน สระ)" },
    { id: "safehouse", th: "บ้านหลบภัย / เซฟเฮาส์ (กันมอนสเตอร์ ห้องกั้นประตู 2 ชั้น)" },
    { id: "lighthouse", th: "ประภาคาร (ทรงกลม ลายขาวแดง)" },
    { id: "igloo", th: "อิกลู (โดมหิมะ)" },
    { id: "desert", th: "บ้านทะเลทราย (หลังคาเรียบ)" },
    { id: "pyramid", th: "ปิรามิด (มีห้องข้างใน)" },
    { id: "nether", th: "ป้อมเนเธอร์ (แบล็กสโตน)" },
    { id: "box", th: "บ้านทรงกล่อง (เลือกวัสดุได้อิสระ)" },
    ...MORE_TYPES,
    ...STATUE_TYPES,
];
/** Name of the chosen variant (for messages), or undefined when the type has only one look. */
export function variantName(o) {
    const v = variantOf(o);
    if (o.type === "underground" || o.type === "safehouse" && false)
        return BUNKER_VARIANTS[v % BUNKER_VARIANTS.length];
    if (isMoreType(o.type))
        return MORE_VARIANTS[o.type][v % MORE_VARIANTS[o.type].length];
    if (o.type === "statue")
        return modelName(o);
    return undefined;
}
export const VARIANT_COUNT = { underground: BUNKER_VARIANTS.length, statue: MODELS.length };
export const MATS = [
    { id: "auto", th: "ตามประเภทบ้าน (แนะนำ)" },
    { id: "stone", th: "อิฐหิน" },
    { id: "wood", th: "ไม้" },
    { id: "brick", th: "อิฐแดง" },
    { id: "sand", th: "หินทราย" },
    { id: "deepslate", th: "ดีพสเลท" },
    { id: "blackstone", th: "แบล็กสโตน" },
    { id: "quartz", th: "ควอตซ์ขาว" },
    { id: "mud", th: "อิฐโคลน" },
    { id: "prismarine", th: "ปริซึมารีน" },
];
const DEFAULT_MAT = {
    underground: "stone", tower: "stone", castle: "stone", treehouse: "wood", igloo: "snow", desert: "sand",
    pyramid: "sand", nether: "blackstone", box: "stone", mansion: "quartz", safehouse: "deepslate", lighthouse: "quartz",
};
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const odd = (n) => (n % 2 === 0 ? n + 1 : n);
/** Size override from the player ("กว้าง 12 ยาว 16"); always odd so the door sits in the middle. */
const dim = (v, def, lo = 5, hi = 31) => (typeof v === "number" && Number.isFinite(v) ? odd(clamp(Math.round(v), lo, hi)) : def);
export function isExtraType(o) {
    return !!o && typeof o.type === "string" && o.type !== "standard" && TYPES.some((t) => t.id === o.type);
}
export function typeName(o) {
    return TYPES.find((t) => t.id === o.type)?.th.split(" (")[0] ?? "บ้าน";
}
function woodSet(w) {
    return {
        planks: M + w + "_planks",
        log: M + w + "_log",
        stairs: M + w + "_stairs",
        slab: M + w + "_slab",
        fence: M + w + "_fence",
        door: w === "oak" ? M + "wooden_door" : M + w + "_door",
        plate: w === "oak" ? M + "wooden_pressure_plate" : M + w + "_pressure_plate",
        leaves: M + w + "_leaves",
    };
}
function palette(o, type, wid, wd) {
    let m = MATS[o.mat ?? 0]?.id ?? "auto";
    if (m === "auto")
        m = DEFAULT_MAT[type] ?? "stone";
    const P = {
        stone: { wall: "stone_bricks", pillar: "chiseled_stone_bricks", stairs: "stone_brick_stairs", slab: "stone_brick_slab" },
        brick: { wall: "brick_block", pillar: "stone_bricks", stairs: "brick_stairs", slab: "brick_slab" },
        sand: { wall: "cut_sandstone", pillar: "chiseled_sandstone", stairs: "sandstone_stairs", slab: "sandstone_slab" },
        deepslate: { wall: "deepslate_bricks", pillar: "polished_deepslate", stairs: "deepslate_brick_stairs", slab: "deepslate_brick_slab" },
        blackstone: { wall: "polished_blackstone_bricks", pillar: "chiseled_polished_blackstone", stairs: "polished_blackstone_brick_stairs", slab: "polished_blackstone_brick_slab" },
        quartz: { wall: "quartz_block", pillar: "chiseled_quartz_block", stairs: "quartz_stairs", slab: "quartz_slab" },
        mud: { wall: "mud_bricks", pillar: "packed_mud", stairs: "mud_brick_stairs", slab: "mud_brick_slab" },
        prismarine: { wall: "prismarine_bricks", pillar: "dark_prismarine", stairs: "prismarine_brick_stairs", slab: "prismarine_brick_slab" },
        snow: { wall: "snow", pillar: "packed_ice", stairs: "spruce_stairs", slab: "spruce_slab" },
    };
    if (m === "wood") {
        const logId = wid === "oak" || wid === "birch" ? "spruce" : "dark_oak";
        return { wall: wd.planks, pillar: M + logId + "_log", stairs: wd.stairs, slab: wd.slab, wood: true };
    }
    const q = P[m] ?? P.stone;
    return { wall: M + q.wall, pillar: M + q.pillar, stairs: M + q.stairs, slab: M + q.slab, wood: false };
}
function ctx() {
    const map = new Map();
    const k = (x, y, z) => `${x},${y},${z}`;
    return {
        map,
        put: (x, y, z, id, ph, st, extra) => {
            map.set(k(x, y, z), { x, y, z, id, ph: ph ?? 1, st, ...extra });
        },
        has: (x, y, z) => map.has(k(x, y, z)),
        del: (x, y, z) => map.delete(k(x, y, z)),
    };
}
function door(put, wd, x, y, z, f) {
    put(x, y, z, wd.door, 3, { f, upper: false, hinge: false }, { item: wd.door });
    put(x, y + 1, z, wd.door, 3, { f, upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
}
function finalize(map, W, D, base) {
    const blocks = [...map.values()];
    blocks.sort((a, b) => a.ph - b.ph || a.y - b.y || a.z - b.z || a.x - b.x);
    let minX = 0, maxX = W - 1, minZ = 0, maxZ = D - 1, maxY = 0;
    for (const b of blocks) {
        minX = Math.min(minX, b.x);
        maxX = Math.max(maxX, b.x);
        minZ = Math.min(minZ, b.z);
        maxZ = Math.max(maxZ, b.z);
        maxY = Math.max(maxY, b.y);
    }
    return {
        blocks, W, D, minX, maxX, minZ, maxZ, maxY, front: 0, doorX: (W - 1) / 2,
        clearArea: { x0: 0, x1: W - 1, z0: 0, z1: D - 1 }, fillBlock: M + "cobblestone", ...base,
    };
}
function rolesForFloor(F, f) {
    if (F <= 3)
        return rolesFor(F, f);
    if (f === 0)
        return rolesFor(3, 0);
    if (f === 1)
        return rolesFor(3, 1);
    if (f === F - 1)
        return rolesFor(3, 2);
    return { roles: ["storage", "workshop"], beds: 0 };
}
/** Hip roof of stairs (hollow) that closes into a ridge/point. */
function pyramidRoof(put, x0, x1, z0, z1, y, P) {
    for (let i = 0;; i++) {
        const a = x0 + i, b = x1 - i, c = z0 + i, d = z1 - i;
        if (a > b || c > d)
            break;
        const yy = y + i;
        for (let x = a; x <= b; x++)
            for (let z = c; z <= d; z++) {
                const eX = x === a || x === b;
                const eZ = z === c || z === d;
                if (!eX && !eZ)
                    continue;
                if (a === b || c === d || (eX && eZ))
                    put(x, yy, z, P.full, 2);
                else if (z === c)
                    put(x, yy, z, P.stairs, 2, { f: "pz", half: "bottom" });
                else if (z === d)
                    put(x, yy, z, P.stairs, 2, { f: "nz", half: "bottom" });
                else if (x === a)
                    put(x, yy, z, P.stairs, 2, { f: "px", half: "bottom" });
                else
                    put(x, yy, z, P.stairs, 2, { f: "nx", half: "bottom" });
            }
    }
}
// ------------------------------------------------------------------ above-ground box houses
function boxHouse(o, type) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const P = palette(o, type, wid, wd);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    let F = clamp(Math.floor(o.floors ?? 2), 1, 8);
    let W, D;
    if (type === "tower") {
        W = D = dim(o.w, [5, 7, 9][s]);
        if (o.height)
            F = Math.ceil(o.height / 4);
        else if (!o.floorsExact)
            F = F * 2;
        F = Math.min(15, F);
    }
    else if (type === "castle" || type === "nether") {
        W = dim(o.w, [9, 11, 13][s]);
        D = dim(o.d, [9, 13, 15][s]);
        F = Math.min(4, F);
    }
    else {
        W = dim(o.w, [7, 9, 11][s]);
        D = dim(o.d, [7, 11, 13][s]);
        F = Math.min(5, F);
    }
    const S = 0;
    const yf = (f) => S + 4 * f;
    const c = (W - 1) / 2;
    const { map, put, has } = ctx();
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const isCorner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const slit = type === "castle" || type === "nether" || type === "tower";
    const glass = slit ? M + "iron_bars" : M + "glass_pane";
    const ladderX = W - 2, ladderZ = D - 2;
    const roofFull = P.wood ? wd.planks : P.wall;
    const topY = yf(F);
    // floors + walls
    for (let f = 0; f < F; f++) {
        const y0 = yf(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === ladderX && z === ladderZ)
                    continue;
                if (isPerim(x, z))
                    put(x, y0, z, isCorner(x, z) ? P.pillar : P.wall, 1, isCorner(x, z) && P.wood ? { axis: "y" } : undefined);
                else
                    put(x, y0, z, wd.planks, 1);
            }
        for (let row = 1; row <= 3; row++) {
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++) {
                    if (!isPerim(x, z))
                        continue;
                    const y = y0 + row;
                    if (isCorner(x, z)) {
                        put(x, y, z, P.pillar, 1, P.wood ? { axis: "y" } : undefined);
                        continue;
                    }
                    let win = false;
                    if (row === 2) {
                        if (z === 0)
                            win = x >= 2 && x <= W - 3 && Math.abs(x - c) >= 2 && x % 2 === 0;
                        else if (z === D - 1)
                            win = x >= 2 && x <= W - 3 && x % 3 === 1;
                        else
                            win = z >= 2 && z <= D - 3 && z % 3 === 1;
                    }
                    put(x, y, z, win ? glass : P.wall, 1);
                }
        }
    }
    // ceiling of the top floor
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, topY, z, isCorner(x, z) ? P.pillar : isPerim(x, z) ? P.wall : roofFull, 1, isCorner(x, z) && P.wood ? { axis: "y" } : undefined);
    // roof
    if (type === "tower" || type === "box") {
        pyramidRoof(put, 0, W - 1, 0, D - 1, topY + 1, { stairs: P.stairs, full: roofFull });
    }
    else if (type === "castle" || type === "nether") {
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++)
                if (isPerim(x, z) && (isCorner(x, z) || (x + z) % 2 === 0))
                    put(x, topY + 1, z, P.wall, 2);
        for (const [x, z] of [[0, 0], [W - 1, 0], [0, D - 1], [W - 1, D - 1]])
            put(x, topY + 2, z, P.wall, 2);
    }
    else {
        // desert: flat roof with a low parapet
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++)
                if (isPerim(x, z))
                    put(x, topY + 1, z, P.slab, 2);
    }
    // castle turrets at the four corners
    if (type === "castle" || type === "nether") {
        const H = topY + 3;
        for (const [cx, cz] of [[0, 0], [W - 1, 0], [0, D - 1], [W - 1, D - 1]]) {
            for (let x = cx - 1; x <= cx + 1; x++)
                for (let z = cz - 1; z <= cz + 1; z++) {
                    for (let y = 0; y <= H; y++)
                        put(x, y, z, x === cx && z === cz ? P.pillar : P.wall, 1);
                }
            // pointed cap
            for (let x = cx - 1; x <= cx + 1; x++)
                for (let z = cz - 1; z <= cz + 1; z++) {
                    const eX = x !== cx, eZ = z !== cz;
                    if (eX && eZ)
                        put(x, H + 1, z, P.wall, 2);
                    else if (eX)
                        put(x, H + 1, z, P.stairs, 2, { f: x < cx ? "px" : "nx", half: "bottom" });
                    else if (eZ)
                        put(x, H + 1, z, P.stairs, 2, { f: z < cz ? "pz" : "nz", half: "bottom" });
                    else
                        put(x, H + 1, z, P.wall, 2);
                }
            put(cx, H + 2, cz, P.slab, 2);
            // arrow slits
            for (const [dx, dz] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
                const px = cx + dx, pz = cz + dz;
                if (px < 0 || px > W - 1 || pz < 0 || pz > D - 1) {
                    put(px, 3, pz, M + "iron_bars", 1);
                    put(px, topY + 2, pz, M + "iron_bars", 1);
                }
            }
        }
    }
    // front door (+ entry step)
    door(put, wd, c, yf(0) + 1, 0, "pz");
    if (S === 0)
        put(c, 0, -1, P.stairs, 3, { f: "pz", half: "bottom" });
    // lanterns beside the door
    if (o.porch !== false && S === 0) {
        for (const x of [c - 2, c + 2])
            for (let y = 0; y <= 1; y++)
                put(x, y, -1, P.pillar, 3, P.wood ? { axis: "y" } : undefined);
        put(c - 2, 2, -1, M + "lantern", 4, { hanging: false });
        put(c + 2, 2, -1, M + "lantern", 4, { hanging: false });
    }
    // ladders between floors
    for (let f = 0; f < F - 1; f++)
        for (let y = yf(f) + 1; y <= yf(f + 1); y++)
            put(ladderX, y, ladderZ, M + "ladder", 3, { f: "nz" });
    // lights
    if (o.light !== false) {
        const zs = D <= 7 ? [Math.floor(D / 2)] : [Math.floor(D / 3), Math.floor((2 * D) / 3)];
        for (let f = 0; f < F; f++)
            for (const z of zs)
                put(c, yf(f) + 3, z, M + "lantern", 4, { hanging: true });
    }
    // furniture
    if (o.furniture !== false) {
        for (let f = 0; f < F; f++) {
            const reserved = new Set();
            if (f === 0) {
                reserved.add(`${c},1`);
                reserved.add(`${c},2`);
            }
            if (F > 1) {
                reserved.add(`${ladderX},${ladderZ}`);
                reserved.add(`${ladderX},${ladderZ - 1}`);
            }
            if (type === "castle" || type === "nether") {
                for (const [x, z] of [[1, 1], [W - 2, 1], [1, D - 2], [W - 2, D - 2]])
                    reserved.add(`${x},${z}`);
            }
            const rr = rolesForFloor(F, f);
            furnishRoom({
                y: yf(f), x0: 1, x1: W - 2, z0: 1, z1: D - 2, reserved, roles: rr.roles, beds: W >= 9 ? rr.beds : Math.min(rr.beds, 1),
                carpet: M + (type === "castle" || type === "nether" ? "red_carpet" : "white_carpet"),
                wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        }
    }
    const fillBlock = P.wall === wd.planks ? M + "cobblestone" : P.wall;
    return finalize(map, W, D, { fillBlock });
}
// ------------------------------------------------------------------ underground
function underground(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const P = palette(o, "underground", wid, wd);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const W = dim(o.w, [7, 9, 11][s]);
    const D = dim(o.d, [7, 11, 13][s]);
    const F = clamp(Math.floor(o.floors ?? 1), 1, 5);
    const c = (W - 1) / 2;
    const { map, put, has } = ctx();
    const y0f = (f) => -2 - 4 * (F - f); // floor layer of floor f (f = F is the ceiling under the grass)
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const isCorner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const ladderX = W - 2, ladderZ = D - 2;
    const clearCells = [];
    const lowest = y0f(0);
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            for (let y = lowest; y <= -2; y++)
                clearCells.push({ x, y, z });
    for (let f = 0; f < F; f++) {
        const y0 = y0f(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === ladderX && z === ladderZ)
                    continue;
                put(x, y0, z, isPerim(x, z) ? P.wall : wd.planks, 1);
            }
        for (let row = 1; row <= 3; row++)
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++)
                    if (isPerim(x, z))
                        put(x, y0 + row, z, isCorner(x, z) ? P.pillar : P.wall, 1, isCorner(x, z) && P.wood ? { axis: "y" } : undefined);
    }
    // ceiling just under the grass
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, -2, z, isPerim(x, z) ? P.wall : wd.planks, 1);
    for (let f = 0; f < F - 1; f++)
        for (let y = y0f(f) + 1; y <= y0f(f + 1); y++)
            put(ladderX, y, ladderZ, M + "ladder", 3, { f: "nz" });
    // entrance stairs from the surface down to the top floor
    const yTop = y0f(F - 1);
    const steps = -1 - yTop; // surface y = -1
    const zs = -1 - steps;
    for (let k = 0; k <= steps; k++) {
        const z = zs + k;
        const y = -1 - k;
        put(c, y, z, P.stairs, 1, { f: "nz", half: "bottom" });
        for (let yy = y + 1; yy <= y + 3; yy++)
            clearCells.push({ x: c, y: yy, z });
        if (k % 2 === 1) {
            put(c - 1, y + 2, z, M + "sea_lantern", 4);
            put(c + 1, y + 2, z, M + "sea_lantern", 4);
        }
    }
    // opening in the front wall of the top floor
    door(put, wd, c, yTop + 1, 0, "pz");
    // surface gateway: two posts and a lintel over the stair top
    for (const dx of [-1, 1])
        for (let y = 0; y <= 2; y++)
            put(c + dx, y, zs, P.pillar, 3, P.wood ? { axis: "y" } : undefined);
    for (let dx = -1; dx <= 1; dx++)
        put(c + dx, 3, zs, P.wall, 3);
    put(c, 2, zs, M + "lantern", 4, { hanging: true });
    // lights
    if (o.light !== false) {
        const lz = D <= 7 ? [Math.floor(D / 2)] : [Math.floor(D / 3), Math.floor((2 * D) / 3)];
        for (let f = 0; f < F; f++)
            for (const z of lz) {
                for (const x of W >= 9 ? [c - 2, c + 2] : [c])
                    put(x, y0f(f) + 4, z, M + "sea_lantern", 4);
            }
    }
    if (o.furniture !== false) {
        for (let f = 0; f < F; f++) {
            const reserved = new Set();
            if (f === F - 1) {
                reserved.add(`${c},1`);
                reserved.add(`${c},2`);
            }
            if (F > 1) {
                reserved.add(`${ladderX},${ladderZ}`);
                reserved.add(`${ladderX},${ladderZ - 1}`);
            }
            const rr = rolesForFloor(F, F - 1 - f); // the floor next to the entrance is the "ground" floor
            furnishRoom({
                y: y0f(f), x0: 1, x1: W - 2, z0: 1, z1: D - 2, reserved, roles: rr.roles, beds: W >= 9 ? rr.beds : Math.min(rr.beds, 1),
                carpet: M + "gray_carpet", wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        }
    }
    return finalize(map, W, D, {
        clearArea: { x0: 0, x1: -1, z0: 0, z1: -1 }, // nothing is cleared above the house: the grass stays
        clearCells, fillBlock: M + "cobblestone", underground: true,
    });
}
// ------------------------------------------------------------------ igloo
function igloo(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const r = [4, 5, 7][s];
    const L = 3; // entrance tunnel length
    const W = 2 * r + 1;
    const D = 2 * r + 1 + L;
    const cx = r;
    const cz = r + L;
    const { map, put, has } = ctx();
    const SNOW = M + "snow";
    for (let x = 0; x < W; x++)
        for (let z = L; z < D; z++)
            for (let y = 0; y <= r; y++) {
                const dx = x - cx, dz = z - cz;
                const d = Math.sqrt(dx * dx + dz * dz + y * y);
                if (y === 0) {
                    if (d <= r + 0.3)
                        put(x, 0, z, d <= r - 1 ? wd.planks : SNOW, 1);
                }
                else if (d <= r + 0.3 && d > r - 1) {
                    put(x, y, z, SNOW, 1);
                }
            }
    // little ice windows
    for (const [ax, az] of [[-1, 0], [1, 0], [0, 1]])
        for (const yy of [2, 3]) {
            const x = Math.round(cx + ax * (r - 0.5)), z = Math.round(cz + az * (r - 0.5));
            if (has(x, yy, z))
                put(x, yy, z, M + "glass", 1);
        }
    // entrance tunnel
    for (let z = 0; z < L; z++) {
        put(cx, 0, z, wd.planks, 1);
        for (const dx of [-1, 1])
            for (let y = 0; y <= 2; y++)
                put(cx + dx, y, z, SNOW, 1);
        for (let dx = -1; dx <= 1; dx++)
            put(cx + dx, 3, z, SNOW, 1);
    }
    for (let y = 1; y <= 2; y++)
        map.delete(`${cx},${y},${L}`);
    door(put, wd, cx, 1, L, "pz");
    put(cx, 0, -1, M + "spruce_stairs", 3, { f: "pz", half: "bottom" });
    if (o.light !== false) {
        put(cx, r - 1, cz, M + "lantern", 4, { hanging: true });
        put(cx - 1, 2, 0, M + "lantern", 4, { hanging: false });
    }
    if (o.furniture !== false) {
        const h = Math.max(2, Math.floor((r - 1) / 1.5));
        const reserved = new Set([`${cx},${L + 1}`, `${cx},${L + 2}`]);
        const rr = rolesFor(1, 0);
        furnishRoom({
            y: 0, x0: cx - h, x1: cx + h, z0: cz - h, z1: cz + h, reserved, roles: rr.roles, beds: 1, carpet: M + "light_blue_carpet",
            wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
        }, put, has);
    }
    return finalize(map, W, D, { doorX: cx, clearArea: { x0: 0, x1: W - 1, z0: 0, z1: D - 1 }, fillBlock: M + "dirt" });
}
// ------------------------------------------------------------------ pyramid
function pyramid(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const P = palette(o, "pyramid", wid, wd);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const N = [13, 17, 23][s];
    const c = (N - 1) / 2;
    const { map, put, has } = ctx();
    const layers = Math.ceil(N / 2);
    const inset = (x, z) => Math.min(x, N - 1 - x, z, N - 1 - z);
    for (let y = 0; y < layers; y++)
        for (let x = 0; x < N; x++)
            for (let z = 0; z < N; z++) {
                const i = inset(x, z);
                if (i < y)
                    continue;
                if (y === 0) {
                    put(x, 0, z, i <= 1 ? P.wall : wd.planks, 1);
                }
                else if (i <= y + 1 || y >= layers - 2) {
                    put(x, y, z, y % 4 === 0 ? P.pillar : P.wall, 1);
                }
            }
    // entrance corridor and chamber lights
    for (let z = 0; z <= 4; z++)
        for (let y = 1; y <= 3; y++)
            map.delete(`${c},${y},${z}`);
    for (let z = 0; z <= 4; z++) {
        for (const dx of [-1, 1])
            for (let y = 1; y <= 3; y++)
                if (!has(c + dx, y, z))
                    put(c + dx, y, z, P.wall, 1);
        put(c, 4, z, P.wall, 1);
    }
    door(put, wd, c, 1, 2, "pz");
    put(c, 0, -1, P.stairs, 3, { f: "pz", half: "bottom" });
    if (o.light !== false) {
        const h = Math.floor(N / 2) - 3;
        for (const [dx, dz] of [[-h, -h], [h, -h], [-h, h], [h, h]])
            put(c + dx, 1, c + dz + 1, M + "lantern", 4, { hanging: false });
    }
    if (o.furniture !== false) {
        const h = Math.floor(N / 2) - 3;
        const rr = rolesFor(1, 0);
        const reserved = new Set([`${c},5`, `${c},6`]);
        furnishRoom({
            y: 0, x0: c - h, x1: c + h, z0: 5, z1: c + h + 1, reserved, roles: rr.roles, beds: 2, carpet: M + "yellow_carpet",
            wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
        }, put, has);
    }
    return finalize(map, N, N, { doorX: c, fillBlock: P.wall });
}
// ------------------------------------------------------------------ real tree house (giant tree + cabin around the trunk)
function hash3(x, y, z) {
    let h = (Math.imul(x + 101, 73856093) ^ Math.imul(y + 57, 19349663) ^ Math.imul(z + 233, 83492791)) >>> 0;
    h ^= h >>> 13;
    h = Math.imul(h, 1274126177) >>> 0;
    return (h % 1000) / 1000;
}
function treehouse(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const W = dim(o.w, [9, 11, 13][s]);
    const D = W;
    const c = (W - 1) / 2;
    const PY = 8; // platform height
    const accent = wid === "dark_oak" || wid === "spruce" ? M + "oak_log" : M + "dark_oak_log";
    const roofStairs = wid === "spruce" || wid === "dark_oak" ? M + "oak_stairs" : M + "spruce_stairs";
    const roofFull = wid === "spruce" || wid === "dark_oak" ? M + "oak_planks" : M + "spruce_planks";
    const TOP = PY + 14;
    const { map, put, has } = ctx();
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const isCorner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    // platform: cabin floor + wrap-around balcony (one block wide) + front deck
    for (let x = -1; x <= W; x++)
        for (let z = -1; z <= D; z++)
            put(x, PY, z, x >= 0 && x < W && z >= 0 && z < D ? (isPerim(x, z) ? accent : wd.planks) : wd.planks, 1, isPerim(x, z) && x >= 0 && x < W && z >= 0 && z < D ? { axis: x === 0 || x === W - 1 ? "z" : "x" } : undefined);
    for (let x = c - 3; x <= c + 3; x++)
        for (let z = -3; z <= -2; z++)
            put(x, PY, z, wd.planks, 1);
    // railings
    for (let x = -1; x <= W; x++)
        for (const z of [-1, D])
            put(x, PY + 1, z, wd.fence, 3);
    for (let z = 0; z < D; z++)
        for (const x of [-1, W])
            put(x, PY + 1, z, wd.fence, 3);
    for (let x = c - 3; x <= c + 3; x++)
        if (x !== c)
            put(x, PY + 1, -3, wd.fence, 3);
    for (const z of [-3, -2])
        for (const x of [c - 3, c + 3])
            put(x, PY + 1, z, wd.fence, 3);
    // the balcony fence on the front line (z = -1) would block the front deck: open it
    for (let x = c - 3; x <= c + 3; x++)
        map.delete(`${x},${PY + 1},-1`);
    // supports
    const posts = [[-1, -1], [W, -1], [-1, D], [W, D], [c - 3, -3], [c + 3, -3], [c, -3], [-1, c], [W, c], [c, D]];
    for (const [x, z] of posts)
        for (let y = 0; y < PY; y++)
            put(x, y, z, wd.log, 1, { axis: "y" });
    // braces under the platform
    for (const [x, z, dx, dz] of [[-1, -1, 1, 1], [W, -1, -1, 1], [-1, D, 1, -1], [W, D, -1, -1]])
        for (let i = 1; i <= 2; i++)
            put(x + dx * i, PY - 1 - (i - 1) * 1 - 0, z + dz * i, wd.stairs, 3, { f: dx > 0 ? "px" : "nx", half: "top" }, { opt: true });
    // ladder up to the deck
    for (let y = 1; y <= PY + 1; y++)
        put(c, y, -4, M + "ladder", 3, { f: "nz" });
    for (let y = 0; y < PY; y++)
        put(c, y, -3, wd.log, 1, { axis: "y" });
    // cabin walls
    for (let row = 1; row <= 3; row++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (!isPerim(x, z))
                    continue;
                const y = PY + row;
                if (isCorner(x, z)) {
                    put(x, y, z, accent, 1, { axis: "y" });
                    continue;
                }
                let win = false;
                if (row === 2) {
                    if (z === 0)
                        win = Math.abs(x - c) >= 2 && x >= 2 && x <= W - 3 && (x - c) % 2 === 0;
                    else if (z === D - 1)
                        win = x >= 2 && x <= W - 3 && (x - c) % 2 === 0;
                    else
                        win = z >= 2 && z <= D - 3 && z % 2 === 0;
                }
                put(x, y, z, win ? M + "glass_pane" : wd.planks, 1);
            }
    // ceiling + hip roof with eaves
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, PY + 4, z, isPerim(x, z) ? accent : wd.planks, 1, isPerim(x, z) ? { axis: x === 0 || x === W - 1 ? "z" : "x" } : undefined);
    pyramidRoof(put, -1, W, -1, D, PY + 5, { stairs: roofStairs, full: roofFull });
    // door
    door(put, wd, c, PY + 1, 0, "pz");
    // trunk: a thick base that goes through the cabin and the roof, then thins out
    for (let y = 0; y <= TOP; y++) {
        const r = y < PY + 5 ? 1 : y < PY + 9 ? 1 : 0;
        for (let dx = -r; dx <= r; dx++)
            for (let dz = -r; dz <= r; dz++)
                put(c + dx, y, c + dz, wd.log, 1, { axis: "y" });
    }
    // roots
    for (const [dx, dz] of [[-2, 0], [2, 0], [0, -2], [0, 2], [-2, -1], [2, 1], [-1, 2], [1, -2]])
        for (let y = 0; y <= 1; y++)
            put(c + dx, y, c + dz, wd.log, 1, { axis: "y" });
    // big branches
    for (let k = 3; k <= 7; k++) {
        put(c + k, PY + 8, c, wd.log, 1, { axis: "x" });
        put(c - k, PY + 8, c, wd.log, 1, { axis: "x" });
        put(c, PY + 9, c + k, wd.log, 1, { axis: "z" });
        put(c, PY + 9, c - k, wd.log, 1, { axis: "z" });
    }
    // canopy
    const rad = [5, 7, 8, 8, 7, 6, 4, 2];
    const leaf = wd.leaves;
    const inReserved = (x, y, z) => {
        if (x >= -1 && x <= W && z >= -1 && z <= D && y >= PY + 1 && y <= PY + 4)
            return true; // cabin + balcony air
        if (x >= c - 4 && x <= c + 4 && z >= -5 && z <= 0 && y >= 1 && y <= PY + 4)
            return true; // front deck + ladder
        return false;
    };
    for (let i = 0; i < rad.length; i++) {
        const y = PY + 6 + i;
        for (let dx = -rad[i]; dx <= rad[i]; dx++)
            for (let dz = -rad[i]; dz <= rad[i]; dz++) {
                const d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad[i] + 0.2 - hash3(dx, y, dz) * 1.6)
                    continue;
                const x = c + dx, z = c + dz;
                if (has(x, y, z) || inReserved(x, y, z))
                    continue;
                put(x, y, z, leaf, 4);
            }
    }
    // hanging leaf clumps around the platform
    for (let x = -4; x <= W + 3; x++)
        for (let z = -2; z <= D + 3; z++) {
            const outside = x < -1 || x > W || z > D;
            if (!outside)
                continue;
            for (let y = PY - 2; y <= PY + 3; y++) {
                if (hash3(x, y, z) > 0.14 || has(x, y, z) || inReserved(x, y, z))
                    continue;
                if (Math.abs(x - c) <= 4 && z < 0)
                    continue;
                put(x, y, z, leaf, 4);
            }
        }
    // lanterns
    for (const [x, z] of [[-1, -1], [W, -1], [-1, D], [W, D]]) {
        put(x, PY + 2, z, wd.fence, 3);
        put(x, PY + 3, z, M + "lantern", 4, { hanging: false });
    }
    for (const x of [c - 3, c + 3])
        put(x, PY + 2, -3, M + "lantern", 4, { hanging: false });
    if (o.light !== false) {
        for (const z of [Math.floor(D / 3), Math.floor((2 * D) / 3)])
            for (const x of [Math.floor(W / 4), Math.ceil((3 * W) / 4) - 1])
                put(x, PY + 3, z, M + "lantern", 4, { hanging: true });
    }
    if (o.furniture !== false) {
        const rr = rolesFor(1, 0);
        furnishRoom({
            y: PY, x0: 1, x1: W - 2, z0: 1, z1: D - 2, reserved: new Set([`${c},1`, `${c},2`]), roles: rr.roles, beds: 2,
            carpet: M + "green_carpet", wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
        }, put, has);
    }
    return finalize(map, W, D, { fillBlock: wd.log, clearArea: { x0: -2, x1: W + 1, z0: -4, z1: D + 1 } });
}
// ------------------------------------------------------------------ luxury mansion
function mansion(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const P0 = palette(o, "mansion", wid, wd);
    const quartz = P0.wall === M + "quartz_block";
    const WALL = quartz ? M + "smooth_quartz" : P0.wall;
    const PIL = quartz ? M + "quartz_pillar" : P0.pillar;
    const STAIRS = P0.stairs;
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const W = dim(o.w, [11, 15, 19][s]);
    const D = dim(o.d, [11, 13, 15][s]);
    const F = clamp(Math.floor(o.floors ?? 2), 1, 4);
    const c = (W - 1) / 2;
    const yf = (f) => 4 * f;
    const topY = yf(F);
    const { map, put, has, del } = ctx();
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const isCorner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    for (let f = 0; f < F; f++) {
        const y0 = yf(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++)
                put(x, y0, z, isPerim(x, z) ? (isCorner(x, z) ? PIL : WALL) : wd.planks, 1);
        for (let row = 1; row <= 3; row++)
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++) {
                    if (!isPerim(x, z))
                        continue;
                    const y = y0 + row;
                    const onFrontBack = z === 0 || z === D - 1;
                    const pillarCol = onFrontBack ? (x - c) % 4 === 0 : (z % 4 === 0);
                    if (isCorner(x, z) || pillarCol) {
                        put(x, y, z, PIL, 1);
                        continue;
                    }
                    const win = row >= 2 && (onFrontBack ? x >= 1 && x <= W - 2 : z >= 1 && z <= D - 2);
                    put(x, y, z, win ? M + "glass_pane" : WALL, 1);
                }
    }
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, topY, z, isPerim(x, z) ? WALL : wd.planks, 1);
    // dark hip roof with deep eaves
    pyramidRoof(put, -1, W, -1, D, topY + 1, { stairs: M + "deepslate_tile_stairs", full: M + "deepslate_tiles" });
    // grand staircase along the left wall
    for (let f = 0; f < F - 1; f++) {
        del(1, yf(f + 1), 3);
        del(1, yf(f + 1), 4);
        for (let k = 0; k < 4; k++)
            put(1, yf(f) + 1 + k, 2 + k, STAIRS, 3, { f: "pz", half: "bottom" });
    }
    // portico with four columns, balcony on top
    const colX = [c - 4, c - 2, c + 2, c + 4].filter((x) => x >= 1 && x <= W - 2);
    const pw = Math.min(c - 1, 5);
    for (let x = c - pw; x <= c + pw; x++)
        for (let z = -3; z <= -1; z++) {
            put(x, 0, z, WALL, 1);
            put(x, 4, z, WALL, 1);
        }
    for (const x of colX)
        for (let y = 1; y <= 3; y++)
            put(x, y, -3, PIL, 1);
    for (let x = c - pw; x <= c + pw; x++)
        put(x, 5, -3, M + "iron_bars", 3);
    for (const x of [c - pw, c + pw])
        for (const z of [-2, -1])
            put(x, 5, z, M + "iron_bars", 3);
    for (let x = c - 1; x <= c + 1; x++)
        put(x, 0, -4, STAIRS, 3, { f: "pz", half: "bottom" });
    // doors
    door(put, wd, c, 1, 0, "pz");
    if (F >= 2)
        door(put, wd, c, 5, 0, "pz");
    // lights
    if (o.light !== false) {
        for (let f = 0; f < F; f++)
            for (const z of [Math.floor(D / 3), Math.floor((2 * D) / 3)])
                for (const x of [c - 3, c, c + 3]) {
                    if (x > 2)
                        put(x, yf(f) + 4, z, M + "sea_lantern", 4);
                }
        for (const x of colX)
            put(x, 4, -2, M + "lantern", 4, { hanging: true });
    }
    // garden: path, lamp posts, hedges, optional pool behind
    if (o.porch !== false) {
        for (let z = -10; z <= -5; z++)
            for (let x = c - 1; x <= c + 1; x++)
                put(x, -1, z, M + "stone_bricks", 1, undefined, { opt: true });
        for (const z of [-6, -10])
            for (const x of [c - 3, c + 3]) {
                put(x, 0, z, M + "stone_brick_wall", 3, undefined, { opt: true });
                put(x, 1, z, M + "stone_brick_wall", 3, undefined, { opt: true });
                put(x, 2, z, M + "lantern", 4, { hanging: false });
            }
        for (let x = c - 7; x <= c + 7; x++) {
            if (Math.abs(x - c) <= 2)
                continue;
            put(x, 0, -11, wd.leaves, 4);
            put(x, 1, -11, wd.leaves, 4);
        }
        for (let x = c - 7; x <= c + 7; x += 7)
            for (let y = 0; y <= 1; y++)
                put(x, y, -11, wd.fence, 3, undefined, { opt: true });
    }
    if (o.pool !== false) {
        const px0 = 3, px1 = W - 4, pz0 = D + 1, pz1 = D + 5;
        for (let x = px0 - 1; x <= px1 + 1; x++)
            for (let z = pz0 - 1; z <= pz1 + 1; z++) {
                const edge = x === px0 - 1 || x === px1 + 1 || z === pz0 - 1 || z === pz1 + 1;
                if (edge) {
                    put(x, -1, z, M + "smooth_quartz", 1, undefined, { tag: "pool", opt: true });
                }
                else {
                    put(x, -2, z, M + "smooth_quartz", 1, undefined, { tag: "pool", opt: true });
                    put(x, -1, z, M + "water", 6, undefined, { tag: "pool", opt: true, item: null });
                }
            }
        for (let x = px0 - 1; x <= px1 + 1; x++)
            put(x, -1, D, M + "smooth_quartz", 1, undefined, { tag: "pool", opt: true });
    }
    if (o.furniture !== false) {
        for (let f = 0; f < F; f++) {
            const reserved = new Set();
            if (f === 0) {
                reserved.add(`${c},1`);
                reserved.add(`${c},2`);
            }
            for (let z = 1; z <= 7; z++) {
                reserved.add(`1,${z}`);
                reserved.add(`2,${z}`);
            }
            const rr = rolesForFloor(F, f);
            furnishRoom({
                y: yf(f), x0: 1, x1: W - 2, z0: 1, z1: D - 2, reserved, roles: rr.roles, beds: Math.min(rr.beds + (f === F - 1 ? 1 : 0), 3),
                carpet: M + "red_carpet", wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        }
    }
    return finalize(map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -1, x1: W, z0: -1, z1: D } });
}
// ------------------------------------------------------------------ safe house (monster proof)
function safehouse(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const P = palette(o, "safehouse", wid, wd);
    const s = clamp(Math.floor(o.size ?? 0), 0, 2);
    const W = dim(o.w, [7, 9, 11][s]);
    const D = dim(o.d, [7, 9, 11][s]);
    const F = clamp(Math.floor(o.floors ?? 1), 1, 3);
    const c = (W - 1) / 2;
    const yf = (f) => 4 * f;
    const topY = yf(F);
    const { map, put, has } = ctx();
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const INNER = M + "stone_bricks";
    const ladderX = W - 2, ladderZ = D - 2;
    for (let f = 0; f < F; f++) {
        const y0 = yf(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === ladderX && z === ladderZ)
                    continue;
                put(x, y0, z, isPerim(x, z) ? P.wall : wd.planks, 1);
            }
        for (let row = 1; row <= 3; row++)
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++) {
                    if (!isPerim(x, z))
                        continue;
                    const slit = row === 2 && ((z === 0 || z === D - 1) ? (x - c) % 2 === 0 && Math.abs(x - c) >= 2 : z % 2 === 0 && z >= 2 && z <= D - 3);
                    put(x, y0 + row, z, slit ? M + "iron_bars" : P.wall, 1);
                }
    }
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, topY, z, isPerim(x, z) ? P.wall : P.wall, 1);
    // low parapet (nothing can spawn if the roof is lit)
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            if (isPerim(x, z))
                put(x, topY + 1, z, P.slab, 2);
    for (const [x, z] of [[1, 1], [W - 2, 1], [1, D - 2], [W - 2, D - 2], [c, Math.floor(D / 2)]])
        put(x, topY + 1, z, M + "sea_lantern", 4);
    // airlock: outer door -> 1 cell -> inner door
    put(c - 1, 1, 1, P.wall, 1);
    for (let y = 1; y <= 3; y++)
        for (const x of [c - 1, c + 1])
            for (const z of [1, 2])
                put(x, y, z, P.wall, 1);
    for (let x = c - 1; x <= c + 1; x++)
        put(x, 4, 1, P.wall, 1);
    door(put, wd, c, 1, 0, "pz");
    door(put, wd, c, 1, 2, "pz");
    put(c, 3, 1, M + "lantern", 4, { hanging: true });
    put(c, 0, -1, P.stairs, 3, { f: "pz", half: "bottom" });
    for (let f = 0; f < F - 1; f++)
        for (let y = yf(f) + 1; y <= yf(f + 1); y++)
            put(ladderX, y, ladderZ, M + "ladder", 3, { f: "nz" });
    // every room fully lit
    for (let f = 0; f < F; f++)
        for (const z of [Math.floor(D / 3) + 1, Math.floor((2 * D) / 3)])
            for (const x of W >= 9 ? [2, W - 3] : [c])
                if (z > 2)
                    put(x, yf(f) + 3, z, M + "lantern", 4, { hanging: true });
    // ring of buried lights around the house: hostile mobs cannot spawn nearby
    const lights = [];
    for (let x = -4; x <= W + 3; x += 4)
        lights.push([x, -4], [x, D + 3]);
    for (let z = -4; z <= D + 3; z += 4)
        lights.push([-4, z], [W + 3, z]);
    for (const [x, z] of lights)
        put(x, -1, z, M + "sea_lantern", 4, undefined, { opt: true });
    if (o.furniture !== false) {
        for (let f = 0; f < F; f++) {
            const reserved = new Set();
            if (f === 0)
                for (let z = 1; z <= 4; z++)
                    reserved.add(`${c},${z}`);
            if (F > 1) {
                reserved.add(`${ladderX},${ladderZ}`);
                reserved.add(`${ladderX},${ladderZ - 1}`);
            }
            const rr = f === 0 && F === 1 ? { roles: ["storage", "kitchen", "workshop", "bedroom", "storage", "brewing"], beds: 2 } : rolesForFloor(F, f);
            furnishRoom({
                y: yf(f), x0: 1, x1: W - 2, z0: 3, z1: D - 2, reserved, roles: rr.roles, beds: Math.min(rr.beds, 3),
                carpet: M + "gray_carpet", wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        }
    }
    return finalize(map, W, D, { fillBlock: INNER, clearArea: { x0: -4, x1: W + 3, z0: -4, z1: D + 3 } });
}
// ------------------------------------------------------------------ lighthouse
function lighthouse(o) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    const r0 = [4, 5, 6][s];
    const H = Math.max(12, Math.min(60, Math.round(o.height ?? (o.floorsExact && o.floors ? o.floors * 5 : [14, 18, 22][s]))));
    const W = 2 * r0 + 1;
    const D = W;
    const cx = r0, cz = r0;
    const { map, put, has } = ctx();
    const WHITE = M + "quartz_block", RED = M + "red_concrete";
    const radAt = (y) => r0 - Math.floor((y * 2) / H);
    for (let y = 0; y <= H; y++) {
        const r = radAt(y);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                const d = Math.sqrt((x - cx) ** 2 + (z - cz) ** 2);
                if (d <= r + 0.3 && d > r - 1) {
                    if (y === 0 || y === H)
                        continue;
                    const band = Math.floor((y - 1) / 3) % 2 === 0;
                    put(x, y, z, band ? WHITE : RED, 1);
                }
            }
    }
    // floors every 5 blocks (hole for the ladder in the middle)
    for (let y = 0; y <= H; y += 5) {
        const r = radAt(y);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                const d = Math.sqrt((x - cx) ** 2 + (z - cz) ** 2);
                if (d > r + 0.3)
                    continue;
                if (y > 0 && y < H && x === cx && z === cz)
                    continue;
                if (y === 0 || d <= r - 1)
                    put(x, y, z, y === 0 ? wd.planks : wd.planks, 1);
            }
    }
    // central ladder column
    for (let y = 1; y <= H - 1; y++) {
        put(cx, y, cz + 1, M + "stone_bricks", 1);
        put(cx, y, cz, M + "ladder", 3, { f: "nz" });
    }
    // lamp room
    const rl = radAt(H - 3);
    for (let y = H - 3; y <= H - 1; y++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                const d = Math.sqrt((x - cx) ** 2 + (z - cz) ** 2);
                if (d <= rl + 0.3 && d > rl - 1)
                    put(x, y, z, M + "glass", 1);
            }
    for (let y = H - 3; y <= H - 2; y++)
        put(cx, y, cz - 1, M + "sea_lantern", 4);
    put(cx, H - 2, cz + 1, M + "sea_lantern", 4);
    // gallery ring + cone roof
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            const d = Math.sqrt((x - cx) ** 2 + (z - cz) ** 2);
            if (d <= rl + 1.3)
                put(x, H - 4 + 0, z, d > rl + 0.3 ? M + "quartz_slab" : WHITE, 2, d > rl + 0.3 ? { half: "bottom" } : undefined);
            if (d <= rl + 1.3 && d > rl + 0.3)
                put(x, H - 3, z, M + "iron_bars", 3);
            if (d <= rl + 0.8)
                put(x, H, z, RED, 2);
            if (d <= rl - 0.5)
                put(x, H + 1, z, M + "red_concrete", 2);
            if (d <= rl - 1.5)
                put(x, H + 2, z, M + "red_concrete", 2);
        }
    put(cx, H + 3, cz, M + "lantern", 4, { hanging: false });
    // door + step
    for (let y = 1; y <= 2; y++)
        map.delete(`${cx},${y},0`);
    door(put, wd, cx, 1, 0, "pz");
    put(cx, 0, -1, M + "quartz_stairs", 3, { f: "pz", half: "bottom" });
    if (o.light !== false)
        for (let y = 3; y < H - 4; y += 5)
            put(cx - 1, y, cz - 1, M + "lantern", 4, { hanging: false });
    return finalize(map, W, D, { fillBlock: M + "stone_bricks", doorX: cx });
}
export function generateExtra(o) {
    switch (o.type) {
        case "underground": return variantOf(o) % BUNKER_VARIANTS.length === 0 ? underground(o) : generateBunker(o);
        case "statue":
        case "portal": return generateStatue(o);
        case "igloo": return igloo(o);
        case "pyramid": return pyramid(o);
        case "treehouse": return treehouse(o);
        case "mansion": return mansion(o);
        case "safehouse": return safehouse(o);
        case "lighthouse": return lighthouse(o);
        default:
            if (isMoreType(o.type))
                return generateMore(o);
            return boxHouse(o, o.type);
    }
}
