// "Build a mine": a staircase down to a chosen depth, then a main tunnel with side branches.
import { BlockPermutation, system } from "@minecraft/server";
import { NEVER_BREAK, ORES, toolKindFor } from "./data.js";
import { face, isStuck, resetProgress, steer, stopMoving } from "./move.js";
import { getName, getOwner, getSettings, notifyOwner, oreEnabled, setAi, setModeProp, setWorking } from "./state.js";
import { countId, getTool, isStorageFull, storageTake } from "./storage.js";
import { equipBest, hasToolKind, toolInfo } from "./tools.js";
import { DIR_VEC, center, dist, distXZ, floorV, isLava, isPassable, itemName, key, leftOf, rightOf, safeBlock, tellOwner, } from "./util.js";
import { beginBreak, handleFullStorage, needTool, tickBreak } from "./work.js";
const M = "minecraft:";
const REACH = 5.2;
export function defaultTunnelY(dimId) {
    if (dimId.includes("nether"))
        return 15; // ancient debris level
    if (dimId.includes("end"))
        return 40;
    return -54; // diamonds, redstone and lapis; safely above bedrock
}
// ------------------------------------------------------------------ persistence
const CACHE = new Map();
export function getMineJob(h) {
    if (CACHE.has(h.id))
        return CACHE.get(h.id) ?? undefined;
    const s = h.getDynamicProperty("h:mine");
    let j;
    if (typeof s === "string") {
        try {
            j = JSON.parse(s);
        }
        catch {
            j = undefined;
        }
    }
    CACHE.set(h.id, j ?? null);
    return j;
}
export function saveMineJob(h, j) {
    CACHE.set(h.id, j ?? null);
    h.setDynamicProperty("h:mine", j ? JSON.stringify(j) : undefined);
}
export function mineProgress(_h, j) {
    const n = planFor(j).length;
    return n ? Math.min(100, Math.floor((j.i / n) * 100)) : 100;
}
// ------------------------------------------------------------------ plan
const PLANS = new Map();
function planKey(j) {
    return `${j.dim}|${j.o.x},${j.o.y},${j.o.z}|${j.d}|${j.ty}|${j.ml}|${j.bl}|${j.sp}`;
}
function planFor(j) {
    return planData(j).steps;
}
function planData(j) {
    const k = planKey(j);
    let p = PLANS.get(k);
    if (!p) {
        const steps = generateMine(j);
        const cellStep = new Map();
        steps.forEach((s, i) => s.cells.forEach((c) => cellStep.set(key(c), i)));
        p = { steps, cellStep };
        if (PLANS.size > 16)
            PLANS.clear();
        PLANS.set(k, p);
    }
    return p;
}
const up = (p, n) => ({ x: p.x, y: p.y + n, z: p.z });
const step = (p, d, n = 1) => ({ x: p.x + DIR_VEC[d].x * n, y: p.y, z: p.z + DIR_VEC[d].z * n });
export function generateMine(j) {
    const steps = [];
    let bseg = 100;
    let p = { ...j.o };
    // staircase: one forward, one down, 3 blocks of head room
    let n = 0;
    while (p.y > j.ty) {
        const nx = step(p, j.d);
        nx.y = p.y - 1;
        n++;
        steps.push({
            from: p, cells: [up(nx, 2), up(nx, 1), nx], floor: [up(nx, -1)], seg: 0, kind: "stairs",
            torch: n % 8 === 0 ? nx : undefined,
        });
        p = nx;
    }
    for (let k = 1; k <= j.ml; k++) {
        const nx = step(p, j.d);
        steps.push({
            from: p, cells: [up(nx, 1), nx], floor: [up(nx, -1)], seg: 1, kind: "main",
            torch: k % 6 === 1 ? nx : undefined,
        });
        p = nx;
        if (k % j.sp === 0 && k < j.ml) {
            for (const side of [leftOf(j.d), rightOf(j.d)]) {
                bseg++;
                let q = p;
                for (let b = 1; b <= j.bl; b++) {
                    const nq = step(q, side);
                    steps.push({
                        from: q, cells: [up(nq, 1), nq], floor: [up(nq, -1)], seg: bseg, kind: "branch",
                        torch: b === j.bl || b % 7 === 0 ? nq : undefined,
                    });
                    q = nq;
                }
            }
            // the next main step starts from the junction again (steps[].from = p)
        }
    }
    return steps;
}
// ------------------------------------------------------------------ start / stop
export function startMineJob(h, owner, dir, opts) {
    const o = opts.origin ? floorV(opts.origin) : floorV(h.location);
    if (!opts.origin)
        o.y = Math.floor(h.location.y + 0.5);
    let ty = defaultTunnelY(h.dimension.id);
    if (opts.flat)
        ty = o.y;
    else if (opts.y !== undefined)
        ty = opts.y;
    else if (opts.depth !== undefined)
        ty = o.y - Math.max(1, opts.depth);
    try {
        ty = Math.max(h.dimension.heightRange.min + 5, Math.min(o.y, ty));
    }
    catch {
        ty = Math.min(o.y, ty);
    }
    if (h.dimension.id.includes("nether"))
        ty = Math.max(6, ty);
    const j = {
        o, d: dir, ty, ml: Math.max(4, Math.min(96, opts.length ?? 32)), bl: Math.max(0, Math.min(32, opts.branch ?? 10)),
        sp: 4, i: 0, dim: h.dimension.id, dug: 0, ores: 0,
    };
    if (j.bl === 0)
        j.sp = 9999;
    saveMineJob(h, j);
    const m = mr(h);
    m.t = undefined;
    m.vein = [];
    m.retry = 0;
    void owner;
    return j;
}
export function cancelMineJob(h) {
    const m = mr(h);
    m.t = undefined;
    m.vein = [];
    saveMineJob(h, undefined);
    setWorking(h, false);
}
export function describeMine(j) {
    const depth = j.o.y - j.ty;
    const dirTh = { north: "ทิศเหนือ", south: "ทิศใต้", east: "ทิศตะวันออก", west: "ทิศตะวันตก" };
    return (depth > 0 ? `บันไดลง ${depth} บล็อก (ถึงระดับ Y=${j.ty}) ` : `ที่ระดับ Y=${j.ty} `) +
        `อุโมงค์หลักยาว ${j.ml} บล็อกไป${dirTh[j.d]}` + (j.bl > 0 ? ` แยกกิ่งซ้าย-ขวายาว ${j.bl} บล็อกทุกๆ ${j.sp} บล็อก` : "");
}
const MR = new Map();
function mr(h) {
    let m = MR.get(h.id);
    if (!m) {
        m = { vein: [], retry: 0, stepAt: -1, tp: 0 };
        MR.set(h.id, m);
    }
    return m;
}
const SIDES = [
    { x: 1, y: 0, z: 0 }, { x: -1, y: 0, z: 0 }, { x: 0, y: 1, z: 0 }, { x: 0, y: -1, z: 0 }, { x: 0, y: 0, z: 1 }, { x: 0, y: 0, z: -1 },
];
const FILL_PREF = [
    "cobblestone", "cobbled_deepslate", "netherrack", "dirt", "andesite", "diorite", "granite", "tuff", "blackstone",
    "basalt", "end_stone", "stone", "deepslate",
].map((x) => M + x);
/** Places a solid filler block (from the bag when possible). */
function placeFiller(h, p) {
    const b = safeBlock(h.dimension, p);
    if (!b)
        return;
    let id = M + "cobblestone";
    for (const f of FILL_PREF) {
        if (countId(h, f) > 0) {
            storageTake(h, f, 1);
            id = f;
            break;
        }
    }
    try {
        b.setPermutation(BlockPermutation.resolve(id));
    }
    catch {
        try {
            b.setType(M + "cobblestone");
        }
        catch {
            /* ignore */
        }
    }
}
/** Stops water and lava from pouring into a freshly opened block. */
function seal(h, p, cellStep, cur) {
    const dim = h.dimension;
    for (const o of SIDES) {
        const q = { x: p.x + o.x, y: p.y + o.y, z: p.z + o.z };
        const b = safeBlock(dim, q);
        if (!b || !b.isLiquid)
            continue;
        const si = cellStep.get(key(q));
        // water that already flowed into our own (dug) tunnel is harmless; lava never is
        if (si !== undefined && si < cur && !isLava(b))
            continue;
        placeFiller(h, q);
    }
}
function blockedSegment(h, j, steps, what) {
    const s = steps[j.i];
    if (s.kind === "stairs") {
        // could not go deeper: make the tunnel at this level instead
        j.ty = s.from.y;
        notifyOwner(h, "mine:deep", `ลงลึกต่อไม่ได้ (เจอ ${what}) เลยขุดอุโมงค์ที่ระดับ Y=${j.ty} แทนนะ`, 20 * 10);
        saveMineJob(h, j);
        return;
    }
    if (s.kind === "main") {
        notifyOwner(h, "mine:main", `อุโมงค์หลักไปต่อไม่ได้ (เจอ ${what}) งานสร้างเหมืองจบแค่นี้`, 20 * 10);
        j.i = steps.length;
        saveMineJob(h, j);
        return;
    }
    const seg = s.seg;
    while (j.i < steps.length && steps[j.i].seg === seg)
        j.i++;
    saveMineJob(h, j);
}
/** Moves to the spot to dig from. Returns true when standing there. */
function goTo(h, m, p) {
    const goal = { x: p.x + 0.5, y: p.y, z: p.z + 0.5 };
    const dxz = distXZ(h.location, goal);
    const dy = Math.abs(h.location.y - p.y);
    if (dxz <= 0.7 && dy <= 1.2) {
        stopMoving(h);
        return true;
    }
    const now = system.currentTick;
    const tp = () => {
        if (now - m.tp < 10)
            return;
        m.tp = now;
        try {
            h.teleport(goal);
        }
        catch {
            /* ignore */
        }
        resetProgress(h);
    };
    if (dxz > 12 || dy > 3) {
        tp();
        return false;
    }
    const res = steer(h, goal, 0.45);
    if (res.status === "blocked" || res.status === "cliff" || isStuck(h, dxz, 30))
        tp();
    return false;
}
function finish(h, j) {
    const owner = getOwner(h);
    tellOwner(owner, getName(h), `§aสร้างเหมืองเสร็จแล้ว!§r ขุดไป ${j.dug} บล็อก ได้แร่ ${j.ores} ก้อน ผมรออยู่ในเหมืองนะ (ทางเข้าอยู่ที่ ${j.o.x}, ${j.o.y}, ${j.o.z})`);
    cancelMineJob(h);
    setModeProp(h, "stay");
    setAi(h, "stay");
}
function placeTorch(h, p) {
    const dim = h.dimension;
    const b = safeBlock(dim, p);
    const below = safeBlock(dim, { x: p.x, y: p.y - 1, z: p.z });
    if (!b || !b.isAir || !below || isPassable(below) || below.isLiquid)
        return;
    if (countId(h, M + "torch") <= 0) {
        notifyOwner(h, "mine:torch", "ถ้าใส่ §eคบเพลิง (Torch)§r ไว้ในกระเป๋าผม ผมจะปักไฟในเหมืองให้ด้วยนะ", 20 * 300);
        return;
    }
    try {
        b.setPermutation(BlockPermutation.resolve(M + "torch", { torch_facing_direction: "top" }));
        storageTake(h, M + "torch", 1);
    }
    catch {
        /* ignore */
    }
}
function afterDug(h, j, m, p, id) {
    const { cellStep } = planData(j);
    j.dug++;
    if (ORES[id])
        j.ores++;
    seal(h, p, cellStep, j.i);
    // ores in the walls: mine the whole vein
    const s = getSettings(h);
    for (const o of SIDES) {
        const q = { x: p.x + o.x, y: p.y + o.y, z: p.z + o.z };
        if (cellStep.has(key(q)) || m.vein.some((v) => v.x === q.x && v.y === q.y && v.z === q.z))
            continue;
        const b = safeBlock(h.dimension, q);
        const ore = b ? ORES[b.typeId] : undefined;
        if (ore && oreEnabled(s, ore.group) && m.vein.length < 48)
            m.vein.push(q);
    }
}
/** Main tick of the "build a mine" mode. */
export function tickDig(h, _owner) {
    const j = getMineJob(h);
    if (!j) {
        setModeProp(h, "stay");
        return;
    }
    const m = mr(h);
    setAi(h, "work");
    if (h.dimension.id !== j.dim) {
        stopMoving(h);
        notifyOwner(h, "mine:dim", "เหมืองที่สั่งไว้อยู่คนละมิติกับผม พาผมกลับไปก่อนนะ", 20 * 60);
        return;
    }
    const { steps, cellStep } = planData(j);
    const dim = h.dimension;
    // ---- breaking in progress
    if (m.t?.breaking) {
        const id = m.t.breakingId ?? "";
        const done = tickBreak(h, m.t);
        if (done) {
            afterDug(h, j, m, done, id);
            m.t = undefined;
            if (j.dug % 8 === 0)
                saveMineJob(h, j);
        }
        else if (!m.t.breaking)
            m.t = undefined;
        return;
    }
    setWorking(h, false);
    if (isStorageFull(h)) {
        handleFullStorage(h);
        if (isStorageFull(h)) {
            stopMoving(h);
            return;
        }
    }
    if (j.i >= steps.length)
        return finish(h, j);
    const st = steps[j.i];
    if (m.stepAt !== j.i) {
        m.stepAt = j.i;
        m.retry = 0;
    }
    if (!goTo(h, m, st.from))
        return;
    const eye = { x: h.location.x, y: h.location.y + 1.6, z: h.location.z };
    // ---- ore veins found in the walls
    while (m.vein.length) {
        const p = m.vein[0];
        const b = safeBlock(dim, p);
        const ore = b ? ORES[b.typeId] : undefined;
        if (!b || !ore || dist(eye, center(p)) > REACH) {
            m.vein.shift();
            continue;
        }
        const pick = equipBest(h, "pickaxe");
        const info = toolInfo(pick);
        if (!info || info.level < ore.tier) {
            m.vein.shift();
            if (!info)
                needTool(h, "pickaxe");
            else
                notifyOwner(h, "tier:" + b.typeId, `เจอ §b${itemName(b.typeId)}§r ข้างทาง แต่อีเต้อนี้ขุดไม่ได้`, 20 * 60);
            continue;
        }
        m.t = mkTask(p, b.typeId);
        if (!beginBreak(h, m.t, b))
            m.t = undefined;
        return;
    }
    // ---- the blocks of this step
    for (const c of st.cells) {
        const b = safeBlock(dim, c);
        if (!b) {
            stopMoving(h);
            return; // chunk not loaded yet
        }
        if (b.isLiquid) {
            seal(h, c, cellStep, j.i);
            placeFiller(h, c); // turn the liquid into a block and dig it out next tick
            return;
        }
        if (isPassable(b) && !b.typeId.includes("web"))
            continue;
        const id = b.typeId;
        let container = false;
        try {
            container = !!b.getComponent("minecraft:inventory");
        }
        catch {
            container = false;
        }
        if (NEVER_BREAK.has(id) || container) {
            blockedSegment(h, j, steps, itemName(id));
            return;
        }
        if (++m.retry > 60) {
            blockedSegment(h, j, steps, "ทรายหรือกรวดที่ไหลลงมาไม่หยุด");
            return;
        }
        const kind = toolKindFor(id);
        if (kind === "pickaxe" || kind === "shovel") {
            if (!hasToolKind(h, kind)) {
                needTool(h, kind);
                stopMoving(h);
                m.retry--;
                return;
            }
            if (kind === "pickaxe" && ORES[id]) {
                const info = toolInfo(equipBest(h, "pickaxe"));
                if (!info || info.level < ORES[id].tier) {
                    notifyOwner(h, "mine:tier", `ทางเหมืองมี §b${itemName(id)}§r ขวางอยู่ แต่อีเต้อของผมขุดไม่ได้ ส่งอีเต้อที่ดีกว่านี้ให้หน่อยนะ (ผมรออยู่)`, 20 * 45);
                    stopMoving(h);
                    m.retry--;
                    return;
                }
            }
        }
        face(h, center(c));
        m.t = mkTask(c, id);
        if (!beginBreak(h, m.t, b, true)) {
            m.t = undefined;
            m.retry--;
            stopMoving(h);
        }
        return;
    }
    // ---- step done: solid floor, light, next
    for (const f of st.floor) {
        const b = safeBlock(dim, f);
        if (b && (isPassable(b) || b.isLiquid))
            placeFiller(h, f);
    }
    if (st.torch)
        placeTorch(h, st.torch);
    j.i++;
    saveMineJob(h, j);
    if (j.i % 20 === 0) {
        notifyOwner(h, "mine:progress", `สร้างเหมืองไปแล้ว ${mineProgress(h, j)}%`, 20 * 60);
    }
}
function mkTask(p, id) {
    return { kind: "mine", target: p, targetId: id, progress: 0, need: 0, started: system.currentTick, phase: "work", fails: 0 };
}
export function mineStatus(h) {
    const j = getMineJob(h);
    if (!j)
        return undefined;
    const tool = getTool(h);
    return `${mineProgress(h, j)}%${tool ? "" : " ไม่มีเครื่องมือ"}`;
}
export function forgetMineRuntime(id) {
    MR.delete(id);
    CACHE.delete(id);
}
