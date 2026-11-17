// Shared helpers for the v1.6 building generators (pure functions, no Minecraft imports).
// Local coordinates: x = left->right seen from the front, z = front->back, y = up, y = 0 is one block above the ground.
// The surface (top ground block) is y = -1. Underground parts use negative y.
export const M = "minecraft:";
export const WOOD_IDS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"];
const MAT_IDS = ["auto", "stone", "wood", "brick", "sand", "deepslate", "blackstone", "quartz", "mud", "prismarine"];
export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const odd = (n) => (n % 2 === 0 ? n + 1 : n);
/** Size override from the player ("กว้าง 12 ยาว 16"); always odd so the door sits in the middle. */
export const dim = (v, def, lo = 5, hi = 41) => (typeof v === "number" && Number.isFinite(v) ? odd(clamp(Math.round(v), lo, hi)) : def);
/** Variant number of a design (picked at random when the job is made, or chosen by the player). */
export function variantOf(o) {
    return typeof o.variant === "number" && Number.isFinite(o.variant) ? Math.abs(Math.floor(o.variant)) : 0;
}
export function woodSet(w) {
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
const PAL = {
    stone: { wall: "stone_bricks", pillar: "chiseled_stone_bricks", stairs: "stone_brick_stairs", slab: "stone_brick_slab", floor: "polished_andesite" },
    brick: { wall: "brick_block", pillar: "stone_bricks", stairs: "brick_stairs", slab: "brick_slab", floor: "stone_bricks" },
    sand: { wall: "cut_sandstone", pillar: "chiseled_sandstone", stairs: "sandstone_stairs", slab: "sandstone_slab", floor: "smooth_sandstone" },
    deepslate: { wall: "deepslate_bricks", pillar: "polished_deepslate", stairs: "deepslate_brick_stairs", slab: "deepslate_brick_slab", floor: "polished_deepslate" },
    blackstone: { wall: "polished_blackstone_bricks", pillar: "chiseled_polished_blackstone", stairs: "polished_blackstone_brick_stairs", slab: "polished_blackstone_brick_slab", floor: "polished_blackstone" },
    quartz: { wall: "quartz_block", pillar: "chiseled_quartz_block", stairs: "quartz_stairs", slab: "quartz_slab", floor: "smooth_quartz" },
    mud: { wall: "mud_bricks", pillar: "packed_mud", stairs: "mud_brick_stairs", slab: "mud_brick_slab", floor: "packed_mud" },
    prismarine: { wall: "prismarine_bricks", pillar: "dark_prismarine", stairs: "prismarine_brick_stairs", slab: "prismarine_brick_slab", floor: "prismarine" },
};
/** Wall palette. `def` is the material used when the player did not choose one. */
export function palette(o, def, wid, wd) {
    let m = MAT_IDS[o.mat ?? 0] ?? "auto";
    if (m === "auto")
        m = def;
    if (m === "wood") {
        const logId = wid === "oak" || wid === "birch" ? "spruce" : "dark_oak";
        return { wall: wd.planks, pillar: M + logId + "_log", stairs: wd.stairs, slab: wd.slab, floor: wd.planks, wood: true };
    }
    const q = PAL[m] ?? PAL.stone;
    return { wall: M + q.wall, pillar: M + q.pillar, stairs: M + q.stairs, slab: M + q.slab, floor: M + q.floor, wood: false };
}
export function ctx() {
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
export function door(put, wd, x, y, z, f) {
    put(x, y, z, wd.door, 3, { f, upper: false, hinge: false }, { item: wd.door });
    put(x, y + 1, z, wd.door, 3, { f, upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
}
export function ironDoor(put, x, y, z, f) {
    put(x, y, z, M + "iron_door", 3, { f, upper: false, hinge: false }, { item: M + "iron_door" });
    put(x, y + 1, z, M + "iron_door", 3, { f, upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
}
/** A bed whose head is at (hx,hz) and whose foot is one cell away: `toHead` is the direction foot -> head. */
export function bed(put, hx, y, hz, toHead) {
    const d = { px: [1, 0], nx: [-1, 0], pz: [0, 1], nz: [0, -1] }[toHead];
    put(hx - d[0], y, hz - d[1], M + "bed", 5, { f: toHead, head: false }, { item: M + "bed", opt: true });
    put(hx, y, hz, M + "bed", 5, { f: toHead, head: true }, { item: null, dep: [-d[0], 0, -d[1]], opt: true });
}
export function box(put, x0, x1, y0, y1, z0, z1, id, ph = 1, st) {
    for (let x = x0; x <= x1; x++)
        for (let y = y0; y <= y1; y++)
            for (let z = z0; z <= z1; z++)
                put(x, y, z, id, ph, st);
}
export function ring(put, x0, x1, z0, z1, y, id, ph = 1) {
    for (let x = x0; x <= x1; x++)
        for (let z = z0; z <= z1; z++)
            if (x === x0 || x === x1 || z === z0 || z === z1)
                put(x, y, z, id, ph);
}
export function finalize(map, W, D, base) {
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
/** Gable roof: the ridge runs along z, the slopes go down toward x = 0 and x = W-1. */
export function gableRoof(put, W, D, y, stairs, full, gableWall) {
    for (let i = 0; i <= (W - 1) / 2; i++) {
        const a = i, b = W - 1 - i, yy = y + i;
        for (let z = 0; z < D; z++) {
            if (a === b)
                put(a, yy, z, full, 2);
            else {
                put(a, yy, z, stairs, 2, { f: "px", half: "bottom" });
                put(b, yy, z, stairs, 2, { f: "nx", half: "bottom" });
            }
        }
        for (let x = a + 1; x <= b - 1; x++) {
            put(x, yy, 0, gableWall, 1);
            put(x, yy, D - 1, gableWall, 1);
        }
    }
}
/** Hip roof (hollow) that closes into a ridge or a point. */
export function hipRoof(put, x0, x1, z0, z1, y, stairs, full) {
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
                    put(x, yy, z, full, 2);
                else if (z === c)
                    put(x, yy, z, stairs, 2, { f: "pz", half: "bottom" });
                else if (z === d)
                    put(x, yy, z, stairs, 2, { f: "nz", half: "bottom" });
                else if (x === a)
                    put(x, yy, z, stairs, 2, { f: "px", half: "bottom" });
                else
                    put(x, yy, z, stairs, 2, { f: "nx", half: "bottom" });
            }
    }
}
/** Vertical-shaft style stairs from the surface down to a door in the front wall (z = 0) of an underground room. */
export function entranceStairs(S, o) {
    const { put, clear } = S;
    const { c, yTop, P, wd, iron, lights } = o;
    const steps = -1 - yTop; // surface y = -1
    const zs = -1 - steps;
    for (let k = 0; k <= steps; k++) {
        const z = zs + k;
        const y = -1 - k;
        put(c, y, z, P.stairs, 1, { f: "nz", half: "bottom" });
        for (let yy = y + 1; yy <= y + 3; yy++)
            clear.push({ x: c, y: yy, z });
        if (lights && k % 2 === 1) {
            put(c - 1, y + 2, z, M + "sea_lantern", 4);
            put(c + 1, y + 2, z, M + "sea_lantern", 4);
        }
    }
    // opening in the front wall of the room
    if (iron)
        ironDoor(put, c, yTop + 1, 0, "pz");
    else
        door(put, wd, c, yTop + 1, 0, "pz");
    // surface gateway: two posts and a lintel over the stair top
    for (const dx of [-1, 1])
        for (let y = 0; y <= 2; y++)
            put(c + dx, y, zs, P.pillar, 3, P.wood ? { axis: "y" } : undefined);
    for (let dx = -1; dx <= 1; dx++)
        put(c + dx, 3, zs, P.wall, 3);
    put(c, 2, zs, M + "lantern", 4, { hanging: true });
}
