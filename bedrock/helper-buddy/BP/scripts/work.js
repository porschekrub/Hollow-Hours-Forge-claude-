import { BlockPermutation, system } from "@minecraft/server";
import { DIG_SOFT, DIG_STONE, LEAF_LIKE, LOGS, ORES, PLANTABLE_SOIL, SAPLING_FOR_LOG, isTreeLoot } from "./data.js";
import { hopToward, isStuck, resetProgress, face, steer, stopMoving } from "./move.js";
import { scanNearest } from "./scan.js";
import { getHome, getMode, getName, getOwner, getSettings, notifyOwner, oreEnabled, rt, setAi, setHome, setModeProp, setWorking, } from "./state.js";
import { countId, depositToContainers, getContainer, getTool, isStorageFull, nearbyContainers, storageAdd, storageTake, storeOrDrop, } from "./storage.js";
import { applyMending, breakPlan, computeDrops, damageTool, equipBest, hasToolKind, toolInfo, } from "./tools.js";
import { JUNK, toolKindFor } from "./data.js";
import { addXp, oreXp } from "./combat.js";
import { report } from "./progress.js";
import { center, dist, distXZ, floorV, isLava, isPassable, itemName, key, safeBlock, tellOwner } from "./util.js";
export const SEARCH_RADIUS = 100; // v1.6: how far the helper can "see" ores, trees and blocks
const HOME_RADIUS = 100; // it roams around the spot where it was sent to work
const REACH = 3.3; // eye -> block centre distance needed to mine an ore
const TREE_REACH = 13; // lumberjack skill: can chop the whole tree from the base
const TASK_TIMEOUT = 20 * 90;
function eyeOf(h) {
    return { x: h.location.x, y: h.location.y + 1.6, z: h.location.z };
}
function same(a, b) {
    return a.x === b.x && a.y === b.y && a.z === b.z;
}
function blacklist(h, p, ticks = 20 * 45) {
    rt(h).blacklist.set(key(p), system.currentTick + ticks);
}
function isBlacklisted(h, p) {
    const r = rt(h);
    const need = r.needBl.get(key(p));
    if (need) {
        if (!hasToolKind(h, need))
            return true;
        r.needBl.delete(key(p)); // got the tool now: try again
    }
    const until = r.blacklist.get(key(p));
    if (until === undefined)
        return false;
    if (system.currentTick > until) {
        r.blacklist.delete(key(p));
        return false;
    }
    return true;
}
export function endTask(h) {
    const r = rt(h);
    r.task = undefined;
    setWorking(h, false);
    stopMoving(h);
}
export function hasLavaNeighbour(dim, p) {
    const n = [
        { x: 1, y: 0, z: 0 }, { x: -1, y: 0, z: 0 }, { x: 0, y: 1, z: 0 }, { x: 0, y: -1, z: 0 }, { x: 0, y: 0, z: 1 }, { x: 0, y: 0, z: -1 },
    ];
    for (const o of n)
        if (isLava(safeBlock(dim, { x: p.x + o.x, y: p.y + o.y, z: p.z + o.z })))
            return true;
    return false;
}
const TOOL_TH = {
    pickaxe: "§eอีเต้อ (Pickaxe)§r สำหรับขุดหินและแร่",
    shovel: "§eพลั่ว (Shovel)§r สำหรับขุดดิน ทราย และกรวด",
    axe: "§eขวาน (Axe)§r สำหรับตัดไม้",
};
/** Tells the owner which tool is missing (rate limited). */
export function needTool(h, kind) {
    rt(h).lastNeed = { kind, at: system.currentTick };
    notifyOwner(h, "need:" + kind, `ผมต้องการ${TOOL_TH[kind] ?? kind} ส่งให้ผมหน่อยนะ (ส่งจากระยะไกลได้ด้วยหนังสือสั่งงาน)`, 20 * 45);
}
/** Natural block the helper may dig through — only with the right tool for it. */
export function canDigThrough(h, b, quiet = false) {
    if (!getSettings(h).dig)
        return false;
    const id = b.typeId;
    if (DIG_STONE[id] === undefined && DIG_SOFT[id] === undefined && !ORES[id])
        return false;
    if (hasLavaNeighbour(h.dimension, b.location))
        return false;
    const kind = toolKindFor(id);
    if (kind === "pickaxe" || kind === "shovel") {
        if (!hasToolKind(h, kind)) {
            if (!quiet)
                needTool(h, kind);
            else
                rt(h).lastNeed = { kind, at: system.currentTick };
            return false;
        }
    }
    return true;
}
function isExposed(dim, p) {
    const n = [
        { x: 1, y: 0, z: 0 }, { x: -1, y: 0, z: 0 }, { x: 0, y: 1, z: 0 }, { x: 0, y: -1, z: 0 }, { x: 0, y: 0, z: 1 }, { x: 0, y: 0, z: -1 },
    ];
    for (const o of n)
        if (isPassable(safeBlock(dim, { x: p.x + o.x, y: p.y + o.y, z: p.z + o.z })))
            return true;
    return false;
}
/** First solid block on the straight line from `from` to the centre of `target`. */
function firstSolidOnRay(dim, from, target) {
    const c = center(target);
    const dx = c.x - from.x;
    const dy = c.y - from.y;
    const dz = c.z - from.z;
    const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (d < 0.01)
        return undefined;
    try {
        const hit = dim.getBlockFromRay(from, { x: dx / d, y: dy / d, z: dz / d }, {
            includePassableBlocks: false,
            includeLiquidBlocks: false,
            maxDistance: Math.ceil(d) + 1,
        });
        return hit?.block;
    }
    catch {
        return undefined;
    }
}
/** True when some visible face of the target block can be seen from the eye (nothing solid in between). */
function canSee(dim, eye, target) {
    const pts = [center(target)];
    const nb = (dx, dy, dz) => isPassable(safeBlock(dim, { x: target.x + dx, y: target.y + dy, z: target.z + dz }));
    if (eye.y > target.y + 1 && nb(0, 1, 0))
        pts.push({ x: target.x + 0.5, y: target.y + 0.98, z: target.z + 0.5 });
    if (eye.y < target.y && nb(0, -1, 0))
        pts.push({ x: target.x + 0.5, y: target.y + 0.02, z: target.z + 0.5 });
    if (eye.x > target.x + 1 && nb(1, 0, 0))
        pts.push({ x: target.x + 0.98, y: target.y + 0.5, z: target.z + 0.5 });
    if (eye.x < target.x && nb(-1, 0, 0))
        pts.push({ x: target.x + 0.02, y: target.y + 0.5, z: target.z + 0.5 });
    if (eye.z > target.z + 1 && nb(0, 0, 1))
        pts.push({ x: target.x + 0.5, y: target.y + 0.5, z: target.z + 0.98 });
    if (eye.z < target.z && nb(0, 0, -1))
        pts.push({ x: target.x + 0.5, y: target.y + 0.5, z: target.z + 0.02 });
    for (const q of pts) {
        const dx = q.x - eye.x, dy = q.y - eye.y, dz = q.z - eye.z;
        const d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.01)
            return true;
        try {
            const hit = dim.getBlockFromRay(eye, { x: dx / d, y: dy / d, z: dz / d }, {
                includePassableBlocks: false, includeLiquidBlocks: false, maxDistance: Math.ceil(d) + 1,
            });
            if (!hit || same(hit.block.location, target))
                return true;
        }
        catch {
            /* ignore */
        }
    }
    return false;
}
// ---------------------------------------------------------------- scanning
const BURIED_RADIUS = 10; // with "dig" on it also tunnels to ores hidden inside the rock nearby
function findOre(h) {
    const s = getSettings(h);
    const info = toolInfo(getTool(h));
    if (!info || info.kind !== "pickaxe")
        return undefined;
    const dim = h.dimension;
    const only = rt(h).oreOnly;
    const types = Object.keys(ORES).filter((id) => (only ? only.includes(ORES[id].group) : oreEnabled(s, ORES[id].group)));
    let tooHard;
    const eye = eyeOf(h);
    const ok = (b) => {
        const o = ORES[b.typeId];
        if (!o || isBlacklisted(h, b.location))
            return false;
        if (o.tier > info.level) {
            tooHard = b.typeId;
            return false;
        }
        return true;
    };
    // ores it can actually see (touching air / caves) within SEARCH_RADIUS blocks
    const seen = scanNearest(dim, eye, SEARCH_RADIUS, types, (b) => ok(b) && isExposed(dim, b.location));
    // ores hidden in the rock close by
    const buried = s.dig ? scanNearest(dim, eye, BURIED_RADIUS, types, ok) : undefined;
    let best = seen;
    if (buried && (!seen || dist(center(buried.location), eye) * 1.6 < dist(center(seen.location), eye)))
        best = buried;
    if (!best && tooHard) {
        notifyOwner(h, "tier:" + tooHard, `เจอ §b${itemName(tooHard)}§r แต่อีเต้อนี้ขุดไม่ได้ ต้องใช้อีเต้อที่ดีกว่านี้`, 20 * 60);
    }
    if (!best)
        return undefined;
    return {
        kind: "mine", target: best.location, targetId: best.typeId, progress: 0, need: 0,
        started: system.currentTick, phase: "approach", fails: 0,
    };
}
// ---------------------------------------------------------------- gathering blocks
export function getGatherIds(h) {
    const r = rt(h);
    if (r.gatherIds?.length)
        return r.gatherIds;
    const v = h.getDynamicProperty("h:gather");
    if (typeof v === "string") {
        try {
            const ids = JSON.parse(v);
            if (Array.isArray(ids) && ids.length) {
                r.gatherIds = ids;
                return ids;
            }
        }
        catch {
            /* ignore */
        }
    }
    return [];
}
export function setGatherIds(h, ids) {
    rt(h).gatherIds = ids?.length ? ids : undefined;
    h.setDynamicProperty("h:gather", ids?.length ? JSON.stringify(ids) : undefined);
}
function standsOn(dim, p) {
    try {
        for (const pl of dim.getPlayers({ location: center(p), maxDistance: 2.5 })) {
            if (Math.floor(pl.location.x) === p.x && Math.floor(pl.location.z) === p.z && Math.floor(pl.location.y - 0.5) === p.y)
                return true;
        }
    }
    catch {
        /* ignore */
    }
    return false;
}
function findGather(h) {
    const ids = getGatherIds(h);
    if (!ids.length)
        return undefined;
    const dim = h.dimension;
    const eye = eyeOf(h);
    const dig = getSettings(h).dig;
    const best = scanNearest(dim, eye, SEARCH_RADIUS, ids, (b) => {
        if (isBlacklisted(h, b.location) || standsOn(dim, b.location))
            return false;
        if (hasLavaNeighbour(dim, b.location))
            return false;
        if (isExposed(dim, b.location))
            return true;
        return dig && dist(center(b.location), eye) <= 6;
    });
    if (!best)
        return undefined;
    return {
        kind: "mine", ids, target: best.location, targetId: best.typeId, progress: 0, need: 0,
        started: system.currentTick, phase: "approach", fails: 0,
    };
}
function analyzeTree(dim, start) {
    const seen = new Set([key(start)]);
    const queue = [start];
    const logs = [];
    let natural = false;
    let logId = "";
    while (queue.length && logs.length < 220) {
        const p = queue.shift();
        const b = safeBlock(dim, p);
        if (!b || !LOGS.has(b.typeId))
            continue;
        if (!logId)
            logId = b.typeId;
        logs.push(p);
        for (let dx = -1; dx <= 1; dx++)
            for (let dy = -1; dy <= 1; dy++)
                for (let dz = -1; dz <= 1; dz++) {
                    if (!dx && !dy && !dz)
                        continue;
                    const q = { x: p.x + dx, y: p.y + dy, z: p.z + dz };
                    if (Math.abs(q.x - start.x) > 8 || Math.abs(q.z - start.z) > 8 || q.y < start.y - 12 || q.y > start.y + 32)
                        continue;
                    const k = key(q);
                    if (seen.has(k))
                        continue;
                    seen.add(k);
                    const nb = safeBlock(dim, q);
                    if (!nb)
                        continue;
                    if (LOGS.has(nb.typeId))
                        queue.push(q);
                    else if (!natural && LEAF_LIKE.has(nb.typeId)) {
                        let persistent = false;
                        try {
                            persistent = nb.permutation.getState("persistent_bit") === true;
                        }
                        catch {
                            persistent = false;
                        }
                        if (!persistent)
                            natural = true;
                    }
                }
    }
    let minY = Infinity;
    for (const l of logs)
        minY = Math.min(minY, l.y);
    const base = logs.filter((l) => l.y === minY);
    return { logs, base, natural, logId };
}
/** True when the log at `p` belongs to a natural tree (has non-persistent leaves), not a player's build. */
export function isNaturalTreeAt(dim, p, known) {
    const k = key(p);
    const hit = known.get(k);
    if (hit !== undefined)
        return hit;
    const t = analyzeTree(dim, p);
    for (const l of t.logs)
        known.set(key(l), t.natural);
    known.set(k, t.natural);
    return t.natural;
}
function findTree(h) {
    const info = toolInfo(getTool(h));
    if (!info || info.kind !== "axe")
        return undefined;
    const dim = h.dimension;
    const types = [...LOGS];
    const from = { x: h.location.x, y: h.location.y + 1, z: h.location.z };
    for (let attempt = 0; attempt < 5; attempt++) {
        const found = scanNearest(dim, from, SEARCH_RADIUS, types, (b) => !isBlacklisted(h, b.location));
        if (!found)
            return undefined;
        const t = analyzeTree(dim, found.location);
        if (!t.natural || !t.logs.length) {
            for (const l of t.logs)
                blacklist(h, l, 20 * 300);
            blacklist(h, found.location, 20 * 300);
            continue;
        }
        // base log closest to the helper
        t.base.sort((a, b) => distXZ(center(a), h.location) - distXZ(center(b), h.location));
        const target = t.base[0];
        return {
            kind: "chop", target, targetId: t.logId, progress: 0, need: 0, started: system.currentTick,
            phase: "approach", tree: t.logs, treeBase: t.base, treeLogId: t.logId, fails: 0,
        };
    }
    return undefined;
}
// ---------------------------------------------------------------- breaking
/** Starts breaking a block with the right tool. Returns false (and asks for it) when the tool is missing. */
export function beginBreak(h, t, b, axeByHand = false) {
    const kind = toolKindFor(b.typeId);
    let hand = kind === "hand" || LEAF_LIKE.has(b.typeId);
    if (!hand) {
        const k = kind;
        if (toolInfo(getTool(h))?.kind !== k && !equipBest(h, k)) {
            if (axeByHand && kind === "axe")
                hand = true; // wood can be broken by hand (slowly)
            else {
                needTool(h, kind);
                return false;
            }
        }
    }
    t.byHand = hand;
    const plan = breakPlan(b.typeId, getTool(h), hand);
    t.breaking = b.location;
    t.breakingId = b.typeId;
    t.progress = 0;
    t.need = plan.ticks;
    setWorking(h, true);
    stopMoving(h);
    face(h, center(b.location));
    return true;
}
function hitSound(id) {
    if (LOGS.has(id))
        return "hit.wood";
    if (DIG_SOFT[id] !== undefined)
        return "hit.gravel";
    if (id.includes("deepslate"))
        return "hit.deepslate";
    return "hit.stone";
}
function breakSound(id) {
    if (LOGS.has(id))
        return "dig.wood";
    if (LEAF_LIKE.has(id))
        return "dig.grass";
    if (id === "minecraft:sand" || id === "minecraft:red_sand")
        return "dig.sand";
    if (DIG_SOFT[id] !== undefined)
        return id.includes("grass") ? "dig.grass" : "dig.gravel";
    if (id.includes("deepslate"))
        return "dig.deepslate";
    if (id === "minecraft:netherrack" || id === "minecraft:nether_gold_ore" || id === "minecraft:quartz_ore")
        return "dig.netherrack";
    return "dig.stone";
}
/** Continues breaking the current block. Returns the broken block location when finished. */
export function tickBreak(h, t) {
    const dim = h.dimension;
    const p = t.breaking;
    const b = safeBlock(dim, p);
    if (!b || b.typeId !== t.breakingId) {
        t.breaking = undefined;
        setWorking(h, false);
        return undefined;
    }
    face(h, center(p));
    stopMoving(h);
    t.progress++;
    if (t.progress % 4 === 1) {
        try {
            dim.playSound(hitSound(b.typeId), center(p), { volume: 0.6, pitch: 0.9 + Math.random() * 0.2 });
        }
        catch {
            /* ignore */
        }
    }
    if (t.progress < t.need)
        return undefined;
    // ---- the block breaks
    const tool = getTool(h);
    const hand = !!t.byHand || toolKindFor(b.typeId) === "hand" || LEAF_LIKE.has(b.typeId);
    const plan = breakPlan(b.typeId, tool, hand);
    const drops = computeDrops(b, tool, plan, hand);
    const id = b.typeId;
    try {
        b.setType("minecraft:air");
    }
    catch {
        t.breaking = undefined;
        return undefined;
    }
    try {
        dim.playSound(breakSound(id), center(p), { volume: 1, pitch: 0.9 + Math.random() * 0.2 });
        dim.spawnParticle("minecraft:basic_crit_particle", center(p));
    }
    catch {
        /* ignore */
    }
    report(h, "break", id);
    if (ORES[id])
        addXp(h, oreXp(ORES[id].group));
    else if (LOGS.has(id))
        addXp(h, 1);
    else if (!LEAF_LIKE.has(id))
        addXp(h, 0.2);
    let spilled = false;
    const keepJunk = getSettings(h).keepJunk;
    // blocks collected on purpose (gather mode) are always kept
    const wanted = getMode(h) === "gather" && getGatherIds(h).includes(id);
    for (const it of drops.items) {
        if (!keepJunk && !wanted && JUNK.has(it.typeId) && countId(h, it.typeId) >= 64)
            continue; // throw the extra rubble away
        if (!storeOrDrop(h, it, center(p)))
            spilled = true;
        report(h, "item", it.typeId, it.amount);
    }
    if (drops.xp > 0) {
        const left = applyMending(h, drops.xp);
        if (left > 0) {
            const owner = getOwner(h); // the XP goes to the owner wherever they are
            try {
                owner?.addExperience(left);
                if (owner && ownerNear(h, 32))
                    owner.playSound("random.orb", { volume: 0.4, pitch: 1.2 });
            }
            catch {
                /* ignore */
            }
        }
    }
    if (!hand && tool) {
        const res = damageTool(h);
        if (res.broke) {
            try {
                dim.playSound("random.break", h.location, { volume: 1 });
            }
            catch {
                /* ignore */
            }
            const spare = toolKindFor(id);
            const more = spare !== "hand" && hasToolKind(h, spare);
            tellOwner(getOwner(h), getName(h), more
                ? `§c${res.name} พังแล้ว!§r ผมเปลี่ยนไปใช้อันสำรองในกระเป๋าแทนนะ`
                : `§c${res.name} พังแล้ว!§r ส่งเครื่องมือใหม่ให้หน่อยนะ (ใช้หนังสือสั่งงาน > ส่งของให้ผู้ช่วย ได้จากระยะไกล)`);
        }
    }
    if (spilled || isStorageFull(h))
        handleFullStorage(h);
    t.breaking = undefined;
    setWorking(h, false);
    return p;
}
export function handleFullStorage(h) {
    if (!isStorageFull(h))
        return;
    if (getSettings(h).chest) {
        const home = getHome(h);
        const cs = nearbyContainers(h, h.location, 8);
        if (!cs.length && home && dist(home, h.location) < 12)
            cs.push(...nearbyContainers(h, home, 8));
        if (cs.length) {
            const moved = depositToContainers(h, cs);
            if (moved > 0) {
                notifyOwner(h, "deposit", `กระเป๋าเต็ม เลยเอาของ ${moved} ชิ้นไปเก็บในหีบใกล้ๆ ให้แล้ว`, 20 * 30);
                return;
            }
        }
    }
    notifyOwner(h, "full", "§eกระเป๋าเต็มแล้ว!§r ผมหยุดพักก่อนนะ รับของออกไปได้ที่ หนังสือสั่งงาน > กระเป๋า (หรือวางหีบไว้ใกล้ๆ)", 20 * 60);
}
function ownerNear(h, maxDist) {
    const id = h.getDynamicProperty("h:owner");
    if (typeof id !== "string")
        return undefined;
    for (const p of h.dimension.getPlayers({ location: h.location, maxDistance: maxDist }))
        if (p.id === id)
            return p;
    const nm = h.getDynamicProperty("h:ownerName");
    for (const p of h.dimension.getPlayers({ location: h.location, maxDistance: maxDist }))
        if (p.name === nm)
            return p;
    return undefined;
}
function fail(h, t) {
    const r = rt(h);
    if (r.lastNeed && system.currentTick - r.lastNeed.at < 60) {
        // could not get there for lack of a tool: skip it until that tool arrives
        r.needBl.set(key(t.target), r.lastNeed.kind);
        if (t.kind === "chop" && t.tree)
            for (const l of t.tree)
                r.needBl.set(key(l), r.lastNeed.kind);
        r.lastNeed = undefined;
        endTask(h);
        return;
    }
    if (t.kind === "chop" && t.tree)
        for (const l of t.tree)
            blacklist(h, l, 20 * 90);
    blacklist(h, t.target);
    endTask(h);
}
// ---------------------------------------------------------------- mining
function tickMine(h, t) {
    const dim = h.dimension;
    if (t.breaking) {
        const done = tickBreak(h, t);
        if (done && same(done, t.target)) {
            endTask(h);
            rt(h).lastScan = 0; // look for the next ore (veins) right away
        }
        return;
    }
    const tb = safeBlock(dim, t.target);
    if (!tb || !(t.ids ? t.ids.includes(tb.typeId) : ORES[tb.typeId])) {
        endTask(h);
        rt(h).lastScan = 0;
        return;
    }
    if (system.currentTick - t.started > TASK_TIMEOUT)
        return fail(h, t);
    const eye = eyeOf(h);
    const c = center(t.target);
    const d = dist(eye, c);
    if (d <= REACH) {
        if (canSee(dim, eye, t.target)) {
            if (!beginBreak(h, t, tb))
                fail(h, t);
            return;
        }
        const hit = firstSolidOnRay(dim, eye, t.target);
        if (!hit || same(hit.location, t.target) || dist(center(hit.location), eye) > d + 0.5) {
            if (!beginBreak(h, t, tb))
                fail(h, t);
            return;
        }
        if (canDigThrough(h, hit) && dist(center(hit.location), eye) <= REACH + 0.6) {
            if (!beginBreak(h, t, hit))
                fail(h, t);
            return;
        }
    }
    const loc = h.location;
    const res = steer(h, { x: c.x, y: loc.y, z: c.z }, 0.7);
    if (res.status === "arrived") {
        // Right above/below the ore but still out of reach.
        if (t.target.y < loc.y - 1) {
            const below = safeBlock(dim, { x: Math.floor(loc.x), y: Math.floor(loc.y + 0.5) - 1, z: Math.floor(loc.z) });
            if (below && (same(below.location, t.target) || canDigThrough(h, below)))
                return void (beginBreak(h, t, below) || fail(h, t));
        }
        const hit = firstSolidOnRay(dim, eye, t.target);
        if (hit && (same(hit.location, t.target) || canDigThrough(h, hit)) && dist(center(hit.location), eye) <= REACH + 1.2) {
            return void (beginBreak(h, t, hit) || fail(h, t));
        }
        return fail(h, t);
    }
    if (res.status === "blocked") {
        const obs = res.obstacles ?? [];
        const tgt = obs.find((b) => same(b.location, t.target));
        if (tgt)
            return void (beginBreak(h, t, tgt) || fail(h, t));
        const dig = obs.find((b) => canDigThrough(h, b));
        if (dig)
            return void (beginBreak(h, t, dig) || fail(h, t));
        if (t.fails++ < 6 && hopToward(h, center(t.target)))
            return;
        return fail(h, t);
    }
    if (res.status === "cliff") {
        const hit = firstSolidOnRay(dim, eye, t.target);
        if (hit && same(hit.location, t.target) && d <= REACH + 1.5)
            return void (beginBreak(h, t, hit) || fail(h, t));
        return fail(h, t);
    }
    if (isStuck(h, distXZ(loc, c))) {
        resetProgress(h);
        const hit = firstSolidOnRay(dim, eye, t.target);
        if (hit && (same(hit.location, t.target) || canDigThrough(h, hit)) && dist(center(hit.location), eye) <= REACH + 1.2) {
            return void (beginBreak(h, t, hit) || fail(h, t));
        }
        t.fails++;
        if (t.fails > 6 || !hopToward(h, center(t.target)))
            fail(h, t);
    }
}
// ---------------------------------------------------------------- chopping
function tickChop(h, t) {
    const dim = h.dimension;
    if (t.breaking) {
        tickBreak(h, t);
        return;
    }
    if (system.currentTick - t.started > TASK_TIMEOUT * 2)
        return fail(h, t);
    const loc = h.location;
    if (t.phase === "approach") {
        const c = center(t.target);
        const flat = distXZ(loc, c);
        if (flat <= 1.9 && Math.abs(t.target.y - loc.y) <= 2.5) {
            t.phase = "work";
            stopMoving(h);
            return;
        }
        const res = steer(h, { x: c.x, y: loc.y, z: c.z }, 1.4);
        if (res.status === "arrived") {
            t.phase = "work";
            return;
        }
        if (res.status === "blocked") {
            const obs = res.obstacles ?? [];
            const leaf = obs.find((b) => LEAF_LIKE.has(b.typeId));
            if (leaf)
                return void (beginBreak(h, t, leaf) || fail(h, t));
            const log = obs.find((b) => t.tree?.some((l) => same(l, b.location)));
            if (log) {
                t.phase = "work";
                return;
            }
            if (t.fails++ < 8 && hopToward(h, c))
                return;
            return fail(h, t);
        }
        if (res.status === "cliff") {
            if (t.fails++ < 8 && hopToward(h, c))
                return;
            return fail(h, t);
        }
        if (isStuck(h, flat)) {
            resetProgress(h);
            t.fails++;
            if (flat < 3.5)
                t.phase = "work";
            else if (t.fails > 8 || !hopToward(h, c))
                fail(h, t);
        }
        return;
    }
    // work: chop logs bottom-up
    const eye = eyeOf(h);
    const logs = (t.tree ?? []).filter((p) => {
        const b = safeBlock(dim, p);
        return !!b && LOGS.has(b.typeId);
    });
    t.tree = logs;
    if (!logs.length) {
        afterTree(h, t);
        return;
    }
    logs.sort((a, b) => a.y - b.y || dist(center(a), eye) - dist(center(b), eye));
    const next = logs.find((p) => dist(center(p), eye) <= TREE_REACH);
    if (!next) {
        afterTree(h, t);
        return;
    }
    const nb = safeBlock(dim, next);
    if (nb && !beginBreak(h, t, nb))
        fail(h, t);
}
function afterTree(h, t) {
    const s = getSettings(h);
    if (s.replant && t.treeBase && t.treeLogId) {
        const sap = SAPLING_FOR_LOG[t.treeLogId];
        if (sap) {
            const r = rt(h);
            const until = system.currentTick + 20 * 180;
            for (const p of t.treeBase)
                r.replant.push({ pos: p, sapling: sap, until });
            tryReplant(h);
        }
    }
    endTask(h);
    rt(h).lastScan = system.currentTick - 5;
}
function tryReplant(h) {
    const r = rt(h);
    if (!r.replant.length)
        return;
    const now = system.currentTick;
    r.replant = r.replant.filter((x) => x.until > now);
    const dim = h.dimension;
    const bySap = new Map();
    for (const x of r.replant) {
        const arr = bySap.get(x.sapling) ?? [];
        arr.push(x);
        bySap.set(x.sapling, arr);
    }
    const keep = [];
    for (const [sap, spots] of bySap) {
        const valid = spots.filter((x) => {
            const b = safeBlock(dim, x.pos);
            const below = safeBlock(dim, { x: x.pos.x, y: x.pos.y - 1, z: x.pos.z });
            return !!b && b.isAir && !!below && PLANTABLE_SOIL.has(below.typeId) && dist(x.pos, h.location) < 12;
        });
        const big = sap.includes("dark_oak") || sap.includes("pale_oak");
        const have = countId(h, sap);
        const needAll = big && valid.length >= 4;
        if (!valid.length)
            continue;
        if (have <= 0 || (needAll && have < valid.length)) {
            keep.push(...valid);
            continue;
        }
        const plantCount = needAll ? valid.length : big ? 0 : Math.min(have, valid.length);
        for (let i = 0; i < plantCount; i++) {
            const spot = valid[i];
            try {
                const b = safeBlock(dim, spot.pos);
                if (!b)
                    continue;
                b.setPermutation(BlockPermutation.resolve(sap));
                storageTake(h, sap, 1);
                dim.playSound("use.grass", center(spot.pos), { volume: 0.8 });
            }
            catch {
                /* sapling type not available */
            }
        }
        for (let i = plantCount; i < valid.length; i++)
            keep.push(valid[i]);
    }
    r.replant = keep;
}
/** Picks up dropped items around the helper that match `want`. */
export function pickupItems(h, want, radius = 9) {
    const dim = h.dimension;
    let picked = 0;
    try {
        for (const e of dim.getEntities({ type: "minecraft:item", location: h.location, maxDistance: radius })) {
            let st;
            try {
                st = e.getComponent("minecraft:item")?.itemStack;
            }
            catch {
                st = undefined;
            }
            if (!st || !want(st.typeId))
                continue;
            const left = storageAdd(h, st);
            if (left <= 0) {
                e.remove();
                picked += st.amount;
                report(h, "item", st.typeId, st.amount);
            }
        }
    }
    catch {
        /* ignore */
    }
    if (picked) {
        try {
            dim.playSound("random.pop", h.location, { volume: 0.5, pitch: 1.4 });
        }
        catch {
            /* ignore */
        }
    }
    return picked;
}
function pickupLoot(h) {
    pickupItems(h, isTreeLoot);
}
// ---------------------------------------------------------------- exploring
/** Walks to a new spot near "home" to look for more ores / trees. */
const ROAM_MSG = {
    mine: "แถวนี้ไม่เหลือแร่ที่ผมมองเห็นแล้ว ลองพาผมไปที่ถ้ำหรือใต้ดิน แล้วสั่งขุดแร่ใหม่ หรือสั่ง 'สร้างเหมือง' ก็ได้นะ",
    chop: "แถวนี้ไม่เหลือต้นไม้ธรรมชาติแล้ว ลองพาผมไปที่ป่า แล้วสั่งตัดไม้ใหม่นะ",
    gather: "แถวนี้ไม่เหลือบล็อกที่ต้องการแล้ว ลองพาผมไปที่อื่นแล้วสั่งใหม่นะ",
    food: "แถวนี้ไม่มีสัตว์หรือพืชผักให้หาอาหารแล้ว (ผมเหลือสัตว์ไว้อย่างละ 2 ตัวให้มันออกลูก) ลองพาผมไปที่อื่นนะ",
};
/** Walks to a new spot near "home" to look for more work. */
export function tickRoam(h, mode) {
    const r = rt(h);
    const now = system.currentTick;
    const home = getHome(h) ?? h.location;
    if (!r.roam || now > r.roamUntil) {
        if (r.roam)
            r.roamFails++;
        if (r.roamFails >= 6)
            notifyOwner(h, "roam:" + mode, ROAM_MSG[mode], 20 * 120);
        // pick a waypoint 10-18 blocks away that stays within HOME_RADIUS of home
        for (let i = 0; i < 8; i++) {
            const a = Math.random() * Math.PI * 2;
            const len = 10 + Math.random() * 8;
            const p = { x: h.location.x + Math.cos(a) * len, y: h.location.y, z: h.location.z + Math.sin(a) * len };
            if (distXZ(p, home) <= HOME_RADIUS || i === 7) {
                if (distXZ(p, home) > HOME_RADIUS) {
                    // too far: head back toward home
                    p.x = home.x + (Math.random() - 0.5) * 16;
                    p.z = home.z + (Math.random() - 0.5) * 16;
                }
                r.roam = p;
                break;
            }
        }
        r.roamUntil = now + 20 * 15;
        resetProgress(h);
    }
    const goal = r.roam;
    const res = steer(h, goal, 1.2);
    if (res.status === "arrived") {
        r.roam = undefined;
        r.lastScan = 0;
        return;
    }
    if (res.status === "blocked") {
        if (hopToward(h, goal))
            return;
        const dig = (res.obstacles ?? []).find((b) => (mode === "mine" || mode === "gather") && canDigThrough(h, b, true));
        if (dig) {
            const t = {
                kind: "mine", target: dig.location, targetId: dig.typeId, progress: 0, need: 0,
                started: now, phase: "approach", fails: 0,
            };
            if (beginBreak(h, t, dig)) {
                r.task = t;
                return;
            }
        }
        r.roam = undefined;
        return;
    }
    if (res.status === "cliff") {
        if (!hopToward(h, goal))
            r.roam = undefined;
        return;
    }
    if (isStuck(h, distXZ(h.location, goal))) {
        if (!hopToward(h, goal))
            r.roam = undefined;
    }
}
// ---------------------------------------------------------------- entry
/**
 * Autonomous work: the helper does NOT follow the owner. It looks for ores / trees
 * it can see within 100 blocks, walks there and works, and explores around its work spot.
 */
