// v1.6: more building types (hotel, hospital, prison, school, library, church, barn/stable, market).
// Every type has several variants (roof, layout, look) picked at random unless the player names one.
import { M, WOOD_IDS, bed, box, clamp, ctx, dim, door, finalize, gableRoof, hipRoof, ironDoor, palette, ring, variantOf, woodSet } from "./house_common.js";
import { furnishRoom } from "./interior.js";
export const MORE_TYPES = [
    { id: "hotel", th: "โรงแรม (ล็อบบี้ + ห้องพักหลายชั้น)" },
    { id: "hospital", th: "โรงพยาบาล (ห้องพักผู้ป่วย กากบาทแดง)" },
    { id: "prison", th: "คุก (กำแพงสูง หอคอย ห้องขัง)" },
    { id: "school", th: "โรงเรียน (ห้องเรียน กระดานดำ เสาธง)" },
    { id: "library", th: "ห้องสมุด (ชั้นหนังสือ มุมร่ายมนตร์)" },
    { id: "church", th: "โบสถ์ (หอระฆัง ม้านั่ง แท่นบูชา)" },
    { id: "barn", th: "โรงนา / คอกม้า / ฟาร์ม (มีคอกสัตว์)" },
    { id: "market", th: "ตลาด / ร้านค้า (แผงขายของมีหลังคาผ้า)" },
];
export const MORE_VARIANTS = {
    hotel: ["หลังคาเรียบ+สระดาดฟ้า", "หลังคาจั่ว", "หลังคาทรงปั้นหยา"],
    hospital: ["หลังคาเรียบ", "หลังคาจั่ว", "หลังคาทรงปั้นหยา"],
    prison: ["คุกมาตรฐาน", "คุกเข้ม (ดีพสเลท)", "คุกหอคอยสูง"],
    school: ["หลังคาเรียบ", "หลังคาจั่ว", "หลังคาทรงปั้นหยา"],
    library: ["หลังคาเรียบ", "หลังคาจั่ว", "หลังคาทรงปั้นหยา"],
    church: ["โบสถ์หินหอระฆังเหลี่ยม", "โบสถ์ไม้", "มหาวิหารหินทราย"],
    barn: ["โรงนาแดง", "คอกม้า", "เล้าไก่/คอกสัตว์เล็ก"],
    market: ["ตลาดผ้าทางสี", "ตลาดผ้าสีเดียว", "ตลาดไม้ + น้ำพุ"],
};
export function isMoreType(id) {
    return MORE_TYPES.some((t) => t.id === id);
}
export function generateMore(o) {
    switch (o.type) {
        case "hotel": return hotel(o, "hotel");
        case "hospital": return hotel(o, "hospital");
        case "prison": return prison(o);
        case "school": return school(o);
        case "library": return library(o);
        case "church": return church(o);
        case "barn": return barn(o);
        case "market": return market(o);
        default: return hotel(o, "hotel");
    }
}
// ------------------------------------------------------------------ common pieces
function base(o, def) {
    const wid = WOOD_IDS[o.wood] ?? "oak";
    const wd = woodSet(wid);
    const s = clamp(Math.floor(o.size ?? 1), 0, 2);
    return { wid, wd, s, P: palette(o, def, wid, wd), v: variantOf(o) };
}
const wooden = (wd) => ({ fence: wd.fence, plate: wd.plate, stairs: wd.stairs });
const perimOf = (W, D) => (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
/**
 * Floors, walls, windows and ceiling of a rectangular building. Floors are 4 high (floor layer + 3 rows).
 * Returns helpers. `g.tall` makes 2-high windows. Door position is left open for the caller.
 */
function shell(S, g) {
    const { put } = S;
    const { W, D, F, P, wd, h = 3 } = g;
    const c = (W - 1) / 2;
    const perim = perimOf(W, D);
    const corner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const rowH = h + 1;
    const ladX = g.ladX ?? W - 2;
    const ladZ = g.ladZ ?? D - 2;
    const pane = g.pane ?? M + "glass_pane";
    const winAt = (t, len) => t % 4 === 2 && t > 0 && t < len - 1;
    for (let f = 0; f < F; f++) {
        const y0 = rowH * f;
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === ladX && z === ladZ)
                    continue;
                put(x, y0, z, perim(x, z) ? P.wall : g.floor ?? P.floor, 1);
            }
        for (let row = 1; row <= h; row++)
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++) {
                    if (!perim(x, z))
                        continue;
                    const t = z === 0 || z === D - 1 ? x : z;
                    const len = z === 0 || z === D - 1 ? W : D;
                    const isPillar = corner(x, z) || (t % 4 === 0 && g.pillars !== false);
                    const win = !isPillar && winAt(t, len) && (g.tall ? row >= 2 : row === 2) && !(f === 0 && z === 0 && Math.abs(x - c) <= 1);
                    put(x, y0 + row, z, isPillar ? P.pillar : win ? pane : P.wall, 1, isPillar && P.wood ? { axis: "y" } : undefined);
                }
    }
    const top = rowH * F;
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, top, z, perim(x, z) ? P.wall : g.ceil ?? wd.planks, 1);
    return { c, top, rowH, perim, ladX, ladZ };
}
function ladders(put, F, rowH, x, z, f = "nz") {
    for (let k = 0; k < F - 1; k++)
        for (let y = rowH * k + 1; y <= rowH * (k + 1); y++)
            put(x, y, z, M + "ladder", 3, { f });
}
/** Roof on top of a rectangular building; kind 0 = flat with parapet, 1 = gable, 2 = hip. */
function roofOn(put, g, top, kind, roofStairs, roofFull) {
    const { W, D, P } = g;
    if (kind === 1)
        gableRoof(put, W, D, top + 1, roofStairs, roofFull, P.wall);
    else if (kind === 2)
        hipRoof(put, -1, W, -1, D, top + 1, roofStairs, roofFull);
    else {
        ring(put, 0, W - 1, 0, D - 1, top + 1, P.slab, 2);
        for (const [x, z] of [[0, 0], [W - 1, 0], [0, D - 1], [W - 1, D - 1]])
            put(x, top + 1, z, P.pillar, 2);
    }
}
function canopy(put, P, wd, c, y = 3) {
    for (const x of [c - 2, c + 2])
        for (let yy = 0; yy < y; yy++)
            put(x, yy, -3, P.pillar, 3, P.wood ? { axis: "y" } : undefined);
    for (let x = c - 3; x <= c + 3; x++)
        for (let z = -3; z <= -1; z++)
            put(x, y, z, P.slab, 3, { half: "bottom" });
    put(c - 1, y - 1, -2, M + "lantern", 4, { hanging: true });
    put(c + 1, y - 1, -2, M + "lantern", 4, { hanging: true });
    for (let x = c - 1; x <= c + 1; x++)
        put(x, 0, -1, P.stairs, 3, { f: "pz", half: "bottom" });
}
// ------------------------------------------------------------------ hotel / hospital
function hotel(o, kind) {
    const hosp = kind === "hospital";
    const { wd, s, P, v } = base(o, hosp ? "quartz" : "brick");
    const W = dim(o.w, [15, 17, 21][s], 15, 41);
    const D = dim(o.d, [17, 21, 27][s], 15, 41);
    const F = clamp(Math.floor(o.floors ?? [2, 3, 4][s]), 1, 8);
    const g = { W, D, F, P, wd, tall: false, ladX: (W - 1) / 2 + 1, ladZ: D - 2 };
    const S = ctx();
    const { put, has } = S;
    const { c, top, rowH } = shell(S, g);
    ladders(put, F, rowH, c + 1, D - 2);
    for (let f = 0; f < F; f++) {
        const y0 = rowH * f;
        const zStart = f === 0 ? 8 : 1;
        for (const px of [c - 2, c + 2])
            for (let z = zStart - (f === 0 ? 1 : 0); z <= D - 2; z++)
                for (let row = 1; row <= 3; row++)
                    put(px, y0 + row, z, P.wall, 1);
        if (f === 0) {
            for (let x = 1; x <= W - 2; x++)
                for (let row = 1; row <= 3; row++)
                    if (Math.abs(x - c) > 1)
                        put(x, y0 + row, 7, P.wall, 1);
            // lobby: reception desk, red carpet, sofas
            for (let z = 1; z <= 6; z++)
                put(c, y0 + 1, z, M + "red_carpet", 5, undefined, { opt: true });
            put(c - 2, y0 + 1, 3, M + "lectern", 5, { f: "pz" }, { opt: true });
            for (const x of [c - 3, c - 4])
                put(x, y0 + 1, 3, wd.slab, 5, { half: "bottom" }, { opt: true });
            for (const x of [2, W - 3]) {
                put(x, y0 + 1, 2, wd.stairs, 5, { f: "pz", half: "bottom" }, { opt: true });
                put(x, y0 + 1, 3, wd.stairs, 5, { f: "pz", half: "bottom" }, { opt: true });
            }
            put(2, y0 + 1, 6, M + "barrel", 5, undefined, { opt: true });
            put(W - 3, y0 + 1, 6, M + "chest", 5, { f: "nz" }, { opt: true });
            if (o.light !== false)
                for (const [x, z] of [[3, 3], [W - 4, 3], [c, 5]])
                    put(x, y0 + 3, z, M + "lantern", 4, { hanging: true });
            door(put, wd, c, 1, 0, "pz");
        }
        // rooms of 3 x 3 on both sides of the corridor
        let k = 0;
        for (let z = zStart; z + 2 <= D - 3; z += 4, k++) {
            const zm = z + 1;
            for (const side of [0, 1]) {
                const x0 = side === 0 ? 1 : c + 3;
                const x1 = side === 0 ? c - 3 : W - 2;
                for (let x = x0; x <= x1; x++)
                    for (let row = 1; row <= 3; row++)
                        put(x, y0 + row, z + 3, P.wall, 1);
                const dx = side === 0 ? c - 2 : c + 2;
                if (hosp)
                    ironDoor(put, dx, y0 + 1, zm, side === 0 ? "px" : "nx");
                else
                    door(put, wd, dx, y0 + 1, zm, side === 0 ? "px" : "nx");
                if (o.furniture !== false)
                    furnishRoom({
                        y: y0, x0, x1, z0: z, z1: z + 2, reserved: new Set([`${side === 0 ? c - 3 : c + 3},${zm}`]),
                        roles: hosp ? ["brewing", "storage"] : k % 3 === 2 ? ["storage", "kitchen"] : ["bedroom"], beds: hosp ? 2 : k % 3 === 2 ? 0 : 2,
                        carpet: hosp ? M + "white_carpet" : M + "red_carpet", wood: wooden(wd),
                    }, put, has);
                if (o.light !== false)
                    put(Math.floor((x0 + x1) / 2), y0 + 3, zm, M + "lantern", 4, { hanging: true });
            }
            if (o.light !== false)
                put(c, y0 + 3, zm, M + "lantern", 4, { hanging: true });
        }
    }
    const roofStairs = P.stairs;
    roofOn(put, g, top, v % 3, roofStairs, P.wall);
    if (v % 3 === 0 && !hosp) {
        // rooftop pool
        const x0 = c - 3, x1 = c + 3, z0 = D - 10, z1 = D - 4;
        for (let x = x0 - 1; x <= x1 + 1; x++)
            for (let z = z0 - 1; z <= z1 + 1; z++) {
                const edge = x === x0 - 1 || x === x1 + 1 || z === z0 - 1 || z === z1 + 1;
                if (edge)
                    put(x, top + 1, z, P.wall, 1);
                else
                    put(x, top + 1, z, M + "water", 3, undefined, { item: null });
            }
    }
    if (hosp) {
        // red cross on the front
        const cy = top - 2;
        for (let d = -2; d <= 2; d++) {
            put(c + d, cy, 0, M + "red_concrete", 3, undefined, { opt: true });
            put(c, cy + d, 0, M + "red_concrete", 3, undefined, { opt: true });
        }
    }
    canopy(put, P, wd, c);
    return finalize(S.map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -4, x1: W + 3, z0: -4, z1: D + 3 } });
}
// ------------------------------------------------------------------ prison
function prison(o) {
    const { wd, s, P: P0, v } = base(o, "stone");
    const P = v % 3 === 1 && (o.mat ?? 0) === 0 ? palette({ ...o, mat: 5 }, "stone", "oak", wd) : P0;
    const W = dim(o.w, [21, 25, 31][s], 19, 41);
    const D = dim(o.d, [23, 29, 35][s], 21, 45);
    const F = clamp(Math.floor(o.floors ?? [1, 2, 2][s]), 1, 3);
    const c = (W - 1) / 2;
    const S = ctx();
    const { put, has } = S;
    const WALL_H = 5;
    const perim = perimOf(W, D);
    // outer wall
    for (let y = 0; y < WALL_H; y++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++)
                if (perim(x, z))
                    put(x, y, z, P.wall, 1);
    // wall walk: crenellations
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            if (perim(x, z) && (x + z) % 2 === 0)
                put(x, WALL_H, z, P.wall, 2);
    // corner towers
    const TH = v % 3 === 2 ? 12 : 8;
    const towers = [[0, 0, "px", 2], [W - 5, 0, "nx", 2], [0, D - 5, "px", D - 3], [W - 5, D - 5, "nx", D - 3]];
    for (const [tx, tz, fdir, dz] of towers) {
        for (let y = 0; y <= TH; y++)
            for (let x = tx; x < tx + 5; x++)
                for (let z = tz; z < tz + 5; z++) {
                    const edge = x === tx || x === tx + 4 || z === tz || z === tz + 4;
                    if (y === 0 || y === TH)
                        put(x, y, z, P.wall, 1);
                    else if (edge)
                        put(x, y, z, P.wall, 1);
                }
        for (let x = tx; x < tx + 5; x++)
            for (let z = tz; z < tz + 5; z++)
                if ((x + z) % 2 === 0 && (x === tx || x === tx + 4 || z === tz || z === tz + 4))
                    put(x, TH + 1, z, P.wall, 2);
        // arrow slits
        for (const [sx, sz] of [[tx + 2, tz], [tx + 2, tz + 4], [tx, tz + 2], [tx + 4, tz + 2]])
            put(sx, TH - 3, sz, M + "iron_bars", 1);
        // door toward the yard + ladder to the top
        const dx = fdir === "px" ? tx + 4 : tx;
        door(put, wd, dx, 1, tz + (dz === 2 ? 2 : 2), fdir);
        const lx = fdir === "px" ? tx + 3 : tx + 1;
        for (let y = 1; y <= TH; y++)
            put(lx, y, tz + 3, M + "ladder", 3, { f: "pz" });
        S.del(lx, TH, tz + 3);
        if (o.light !== false) {
            put(tx + 2, TH - 1, tz + 2, M + "lantern", 4, { hanging: true });
            put(tx + 2, 3, tz + 2, M + "lantern", 4, { hanging: true });
        }
    }
    // main gate
    for (let y = 1; y <= 3; y++)
        for (const x of [c - 1, c, c + 1])
            put(x, y, 0, M + "iron_bars", 1);
    ironDoor(put, c, 1, 0, "pz");
    put(c - 1, 1, 0, M + "iron_bars", 1);
    put(c + 1, 1, 0, M + "iron_bars", 1);
    put(c - 2, 4, -1, M + "lantern", 4, { hanging: false });
    put(c + 2, 4, -1, M + "lantern", 4, { hanging: false });
    // cell block in the middle
    const bx0 = 5, bx1 = W - 6, bz0 = 7, bz1 = D - 6;
    const bw = bx1 - bx0 + 1;
    const bc = Math.floor((bx0 + bx1) / 2);
    const lcx = (x) => x - bx0; // local x inside the block
    void lcx;
    const rowH = 4;
    const top = rowH * F;
    const inBlock = (x, z) => x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1;
    const bperim = (x, z) => inBlock(x, z) && (x === bx0 || x === bx1 || z === bz0 || z === bz1);
    for (let f = 0; f < F; f++) {
        const y0 = rowH * f;
        for (let x = bx0; x <= bx1; x++)
            for (let z = bz0; z <= bz1; z++) {
                if (f > 0 && x === bc + 1 && z === bz1 - 1)
                    continue;
                put(x, y0, z, bperim(x, z) ? P.wall : P.floor, 1);
            }
        for (let row = 1; row <= 3; row++)
            for (let x = bx0; x <= bx1; x++)
                for (let z = bz0; z <= bz1; z++)
                    if (bperim(x, z))
                        put(x, y0 + row, z, row === 2 && (x - bx0) % 4 === 2 && (z === bz1 || x === bx0 || x === bx1) ? M + "iron_bars" : P.wall, 1);
        // guard room on the ground floor, cells behind it
        const zCells = f === 0 ? bz0 + 6 : bz0 + 1;
        if (f === 0) {
            for (let x = bx0 + 1; x <= bx1 - 1; x++)
                for (let row = 1; row <= 3; row++)
                    put(x, y0 + row, bz0 + 5, Math.abs(x - bc) <= 0 ? M + "iron_bars" : P.wall, 1);
            ironDoor(put, bc, y0 + 1, bz0 + 5, "pz");
            if (o.furniture !== false)
                furnishRoom({
                    y: y0, x0: bx0 + 1, x1: bx1 - 1, z0: bz0 + 1, z1: bz0 + 4, reserved: new Set([`${bc},${bz0 + 4}`, `${bc},${bz0 + 1}`]),
                    roles: ["workshop", "storage", "kitchen"], beds: 1, carpet: M + "gray_carpet", wood: wooden(wd),
                }, put, has);
            if (o.light !== false)
                for (const x of [bx0 + 3, bx1 - 3])
                    put(x, y0 + 3, bz0 + 2, M + "lantern", 4, { hanging: true });
        }
        const lPart = bc - 2, rPart = bc + 2;
        for (const px of [lPart, rPart])
            for (let z = zCells; z <= bz1 - 1; z++)
                for (let row = 1; row <= 3; row++)
                    put(px, y0 + row, z, M + "iron_bars", 1);
        let k = 0;
        for (let z = zCells; z + 2 <= bz1 - 2; z += 4, k++) {
            const zm = z + 1;
            for (const side of [0, 1]) {
                const x0 = side === 0 ? bx0 + 1 : rPart + 1;
                const x1 = side === 0 ? lPart - 1 : bx1 - 1;
                for (let x = x0; x <= x1; x++)
                    for (let row = 1; row <= 3; row++)
                        put(x, y0 + row, z + 3, P.wall, 1);
                const px = side === 0 ? lPart : rPart;
                ironDoor(put, px, y0 + 1, zm, side === 0 ? "px" : "nx");
                if (o.furniture !== false) {
                    if (side === 0)
                        bed(put, x0, y0 + 1, zm, "nx");
                    else
                        bed(put, x1, y0 + 1, zm, "px");
                    put(side === 0 ? x1 : x0, y0 + 1, z, M + "cauldron", 5, undefined, { opt: true });
                }
                if (o.light !== false)
                    put(Math.floor((x0 + x1) / 2), y0 + 3, zm, M + "lantern", 4, { hanging: true });
            }
            if (o.light !== false)
                put(bc, y0 + 3, zm, M + "lantern", 4, { hanging: true });
        }
    }
    for (let k = 0; k < F - 1; k++)
        for (let y = rowH * k + 1; y <= rowH * (k + 1); y++)
            put(bc + 1, y, bz1 - 1, M + "ladder", 3, { f: "nz" });
    for (let x = bx0; x <= bx1; x++)
        for (let z = bz0; z <= bz1; z++)
            put(x, top, z, bperim(x, z) ? P.wall : P.wall, 1);
    ring(put, bx0, bx1, bz0, bz1, top + 1, P.slab, 2);
    // front door of the block and a stone path from the gate
    ironDoor(put, bc, 1, bz0, "pz");
    for (let z = 1; z < bz0; z++)
        put(bc, -1, z, M + "stone_bricks", 1, undefined, { opt: true });
    // watchlights on the wall
    if (o.light !== false)
        for (let x = 6; x < W - 6; x += 6) {
            put(x, WALL_H + 1, 0, M + "lantern", 4, { hanging: false });
            put(x, WALL_H + 1, D - 1, M + "lantern", 4, { hanging: false });
        }
    void bw;
    return finalize(S.map, W, D, { fillBlock: M + "cobblestone", clearArea: { x0: -2, x1: W + 1, z0: -3, z1: D + 1 } });
}
// ------------------------------------------------------------------ school
function school(o) {
    const { wd, s, P, v } = base(o, "brick");
    const W = dim(o.w, [19, 23, 29][s], 19, 41);
    const D = dim(o.d, [15, 19, 25][s], 15, 41);
    const F = clamp(Math.floor(o.floors ?? [1, 2, 2][s]), 1, 4);
    const g = { W, D, F, P, wd, tall: true, ladX: (W - 1) / 2 + 1, ladZ: D - 2 };
    const S = ctx();
    const { put, has } = S;
    const { c, top, rowH } = shell(S, g);
    ladders(put, F, rowH, c + 1, D - 2);
    const ps = [c - 2, c + 2];
    for (let f = 0; f < F; f++) {
        const y0 = rowH * f;
        for (const px of ps)
            for (let z = 1; z <= D - 2; z++)
                for (let row = 1; row <= 3; row++)
                    put(px, y0 + row, z, P.wall, 1);
        let k = 0;
        for (let z = 1; z + 4 <= D - 2; z += 6, k++) {
            const zm = z + 2;
            for (const side of [0, 1]) {
                const x0 = side === 0 ? 1 : c + 3;
                const x1 = side === 0 ? c - 3 : W - 2;
                for (let x = x0; x <= x1; x++)
                    for (let row = 1; row <= 3; row++)
                        put(x, y0 + row, z + 5, P.wall, 1);
                const px = side === 0 ? c - 2 : c + 2;
                door(put, wd, px, y0 + 1, zm + 2, side === 0 ? "px" : "nx");
                // blackboard in the partition
                for (let zz = zm - 1; zz <= zm + 1; zz++) {
                    put(px, y0 + 2, zz, M + "black_concrete", 1);
                    put(px, y0 + 3, zz, M + "black_concrete", 1);
                }
                if (o.furniture !== false) {
                    const toBoard = side === 0 ? "px" : "nx";
                    const away = side === 0 ? "nx" : "px";
                    const dir = side === 0 ? 1 : -1;
                    const bx = px - dir; // first cell in front of the board
                    put(bx - dir, y0 + 1, zm, M + "lectern", 5, { f: toBoard === "px" ? "nx" : "px" }, { opt: true }); // teacher desk
                    for (const col of [3, 5]) {
                        const dx = bx - dir * col;
                        if (dx < x0 || dx > x1)
                            continue;
                        for (let zz = zm - 1; zz <= zm + 1; zz++) {
                            put(dx, y0 + 1, zz, wd.slab, 5, { half: "bottom" }, { opt: true });
                            const cx = dx - dir;
                            if (cx >= x0 && cx <= x1)
                                put(cx, y0 + 1, zz, wd.stairs, 5, { f: away, half: "bottom" }, { opt: true });
                        }
                    }
                }
                if (o.light !== false) {
                    put(Math.floor((x0 + x1) / 2), y0 + 3, zm - 1, M + "lantern", 4, { hanging: true });
                    put(Math.floor((x0 + x1) / 2), y0 + 3, zm + 2, M + "lantern", 4, { hanging: true });
                }
            }
            if (o.light !== false)
                put(c, y0 + 3, zm, M + "lantern", 4, { hanging: true });
        }
    }
    door(put, wd, c, 1, 0, "pz");
    // flag pole in front
    const fx = c - 5;
    for (let y = 0; y <= 8; y++)
        put(fx, y, -3, wd.fence, 3);
    for (let y = 6; y <= 7; y++)
        for (let x = fx + 1; x <= fx + 3; x++)
            put(x, y, -3, y === 6 ? M + "white_wool" : M + "red_wool", 3);
    put(fx, 9, -3, M + "lantern", 4, { hanging: false });
    roofOn(put, g, top, v % 3, P.stairs, P.wall);
    canopy(put, P, wd, c);
    return finalize(S.map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -4, x1: W + 3, z0: -5, z1: D + 3 } });
}
// ------------------------------------------------------------------ library
function library(o) {
    const { wd, s, P, v } = base(o, "stone");
    const W = dim(o.w, [15, 19, 25][s], 13, 41);
    const D = dim(o.d, [19, 23, 29][s], 15, 41);
    const F = clamp(Math.floor(o.floors ?? [1, 2, 2][s]), 1, 4);
    const g = { W, D, F, P, wd, tall: false, floor: wd.planks };
    const S = ctx();
    const { put, has } = S;
    const { c, top, rowH } = shell(S, g);
    ladders(put, F, rowH, W - 2, D - 2);
    for (let f = 0; f < F; f++) {
        const y0 = rowH * f;
        // aisles of bookshelves (2 high), leaving the front hall and the back study free
        for (const x of [3, 6, W - 4, W - 7])
            for (let z = 4; z <= D - 10; z++) {
                if ((z - 4) % 6 === 5)
                    continue; // crossing
                put(x, y0 + 1, z, M + "bookshelf", 5, undefined, { opt: true });
                put(x, y0 + 2, z, M + "bookshelf", 5, undefined, { opt: true });
            }
        // shelves on the wall
        for (const x of [1, W - 2])
            for (let z = 3; z <= D - 4; z += 1) {
                if (z === D - 2)
                    continue;
                put(x, y0 + 1, z, M + "bookshelf", 5, undefined, { opt: true });
                put(x, y0 + 2, z, M + "bookshelf", 5, undefined, { opt: true });
            }
        // reading tables in the front hall
        if (o.furniture !== false) {
            for (const x of [c - 3, c + 3])
                for (const z of [3, 5]) {
                    put(x, y0 + 1, z, M + "lectern", 5, { f: "nz" }, { opt: true });
                    put(x, y0 + 1, z - 1, wd.stairs, 5, { f: "nz", half: "bottom" }, { opt: true });
                }
            // study + enchanting nook at the back
            furnishRoom({
                y: y0, x0: 2, x1: W - 3, z0: D - 9, z1: D - 2, reserved: new Set([`${c},${D - 9}`, `${W - 2},${D - 2}`, `${W - 3},${D - 2}`]),
                roles: f === 0 ? ["enchant", "storage"] : ["enchant", "brewing"], beds: 0, carpet: M + "red_carpet", wood: wooden(wd),
            }, put, has);
        }
        if (o.light !== false)
            for (let z = 4; z <= D - 4; z += 5)
                for (const x of [c, 4, W - 5])
                    put(x, y0 + 3, z, M + "lantern", 4, { hanging: true });
    }
    door(put, wd, c, 1, 0, "pz");
    roofOn(put, g, top, v % 3, P.stairs, P.wall);
    canopy(put, P, wd, c);
    return finalize(S.map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -4, x1: W + 3, z0: -4, z1: D + 3 } });
}
// ------------------------------------------------------------------ church
function church(o) {
    const { wd, s, P: P0, v } = base(o, "stone");
    const mat = (o.mat ?? 0) === 0 ? (v % 3 === 1 ? 2 : v % 3 === 2 ? 4 : 1) : o.mat;
    const P = palette({ ...o, mat }, "stone", WOOD_IDS[o.wood] ?? "oak", wd);
    void P0;
    const W = dim(o.w, [11, 13, 15][s], 9, 25);
    const D = dim(o.d, [19, 23, 29][s], 15, 41);
    const c = (W - 1) / 2;
    const H = 7; // wall height (rows 1..H)
    const S = ctx();
    const { put } = S;
    const perim = perimOf(W, D);
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            put(x, 0, z, perim(x, z) ? P.wall : (x + z) % 2 === 0 ? M + "polished_andesite" : M + "stone_bricks", 1);
        }
    for (let row = 1; row <= H; row++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (!perim(x, z))
                    continue;
                const t = z === 0 || z === D - 1 ? x : z;
                const len = z === 0 || z === D - 1 ? W : D;
                const buttress = t % 4 === 0 || x === 0 && z === 0;
                const win = !buttress && t % 4 === 2 && t > 0 && t < len - 1 && row >= 3 && row <= 6 && !(z === 0);
                put(x, row, z, buttress ? P.pillar : win ? M + "glass_pane" : P.wall, 1, buttress && P.wood ? { axis: "y" } : undefined);
            }
    gableRoof(put, W, D, H + 1, P.stairs, P.wall, P.wall);
    // pews facing the altar
    if (o.furniture !== false)
        for (let z = 4; z <= D - 9; z += 2)
            for (const x of [c - 3, c - 2, c + 2, c + 3])
                put(x, 1, z, wd.stairs, 5, { f: "nz", half: "bottom" }, { opt: true });
    // aisle carpet
    for (let z = 1; z <= D - 3; z++)
        put(c, 1, z, M + "red_carpet", 5, undefined, { opt: true });
    // altar
    for (let x = c - 2; x <= c + 2; x++)
        for (let z = D - 5; z <= D - 3; z++)
            put(x, 1, z, P.slab, 1, { half: "bottom" });
    put(c, 1, D - 4, M + "quartz_block", 1);
    put(c, 2, D - 4, M + "lectern", 5, { f: "nz" }, { opt: true });
    for (const x of [c - 2, c + 2]) {
        put(x, 2, D - 3, M + "lantern", 4, { hanging: false });
    }
    if (o.light !== false)
        for (let z = 4; z <= D - 6; z += 5)
            for (const x of [c - 4, c + 4])
                put(x, 5, z, M + "lantern", 4, { hanging: true });
    // bell tower in front (protrudes forward)
    const tx0 = c - 2, tx1 = c + 2, tz0 = -4, tz1 = 0;
    const TH = 14;
    for (let y = 0; y <= TH; y++)
        for (let x = tx0; x <= tx1; x++)
            for (let z = tz0; z <= tz1; z++) {
                const edge = x === tx0 || x === tx1 || z === tz0 || z === tz1;
                if (y === 0)
                    put(x, y, z, P.wall, 1);
                else if (edge)
                    put(x, y, z, P.wall, 1);
            }
    // belfry floor (with a hole for the ladder)
    for (let x = tx0; x <= tx1; x++)
        for (let z = tz0; z <= tz1; z++)
            if (!(x === tx0 + 1 && z === tz0 + 1))
                put(x, 8, z, P.wall, 1);
    put(c, 9, tz0 + 2, M + "bell", 5, undefined, { opt: true });
    hipRoof(put, tx0 - 1, tx1 + 1, tz0 - 1, tz1 + 1, TH + 1, P.stairs, P.wall);
    for (let y = 1; y <= 9; y++)
        put(tx0 + 1, y, tz0 + 1, M + "ladder", 3, { f: "pz" });
    // open arches in the belfry (front, left, right)
    for (let y = 10; y <= 12; y++) {
        S.del(c, y, tz0);
        S.del(tx0, y, tz0 + 2);
        S.del(tx1, y, tz0 + 2);
    }
    // doorway: tower front door + wide arch into the nave
    door(put, wd, c, 1, tz0, "pz");
    for (let y = 1; y <= 3; y++)
        for (const x of [c - 1, c, c + 1])
            S.del(x, y, 0);
    for (let x = c - 1; x <= c + 1; x++)
        put(x, 4, 0, P.stairs, 1, { f: "pz", half: "top" });
    for (let x = c - 1; x <= c + 1; x++)
        put(x, 0, tz0 - 1, P.stairs, 3, { f: "pz", half: "bottom" });
    return finalize(S.map, W, D, { fillBlock: M + "stone_bricks", clearArea: { x0: -2, x1: W + 1, z0: -7, z1: D + 1 } });
}
// ------------------------------------------------------------------ barn / stable / coop
function barn(o) {
    const { wd, s, v } = base(o, "wood");
    const kind = v % 3;
    const redWall = kind === 0;
    const P = {
        wall: kind === 0 ? M + "red_terracotta" : wd.planks, pillar: M + (kind === 0 ? "white_terracotta" : "stripped_" + (WOOD_IDS[o.wood] ?? "oak") + "_log"),
        stairs: wd.stairs, slab: wd.slab, floor: wd.planks, wood: !redWall,
    };
    void redWall;
    const W = dim(o.w, kind === 2 ? [9, 11, 13][s] : [11, 13, 17][s], 9, 31);
    const D = dim(o.d, kind === 2 ? [9, 11, 13][s] : [15, 19, 25][s], 9, 41);
    const c = (W - 1) / 2;
    const H = kind === 2 ? 3 : 5;
    const S = ctx();
    const { put } = S;
    const perim = perimOf(W, D);
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, 0, z, perim(x, z) ? M + "cobblestone" : M + "coarse_dirt", 1);
    for (let row = 1; row <= H; row++)
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (!perim(x, z))
                    continue;
                const t = z === 0 || z === D - 1 ? x : z;
                const post = t % 4 === 0;
                const win = !post && t % 4 === 2 && row === 3 && !(z === 0 && Math.abs(x - c) <= 2);
                put(x, row, z, post ? P.pillar : win ? M + "glass_pane" : P.wall, 1);
            }
    gableRoof(put, W, D, H + 1, wd.stairs, wd.planks, P.wall);
    // big front doors (3 wide gap with two doors + hay above)
    for (let y = 1; y <= 3; y++)
        for (let x = c - 1; x <= c + 1; x++)
            S.del(x, y, 0);
    door(put, wd, c - 1, 1, 0, "pz");
    door(put, wd, c + 1, 1, 0, "pz");
    put(c, 1, 0, wd.fence, 1);
    put(c, 2, 0, wd.fence, 1);
    for (const x of [c - 1, c + 1])
        put(x, 3, 0, wd.planks, 1);
    // stalls along both sides
    if (o.furniture !== false) {
        const step = kind === 1 ? 3 : 4;
        for (let z = 3; z <= D - 4; z += step) {
            for (const side of [0, 1]) {
                const wx = side === 0 ? 1 : W - 2;
                const px = side === 0 ? 4 : W - 5;
                for (let zz = z; zz <= z + step - 2 && zz <= D - 3; zz++)
                    put(px, 1, zz, wd.fence, 5, undefined, { opt: true });
                put(px, 1, z + step - 1 <= D - 3 ? z + step - 1 : z, wd.fence, 5, undefined, { opt: true });
                put(wx, 1, z, M + (kind === 2 ? "composter" : "hay_block"), 5, undefined, { opt: true });
                put(wx, 1, z + 1, kind === 1 ? M + "barrel" : M + "hay_block", 5, undefined, { opt: true });
            }
        }
        for (let x = 2; x <= W - 3; x++) {
            put(x, 1, D - 2, M + "hay_block", 5, undefined, { opt: true });
            if (x % 2 === 0)
                put(x, 2, D - 2, M + "hay_block", 5, undefined, { opt: true });
        }
        put(c, 1, D - 4, M + "cauldron", 5, undefined, { opt: true });
    }
    if (o.light !== false)
        for (let z = 3; z <= D - 3; z += 5)
            put(c, H, z, M + "lantern", 4, { hanging: true });
    // fenced paddock in front
    const px0 = 0, px1 = W - 1, pz0 = -9, pz1 = -2;
    for (let x = px0; x <= px1; x++)
        for (let z = pz0; z <= pz1; z++)
            if (x === px0 || x === px1 || z === pz0 || z === pz1)
                if (!(z === pz1 && Math.abs(x - c) <= 1))
                    put(x, 0, z, wd.fence, 3);
    for (let z = -1; z >= pz1 + 1; z--)
        put(c, -1, z, M + "coarse_dirt", 1);
    put(c, -1, -1, M + "coarse_dirt", 1);
    return finalize(S.map, W, D, { fillBlock: M + "dirt", clearArea: { x0: -2, x1: W + 1, z0: -11, z1: D + 1 } });
}
// ------------------------------------------------------------------ market
function market(o) {
    const { wd, s, P, v } = base(o, "wood");
    const W = dim(o.w, [21, 27, 33][s], 17, 41);
    const D = dim(o.d, [13, 17, 21][s], 11, 41);
    const c = (W - 1) / 2;
    const S = ctx();
    const { put } = S;
    const cols = ["red", "white", "blue", "yellow", "green", "orange"];
    const kind = v % 3;
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++)
            put(x, 0, z, (x + z) % 5 === 0 ? M + "stone_bricks" : M + "gravel", 1, undefined, { opt: false });
    // stall rows on both long sides: 5 wide, 3 deep, with an awning
    let si = 0;
    for (let x = 1; x + 4 <= W - 2; x += 6, si++) {
        for (const row of [0, 1]) {
            const z0 = row === 0 ? 1 : D - 4;
            const z1 = z0 + 2;
            const front = row === 0 ? z1 : z0;
            // posts
            for (const px of [x, x + 4])
                for (const pz of [z0, z1])
                    for (let y = 1; y <= 3; y++)
                        put(px, y, pz, wd.fence, 1);
            // awning
            const col = kind === 1 ? cols[v % cols.length] : cols[(si + row) % cols.length];
            for (let xx = x; xx <= x + 4; xx++)
                for (let zz = z0; zz <= z1; zz++) {
                    const striped = kind === 0 && (xx - x) % 2 === 1;
                    put(xx, 4, zz, M + (striped ? "white" : col) + "_wool", 2);
                }
            // counter at the aisle side and goods
            for (let xx = x + 1; xx <= x + 3; xx++) {
                put(xx, 1, front, kind === 2 ? wd.planks : M + "barrel", 5, undefined, { opt: true });
                put(xx, 2, front, xx === x + 2 ? M + "chest" : wd.slab, 5, xx === x + 2 ? { f: row === 0 ? "pz" : "nz" } : { half: "bottom" }, { opt: true });
            }
            put(x + 2, 1, row === 0 ? z0 : z1, M + "crafting_table", 5, undefined, { opt: true });
            if (o.light !== false)
                put(x + 2, 3, row === 0 ? z1 : z0, M + "lantern", 4, { hanging: true });
        }
    }
    // central fountain / well
    const fz = Math.floor(D / 2);
    for (let x = c - 2; x <= c + 2; x++)
        for (let z = fz - 2; z <= fz + 2; z++) {
            const edge = Math.abs(x - c) === 2 || Math.abs(z - fz) === 2;
            if (edge)
                put(x, 1, z, kind === 2 ? M + "prismarine_bricks" : M + "stone_brick_slab", 1, kind === 2 ? undefined : { half: "bottom" });
            else
                put(x, 0, z, M + "water", 3, undefined, { item: null });
        }
    put(c, 1, fz, wd.fence, 1);
    put(c, 2, fz, wd.fence, 1);
    put(c, 3, fz, M + "lantern", 4, { hanging: false });
    // lamp posts in the aisle
    if (o.light !== false)
        for (let x = 4; x < W - 3; x += 8) {
            if (Math.abs(x - c) <= 3)
                continue;
            for (let y = 1; y <= 3; y++)
                put(x, y, fz, wd.fence, 3);
            put(x, 4, fz, M + "lantern", 4, { hanging: false });
        }
    return finalize(S.map, W, D, { fillBlock: M + "dirt", clearArea: { x0: -1, x1: W, z0: -1, z1: D } });
}
void box;
