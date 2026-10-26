// v1.6: underground bunker variants (variant 0 is the classic cellar in house_extra.js).
// 1 = military corridor bunker, 2 = round cavern hall, 3 = vault, 4 = deep silo.
import { M, WOOD_IDS, bed, clamp, ctx, dim, door, entranceStairs, finalize, ironDoor, palette, variantOf, woodSet } from "./house_common.js";
import { furnishRoom } from "./interior.js";
export const BUNKER_VARIANTS = ["ห้องใต้ดินคลาสสิก", "บังเกอร์ทหาร", "โถงถ้ำทรงกลม", "ห้องนิรภัย", "ไซโลหลุมลึก"];
const CARPET = M + "gray_carpet";
function base(o, def) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    return { wid, wd, P: palette(o, def, wid, wd), s: clamp(Math.floor(o.size ?? 1), 0, 2) };
}
const wooden = (wd) => ({ fence: wd.fence, plate: wd.plate, stairs: wd.stairs });
export function generateBunker(o) {
    switch (variantOf(o) % BUNKER_VARIANTS.length) {
        case 1: return military(o);
        case 2: return cavern(o);
        case 3: return vault(o);
        case 4: return silo(o);
        default: return military(o);
    }
}
// ------------------------------------------------------------------ 1. military corridor bunker
function military(o) {
    const { wd, P, s } = base(o, "stone");
    const W = dim(o.w, [13, 15, 17][s], 13, 31);
    const D = dim(o.d, [19, 25, 31][s], 15, 41);
    const F = clamp(Math.floor(o.floors ?? 1), 1, 3);
    const c = (W - 1) / 2;
    const { map, put, has } = ctx();
    const clear = [];
    const y0f = (f) => -2 - 4 * (F - f);
    const perim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const corner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const ladZ = D - 2;
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            for (let y = y0f(0); y <= -2; y++)
                clear.push({ x, y, z });
    for (let f = 0; f < F; f++) {
        const y0 = y0f(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === c && z === ladZ)
                    continue;
                put(x, y0, z, perim(x, z) ? P.wall : P.floor, 1);
            }
        for (let row = 1; row <= 3; row++)
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++)
                    if (perim(x, z))
                        put(x, y0 + row, z, corner(x, z) ? P.pillar : P.wall, 1, corner(x, z) && P.wood ? { axis: "y" } : undefined);
        // corridor partitions
        for (const px of [c - 2, c + 2])
            for (let z = 1; z <= D - 2; z++)
                for (let row = 1; row <= 3; row++)
                    put(px, y0 + row, z, P.wall, 1);
        // rooms of 3 x 3 on both sides
        const seq = [["bedroom"], ["kitchen"], ["workshop"], ["storage", "storage"], ["brewing", "storage"], ["bedroom"], ["workshop", "storage"]];
        let k = 0;
        for (let z = 1; z + 2 <= D - 3; z += 4, k++) {
            const zm = z + 1;
            const nextExists = z + 4 + 2 <= D - 3;
            for (const side of [0, 1]) {
                const x0 = side === 0 ? 1 : c + 3;
                const x1 = side === 0 ? c - 3 : W - 2;
                if (nextExists || true)
                    for (let x = x0; x <= x1; x++)
                        for (let row = 1; row <= 3; row++)
                            put(x, y0 + row, z + 3, P.wall, 1);
                const doorX = side === 0 ? c - 2 : c + 2;
                ironDoor(put, doorX, y0 + 1, zm, side === 0 ? "px" : "nx");
                const roles = seq[(k * 2 + side) % seq.length];
                const reserved = new Set([`${side === 0 ? c - 3 : c + 3},${zm}`]);
                if (o.furniture !== false)
                    furnishRoom({
                        y: y0, x0, x1, z0: z, z1: z + 2, reserved, roles, beds: roles.includes("bedroom") ? 2 : 0,
                        carpet: CARPET, wood: wooden(wd),
                    }, put, has);
                if (o.light !== false)
                    put(Math.floor((x0 + x1) / 2), y0 + 4, zm, M + "sea_lantern", 4);
            }
            if (o.light !== false && k % 1 === 0)
                put(c, y0 + 4, zm, M + "sea_lantern", 4);
        }
        // end of the corridor: blast door + generator nook
        for (let x = c - 1; x <= c + 1; x++)
            for (let row = 1; row <= 3; row++)
                if (f === 0 && D >= 21)
                    put(x, y0 + row, D - 5, x === c && row <= 2 ? M + "iron_bars" : P.wall, 1);
        if (f === 0 && D >= 21) {
            ironDoor(put, c, y0 + 1, D - 5, "pz");
            for (let x = c - 1; x <= c + 1; x++)
                for (let z = D - 4; z <= D - 2; z++) {
                    put(x, y0 + 1, z, x === c ? M + "redstone_block" : M + "furnace", 5, x === c ? undefined : { f: "nz" }, { opt: true });
                }
            put(c, y0 + 4, D - 3, M + "sea_lantern", 4);
        }
    }
    for (let f = 0; f < F - 1; f++)
        for (let y = y0f(f) + 1; y <= y0f(f + 1); y++)
            put(c, y, ladZ, M + "ladder", 3, { f: "nz" });
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, -2, z, perim(x, z) ? P.wall : P.floor, 1);
    entranceStairs({ put, clear }, { c, yTop: y0f(F - 1), P, wd, iron: true, lights: o.light !== false });
    return finalize(map, W, D, { clearArea: { x0: 0, x1: -1, z0: 0, z1: -1 }, clearCells: clear, fillBlock: M + "cobblestone", underground: true });
}
// ------------------------------------------------------------------ circular helpers
/** Places "stations" (beds, chests, furnaces ...) around a circle. */
function ringStations(put, has, R, cx, cz, rad, y, items, inside, skipFront) {
    const n = items.length;
    for (let j = 0; j < n; j++) {
        const th = ((j + 0.5) / n) * Math.PI * 2; // 0 = toward +z (the back)
        if (skipFront && Math.abs(th - Math.PI) < 0.55)
            continue;
        const x = Math.round(cx + Math.sin(th) * rad);
        const z = Math.round(cz + Math.cos(th) * rad);
        const dx = cx - x, dz = cz - z;
        const f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "px" : "nx") : dz > 0 ? "pz" : "nz";
        const id = items[j];
        if (id === "bed") {
            const toHead = f === "px" ? "nx" : f === "nx" ? "px" : f === "pz" ? "nz" : "pz";
            const d = { px: [1, 0], nx: [-1, 0], pz: [0, 1], nz: [0, -1] }[toHead];
            if (inside(x, z) && inside(x - d[0], z - d[1]) && !has(x, y, z) && !has(x - d[0], y, z - d[1]))
                bed(put, x, y, z, toHead);
            continue;
        }
        if (!inside(x, z) || has(x, y, z))
            continue;
        const facing = ["furnace", "chest", "smoker", "blast_furnace", "anvil", "grindstone", "stonecutter_block", "loom", "lectern"].includes(id);
        put(x, y, z, M + id, 5, facing ? { f } : undefined, { opt: true });
    }
}
// ------------------------------------------------------------------ 2. round cavern hall
function cavern(o) {
    const { wd, P, s } = base(o, "deepslate");
    const R = typeof o.w === "number" ? clamp(Math.floor(o.w / 2), 6, 16) : [7, 9, 11][s];
    const W = 2 * R + 1, D = W, c = R;
    const { map, put, has } = ctx();
    const clear = [];
    const yF = -7;
    const dist = (x, z) => Math.hypot(x - R, z - R);
    const isWall = (x, z) => dist(x, z) > R - 1 && dist(x, z) <= R;
    const isIn = (x, z) => dist(x, z) <= R - 1;
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            const d = dist(x, z);
            if (d > R)
                continue;
            put(x, yF, z, isWall(x, z) ? P.wall : ((x + z) % 2 === 0 ? P.floor : P.wall), 1);
            put(x, -2, z, P.wall, 1);
            for (let y = yF + 1; y <= -3; y++) {
                if (isWall(x, z))
                    put(x, y, z, P.wall, 1);
                else
                    clear.push({ x, y, z });
            }
        }
    // columns around the hall
    for (let j = 0; j < 8; j++) {
        const th = (j / 8) * Math.PI * 2 + Math.PI / 8;
        const x = Math.round(R + Math.sin(th) * (R - 3));
        const z = Math.round(R + Math.cos(th) * (R - 3));
        if (!isIn(x, z))
            continue;
        for (let y = yF + 1; y <= -3; y++)
            put(x, y, z, P.pillar, 1, P.wood ? { axis: "y" } : undefined);
    }
    // fountain in the middle: rim at y = yF+1, water inside
    for (let x = R - 3; x <= R + 3; x++)
        for (let z = R - 3; z <= R + 3; z++) {
            const d = dist(x, z);
            if (d <= 1.2)
                put(x, yF + 1, z, M + "water", 3, undefined, { item: null });
            else if (d <= 2.3)
                put(x, yF + 1, z, P.pillar, 1, P.wood ? { axis: "y" } : undefined);
        }
    put(R, yF, R, M + "sea_lantern", 4);
    if (o.light !== false) {
        for (let j = 0; j < 8; j++) {
            const th = (j / 8) * Math.PI * 2;
            const x = Math.round(R + Math.sin(th) * (R / 2));
            const z = Math.round(R + Math.cos(th) * (R / 2));
            put(x, -2, z, M + "sea_lantern", 4);
        }
        put(R, -2, R, M + "sea_lantern", 4);
    }
    if (o.furniture !== false) {
        const items = ["bed", "chest", "crafting_table", "bed", "furnace", "barrel", "anvil", "bed", "brewing_stand", "chest", "smoker", "bed", "lectern", "barrel", "grindstone", "stonecutter_block"];
        ringStations(put, has, R, R, R, R - 2, yF + 1, items, isIn, true);
    }
    entranceStairs({ put, clear }, { c, yTop: yF, P, wd, iron: false, lights: o.light !== false });
    // the opening of the door is inside the wall cell (c, 0)
    return finalize(map, W, D, { clearArea: { x0: 0, x1: -1, z0: 0, z1: -1 }, clearCells: clear, fillBlock: M + "cobblestone", underground: true });
}
// ------------------------------------------------------------------ 3. vault
function vault(o) {
    const { wd, P, s } = base(o, "blackstone");
    const W = dim(o.w, [13, 15, 17][s], 11, 31);
    const D = dim(o.d, [21, 25, 29][s], 17, 41);
    const c = (W - 1) / 2;
    const { map, put, has } = ctx();
    const clear = [];
    const y0 = -6;
    const perim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const corner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const sepZ = Math.floor(D / 2) - 1; // wall with the vault door
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            for (let y = y0; y <= -2; y++)
                clear.push({ x, y, z });
            put(x, y0, z, perim(x, z) ? P.wall : z > sepZ ? P.floor : wd.planks, 1);
            put(x, -2, z, perim(x, z) ? P.wall : P.floor, 1);
        }
    for (let row = 1; row <= 3; row++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++)
                if (perim(x, z))
                    put(x, y0 + row, z, corner(x, z) ? P.pillar : P.wall, 1, corner(x, z) && P.wood ? { axis: "y" } : undefined);
    // vault wall: solid, bars around the door
    for (let x = 1; x <= W - 2; x++)
        for (let row = 1; row <= 3; row++)
            put(x, y0 + row, sepZ, Math.abs(x - c) <= 2 && row <= 3 ? M + "iron_bars" : P.wall, 1);
    ironDoor(put, c, y0 + 1, sepZ, "pz");
    put(c - 1, y0 + 1, sepZ, P.wall, 1);
    put(c + 1, y0 + 1, sepZ, P.wall, 1);
    put(c - 1, y0 + 2, sepZ, P.wall, 1);
    put(c + 1, y0 + 2, sepZ, P.wall, 1);
    put(c - 1, y0 + 3, sepZ, P.wall, 1);
    put(c + 1, y0 + 3, sepZ, P.wall, 1);
    for (const x of [c - 2, c + 2])
        for (let row = 1; row <= 3; row++)
            put(x, y0 + row, sepZ, M + "iron_bars", 1);
    // the inner sanctum: gold strip, display blocks and chests
    for (let z = sepZ + 1; z <= D - 2; z++)
        put(c, y0, z, M + "gold_block", 1);
    const shows = [M + "diamond_block", M + "emerald_block", M + "gold_block", M + "netherite_block", M + "lapis_block", M + "iron_block"];
    let si = 0;
    for (let z = sepZ + 3; z <= D - 4; z += 3)
        for (const x of [c - 3, c + 3]) {
            if (x < 2 || x > W - 3)
                continue;
            put(x, y0 + 1, z, shows[si++ % shows.length], 5, undefined, { opt: true });
            put(x, y0 + 2, z, M + "lantern", 4, { hanging: false });
        }
    for (let x = 2; x <= W - 3; x += 2)
        put(x, y0 + 1, D - 2, M + "chest", 5, { f: "nz" }, { opt: true });
    for (let z = sepZ + 2; z <= D - 3; z += 2) {
        put(1, y0 + 1, z, M + "chest", 5, { f: "px" }, { opt: true });
        put(W - 2, y0 + 1, z, M + "chest", 5, { f: "nx" }, { opt: true });
    }
    // living quarters in front of the vault
    if (o.furniture !== false)
        furnishRoom({
            y: y0, x0: 1, x1: W - 2, z0: 2, z1: sepZ - 1, reserved: new Set([`${c},1`, `${c},2`, `${c},${sepZ - 1}`, `${c - 1},${sepZ - 1}`, `${c + 1},${sepZ - 1}`]),
            roles: ["kitchen", "workshop", "bedroom", "storage", "brewing"], beds: 3, carpet: CARPET, wood: wooden(wd),
        }, put, has);
    if (o.light !== false) {
        for (let z = 3; z <= D - 3; z += 4)
            for (const x of [3, c, W - 4])
                put(x, -2, z, M + "sea_lantern", 4);
    }
    entranceStairs({ put, clear }, { c, yTop: y0, P, wd, iron: true, lights: o.light !== false });
    return finalize(map, W, D, { clearArea: { x0: 0, x1: -1, z0: 0, z1: -1 }, clearCells: clear, fillBlock: M + "cobblestone", underground: true });
}
// ------------------------------------------------------------------ 4. deep silo
function silo(o) {
    const { wd, P, s } = base(o, "stone");
    const R = typeof o.w === "number" ? clamp(Math.floor(o.w / 2), 5, 10) : [5, 6, 7][s];
    const W = 2 * R + 1, D = W, c = R;
    const L = clamp(Math.floor(o.floors ?? [3, 4, 5][s]), 2, 6);
    const { map, put, has } = ctx();
    const clear = [];
    const y0f = (f) => -2 - 4 * (L - f);
    const dist = (x, z) => Math.hypot(x - R, z - R);
    const isWall = (x, z) => dist(x, z) > R - 1 && dist(x, z) <= R;
    const isIn = (x, z) => dist(x, z) <= R - 1;
    const kinds = [["bed", "bed", "chest", "bed", "bed", "barrel", "chest", "lectern"], ["crafting_table", "furnace", "smoker", "chest", "barrel", "chest", "blast_furnace", "barrel"], ["anvil", "grindstone", "stonecutter_block", "loom", "chest", "enchanting_table", "bookshelf", "brewing_stand"]];
    for (let f = 0; f < L; f++) {
        const y0 = y0f(f);
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (dist(x, z) > R)
                    continue;
                if (!(f > 0 && x === c && z === c))
                    put(x, y0, z, isWall(x, z) ? P.wall : (x + z) % 2 === 0 ? P.floor : P.wall, 1);
                for (let row = 1; row <= 3; row++) {
                    if (isWall(x, z))
                        put(x, y0 + row, z, P.wall, 1);
                    else
                        clear.push({ x, y: y0 + row, z });
                }
                if (f === 0)
                    clear.push({ x, y: y0, z });
            }
        for (let row = 1; row <= 3; row++)
            put(c, y0 + row, c + 1, P.pillar, 1, P.wood ? { axis: "y" } : undefined);
        if (o.light !== false)
            put(c, y0 + 4, c - 1, M + "sea_lantern", 4);
        if (o.furniture !== false) {
            const items = kinds[(L - 1 - f) % kinds.length].slice();
            ringStations(put, has, R, c, c, R - 2, y0 + 1, items, (x, z) => isIn(x, z) && dist(x, z) > 2.2, f === L - 1);
        }
    }
    for (let f = 0; f < L - 1; f++)
        for (let y = y0f(f) + 1; y <= y0f(f + 1); y++)
            put(c, y, c, M + "ladder", 3, { f: "nz" });
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            if (dist(x, z) <= R)
                put(x, -2, z, isWall(x, z) ? P.wall : P.floor, 1);
    if (o.light !== false)
        for (let j = 0; j < 4; j++) {
            const th = (j / 4) * Math.PI * 2 + 0.7;
            put(Math.round(c + Math.sin(th) * (R - 2)), -2, Math.round(c + Math.cos(th) * (R - 2)), M + "sea_lantern", 4);
        }
    entranceStairs({ put, clear }, { c, yTop: y0f(L - 1), P, wd, iron: false, lights: o.light !== false });
    return finalize(map, W, D, { clearArea: { x0: 0, x1: -1, z0: 0, z1: -1 }, clearCells: clear, fillBlock: M + "cobblestone", underground: true });
}
// unused import guard (door is used by other generators)
void door;