export function tickWork(h, mode, _owner) {
    const r = rt(h);
    const now = system.currentTick;
    setAi(h, "work");
    if (!getHome(h))
        setHome(h, floorV(h.location));
    if (r.task) {
        if (getMode(h) !== mode) {
            endTask(h);
            return;
        }
        // a roaming dig step finishes as soon as the block is gone
        if (r.task.kind === "mine" && mode === "chop" && !r.task.ids) {
            tickBreak(h, r.task);
            if (!r.task.breaking)
                endTask(h);
            return;
        }
        if (mode === "mine" || mode === "gather")
            tickMine(h, r.task);
        else
            tickChop(h, r.task);
        if (!r.task)
            r.roamFails = 0;
        return;
    }
    setWorking(h, false);
    if (isStorageFull(h)) {
        handleFullStorage(h);
        if (isStorageFull(h)) {
            stopMoving(h);
            return; // waits until the owner empties the bag
        }
    }
    if (mode === "chop" && now - r.lastPickup >= 20) {
        r.lastPickup = now;
        if (getSettings(h).pickup)
            pickupLoot(h);
        tryReplant(h);
    }
    if (mode === "gather" && now - r.lastScan >= 20) {
        r.lastScan = now;
        const ids = getGatherIds(h);
        if (!ids.length) {
            setModeProp(h, "stay");
            return;
        }
        const kind = toolKindFor(ids[0]);
        if (kind !== "hand" && !equipBest(h, kind)) {
            stopMoving(h);
            needTool(h, kind);
            return;
        }
        const t = findGather(h);
        if (t) {
            r.task = t;
            r.roam = undefined;
            resetProgress(h);
            return;
        }
        r.lastScan = now + 40; // nothing in sight: wait a bit longer before the next big scan
    }
    else if (mode !== "gather" && now - r.lastScan >= 20) {
        r.lastScan = now;
        const kind = mode === "mine" ? "pickaxe" : "axe";
        const tool = equipBest(h, kind);
        if (!tool) {
            stopMoving(h);
            notifyOwner(h, "notool:" + kind, mode === "mine"
                ? "§eยังไม่มีอีเต้อ (Pickaxe)!§r ส่งอีเต้อให้ผมหน่อย (กดที่ตัวผม หรือใช้หนังสือสั่งงาน > ส่งของให้ผู้ช่วย)"
                : "§eยังไม่มีขวาน (Axe)!§r ส่งขวานให้ผมหน่อย (กดที่ตัวผม หรือใช้หนังสือสั่งงาน > ส่งของให้ผู้ช่วย)", 20 * 30);
            return;
        }
        const t = mode === "mine" ? findOre(h) : findTree(h);
        if (t) {
            r.task = t;
            r.roam = undefined;
            resetProgress(h);
            return;
        }
        r.lastScan = now + 40; // nothing in sight: wait a bit longer before the next big scan
    }
    if (!r.task && (toolInfo(getTool(h)) || mode === "gather"))
        tickRoam(h, mode);
}
export function hasStorageRoom(h) {
    return !!getContainer(h) && !isStorageFull(h);
}
