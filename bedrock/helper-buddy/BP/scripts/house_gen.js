// Pure house generator (no Minecraft imports) so it can also run in Node for previews.
// Local coordinates: x = left->right as seen from the front, z = front->back, y = up.
// y = 0 is one block above the ground the player stands on.
const M = "minecraft:";
import { furnishRoom, rolesFor } from "./interior.js";
import { MATS, TYPES, VARIANT_COUNT, generateExtra, isExtraType, typeName, variantName } from "./house_extra.js";
export { MATS, TYPES, VARIANT_COUNT, isExtraType, variantName };
/** Display name of a house design (type or style). */
export function houseName(o) {
    return isExtraType(o) ? typeName(o) : STYLES[o.style] ?? "บ้าน";
}
export const WOODS = [
    { id: "oak", th: "ไม้โอ๊ค" },
    { id: "spruce", th: "ไม้สปรูซ" },
    { id: "birch", th: "ไม้เบิร์ช" },
    { id: "jungle", th: "ไม้ป่าดิบ" },
    { id: "acacia", th: "ไม้อะเคเชีย" },
    { id: "dark_oak", th: "ไม้โอ๊คเข้ม" },
    { id: "mangrove", th: "ไม้โกงกาง" },
    { id: "cherry", th: "ไม้เชอร์รี่" },
    { id: "pale_oak", th: "ไม้โอ๊คซีด" },
];
export const SIZES = [
    { th: "เล็ก (7x7)", W: 7, D: 7 },
    { th: "กลาง (9x11)", W: 9, D: 11 },
    { th: "ใหญ่ (11x13)", W: 11, D: 13 },
];
export const STYLES = [
    "บ้านไม้คันทรี (Cottage)",
    "บ้านยุคกลาง (Medieval)",
    "บ้านโมเดิร์น (Modern)",
    "เรือนไทย (Thai)",
];
export const ROOFS = [
    "แนะนำตามสไตล์บ้าน (สวยที่สุด)",
    "ไม้ชนิดเดียวกับบ้าน",
    "ไม้สปรูซ (น้ำตาลเข้ม)",
    "ไม้โอ๊คเข้ม (เกือบดำ)",
    "อิฐแดง (สีส้มอิฐ)",
    "กระเบื้องดีพสเลท (สีเทาดำ)",
    "อิฐหิน (สีเทา)",
];
/** A darker log that contrasts nicely with the chosen planks (timber framing). */
function frameLog(w) {
    switch (w) {
        case "oak": return M + "spruce_log";
        case "birch": return M + "spruce_log";
        case "dark_oak": return M + "spruce_log";
        case "cherry": return M + "cherry_log";
        case "spruce": return M + "dark_oak_log";
        default: return M + "dark_oak_log"; // jungle, acacia, mangrove, pale oak
    }
}
/** The roof that looks best for each style ("recommended" choice). */
function recommendedRoof(style, w) {
    if (style === 1)
        return 5; // medieval: dark slate tiles
    if (style === 3)
        return 4; // thai: clay-coloured bricks
    return w === "spruce" || w === "dark_oak" ? 3 : 2; // cottage: dark wood roof
}
export function woodSet(w) {
    return {
        planks: M + w + "_planks",
        log: M + w + "_log",
        stairs: M + w + "_stairs",
        slab: M + w + "_slab",
        fence: M + w + "_fence",
        door: w === "oak" ? M + "wooden_door" : M + w + "_door",
        trapdoor: w === "oak" ? M + "trapdoor" : M + w + "_trapdoor",
        plate: w === "oak" ? M + "wooden_pressure_plate" : M + w + "_pressure_plate",
    };
}
function roofSet(opt, w, style) {
    if (opt <= 0 || opt > 6)
        opt = recommendedRoof(style, w);
    switch (opt) {
        case 2: return { stairs: M + "spruce_stairs", slab: M + "spruce_slab", full: M + "spruce_planks" };
        case 3: return { stairs: M + "dark_oak_stairs", slab: M + "dark_oak_slab", full: M + "dark_oak_planks" };
        case 4: return { stairs: M + "brick_stairs", slab: M + "brick_slab", full: M + "brick_block" };
        case 5: return { stairs: M + "deepslate_tile_stairs", slab: M + "deepslate_tile_slab", full: M + "deepslate_tiles" };
        case 6: return { stairs: M + "stone_brick_stairs", slab: M + "stone_brick_slab", full: M + "stone_bricks" };
        default: {
            const s = woodSet(w);
            return { stairs: s.stairs, slab: s.slab, full: s.planks };
        }
    }
}
function styleCfg(o) {
    const wid = WOODS[o.wood]?.id ?? "oak";
    const w = woodSet(wid);
    switch (o.style) {
        case 1: { // medieval (Tudor): stone ground floor, white plaster infill, dark timber frame
            const timber = wid === "spruce" || wid === "dark_oak" ? w.log : M + "dark_oak_log";
            const plaster = M + "white_terracotta";
            return {
                stilts: 0, foundation: M + "stone_bricks", floor: w.planks,
                frame: () => timber, wall: (f) => (f === 0 ? M + "stone_bricks" : plaster), beam: timber, ceiling: w.planks,
                window: M + "glass_pane", windowRows: [2], roofType: "gable", pitch: 1, stepId: M + "stone_brick_stairs",
                stepSlab: false, chimney: M + "stone_bricks", veranda: false, gableWall: plaster, carpet: M + "red_carpet",
                flatRoof: "", roofFill: w.planks,
            };
        }
        case 2: // modern: smooth quartz, gray concrete frame, tinted glass, wood floor
            return {
                stilts: 0, foundation: M + "gray_concrete", floor: w.planks,
                frame: () => M + "gray_concrete", wall: () => M + "smooth_quartz", beam: M + "gray_concrete",
                ceiling: M + "smooth_quartz", window: M + "light_gray_stained_glass", windowRows: [1, 2], roofType: "flat",
                pitch: 0, stepId: M + "smooth_quartz_slab", stepSlab: true, chimney: null, veranda: false,
                gableWall: M + "smooth_quartz", carpet: M + "white_carpet", flatRoof: M + "smooth_quartz_slab",
                roofFill: M + "smooth_quartz",
            };
        case 3: // thai stilt house: one wood throughout, like a teak house
            return {
                stilts: 3, foundation: w.log, floor: w.planks,
                frame: () => w.log, wall: () => w.planks, beam: w.log, ceiling: w.planks,
                window: M + "glass_pane", windowRows: [2], roofType: "gable", pitch: 2, stepId: w.stairs,
                stepSlab: false, chimney: null, veranda: true, gableWall: w.planks, carpet: M + "yellow_carpet", flatRoof: "",
                roofFill: w.planks,
            };
        default: { // cottage: planks with a contrasting timber frame, stone-brick base, brick chimney
            const timber = frameLog(wid);
            return {
                stilts: 0, foundation: M + "stone_bricks", floor: w.planks,
                frame: () => timber, wall: () => w.planks, beam: timber, ceiling: w.planks,
                window: M + "glass_pane", windowRows: [2], roofType: "gable", pitch: 1, stepId: M + "stone_brick_stairs",
                stepSlab: false, chimney: M + "brick_block", veranda: false, gableWall: w.planks, carpet: M + "red_carpet",
                flatRoof: "", roofFill: w.planks,
            };
        }
    }
}
/** Pillar positions along a wall of length L (including both corners). */
function pillarsFor(L) {
    if (L <= 7)
        return [0, L - 1];
    if (L <= 11)
        return [0, Math.floor((L - 1) / 2), L - 1];
    const out = [];
    for (let i = 0; i < L - 1; i += 4)
        out.push(i);
    out.push(L - 1);
    return out;
}
function windowsBetween(pillars) {
    const out = [];
    for (let i = 0; i + 1 < pillars.length; i++) {
        const a = pillars[i];
        const b = pillars[i + 1];
        const m = b - a - 1;
        if (m === 3)
            out.push(a + 2);
        else if (m === 4)
            out.push(a + 2, a + 3);
        else if (m === 5)
            out.push(a + 2, a + 4);
        else if (m >= 6)
            for (let k = a + 2; k <= b - 2; k++)
                out.push(k);
        else if (m === 2)
            out.push(a + 1);
    }
    return out;
}
function frontWindows(W, upper) {
    const c = (W - 1) / 2;
    let base;
    if (W <= 7)
        base = [1, 5];
    else if (W <= 9)
        base = [2, 6];
    else
        base = [2, 3, W - 4, W - 3];
    if (upper)
        base = [...base, c];
    return base.filter((x) => x > 0 && x < W - 1).sort((a, b) => a - b);
}
/** Modern style: wide glass front, leaving the door bay solid on the ground floor. */
function wideWindows(W, upper) {
    const c = (W - 1) / 2;
    const out = [];
    for (let x = 1; x <= W - 2; x++)
        if (upper || Math.abs(x - c) > 1)
            out.push(x);
    return out;
}
export function generateHouse(o) {
    if (isExtraType(o))
        return generateExtra(o);
    if (o.style === 2)
        return generateModern(o);
    const sz = SIZES[Math.max(0, Math.min(2, o.size))];
    const W = sz.W;
    const D = sz.D;
    const F = Math.max(1, Math.min(3, Math.floor(o.floors)));
    const cfg = styleCfg(o);
    const wd = woodSet(WOODS[o.wood]?.id ?? "oak");
    const roof = roofSet(o.roof, WOODS[o.wood]?.id ?? "oak", o.style);
    const S = cfg.stilts;
    const c = (W - 1) / 2;
    const top = S + 4 * F; // ceiling layer of the top floor
    const yf = (f) => S + 4 * f;
    const map = new Map();
    const k = (x, y, z) => `${x},${y},${z}`;
    const put = (x, y, z, id, ph, st, extra) => {
        map.set(k(x, y, z), { x, y, z, id, ph, st, ...extra });
    };
    const has = (x, y, z) => map.has(k(x, y, z));
    const del = (x, y, z) => map.delete(k(x, y, z));
    const isPerim = (x, z) => x === 0 || x === W - 1 || z === 0 || z === D - 1;
    const isCorner = (x, z) => (x === 0 || x === W - 1) && (z === 0 || z === D - 1);
    const sidePillars = pillarsFor(D); // along z on x = 0 and x = W-1
    const backPillars = pillarsFor(W); // along x on z = D-1
    const pillarAt = (x, z) => {
        if (isCorner(x, z))
            return true;
        if ((x === 0 || x === W - 1) && sidePillars.includes(z))
            return true;
        if (z === D - 1 && backPillars.includes(x))
            return true;
        return false;
    };
    const ladderX = W - 2;
    const ladderZ = D - 2;
    // ---------------- stilts (thai)
    if (S > 0) {
        const posts = [];
        for (const z of sidePillars)
            posts.push([0, z], [W - 1, z]);
        for (const x of backPillars)
            posts.push([x, 0], [x, D - 1]);
        posts.push([c, Math.floor(D / 2)]);
        if (cfg.veranda)
            posts.push([0, -3], [W - 1, -3]);
        for (const [x, z] of posts)
            for (let y = 0; y < S; y++)
                put(x, y, z, cfg.frame(0), 1, { axis: "y" });
    }
    // ---------------- floors & walls
    const wins = [];
    for (let f = 0; f < F; f++) {
        const y0 = yf(f);
        // floor layer
        for (let x = 0; x < W; x++)
            for (let z = 0; z < D; z++) {
                if (f > 0 && x === ladderX && z === ladderZ)
                    continue; // ladder hole
                if (isPerim(x, z)) {
                    if (f === 0 && S === 0)
                        put(x, y0, z, cfg.foundation, 1);
                    else if (isCorner(x, z) || pillarAt(x, z))
                        put(x, y0, z, cfg.frame(f), 1, { axis: "y" });
                    else
                        put(x, y0, z, cfg.beam, 1, { axis: x === 0 || x === W - 1 ? "z" : "x" });
                }
                else
                    put(x, y0, z, cfg.floor, 1);
            }
        // walls
        const fw = o.style === 2 ? wideWindows(W, f > 0) : frontWindows(W, f > 0);
        const bw = windowsBetween(backPillars);
        const sw = windowsBetween(sidePillars);
        for (let row = 1; row <= 3; row++) {
            const y = y0 + row;
            const isWinRow = cfg.windowRows.includes(row);
            for (let x = 0; x < W; x++)
                for (let z = 0; z < D; z++) {
                    if (!isPerim(x, z))
                        continue;
                    if (pillarAt(x, z)) {
                        put(x, y, z, cfg.frame(f), 1, { axis: "y" });
                        continue;
                    }
                    // door opening (front center, ground floor)
                    if (f === 0 && z === 0 && x === c && row <= 2)
                        continue;
                    let win = false;
                    if (isWinRow) {
                        if (z === 0)
                            win = fw.includes(x);
                        else if (z === D - 1)
                            win = bw.includes(x);
                        else
                            win = sw.includes(z);
                    }
                    if (win) {
                        put(x, y, z, cfg.window, 3);
                        if (row === 2)
                            wins.push({ x, y, z });
                    }
                    else
                        put(x, y, z, cfg.wall(f), 1);
                }
        }
    }
    // top (ceiling) layer
    for (let x = 0; x < W; x++)
        for (let z = 0; z < D; z++) {
            if (isPerim(x, z)) {
                if (isCorner(x, z) || pillarAt(x, z))
                    put(x, top, z, cfg.frame(F - 1), 1, { axis: "y" });
                else
                    put(x, top, z, cfg.beam, 1, { axis: x === 0 || x === W - 1 ? "z" : "x" });
            }
            else
                put(x, top, z, cfg.ceiling, 1);
        }
    // ---------------- roof
    let roofTop = top;
    if (cfg.roofType === "flat") {
        // flat roof: parapet ring, slab deck and a thin cantilevered edge
        for (let x = -1; x <= W; x++)
            for (let z = -1; z <= D; z++) {
                const inside = x >= 0 && x <= W - 1 && z >= 0 && z <= D - 1;
                if (!inside) {
                    put(x, top, z, cfg.flatRoof, 2, { half: "top" });
                    continue;
                }
                if (isPerim(x, z))
                    put(x, top + 1, z, cfg.frame(F - 1), 2);
                else
                    put(x, top + 1, z, cfg.flatRoof, 2, { half: "bottom" });
            }
        roofTop = top + 1;
    }
    else {
        const cells = [];
        const cc = c; // ridge column
        if (cfg.pitch === 1) {
            for (let i = 0; i <= cc; i++) {
                const xl = i - 1;
                const xr = W - i;
                const y = top + i;
                cells.push({ x: xl, y, kind: "stair", f: "px" }, { x: xr, y, kind: "stair", f: "nx" });
                if (i >= 1)
                    cells.push({ x: xl, y: y - 1, kind: "fill" }, { x: xr, y: y - 1, kind: "fill" });
            }
            cells.push({ x: cc, y: top + cc + 1, kind: "slab" }, { x: cc, y: top + cc, kind: "fill" });
            roofTop = top + cc + 1;
        }
        else {
            for (let i = 0; i <= cc; i++) {
                const xl = i - 1;
                const xr = W - i;
                const y = top + 2 * i;
                cells.push({ x: xl, y, kind: "full" }, { x: xr, y, kind: "full" });
                cells.push({ x: xl, y: y + 1, kind: "stair", f: "px" }, { x: xr, y: y + 1, kind: "stair", f: "nx" });
                if (i >= 1)
                    cells.push({ x: xl, y: y - 1, kind: "fill" }, { x: xr, y: y - 1, kind: "fill" });
            }
            const yr = top + 2 * cc;
            cells.push({ x: cc, y: yr, kind: "full" }, { x: cc, y: yr + 1, kind: "full" }, { x: cc, y: yr + 2, kind: "slab" });
            cells.push({ x: cc, y: yr - 1, kind: "fill" });
            roofTop = yr + 2;
        }
        for (const cell of cells) {
            if (cell.kind === "fill") {
                if (cell.x < 0 || cell.x > W - 1)
                    continue;
                for (let z = 0; z < D; z++)
                    if (!has(cell.x, cell.y, z))
                        put(cell.x, cell.y, z, cfg.roofFill, 2);
                continue;
            }
            for (let z = -1; z <= D; z++) {
                if (cell.kind === "stair")
                    put(cell.x, cell.y, z, roof.stairs, 2, { f: cell.f, half: "bottom" });
                else if (cell.kind === "full")
                    put(cell.x, cell.y, z, roof.full, 2);
                else
                    put(cell.x, cell.y, z, roof.slab, 2, { half: "bottom" });
            }
        }
        // gable walls (front and back), with a small attic window
        for (const z of [0, D - 1]) {
            for (let x = 0; x < W; x++) {
                let lowest = Infinity;
                for (const cell of cells)
                    if (cell.x === x)
                        lowest = Math.min(lowest, cell.y);
                for (let y = top + 1; y < lowest; y++)
                    if (!has(x, y, z))
                        put(x, y, z, cfg.gableWall, 2);
            }
            let lowestC = Infinity;
            for (const cell of cells)
                if (cell.x === cc)
                    lowestC = Math.min(lowestC, cell.y);
            const hC = lowestC - (top + 1);
            if (hC >= 2)
                put(cc, top + 1 + Math.floor((hC - 1) / 2), z, cfg.window, 3);
        }
        if (o.style === 3) {
            // "cho fa" finials on both ends of the ridge
            put(cc, roofTop + 1, -1, wd.fence, 3);
            put(cc, roofTop + 1, D, wd.fence, 3);
            put(-1, top + 1, -1, wd.fence, 3);
            put(W, top + 1, -1, wd.fence, 3);
            put(-1, top + 1, D, wd.fence, 3);
            put(W, top + 1, D, wd.fence, 3);
        }
    }
    // ---------------- door
    const y0 = yf(0);
    if (o.style === 2) {
        for (let row = 1; row <= 3; row++) {
            put(c - 1, y0 + row, 0, wd.planks, 1);
            put(c + 1, y0 + row, 0, wd.planks, 1);
        }
        put(c, y0 + 3, 0, wd.planks, 1);
    }
    put(c, y0 + 1, 0, wd.door, 3, { f: "pz", upper: false, hinge: false }, { item: wd.door });
    put(c, y0 + 2, 0, wd.door, 3, { f: "pz", upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
    // ---------------- ladders between floors
    for (let f = 0; f < F - 1; f++) {
        for (let y = yf(f) + 1; y <= yf(f + 1); y++)
            put(ladderX, y, ladderZ, M + "ladder", 3, { f: "nz" });
    }
    // ---------------- porch / veranda
    let front = -3;
    if (cfg.veranda) {
        for (let x = 0; x < W; x++)
            for (let z = -3; z <= -1; z++)
                put(x, S, z, wd.planks, 1);
        for (let z = -3; z <= -1; z++) {
            put(0, S + 1, z, wd.fence, 3);
            put(W - 1, S + 1, z, wd.fence, 3);
        }
        for (let x = 1; x < W - 1; x++)
            if (x !== c)
                put(x, S + 1, -3, wd.fence, 3);
        // stairs down to the ground with a solid core underneath
        for (let i = 1; i <= S; i++) {
            const y = S - i;
            const z = -3 - i;
            put(c, y, z, wd.stairs, 3, { f: "pz", half: "bottom" });
            for (let yy = 0; yy < y; yy++)
                put(c, yy, z, wd.planks, 1);
        }
        front = -4 - S;
        if (o.light) {
            put(0, S + 2, -3, M + "lantern", 4, { hanging: false });
            put(W - 1, S + 2, -3, M + "lantern", 4, { hanging: false });
        }
    }
    else if (o.porch) {
        if (cfg.stepSlab)
            put(c, 0, -1, cfg.stepId, 3, { half: "bottom" });
        else
            put(c, 0, -1, cfg.stepId, 3, { f: "pz", half: "bottom" });
        if (o.style === 2) {
            // flat canopy over the door and low hedges along the facade
            for (let x = c - 1; x <= c + 1; x++)
                for (let z = -2; z <= -1; z++)
                    put(x, 3, z, M + "smooth_quartz_slab", 3, { half: "top" });
            for (const x of [1, 2, W - 3, W - 2]) {
                if (Math.abs(x - c) <= 1)
                    continue;
                put(x, 0, -1, (x + W) % 2 ? M + "azalea_leaves_flowered" : M + "azalea_leaves", 4, { persistent: true }, { opt: true });
            }
            front = -4;
        }
        // little awning above the door
        if (o.style !== 2)
            for (let x = c - 1; x <= c + 1; x++)
                put(x, 3, -1, roof.stairs, 3, { f: "pz", half: "bottom" });
        // lamp posts
        for (const x of o.style === 2 ? [] : [c - 3, c + 3]) {
            put(x, 0, -2, wd.fence, 3);
            put(x, 1, -2, wd.fence, 3);
            if (o.light)
                put(x, 2, -2, M + "lantern", 4, { hanging: false });
        }
        front = -4;
    }
    // ---------------- chimney
    if (o.chimney && cfg.chimney && cfg.roofType === "gable") {
        const zc = D - 3;
        const ch = cfg.chimney;
        for (let y = 0; y <= top + 3; y++) {
            del(-1, y, zc);
            put(-1, y, zc, ch, 1);
        }
        put(-1, top + 4, zc, M + "campfire", 4, undefined, { opt: true });
    }
    // ---------------- window sills (decoration)
    if (o.style === 0 || o.style === 1) {
        for (const wv of wins) {
            let ox = wv.x;
            let oz = wv.z;
            let f = "pz";
            if (wv.z === 0) {
                oz = -1;
                f = "pz";
            }
            else if (wv.z === D - 1) {
                oz = D;
                f = "nz";
            }
            else if (wv.x === 0) {
                ox = -1;
                f = "px";
            }
            else {
                ox = W;
                f = "nx";
            }
            if (!has(ox, wv.y - 1, oz) && !has(ox, wv.y, oz))
                put(ox, wv.y - 1, oz, wd.stairs, 4, { f, half: "top" }, { opt: true });
        }
    }
    // ---------------- lights
    if (o.light) {
        const zs = D <= 7 ? [Math.floor(D / 2)] : [Math.floor(D / 3), Math.floor((2 * D) / 3)];
        for (let f = 0; f < F; f++)
            for (const z of zs) {
                if (o.style === 2) {
                    // recessed ceiling lights (sit in the ceiling / next floor)
                    for (const x of W >= 9 ? [c - 2, c + 2] : [c])
                        put(x, yf(f) + 4, z, M + "sea_lantern", 4);
                }
                else
                    put(c, yf(f) + 3, z, M + "lantern", 4, { hanging: true });
            }
    }
    // ---------------- furniture (optional): every floor gets its own rooms
    if (o.furniture) {
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
            const rr = rolesFor(F, f);
            furnishRoom({
                y: yf(f), x0: 1, x1: W - 2, z0: 1, z1: D - 2, reserved, roles: rr.roles, beds: W >= 9 ? rr.beds : Math.min(rr.beds, 1),
                carpet: cfg.carpet, wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        }
    }
    // ---------------- finalize
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
        blocks, W, D, minX, maxX, minZ, maxZ, maxY: Math.max(maxY, roofTop), front, doorX: c,
        clearArea: { x0: 0, x1: W - 1, z0: 0, z1: D - 1 }, fillBlock: S > 0 ? wd.log : cfg.foundation,
    };
}
/** Bill of materials: item id -> { req, opt } counts. */
export function billOfMaterials(plan) {
    const m = new Map();
    for (const b of plan.blocks) {
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
/** Window rhythm for a wall run: J = dark jamb, G = glass, W = plain wall. */
function facadePattern(L) {
    if (L < 3)
        return new Array(L).fill("W");
    const n = L - 2;
    const g = Math.max(1, Math.round((n + 1) / 4));
    const glass = n - (g - 1);
    const sizes = [];
    for (let i = 0; i < g; i++)
        sizes.push(Math.floor(glass / g) + (i < glass % g ? 1 : 0));
    const out = ["J"];
    sizes.forEach((sz, i) => {
        for (let k = 0; k < sz; k++)
            out.push("G");
        if (i < sizes.length - 1)
            out.push("J");
    });
    out.push("J");
    return out;
}
function generateModern(o) {
    const sz = SIZES[Math.max(0, Math.min(2, o.size))];
    const W = sz.W;
    const D = sz.D;
    const F = Math.max(1, Math.min(3, Math.floor(o.floors)));
    const wid = WOODS[o.wood]?.id ?? "oak";
    const wd = woodSet(wid);
    const WHITE = M + "smooth_quartz";
    const BLACK = M + "black_concrete";
    const GRAY = M + "gray_concrete";
    const GLASS = M + "glass_pane";
    const SLAT = M + "stripped_" + wid + "_log";
    const sh = W >= 9 ? 4 : 3; // how far the upper floor is shifted sideways (cantilever)
    const xd = W >= 9 ? W - 3 : W - 2; // front door column
    const map = new Map();
    const key = (x, y, z) => `${x},${y},${z}`;
    const put = (x, y, z, id, ph, st, extra) => {
        map.set(key(x, y, z), { x, y, z, id, ph, st, ...extra });
    };
    const has = (x, y, z) => map.has(key(x, y, z));
    const del = (x, y, z) => map.delete(key(x, y, z));
    const boxes = [{ x0: 0, x1: W - 1, z0: 0, z1: D - 1, y0: 0 }];
    if (F >= 2)
        boxes.push({ x0: sh, x1: W - 1 + sh, z0: 0, z1: D - 1, y0: 4 });
    if (F >= 3)
        boxes.push({ x0: sh, x1: sh + W - 4, z0: 2, z1: D - 1, y0: 8 });
    const ladders = [];
    if (F >= 2)
        ladders.push({ x: W - 2, z: D - 2, from: 0 });
    if (F >= 3)
        ladders.push({ x: sh + 1, z: D - 2, from: 1 });
    const zMid = Math.floor(D / 2);
    const b2xm = Math.floor((sh + sh + W - 4) / 2);
    // doors onto the terraces
    const terraceDoors = [];
    if (F >= 2)
        terraceDoors.push({ x: sh, z: zMid, y: 4, f: "px" });
    if (F >= 3)
        terraceDoors.push({ x: b2xm, z: 2, y: 8, f: "pz" });
    // ---------------- boxes: floor, walls, roof slab
    boxes.forEach((b, i) => {
        const top = i === boxes.length - 1;
        // floor
        for (let x = b.x0; x <= b.x1; x++)
            for (let z = b.z0; z <= b.z1; z++) {
                const perim = x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1;
                put(x, b.y0, z, perim ? WHITE : wd.planks, 1);
            }
        // walls
        const wallMat = (x, z) => (i === 1 && (x >= W || (x === b.x1 && z > b.z0 && z < b.z1)) ? GRAY : WHITE);
        const glassRows = i >= 1 ? [1, 2, 3] : [1, 2];
        const faces = [];
        const run = (cells, front = false) => faces.push({ cells, front });
        if (i === 0) {
            const left = [];
            for (let x = 1; x <= xd - 2; x++)
                left.push([x, 0]);
            run(left, true);
            const right = [];
            for (let x = xd + 2; x <= W - 2; x++)
                right.push([x, 0]);
            run(right, true);
        }
        else {
            const fr = [];
            for (let x = b.x0 + 1; x <= b.x1 - 1; x++)
                fr.push([x, b.z0]);
            run(fr, true);
        }
        const back = [];
        for (let x = b.x0 + 1; x <= b.x1 - 1; x++)
            back.push([x, b.z1]);
        run(back);
        const lf = [];
        for (let z = b.z0 + 1; z <= b.z1 - 1; z++)
            lf.push([b.x0, z]);
        run(lf);
        const rt = [];
        for (let z = b.z0 + 1; z <= b.z1 - 1; z++)
            rt.push([b.x1, z]);
        run(rt);
        // corners
        for (const [x, z] of [[b.x0, b.z0], [b.x1, b.z0], [b.x0, b.z1], [b.x1, b.z1]])
            for (let row = 1; row <= 3; row++)
                put(x, b.y0 + row, z, WHITE, 1);
        for (const fc of faces) {
            const pat = facadePattern(fc.cells.length);
            const rows = fc.front ? glassRows : [1, 2];
            fc.cells.forEach(([x, z], idx) => {
                for (let row = 1; row <= 3; row++) {
                    const t = pat[idx];
                    let id = wallMat(x, z);
                    if (t === "G")
                        id = rows.includes(row) ? GLASS : BLACK;
                    else if (t === "J")
                        id = row <= Math.max(...rows) || rows.length === 2 ? BLACK : id;
                    put(x, b.y0 + row, z, id, id === GLASS ? 3 : 1);
                }
            });
        }
        // roof slab (also the floor edge / terrace of the box above)
        const zf = top ? b.z0 - 2 : b.z0 - 1;
        const xr = i === 0 && F === 1 ? b.x1 + sh + 1 : b.x1 + 1; // single storey: slab reaches over the patio
        for (let x = b.x0 - 1; x <= xr; x++)
            for (let z = zf; z <= b.z1 + 1; z++)
                put(x, b.y0 + 4, z, WHITE, 2);
    });
    // ---------------- upper floors laid over the slabs below (re-apply their floors)
    boxes.forEach((b, i) => {
        if (i === 0)
            return;
        for (let x = b.x0; x <= b.x1; x++)
            for (let z = b.z0; z <= b.z1; z++) {
                const perim = x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1;
                put(x, b.y0, z, perim ? WHITE : wd.planks, 1);
            }
    });
    // ---------------- terraces: glass railing on the open edge of each lower slab, planters by the wall
    for (let i = 0; i + 1 < boxes.length; i++) {
        const b = boxes[i];
        const up = boxes[i + 1];
        const y = b.y0 + 5;
        const inSlab = (x, z) => x >= b.x0 - 1 && x <= b.x1 + 1 && z >= b.z0 - 1 && z <= b.z1 + 1;
        const covered = (x, z) => x >= up.x0 && x <= up.x1 && z >= up.z0 && z <= up.z1;
        for (let x = b.x0 - 1; x <= b.x1 + 1; x++)
            for (let z = b.z0 - 1; z <= b.z1 + 1; z++) {
                if (covered(x, z))
                    continue;
                const edge = !inSlab(x + 1, z) || !inSlab(x - 1, z) || !inSlab(x, z + 1) || !inSlab(x, z - 1);
                if (edge)
                    put(x, y, z, GLASS, 3);
            }
        const planters = i === 0 ? [[up.x0 - 1, b.z0], [up.x0 - 1, b.z1]] : [[up.x0, up.z0 - 1], [up.x1, up.z0 - 1]];
        planters.forEach(([x, z], n) => put(x, y, z, n % 2 ? M + "azalea_leaves" : M + "azalea_leaves_flowered", 4, { persistent: true }, { opt: true }));
    }
    // ---------------- patio under the cantilever / canopy: slat columns, deck, optional pool
    const px0 = W;
    const px1 = W + sh - 1;
    for (const z of [0, D - 1])
        for (let y = 0; y <= 3; y++)
            put(px1, y, z, SLAT, 1, { axis: "y" });
    const pool = !!o.pool;
    for (let x = px0; x <= px1 + 1; x++)
        for (let z = -1; z <= D; z++) {
            const water = pool && x >= px0 && x <= px1 - 1 && z >= 1 && z <= D - 2;
            if (water) {
                put(x, -2, z, WHITE, 1, undefined, { tag: "pool", opt: true });
                put(x, -1, z, M + "water", 6, undefined, { tag: "pool", opt: true, item: null });
            }
            else
                put(x, -1, z, wd.planks, 1, undefined, { tag: pool ? "pool" : undefined, opt: true });
        }
    if (pool)
        for (let z = 1; z <= D - 2; z++)
            put(W - 1, -1, z, WHITE, 1, undefined, { tag: "pool", opt: true });
    // hedge along the far side of the patio
    for (let z = 0; z <= D - 1; z++)
        put(px1 + 2, 0, z, z % 3 === 1 ? M + "azalea_leaves_flowered" : M + "azalea_leaves", 4, { persistent: true }, { opt: true });
    // ---------------- front: door bay with wood cladding, step, flower bed
    for (let row = 1; row <= 3; row++) {
        put(xd - 1, row, 0, wd.planks, 1);
        put(xd + 1, row, 0, wd.planks, 1);
    }
    put(xd, 3, 0, wd.planks, 1);
    put(xd, 1, 0, wd.door, 3, { f: "pz", upper: false, hinge: false }, { item: wd.door });
    put(xd, 2, 0, wd.door, 3, { f: "pz", upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
    if (o.porch) {
        put(xd, 0, -1, M + "smooth_quartz_slab", 3, { half: "bottom" });
        const flowers = [M + "poppy", M + "red_tulip", M + "poppy", M + "azalea_leaves_flowered"];
        for (let x = 0; x <= xd - 2; x++) {
            put(x, 0, -1, M + "grass_block", 4, undefined, { opt: true });
            const fl = flowers[x % flowers.length];
            put(x, 1, -1, fl, 5, fl.includes("leaves") ? { persistent: true } : undefined, { opt: true, dep: [0, -1, 0] });
        }
    }
    // terrace doors
    for (const d of terraceDoors) {
        put(d.x, d.y + 1, d.z, wd.door, 3, { f: d.f, upper: false, hinge: false }, { item: wd.door });
        put(d.x, d.y + 2, d.z, wd.door, 3, { f: d.f, upper: true, hinge: false }, { item: null, dep: [0, -1, 0] });
        if (d.f === "px") {
            put(d.x, d.y + 3, d.z, BLACK, 1);
        }
        else
            put(d.x, d.y + 3, d.z, BLACK, 1);
    }
    // ---------------- ladders (and the holes they need)
    for (const l of ladders) {
        const b = boxes[l.from];
        const nb = boxes[l.from + 1];
        del(l.x, nb.y0, l.z);
        for (let y = b.y0 + 1; y <= nb.y0; y++)
            put(l.x, y, l.z, M + "ladder", 3, { f: "nz" });
    }
    // ---------------- recessed lights in the slabs + under the cantilever
    const ladderCells = new Set(ladders.map((l) => `${l.x},${l.z}`));
    if (o.light) {
        boxes.forEach((b) => {
            for (let x = b.x0 + 2; x <= b.x1 - 2; x += 3)
                for (let z = b.z0 + 2; z <= b.z1 - 2; z += 3) {
                    if (ladderCells.has(`${x},${z}`))
                        continue;
                    put(x, b.y0 + 4, z, M + "sea_lantern", 4);
                }
        });
        if (F === 1)
            for (let z = 2; z <= D - 3; z += 3)
                put(W + 1, 4, z, M + "sea_lantern", 4);
    }
    // ---------------- interior
    if (o.furniture) {
        boxes.forEach((b, i) => {
            const reserved = new Set();
            if (i === 0) {
                reserved.add(`${xd},1`);
                reserved.add(`${xd},2`);
            }
            for (const l of ladders) {
                if (l.from === i || l.from === i - 1) {
                    reserved.add(`${l.x},${l.z}`);
                    reserved.add(`${l.x},${l.z - 1}`);
                }
            }
            for (const d of terraceDoors) {
                if (d.y !== b.y0)
                    continue;
                if (d.f === "px") {
                    reserved.add(`${d.x + 1},${d.z}`);
                    reserved.add(`${d.x + 2},${d.z}`);
                }
                else {
                    reserved.add(`${d.x},${d.z + 1}`);
                    reserved.add(`${d.x},${d.z + 2}`);
                }
            }
            const rr = rolesFor(F, i);
            furnishRoom({
                y: b.y0, x0: b.x0 + 1, x1: b.x1 - 1, z0: b.z0 + 1, z1: b.z1 - 1, reserved, roles: rr.roles,
                beds: W >= 9 ? rr.beds : Math.min(rr.beds, 1), carpet: M + "white_carpet",
                wood: { fence: wd.fence, plate: wd.plate, stairs: wd.stairs },
            }, put, has);
        });
    }
    // ---------------- finalize
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
        blocks, W, D, minX, maxX, minZ, maxZ, maxY, front: minZ - 1, doorX: xd,
        clearArea: { x0: minX, x1: maxX, z0: minZ, z1: maxZ }, fillBlock: WHITE,
    };
}
